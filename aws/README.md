# Production on AWS

Production runs on one small Graviton instance. Staging stays on the Mac. Everything here is
Terraform or OpenTofu; the scripts it relies on are in `deploy/aws/`.

## What it sets up

- **One EC2 instance** (`instance.tf`): t4g.small, Amazon Linux 2023, 20 GB disk. It runs
  `deploy/compose.yml`, the same app, Postgres and cloudflared stack as staging. There are no
  inbound ports and no SSH key. Cloudflare's tunnel and SSM both connect outward.
- **Two IAM roles and no access keys** (`iam.tf`). `credcloud-prod` is the instance's own
  identity. `credcloud-deploy` is assumed by the Promote workflow through GitHub's OIDC token,
  and only from `main`.
- **ECR** (`storage.tf`) holds the last 10 images. Tags are commits and can't be overwritten.
- **An S3 bucket for backups** (`storage.tf`). Backups are age-encrypted on the instance before
  upload. The instance can write backups but can't delete them; lifecycle rules expire
  `daily/` and `pre-deploy/` after 30 days and `monthly/` after 400.
- **SSM Parameter Store** holds the app's settings (`/credcloud/prod/env/*`, SecureStrings
  written by `put-secrets.sh`, never in Terraform state) and what the scripts need to find
  things (`/credcloud/deploy/*`).

## Cost

us-east-1, on demand:

| Item | Per month |
|------|-----------|
| t4g.small, standard CPU credits | $12.26 |
| Public IPv4 address | $3.65 |
| 20 GB gp3 disk | $1.60 |
| ECR, S3 backups, Parameter Store | under $0.25 |
| Data out (first 100 GB free) | $0 |
| **Total** | **about $17.75** |

To go lower:
- A 1-year EC2 Instance Savings Plan (no upfront) cuts the instance line by about a third.
- `instance_type = "t4g.micro"` (1 GB) saves about $6. It works now because Playwright runs on
  the coordinator's computer, not here. There is little headroom, though.
- The IPv4 address is the price of skipping a $32 NAT gateway. GitHub, which the instance
  downloads Compose and age from, doesn't serve IPv6 yet.

## One-time setup

On the Mac, signed in to AWS as an admin (`aws sso login` or a profile):

1. Install the tools: `brew install awscli terraform` and
   `brew install --cask session-manager-plugin`. The runner uses `aws` too.
2. Create the infrastructure:
   ```sh
   cd aws
   cp terraform.tfvars.example terraform.tfvars   # backup_recipient: cat ~/credcloud/backup-key.pub
   terraform init
   terraform apply
   ```
   The instance takes about 3 more minutes to install Docker after the apply finishes.
3. Copy production's settings into Parameter Store: `bash deploy/aws/put-secrets.sh`
4. Tell the Promote workflow which role to use:
   ```sh
   gh variable set AWS_DEPLOY_ROLE_ARN --body "$(terraform -chdir=aws output -raw deploy_role_arn)"
   gh variable set AWS_REGION --body us-east-1
   ```
5. Move production. It's down for a few minutes, and the script starts it on the Mac again if
   anything fails:
   ```sh
   bash deploy/aws/migrate-from-mac.sh "$(cat ~/credcloud/prod/deployed)"
   ```
6. Merge this branch. From then on, **Promote to production** deploys to EC2.

## Day to day

- **Deploy:** run the Promote workflow. It pushes the image staging runs to ECR. The instance
  backs up, swaps the image in, and rolls back if the new one isn't healthy within 3 minutes.
- **Change a setting:** edit `~/credcloud/prod/.env`, run `put-secrets.sh`, then promote the
  same tag again. Each deploy rewrites the instance's `.env` from Parameter Store.
- **Shell:** `$(terraform -chdir=aws output -raw shell)`, then `sudo -i`.
- **Backups:** `journalctl -u credcloud-backup` shows the log. `sudo /opt/credcloud/bin/backup.sh --drill`
  backs up now and proves the dump restores. Sundays run the drill on their own.
- **Restore:** `sudo /opt/credcloud/bin/restore.sh s3://<bucket>/daily/<file>.dump.age`. It asks
  for the backup key from your password manager and saves the current database first.
- **Patch the OS:** `sudo dnf upgrade --releasever=latest -y && sudo reboot`. The containers
  start again on their own.
