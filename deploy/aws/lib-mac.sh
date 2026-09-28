# Shared by the scripts that run on the Mac and talk to production on AWS. Sourced, not run.
# Written for the Mac's bash 3.2. They use whatever AWS sign-in the shell has: the Promote
# workflow's OIDC role, or your own admin sign-in (aws sso login, a profile).
# shellcheck shell=bash

# The runner and launchd start with a bare PATH.
export PATH="$HOME/.homebrew/bin:/opt/homebrew/bin:/usr/local/bin:$HOME/.docker/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
export AWS_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-us-east-1}}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

command -v aws >/dev/null || { echo "Install the AWS CLI on this Mac: brew install awscli"; exit 1; }

param() {
  local value
  value=$(aws ssm get-parameters --names "$1" --query 'Parameters[0].Value' --output text)
  [ -n "$value" ] && [ "$value" != None ] || { echo "No $1 parameter. Has aws/ been applied in $AWS_REGION?" >&2; return 1; }
  echo "$value"
}

check_tag() {
  [[ "$1" =~ ^[0-9a-f]{7,40}$ ]] || { echo "\"$1\" isn't a commit tag like 3145069"; exit 1; }
}

# Pushes credcloud:<tag>, which the Mac built for staging, to ECR. Skips it if already there.
push_image() {
  local tag="$1" repo arch
  repo=$(param /credcloud/deploy/ecr-repository)
  docker image inspect "credcloud:$tag" >/dev/null 2>&1 || { echo "No image credcloud:$tag on this Mac. Was it built?"; return 1; }
  arch=$(docker image inspect -f '{{.Architecture}}' "credcloud:$tag")
  [ "$arch" = arm64 ] || { echo "credcloud:$tag is built for $arch; the instance needs arm64"; return 1; }
  if aws ecr describe-images --repository-name credcloud --image-ids imageTag="$tag" >/dev/null 2>&1; then
    echo "$repo:$tag is already in ECR"
    return 0
  fi
  aws ecr get-login-password | docker login --username AWS --password-stdin "${repo%%/*}" >/dev/null
  docker tag "credcloud:$tag" "$repo:$tag"
  docker push -q "$repo:$tag"
  docker logout "${repo%%/*}" >/dev/null 2>&1 || true
  echo "Pushed $repo:$tag"
}

json_str() {
  local s="${1//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '"%s"' "$s"
}

# Runs shell commands on the instance as root through SSM Run Command and waits. Prints the
# instance's output; returns non-zero if the commands failed.
#   on_instance "Comment" "command 1" "command 2" ...
on_instance() {
  local comment="$1" instance params cmd_id status waited=0 first=true c
  shift
  instance=$(param /credcloud/deploy/instance-id)
  params=$(mktemp)
  {
    printf '{"commands":['
    for c in "$@"; do
      $first || printf ','
      first=false
      json_str "$c"
    done
    printf ']}'
  } > "$params"
  cmd_id=$(aws ssm send-command --instance-ids "$instance" --document-name AWS-RunShellScript \
    --comment "$comment" --timeout-seconds 600 --parameters "file://$params" \
    --query Command.CommandId --output text)
  rm -f "$params"

  while :; do
    sleep 5
    waited=$((waited + 5))
    status=$(aws ssm get-command-invocation --command-id "$cmd_id" --instance-id "$instance" \
      --query Status --output text 2>/dev/null || echo Pending)
    case "$status" in
      Pending|InProgress|Delayed)
        [ "$waited" -lt 900 ] || { echo "Gave up waiting after 15 minutes (command $cmd_id)"; return 1; }
        ;;
      *) break ;;
    esac
  done
  aws ssm get-command-invocation --command-id "$cmd_id" --instance-id "$instance" \
    --query StandardOutputContent --output text
  if [ "$status" != Success ]; then
    aws ssm get-command-invocation --command-id "$cmd_id" --instance-id "$instance" \
      --query StandardErrorContent --output text >&2
    echo "The instance reported $status (command $cmd_id)" >&2
    return 1
  fi
}

# Ships this checkout's deploy/ directory to the instance and runs deploy.sh there.
#   run_deploy <tag> [deploy.sh options]
run_deploy() {
  local tag="$1" bundle dir
  shift
  bundle=$(COPYFILE_DISABLE=1 tar --no-mac-metadata --exclude .DS_Store -czf - -C "$repo_root/deploy" compose.yml aws | base64 | tr -d '\n')
  dir="/opt/credcloud/releases/$tag"
  on_instance "Deploy $tag" \
    "set -e" \
    "rm -rf $dir && mkdir -p $dir" \
    "echo $bundle | base64 -d | tar -xzf - --warning=no-unknown-keyword -C $dir" \
    "bash $dir/aws/deploy.sh $tag $*"
}

instance_online() {
  local instance status
  instance=$(param /credcloud/deploy/instance-id)
  status=$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$instance" \
    --query 'InstanceInformationList[0].PingStatus' --output text)
  [ "$status" = Online ] || { echo "The instance $instance isn't reachable through SSM ($status). Is it running?"; return 1; }
}
