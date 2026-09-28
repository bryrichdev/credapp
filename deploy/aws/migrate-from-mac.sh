#!/bin/bash
# Moves production from this Mac to EC2. Run it once, signed in to AWS as an admin, after
# terraform apply in aws/ and deploy/aws/put-secrets.sh:
#   bash deploy/aws/migrate-from-mac.sh "$(cat ~/credcloud/prod/deployed)"
#
# Production is down for a few minutes in the middle:
#   1. checks the instance is online and has the same SSN key as this Mac, then pushes the image
#   2. has the instance make a one-time age key and send back only its public half
#   3. stops production's app and tunnel here
#   4. dumps the database, encrypts it to the one-time key and uploads it to S3
#   5. has the instance restore it and start production; the tunnel now ends at EC2
#   6. stops the database here and turns off this Mac's nightly production backup
# If step 5 fails, production starts again on this Mac and nothing else changes.
# Staging stays on this Mac.
set -euo pipefail
# shellcheck source=lib-mac.sh
. "$(dirname "$0")/lib-mac.sh"

tag="${1:?Give the image tag to run on EC2, usually: cat ~/credcloud/prod/deployed}"
check_tag "$tag"
home="$HOME/credcloud/prod"
mac_dc() {
  APP_TAG="$(cat "$home/deployed")" docker compose -f "$repo_root/deploy/compose.yml" \
    -p credcloud-prod --env-file "$home/.env" "$@"
}

[ -n "$(mac_dc ps -q db)" ] || { echo "Production's database isn't running on this Mac."; exit 1; }
instance_online

# SSNs, CAQH passwords and documents are encrypted with this key. A mismatch would move data
# nobody can open.
mac_key=$(sed -n 's/^CREDAPP_SSN_KEY=//p' "$home/.env" | tr -d "\"'")
aws_key=$(aws ssm get-parameter --name /credcloud/prod/env/CREDAPP_SSN_KEY --with-decryption \
  --query Parameter.Value --output text 2>/dev/null || true)
[ -n "$mac_key" ] && [ "$mac_key" = "$aws_key" ] \
  || { echo "The SSN key in SSM doesn't match $home/.env. Run deploy/aws/put-secrets.sh first."; exit 1; }

push_image "$tag"

echo "Asking the instance for a one-time key..."
recipient=$(on_instance "One-time migration key" \
  "set -e" \
  "umask 077" \
  "rm -f /root/migration.key" \
  "age-keygen -o /root/migration.key 2>/dev/null" \
  "age-keygen -y /root/migration.key" | grep '^age1' | tail -1)
[ -n "$recipient" ] || { echo "The instance didn't return a key."; exit 1; }

echo
echo "This takes production down on this Mac and brings it up on EC2 with the same data."
printf 'Type "move production" to go ahead: '
read -r answer
[ "$answer" = "move production" ] || { echo "Nothing changed."; exit 1; }

bucket=$(param /credcloud/deploy/backup-bucket)
object="s3://$bucket/migration/credcloud-prod-$(date +%Y%m%d-%H%M%S).dump.age"

mac_dc stop app cloudflared
echo "Production is stopped here. Uploading the database..."
if ! mac_dc exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' \
    | age -r "$recipient" | aws s3 cp - "$object" --only-show-errors; then
  echo "The upload failed. Starting production here again."
  mac_dc start app cloudflared
  exit 1
fi

if ! run_deploy "$tag" --restore "$object" --identity /root/migration.key; then
  echo "EC2 didn't come up. Starting production here again."
  mac_dc start app cloudflared
  exit 1
fi

mac_dc stop db
launchctl bootout "gui/$(id -u)/app.credcloud.backup" 2>/dev/null || true
echo
echo "Production runs on EC2 now, on $tag."
echo "This Mac's production containers are stopped, not removed. Once EC2 has a week of good"
echo "backups, remove them (the data volume stays): docker compose -p credcloud-prod down"
