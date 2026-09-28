# Images: the Mac builds each commit for staging, and the Promote workflow pushes that same
# image here. Tags are commits, so they never change once pushed.
resource "aws_ecr_repository" "app" {
  name                 = "credcloud"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images, enough to roll back"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# Backups: encrypted with age on the instance before upload. The instance can write and read
# them but not delete them; only the lifecycle rules below delete, so a compromised instance
# can't wipe the history.
resource "aws_s3_bucket" "backups" {
  bucket = "credcloud-backups-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket                  = aws_s3_bucket.backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "daily"
    status = "Enabled"
    filter {
      prefix = "daily/"
    }
    expiration {
      days = var.daily_backup_days
    }
  }

  rule {
    id     = "pre-deploy"
    status = "Enabled"
    filter {
      prefix = "pre-deploy/"
    }
    expiration {
      days = var.daily_backup_days
    }
  }

  rule {
    id     = "monthly"
    status = "Enabled"
    filter {
      prefix = "monthly/"
    }
    expiration {
      days = var.monthly_backup_days
    }
  }

  # The one-off copy the move from the Mac goes through.
  rule {
    id     = "migration"
    status = "Enabled"
    filter {
      prefix = "migration/"
    }
    expiration {
      days = 7
    }
  }

  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"
    filter {}
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_policy" "backups" {
  bucket = aws_s3_bucket.backups.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "HttpsOnly"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.backups.arn, "${aws_s3_bucket.backups.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.backups]
}
