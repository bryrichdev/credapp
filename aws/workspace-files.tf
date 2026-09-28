# Document files, one bucket per workspace, which the app creates on the first upload.
#
# The instance role can make and set up buckets named credcloud-<account>-ws-<id>, but can't
# read or write objects in them. To touch files it assumes credcloud-workspace-data with the
# session tagged workspace=<id>. That role only allows the bucket whose name ends in the tag,
# so a wrong id in the app can't reach another workspace's files.

locals {
  workspace_bucket_prefix = "credcloud-${local.account_id}-ws-"
  workspace_bucket_arn    = "arn:${local.partition}:s3:::${local.workspace_bucket_prefix}"
}

resource "aws_iam_role" "workspace_data" {
  name = "credcloud-workspace-data"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = aws_iam_role.prod.arn }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
      Condition = {
        # Every session names exactly one workspace, and nothing else.
        StringLike                  = { "aws:RequestTag/workspace" = "?*" }
        "ForAllValues:StringEquals" = { "aws:TagKeys" = ["workspace"] }
      }
    }]
  })
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "workspace_data" {
  name = "credcloud-workspace-data"
  role = aws_iam_role.workspace_data.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "OwnWorkspaceFiles"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${local.workspace_bucket_arn}$${aws:PrincipalTag/workspace}/*"
      },
      {
        Sid      = "ListOwnWorkspace"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = "${local.workspace_bucket_arn}$${aws:PrincipalTag/workspace}"
      },
    ]
  })
}

resource "aws_iam_role_policy" "prod_workspace_files" {
  name = "credcloud-workspace-files"
  role = aws_iam_role.prod.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AssumeWorkspaceData"
        Effect   = "Allow"
        Action   = ["sts:AssumeRole", "sts:TagSession"]
        Resource = aws_iam_role.workspace_data.arn
      },
      {
        # Bucket setup only. No object actions: those go through the data role.
        Sid    = "SetUpWorkspaceBuckets"
        Effect = "Allow"
        Action = [
          "s3:CreateBucket", "s3:PutBucketVersioning", "s3:PutLifecycleConfiguration",
        ]
        Resource = "${local.workspace_bucket_arn}*"
      },
    ]
  })
}

# Settings the app reads from its .env. deploy.sh writes .env from /credcloud/prod/env/ at each
# deploy, so changing document_storage takes effect on the next promote.
resource "aws_ssm_parameter" "storage_mode" {
  name  = "/credcloud/prod/env/CREDAPP_STORAGE"
  type  = "String"
  value = var.document_storage
}

resource "aws_ssm_parameter" "storage_bucket_prefix" {
  name  = "/credcloud/prod/env/CREDAPP_STORAGE_BUCKET_PREFIX"
  type  = "String"
  value = local.workspace_bucket_prefix
}

resource "aws_ssm_parameter" "storage_data_role" {
  name  = "/credcloud/prod/env/CREDAPP_STORAGE_DATA_ROLE_ARN"
  type  = "String"
  value = aws_iam_role.workspace_data.arn
}

resource "aws_ssm_parameter" "aws_region" {
  name  = "/credcloud/prod/env/AWS_REGION"
  type  = "String"
  value = var.region
}
