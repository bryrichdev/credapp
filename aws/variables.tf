variable "region" {
  description = "AWS region for production."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "Graviton (arm64) instance type. The Mac builds arm64 images, so this must stay a t4g/m7g/c7g type. t4g.small has 2 GB; t4g.micro (1 GB) saves about $6 a month but leaves little headroom."
  type        = string
  default     = "t4g.small"

  validation {
    condition     = can(regex("^[a-z][0-9]+g[a-z]*\\.", var.instance_type))
    error_message = "Use a Graviton (arm64) type such as t4g.small."
  }
}

variable "root_volume_gb" {
  description = "Size of the one disk, which holds the OS, Docker images and the database."
  type        = number
  default     = 20
}

variable "timezone" {
  description = "Instance time zone. The nightly backup runs at 2:30 in this zone."
  type        = string
  default     = "America/New_York"
}

variable "github_subject_prefix" {
  description = "Start of the subject in GitHub's OIDC token for this repository. The repository uses immutable subjects, which name the owner and repo by ID so a rename can't hand the role to someone else. Check with: gh api repos/bryrichdev/credapp/actions/oidc/customization/sub (sub_claim_prefix)."
  type        = string
  default     = "repo:bryrichdev@316645314/credapp@1371664179"
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub Actions OIDC identity provider. Set false if the account already has one for token.actions.githubusercontent.com; an account can only have one."
  type        = bool
  default     = true
}

variable "backup_recipient" {
  description = "age public key backups are encrypted to (age1...). Reuse the Mac's: cat ~/credcloud/backup-key.pub. The private key never goes to AWS."
  type        = string

  validation {
    condition     = can(regex("^age1[a-z0-9]{58}$", var.backup_recipient))
    error_message = "Give an age public key: it starts with age1 and is 62 characters long."
  }
}

variable "backup_ping_url" {
  description = "Optional URL opened after each good backup, such as a healthchecks.io check that emails you when a night goes by without one."
  type        = string
  default     = ""
}

variable "daily_backup_days" {
  description = "How long nightly backups (and pre-deploy backups) are kept."
  type        = number
  default     = 30
}

variable "monthly_backup_days" {
  description = "How long the first backup of each month is kept."
  type        = number
  default     = 400
}
