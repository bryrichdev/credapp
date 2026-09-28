#!/usr/bin/env bash
# Deploys a built image to staging on this Mac. Production is on EC2: deploy/aws/promote.sh.
#   bash deploy/deploy.sh staging 3145069
set -euo pipefail

env="$1"
tag="$2"
case "$env" in
  staging) ;;
  prod) echo "Production runs on EC2. Use the Promote workflow, or bash deploy/aws/promote.sh $tag"; exit 1 ;;
  *) echo "Environment must be staging"; exit 1 ;;
esac

home="$HOME/credcloud/$env"
dc() { APP_TAG="$tag" docker compose -f deploy/compose.yml -p "credcloud-$env" --env-file "$home/.env" "$@"; }

docker image inspect "credcloud:$tag" >/dev/null || { echo "No image credcloud:$tag. Was it built?"; exit 1; }

previous=$(cat "$home/deployed" 2>/dev/null || true)
echo "$env: ${previous:-nothing} -> $tag"

if dc up -d --wait --wait-timeout 180; then
  echo "$tag" > "$home/deployed"
  echo "$env is running $tag"
  exit 0
fi

echo "$tag didn't come up healthy. Last app log lines:"
dc logs app --tail 80 || true
if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
  echo "Rolling back to $previous"
  tag="$previous"
  dc up -d --wait --wait-timeout 180 || echo "Rollback failed too. Staging data is disposable: remove the volume and deploy again."
fi
exit 1