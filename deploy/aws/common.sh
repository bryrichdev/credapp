# Shared by the scripts that run on the production instance. Sourced, not run.
# bootstrap.sh.tftpl writes /opt/credcloud/config: REGION, REPOSITORY, REGISTRY, BACKUP_BUCKET.
# shellcheck shell=bash

root=/opt/credcloud
# shellcheck source=/dev/null
. "$root/config"
export AWS_DEFAULT_REGION="$REGION"
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
env_file="$root/prod/.env"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# Compose for production. APP_TAG picks the image and defaults to the one running now.
dc() {
  APP_TAG="${APP_TAG:-$(cat "$root/prod/deployed" 2>/dev/null || echo none)}" \
    docker compose -f "$root/compose.yml" -p credcloud-prod --env-file "$env_file" "$@"
}

# Writes .env from the SSM parameters under /credcloud/prod/env/ (put-secrets.sh sets them).
# Values are single-quoted so Compose takes them literally: a $ in a password stays a $.
write_env() {
  local tmp="$env_file.new" name
  (
    umask 077
    aws ssm get-parameters-by-path --path /credcloud/prod/env --with-decryption --output json \
      | python3 -c '
import json, sys
for p in json.load(sys.stdin)["Parameters"]:
    name, value = p["Name"].rsplit("/", 1)[1], p["Value"]
    if "\x27" in value or "\n" in value:
        sys.exit(name + " has a single quote or a line break, which .env cannot hold")
    print(name + "=\x27" + value + "\x27")
' > "$tmp"
    echo "APP_IMAGE='$REPOSITORY'" >> "$tmp"
  )
  for name in DB_NAME DB_USERNAME DB_PASSWORD CREDAPP_SSN_KEY CREDAPP_BASE_URL CLOUDFLARE_TUNNEL_TOKEN; do
    grep -q "^$name=" "$tmp" || { rm -f "$tmp"; log "Missing /credcloud/prod/env/$name. Run deploy/aws/put-secrets.sh."; return 1; }
  done
  mv "$tmp" "$env_file"
}

# The ECR credential helper signs Docker in with the instance role on every pull. Without it,
# fall back to a 12-hour token from the CLI.
ecr_login() {
  if command -v docker-credential-ecr-login >/dev/null; then
    return 0
  fi
  aws ecr get-login-password | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
}

# pg_dump custom format on stdout.
dump_db() {
  dc exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"'
}

# Replaces production's database with a pg_dump custom-format stream on stdin. The app must
# be stopped. Sessions from the backup's time are cleared so they don't come back to life.
replace_db() {
  dc exec -T db sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
  dc exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error'
  dc exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -qc "SET client_min_messages = warning; TRUNCATE spring_session CASCADE"'
}
