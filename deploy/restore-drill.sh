#!/bin/bash
# Proves a backup restores: decrypts it into a throwaway Postgres, checks the data is there,
# then throws the copy away. Production isn't touched. backup.sh runs this every Sunday.
#
#   ~/credcloud/bin/restore-drill.sh                   the newest backup
#   ~/credcloud/bin/restore-drill.sh <file.dump.age>   a particular one
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:$HOME/.docker/bin:/Applications/Docker.app/Contents/Resources/bin:/usr/bin:/bin:/usr/sbin:/sbin"

root="$HOME/credcloud"
if [ -f "$root/backup.conf" ]; then
  . "$root/backup.conf"
fi
dest="${BACKUP_DIR:-$HOME/Library/Mobile Documents/com~apple~CloudDocs/CredCloud Backups}"
key="${BACKUP_KEY:-$root/backup-key.txt}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') drill: $*"; }

file="${1:-}"
if [ -z "$file" ]; then
  file=$(ls -1 "$dest"/credcloud-prod-*.dump.age 2>/dev/null | tail -1 || true)
fi
[ -n "$file" ] && [ -f "$file" ] || { log "no backup found in $dest"; exit 1; }
[ -f "$key" ] || { log "no key at $key; restore it from your password manager"; exit 1; }

name="credcloud-restore-drill-$$"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$name" -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=drill postgres:17 >/dev/null

# Over TCP: the server only listens there once its first-start setup is finished.
for _ in $(seq 1 60); do
  if docker exec "$name" pg_isready -q -h 127.0.0.1 -U postgres -d drill; then
    break
  fi
  sleep 1
done

log "restoring $(basename "$file")"
age -d -i "$key" "$file" \
  | docker exec -i "$name" pg_restore -U postgres -d drill --no-owner --no-privileges --exit-on-error

q() { docker exec "$name" psql -U postgres -d drill -Atc "$1"; }
version=$(q "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
# Only tables every schema version has, so a backup of an older release checks out too.
counts=$(q "SELECT 'user groups ' || (SELECT count(*) FROM user_groups) || ', users ' || (SELECT count(*) FROM users)
             || ', providers ' || (SELECT count(*) FROM providers) || ', groups ' || (SELECT count(*) FROM groups)")
users=$(q "SELECT count(*) FROM users")

if [ "$users" -lt 1 ]; then
  log "FAILED: the restored database has no accounts ($counts)"
  exit 1
fi
log "PASS: schema V$version, $counts"
