#!/bin/bash
# Backs up production: dumps the database, encrypts it, writes it to iCloud Drive and prunes
# old copies. launchd runs it every night at 2:30 (see install-backups.sh); on Sundays it also
# runs a restore drill, so a backup that can't be restored doesn't go unnoticed.
#
#   ~/credcloud/bin/backup.sh           back up now
#   ~/credcloud/bin/backup.sh --drill   back up, then prove the new backup restores
#
# Backups are encrypted to ~/credcloud/backup-key.pub. Only backup-key.txt can read them, so
# keep a copy of that file in your password manager: without it the backups are useless.
#
# Optional settings go in ~/credcloud/backup.conf:
#   BACKUP_DIR=...        where backups go (default: "CredCloud Backups" in iCloud Drive)
#   BACKUP_PING_URL=...   opened after each good backup, e.g. a healthchecks.io check that
#                         emails you when a night goes by without one
set -euo pipefail

# launchd starts jobs with a bare PATH.
export PATH="/opt/homebrew/bin:/usr/local/bin:$HOME/.docker/bin:/Applications/Docker.app/Contents/Resources/bin:/usr/bin:/bin:/usr/sbin:/sbin"

root="$HOME/credcloud"
if [ -f "$root/backup.conf" ]; then
  . "$root/backup.conf"
fi
dest="${BACKUP_DIR:-$HOME/Library/Mobile Documents/com~apple~CloudDocs/CredCloud Backups}"
recipient="$root/backup-key.pub"
keep_days=30      # every backup from the last 30 days
keep_months=12    # and the first backup of each month for a year

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

fail() {
  log "FAILED: $*"
  osascript -e "display notification \"$*\" with title \"CredCloud backup failed\" sound name \"Basso\"" \
    >/dev/null 2>&1 || true
  exit 1
}

# Keeps the last $keep_days days of backups plus the first of each month for $keep_months
# months, and deletes the rest. Backups are named credcloud-prod-YYYYMMDD-HHMMSS.*, so the
# names sort by date.
prune() {
  local dir="$1" recent monthly stamp day month last_month=""
  recent=$(date -v-"${keep_days}"d +%Y%m%d 2>/dev/null || date -d "$keep_days days ago" +%Y%m%d)
  monthly=$(date -v-"${keep_months}"m +%Y%m%d 2>/dev/null || date -d "$keep_months months ago" +%Y%m%d)
  for stamp in $(ls -1 "$dir" | sed -n 's/^credcloud-prod-\([0-9]\{8\}-[0-9]\{6\}\)\.dump\.age$/\1/p' | sort); do
    day=${stamp%-*}
    month=${day:0:6}
    if [ "$month" != "$last_month" ]; then
      last_month=$month
      if [[ ! "$day" < "$monthly" ]]; then
        continue # the month's first backup
      fi
    fi
    if [[ ! "$day" < "$recent" ]]; then
      continue
    fi
    rm -f "$dir/credcloud-prod-$stamp.dump.age" "$dir/credcloud-prod-$stamp.env.age"
    log "Pruned $stamp"
  done
}

main() {
  local drill=false
  if [ "${1:-}" = "--drill" ] || [ "$(date +%u)" = 7 ]; then
    drill=true
  fi
  trap 'fail "the backup stopped at line $LINENO; see ~/credcloud/logs/backup.log"' ERR

  command -v age >/dev/null || fail "age isn't installed (brew install age)"
  [ -f "$recipient" ] || fail "no backup key; run deploy/install-backups.sh"
  docker info >/dev/null 2>&1 || fail "Docker isn't running, so production couldn't be backed up"
  local db
  db=$(docker ps -q --filter label=com.docker.compose.project=credcloud-prod \
                    --filter label=com.docker.compose.service=db)
  [ -n "$db" ] || fail "production's database isn't running"

  mkdir -p "$dest" || fail "can't write to $dest"
  local name="credcloud-prod-$(date +%Y%m%d-%H%M%S)"
  local out="$dest/$name.dump.age"

  # Written under a temporary name and renamed once complete, so a half-written backup
  # never looks like a good one.
  docker exec "$db" sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' | age -R "$recipient" > "$out.partial"
  local size
  size=$(wc -c < "$out.partial" | tr -d ' ')
  [ "$size" -gt 1000 ] || { rm -f "$out.partial"; fail "the dump came out empty"; }
  mv "$out.partial" "$out"

  # The .env holds the SSN key (which also encrypts documents) and the database password.
  # A restored database is unreadable without that key, so it travels with the backup.
  if [ -f "$root/prod/.env" ]; then
    age -R "$recipient" < "$root/prod/.env" > "$dest/$name.env.age"
  fi
  log "Backed up production to $out ($((size / 1024)) KB)"

  prune "$dest"
  # The unencrypted dumps deploy.sh takes before each production deploy.
  if [ -d "$root/backups" ]; then
    find "$root/backups" -name 'prod-*.dump' -mtime +"$keep_days" -print -delete | sed 's/^/Pruned /'
  fi

  if $drill; then
    "$(dirname "$0")/restore-drill.sh" "$out" || fail "the restore drill failed; the new backup may not restore"
  fi

  if [ -n "${BACKUP_PING_URL:-}" ]; then
    curl -fsS -m 15 --retry 3 "$BACKUP_PING_URL" >/dev/null || log "Couldn't reach BACKUP_PING_URL"
  fi
  log "Done"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
