terraform {
  required_version = ">= 1.6"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.25"
    }
  }
}

# Reads the API token from the CLOUDFLARE_API_TOKEN environment variable, so it never
# sits in a file. See README.md for the permissions it needs.
provider "cloudflare" {}
