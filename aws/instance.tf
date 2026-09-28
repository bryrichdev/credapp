# One Graviton instance runs production: the app, Postgres and cloudflared, from
# deploy/compose.yml, the same file the Mac uses for staging.

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "prod" {
  ami                         = data.aws_ssm_parameter.al2023_arm64.insecure_value
  instance_type               = var.instance_type
  subnet_id                   = sort(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids      = [aws_security_group.prod.id]
  iam_instance_profile        = aws_iam_instance_profile.prod.name
  associate_public_ip_address = true

  # Terminating would take the database with it. Turn this off in the console first if you
  # really mean to.
  disable_api_termination = true

  # Standard credits throttle a busy CPU instead of billing for the extra. Cheaper, and a
  # handful of coordinators never keep it busy.
  credit_specification {
    cpu_credits = "standard"
  }

  # IMDSv2 only. Two hops so containers can reach it: the app uses the instance role later
  # to assume customer roles.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  user_data = templatefile("${path.module}/bootstrap.sh.tftpl", {
    region        = var.region
    timezone      = var.timezone
    registry      = split("/", aws_ecr_repository.app.repository_url)[0]
    repository    = aws_ecr_repository.app.repository_url
    backup_bucket = aws_s3_bucket.backups.bucket
  })

  tags = {
    Name = "credcloud-prod"
  }

  # The bootstrap only runs on first boot, and a newer AMI would mean a new instance.
  # Neither should replace a running production box. Patch in place: sudo dnf upgrade.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# Settings the scripts read at run time, so changing them doesn't mean a new instance.
resource "aws_ssm_parameter" "backup_recipient" {
  name  = "/credcloud/prod/backup/recipient"
  type  = "String"
  value = var.backup_recipient
}

resource "aws_ssm_parameter" "backup_ping_url" {
  count = var.backup_ping_url == "" ? 0 : 1
  name  = "/credcloud/prod/backup/ping-url"
  type  = "String"
  value = var.backup_ping_url
}

# What the Promote workflow and the Mac scripts need to find production.
resource "aws_ssm_parameter" "deploy_instance" {
  name  = "/credcloud/deploy/instance-id"
  type  = "String"
  value = aws_instance.prod.id
}

resource "aws_ssm_parameter" "deploy_repository" {
  name  = "/credcloud/deploy/ecr-repository"
  type  = "String"
  value = aws_ecr_repository.app.repository_url
}

resource "aws_ssm_parameter" "deploy_bucket" {
  name  = "/credcloud/deploy/backup-bucket"
  type  = "String"
  value = aws_s3_bucket.backups.bucket
}
