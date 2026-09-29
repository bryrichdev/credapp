# Cloudflare Access puts a second sign-in (a one-time code sent by email) in front of the
# superuser-only pages: viewing another user group, importing into it, and wiping it.
# Practice users never hit these paths, so they're unaffected. Free for up to 50 users.

resource "cloudflare_zero_trust_access_identity_provider" "one_time_pin" {
  count = var.create_one_time_pin_login ? 1 : 0

  account_id = var.account_id
  name       = "One-time PIN"
  type       = "onetimepin"
  config     = {}
}

resource "cloudflare_zero_trust_access_policy" "superusers" {
  account_id = var.account_id
  name       = "CredCloud superusers"
  decision   = "allow"
  include    = [for email in var.superuser_emails : { email = { email = email } }]
}

resource "cloudflare_zero_trust_access_application" "superuser_pages" {
  account_id = var.account_id
  name       = "CredCloud superuser pages"
  type       = "self_hosted"
  domain     = "${var.hostname}/admin/user-groups"
  destinations = [{
    type = "public"
    uri  = "${var.hostname}/admin/user-groups"
  }]

  session_duration           = var.access_session_duration
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  same_site_cookie_attribute = "lax"

  policies = [{
    id         = cloudflare_zero_trust_access_policy.superusers.id
    precedence = 1
  }]
}

locals {
  staging_hostname = "staging.${var.hostname}"
}

# All of staging sits behind Access: only superuser_emails get in, with a one-time code.
# CredCloud Helper's API, install scripts and builds are the exception (below).
resource "cloudflare_zero_trust_access_application" "staging" {
  account_id = var.account_id
  name       = "CredCloud staging"
  type       = "self_hosted"
  domain     = local.staging_hostname
  destinations = [{
    type = "public"
    uri  = local.staging_hostname
  }]

  session_duration           = "24h"
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  same_site_cookie_attribute = "lax"

  policies = [{
    id         = cloudflare_zero_trust_access_policy.superusers.id
    precedence = 1
  }]
}

# CredCloud Helper can't sign in to Access: it's a program, not a browser. On staging, its API
# and installer skip Access; the API still needs a helper's token, and an install script a
# live one-time code, which only a signed-in staging page hands out. Access matches the most
# specific path, so the rest of staging stays behind the sign-in above.
resource "cloudflare_zero_trust_access_policy" "helper_bypass" {
  account_id = var.account_id
  name       = "CredCloud Helper (token or one-time code checked by the app)"
  decision   = "bypass"
  include    = [{ everyone = {} }]
}

resource "cloudflare_zero_trust_access_application" "staging_helper" {
  account_id = var.account_id
  name       = "CredCloud staging: CredCloud Helper"
  type       = "self_hosted"
  domain     = "${local.staging_hostname}/runner/api"
  destinations = [
    { type = "public", uri = "${local.staging_hostname}/runner/api" },
    { type = "public", uri = "${local.staging_hostname}/helper/install" },
    { type = "public", uri = "${local.staging_hostname}/helper/download" },
  ]

  app_launcher_visible = false

  policies = [{
    id         = cloudflare_zero_trust_access_policy.helper_bypass.id
    precedence = 1
  }]
}
