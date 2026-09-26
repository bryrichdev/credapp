#!/bin/bash
# Replaces production's database with a backup. For a real emergency; restore-drill.sh is the
# safe way to practise.
#
#   bash deploy/restore.sh ~/Library/Mobile\ Documents/com~apple~CloudDocs/CredCloud\ Backups/credcloud-prod-20261001-023000.dump.age
#
# What it does:
#   1. saves today's database to ~/credcloud/backups first, so the restore itself can be undone
#   2. stops the app, replaces the database with the backup, clears sign-in sessions
#   3. starts the app and waits for it to report healthy
#
# If the backup came from a different .env (a new laptop, a lost key), decrypt the matching
# .env.age first and put its CREDAPP_SSN_KEY in ~/credcloud/prod/.env, or SSNs, CAQH
# passwords and documents won't open:
#   age -d -i ~/credcloud/backup-key.txt credcloud-prod-<stamp>.env.age
set -euo pipefail

file="${1:?Give the backup to restore (a .dump.age file)}"
key="${BACKUP_KEY:-$HOME/credcloud/backup-key.txt}"
[ -f "$file" ] || { echo "No such file: $file"; exit 1; }
[ -f "$key" ] || { echo "No key at $key. Restore it from your password manager."; exit 1; }

container() {
  docker ps -aq --filter label=com.docker.compose.project=credcloud-prod \
                --filter label=com.docker.compose.service="$1" | head -1
}
db=$(container db)
app=$(container app)
[ -n "$db" ] && [ -n "$app" ] || { echo "Production isn't set up here (no credcloud-prod containers)."; exit 1; }
docker start "$db" >/dev/null

echo "This replaces ALL of production's data with $(basename "$file")."
printf 'Type "restore production" to go ahead: '
read -r answer
[ "$answer" = "restore production" ] || { echo "Nothing changed."; exit 1; }

# The backup's own contents are checked before anything is dropped.
age -d -i "$key" "$file" | docker exec -i "$db" pg_restore --list >/dev/null \
  || { echo "That backup can't be read. Nothing changed."; exit 1; }

mkdir -p "$HOME/credcloud/backups"
safety="$HOME/credcloud/backups/prod-$(date +%Y%m%d-%H%M%S)-before-restore.dump"
docker exec "$db" sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$safety"
echo "Saved the current database to $safety"

docker stop "$app" >/dev/null
docker exec "$db" sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
age -d -i "$key" "$file" \
  | docker exec -i "$db" sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error'
# Sessions from the backup's time shouldn't come back to life.
docker exec "$db" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -qc "SET client_min_messages = warning; TRUNCATE spring_session CASCADE"'
echo "Restored. Starting the app..."

docker start "$app" >/dev/null
for _ in $(seq 1 36); do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$app")
  if [ "$status" = healthy ]; then
    echo "Production is back up on $(basename "$file")."
    exit 0
  fi
  sleep 5
done
echo "The app didn't report healthy within 3 minutes. Check: docker logs $app"
exit 1
