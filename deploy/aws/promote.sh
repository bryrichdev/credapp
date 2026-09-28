#!/bin/bash
# Deploys a commit to production on EC2: pushes the image staging already runs to ECR, then
# has the instance pull it and swap it in, with a backup first and a rollback if it fails.
# The Promote workflow runs this on the Mac runner, signed in as credcloud-deploy.
#   bash deploy/aws/promote.sh 3145069
set -euo pipefail
# shellcheck source=lib-mac.sh
. "$(dirname "$0")/lib-mac.sh"

tag="${1:?Give the commit to deploy}"
check_tag "$tag"
instance_online
push_image "$tag"
run_deploy "$tag"
