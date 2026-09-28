# Two roles, and no access keys anywhere:
#   credcloud-prod    the instance's own identity, from the instance metadata service
#   credcloud-deploy  assumed by the Promote workflow through GitHub's OIDC token

locals {
  account_id    = data.aws_caller_identity.current.account_id
  partition     = data.aws_partition.current.partition
  env_param_arn = "arn:${local.partition}:ssm:${var.region}:${local.account_id}:parameter/credcloud/prod/*"
}

# --- The instance ---

resource "aws_iam_role" "prod" {
  name = "credcloud-prod"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# SSM Run Command and Session Manager: how deploys arrive and how you get a shell. No SSH.
resource "aws_iam_role_policy_attachment" "prod_ssm" {
  role       = aws_iam_role.prod.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "prod" {
  name = "credcloud-prod"
  role = aws_iam_role.prod.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PullImages"
        Effect   = "Allow"
        Action   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
        Resource = aws_ecr_repository.app.arn
      },
      {
        Sid      = "EcrSignIn"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        # The app's secrets (database password, SSN key, tunnel token, mail password) and the
        # backup settings. SecureStrings use the AWS-managed aws/ssm key, which any principal
        # allowed to read the parameter can decrypt through SSM.
        Sid      = "ReadSettings"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = local.env_param_arn
      },
      {
        Sid      = "ListBackups"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.backups.arn
      },
      {
        # Read for restores and the monthly copy. No s3:DeleteObject: lifecycle rules prune.
        Sid      = "WriteBackups"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = "${aws_s3_bucket.backups.arn}/*"
      },
    ]
  })
}

resource "aws_iam_instance_profile" "prod" {
  name = "credcloud-prod"
  role = aws_iam_role.prod.name
}

# --- The Promote workflow ---

resource "aws_iam_openid_connect_provider" "github" {
  count          = var.create_github_oidc_provider ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

resource "aws_iam_role" "deploy" {
  name = "credcloud-deploy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.github_oidc_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          # Only workflows run from main. A pull request branch can't deploy production.
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:ref:refs/heads/main"
        }
      }
    }]
  })
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "deploy" {
  name = "credcloud-deploy"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "PushImages"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:CompleteLayerUpload",
          "ecr:DescribeImages", "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart",
        ]
        Resource = aws_ecr_repository.app.arn
      },
      {
        Sid      = "EcrSignIn"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "RunDeployOnInstance"
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          aws_instance.prod.arn,
          "arn:${local.partition}:ssm:${var.region}::document/AWS-RunShellScript",
        ]
      },
      {
        Sid      = "WatchDeploy"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:DescribeInstanceInformation"]
        Resource = "*"
      },
      {
        Sid      = "FindTargets"
        Effect   = "Allow"
        Action   = "ssm:GetParameters"
        Resource = "arn:${local.partition}:ssm:${var.region}:${local.account_id}:parameter/credcloud/deploy/*"
      },
    ]
  })
}
