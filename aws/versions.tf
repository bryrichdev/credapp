terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# Uses whatever AWS sign-in the shell has (aws sso login, a profile, or environment variables).
# Nothing here holds a key.
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      app = "credcloud"
    }
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
