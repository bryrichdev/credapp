#!/bin/bash
# Replaces production's database with a backup from S3. For a real emergency; the Sunday drill
# is the safe way to practise.
#
#   aws ssm start-session --target <instance>      (terraform output shell)
#   sudo /opt/credcloud/bin/restore.sh s3://credcloud-backups-<account>/daily/credcloud-prod-20261001-023000.dump.age
#
# It asks you to paste the backup key (AGE-SECRET-KEY-1...) from your password manager. The key
# sits in memory (/dev/shm) only while this runs.
#   1. checks the backup opens, before anything changes
#   2. backs up today's database to pre-deploy/, so the restore itself can be undone
#   3. stops the app, replaces the database, clears sign-in sessions, starts the app
#
# A backup from a different SSN key won't open SSNs, CAQH passwords or documents. The
# matching .env.age beside the dump has the key; put it back with put-secrets.sh, then deploy.
set -euo pipefail
# shellcheck source=common.sh
. "$(dirname "$0")/common.sh"

src="${1:?Give the backup to restore: s3://.../credcloud-prod-<stamp>.dump.age}"
[ -n "$(dc ps -aq db)" ] && [ -n "$(dc ps -aq app)" ] || { echo "Production isn't set up here."; exit 1; }
aws s3 ls "$src" >/dev/null || { echo "No such backup: $src"; exit 1; }

key=$(mktemp /dev/shm/credcloud-key.XXXXXX)
trap 'shred -u "$key" 2>/dev/null || rm -f "$key"' EXIT
printf 'Paste the backup key (it will not echo), then press Enter: '
read -rs secret
echo
printf '%s\n' "$secret" > "$key"
unset secret

dc start db >/dev/null
aws s3 cp "$src" - --only-show-errors | age -d -i "$key" | dc exec -T db pg_restore --list >/dev/null \
  || { echo "That backup can't be read with this key. Nothing changed."; exit 1; }

echo "This replaces ALL of production's data with $(basename "$src")."
printf 'Type "restore production" to go ahead: '
read -r answer
[ "$answer" = "restore production" ] || { echo "Nothing changed."; exit 1; }

"$root/bin/backup.sh" --pre-deploy restore
dc stop app >/dev/null
aws s3 cp "$src" - --only-show-errors | age -d -i "$key" | replace_db
echo "Restored. Starting the app..."
if dc up -d --wait --wait-timeout 180; then
  echo "Production is back up on $(basename "$src")."
  exit 0
fi
echo "The app didn't report healthy within 3 minutes. Check: sudo docker logs credcloud-prod-app-1"
exit 1
