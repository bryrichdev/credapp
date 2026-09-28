#!/bin/bash
# Deploys production on the EC2 instance. promote.sh ships the deploy/ directory of the commit
# being deployed to /opt/credcloud/releases/<tag>/ through SSM Run Command and runs:
#   bash /opt/credcloud/releases/<tag>/aws/deploy.sh <tag>
# The one-time move from the Mac also gives a database to start from:
#   ... deploy.sh <tag> --restore s3://<bucket>/migration/<file>.dump.age --identity /root/migration.key
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=common.sh
. "$here/common.sh"

tag="${1:?Give the image tag to deploy}"
shift
restore=""
identity=""
while [ $# -gt 0 ]; do
  case "$1" in
    --restore) restore="$2"; shift 2 ;;
    --identity) identity="$2"; shift 2 ;;
    *) log "Unknown option $1"; exit 1 ;;
  esac
done

# This release's scripts and compose file become the installed ones.
install -m 700 "$here/common.sh" "$here/backup.sh" "$here/restore.sh" "$root/bin/"
install -m 600 "$here/../compose.yml" "$root/compose.yml"
write_env
ecr_login
docker pull -q "$REPOSITORY:$tag"

previous=$(cat "$root/prod/deployed" 2>/dev/null || true)
log "production: ${previous:-nothing} -> $tag"

if [ -n "$restore" ]; then
  [ -z "$previous" ] || { log "Production already has a database here. Use restore.sh to replace it."; exit 1; }
  [ -f "$identity" ] || { log "No key at $identity"; exit 1; }
  APP_TAG="$tag" dc up -d --wait db
  log "Restoring $restore"
  aws s3 cp "$restore" - --only-show-errors | age -d -i "$identity" | APP_TAG="$tag" replace_db
  shred -u "$identity"
  log "Restored"
elif [ -n "$previous" ] && [ -n "$(dc ps -q db)" ]; then
  "$root/bin/backup.sh" --pre-deploy "$tag"
fi

if APP_TAG="$tag" dc up -d --wait --wait-timeout 180; then
  echo "$tag" > "$root/prod/deployed"
  # Images unused for a week go; rolling back further pulls from ECR again.
  docker image prune -af --filter until=168h >/dev/null || true
  # shellcheck disable=SC2012
  ls -1t "$root/releases" | tail -n +6 | while read -r old; do rm -rf "${root:?}/releases/$old"; done
  log "production is running $tag"
  exit 0
fi

log "$tag didn't come up healthy. Last app log lines:"
APP_TAG="$tag" dc logs app --tail 80 || true
if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
  log "Rolling back to $previous"
  APP_TAG="$previous" dc up -d --wait --wait-timeout 180 \
    || log "Rollback failed too. Restore the pre-deploy backup with restore.sh."
fi
exit 1
