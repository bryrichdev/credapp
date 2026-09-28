#!/bin/bash
# Copies production's settings into SSM Parameter Store, where the instance reads them at each
# deploy. Run it on the Mac, signed in to AWS as an admin, whenever a setting changes; the next
# deploy picks it up.
#   bash deploy/aws/put-secrets.sh                    from ~/credcloud/prod/.env
#   bash deploy/aws/put-secrets.sh path/to/.env
#
# Each NAME=value line becomes the SecureString /credcloud/prod/env/NAME. They never pass
# through Terraform, so they aren't in its state file. Blank values are skipped.
set -euo pipefail
# shellcheck source=lib-mac.sh
. "$(dirname "$0")/lib-mac.sh"

file="${1:-$HOME/credcloud/prod/.env}"
[ -f "$file" ] || { echo "No such file: $file"; exit 1; }

while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in '' | \#*) continue ;; esac
  name="${line%%=*}"
  value="${line#*=}"
  if ! [[ "$name" =~ ^[A-Z_][A-Z0-9_]*$ ]]; then
    echo "Skipped a line that isn't NAME=value"
    continue
  fi
  # One layer of matching quotes, as Compose reads them.
  if [[ "$value" =~ ^\"(.*)\"$ ]] || [[ "$value" =~ ^\'(.*)\'$ ]]; then
    value="${BASH_REMATCH[1]}"
  fi
  if [ -z "$value" ]; then
    echo "$name is blank; skipped"
    continue
  fi
  aws ssm put-parameter --name "/credcloud/prod/env/$name" --type SecureString \
    --value "$value" --overwrite >/dev/null
  echo "Saved $name"
done < "$file"
