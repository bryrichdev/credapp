output "instance_id" {
  value = aws_instance.prod.id
}

output "deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN repository variable."
  value       = aws_iam_role.deploy.arn
}

output "ecr_repository" {
  value = aws_ecr_repository.app.repository_url
}

output "backup_bucket" {
  value = aws_s3_bucket.backups.bucket
}

output "shell" {
  description = "A root-capable shell on the instance, through Session Manager. Needs the session-manager-plugin."
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.prod.id}"
}
