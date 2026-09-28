#!/bin/bash
# Backs up production to S3. systemd runs it at 2:30 every night (credcloud-backup.timer).
#   sudo /opt/credcloud/bin/backup.sh                     nightly; a restore drill on Sundays
#   sudo /opt/credcloud/bin/backup.sh --drill             back up and prove the dump restores
#   sudo /opt/credcloud/bin/backup.sh --pre-deploy <tag>  deploy.sh runs this first
#   journalctl -u credcloud-backup                        the nightly log
#
# Each dump is encrypted with age to the public key in /credcloud/prod/backup/recipient. The
# private key stays in your password manager, so neither this instance nor anyone who reads
# the bucket can open a backup. That's also why the drill restores the dump before it's
# encrypted: it proves pg_restore rebuilds the database from exactly these bytes.
#
# In the bucket, where lifecycle rules expire each prefix:
#   daily/credcloud-prod-YYYYMMDD-HHMMSS.dump.age  and .env.age (the SSN key travels with it)
#   monthly/...                                    a copy of each month's first backup
#   pre-deploy/credcloud-prod-YYYYMMDD-HHMMSS-before-<tag>.dump.age
set -Eeuo pipefail
# shellcheck source=common.sh
. "$(dirname "$0")/common.sh"

mode=daily
tag=""
drill=false
case "${1:-}" in
  --drill) drill=true ;;
  --pre-deploy) mode=pre-deploy; tag="${2:?Give the tag being deployed}" ;;
  "") if [ "$(date +%u)" = 7 ]; then drill=true; fi ;;
  *) echo "Unknown option $1"; exit 1 ;;
esac

param() { aws ssm get-parameter --name "$1" --query Parameter.Value --output text 2>/dev/null || true; }
recipient=$(param /credcloud/prod/backup/recipient)
ping_url=""
if [ "$mode" = daily ]; then
  ping_url=$(param /credcloud/prod/backup/ping-url)
fi

drill_name=credcloud-restore-drill
work=$(mktemp -d /var/tmp/credcloud-backup.XXXXXX)
trap 'rm -rf "$work"; docker rm -f "$drill_name" >/dev/null 2>&1 || true' EXIT

fail() {
  log "FAILED: $*"
  if [ -n "$ping_url" ]; then
    curl -fsS -m 15 --retry 3 "$ping_url/fail" >/dev/null || true
  fi
  exit 1
}
trap 'fail "the backup stopped at line $LINENO"' ERR

# Restores the dump into a throwaway Postgres and checks the data is there. Only tables every
# schema version has, so an older release's backup checks out too.
drill() {
  docker run -d --name "$drill_name" -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=drill postgres:17 >/dev/null
  # Over TCP: the server only listens there once its first-start setup is finished.
  for _ in $(seq 1 60); do
    if docker exec "$drill_name" pg_isready -q -h 127.0.0.1 -U postgres -d drill; then
      break
    fi
    sleep 1
  done
  docker exec -i "$drill_name" pg_restore -U postgres -d drill --no-owner --no-privileges --exit-on-error < "$1"
  q() { docker exec "$drill_name" psql -U postgres -d drill -Atc "$1"; }
  local version counts
  version=$(q "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
  counts=$(q "SELECT 'user groups ' || (SELECT count(*) FROM user_groups) || ', users ' || (SELECT count(*) FROM users)
               || ', providers ' || (SELECT count(*) FROM providers) || ', groups ' || (SELECT count(*) FROM groups)")
  [ "$(q "SELECT count(*) FROM users")" -ge 1 ] || fail "the restored database has no accounts ($counts)"
  docker rm -f "$drill_name" >/dev/null
  log "drill PASS: schema V$version, $counts"
}

upload() {
  age -r "$recipient" < "$1" | aws s3 cp - "s3://$BACKUP_BUCKET/$2" --only-show-errors
}

[ -n "$recipient" ] || fail "no /credcloud/prod/backup/recipient parameter"
[ -n "$(dc ps -q db)" ] || fail "production's database isn't running"

name="credcloud-prod-$(date +%Y%m%d-%H%M%S)"
if [ "$mode" = pre-deploy ]; then
  name="$name-before-$tag"
fi
dump="$work/$name.dump"
dump_db > "$dump"
size=$(stat -c %s "$dump")
[ "$size" -gt 1000 ] || fail "the dump came out empty"

if $drill; then
  drill "$dump"
fi

upload "$dump" "$mode/$name.dump.age"
upload "$env_file" "$mode/$name.env.age"
log "Backed up production to s3://$BACKUP_BUCKET/$mode/$name.dump.age ($((size / 1024)) KB)"

# The month's first nightly backup is also kept as the monthly one.
if [ "$mode" = daily ] && ! aws s3 ls "s3://$BACKUP_BUCKET/monthly/credcloud-prod-$(date +%Y%m)" >/dev/null; then
  for ext in dump.age env.age; do
    aws s3 cp "s3://$BACKUP_BUCKET/daily/$name.$ext" "s3://$BACKUP_BUCKET/monthly/$name.$ext" --only-show-errors
  done
  log "Kept as this month's backup"
fi

if [ -n "$ping_url" ]; then
  curl -fsS -m 15 --retry 3 "$ping_url" >/dev/null || log "Couldn't reach the ping URL"
fi
log "Done"
