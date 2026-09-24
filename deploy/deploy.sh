#!/usr/bin/env bash
# Deploys a built image to staging or production.
#   bash deploy/deploy.sh staging 3145069
#   bash deploy/deploy.sh prod 3145069
set -euo pipefail

env="$1"
tag="$2"
case "$env" in staging|prod) ;; *) echo "Environment must be staging or prod"; exit 1 ;; esac

home="$HOME/credcloud/$env"
dc() { APP_TAG="$tag" docker compose -f deploy/compose.yml -p "credcloud-$env" --env-file "$home/.env" "$@"; }

docker image inspect "credcloud:$tag" >/dev/null || { echo "No image credcloud:$tag. Was it built?"; exit 1; }

previous=$(cat "$home/deployed" 2>/dev/null || true)
echo "$env: ${previous:-nothing} -> $tag"

# Back up production before anything changes. Staging data is disposable.
if [ "$env" = prod ] && [ -n "$(dc ps -q db)" ]; then
  mkdir -p "$HOME/credcloud/backups"
  backup="$HOME/credcloud/backups/prod-$(date +%Y%m%d-%H%M%S)-before-$tag.dump"
  dc exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$backup"
  echo "Backed up production to $backup"
fi

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
  dc up -d --wait --wait-timeout 180 || echo "Rollback failed too. Restore the backup."
fi
exit 1