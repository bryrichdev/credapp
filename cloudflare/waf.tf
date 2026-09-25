locals {
  sign_in_paths  = "{\"/login\" \"/register\" \"/password-reset\"}"
  password_paths = "{\"/login\" \"/register\" \"/account\" \"/password-reset\"}"
  countries      = join(" ", [for c in var.sign_in_countries : "\"${c}\""])
}

# Custom rules: the Free plan allows 5, and this uses all 5. They run in order and the
# first that matches decides.
resource "cloudflare_ruleset" "firewall" {
  zone_id     = var.zone_id
  name        = "CredCloud firewall"
  description = "Custom WAF rules for CredCloud"
  kind        = "zone"
  phase       = "http_request_firewall_custom"

  rules = [
    {
      ref         = "block_internal_endpoints"
      description = "The JSON API (HTTP Basic, no lockout) and actuator aren't used by the browser; keep them off the internet"
      expression  = "starts_with(http.request.uri.path, \"/api/\") or starts_with(http.request.uri.path, \"/actuator\")"
      action      = "block"
    },
    {
      ref         = "block_unused_methods"
      description = "The app only answers GET, HEAD and POST"
      expression  = "not http.request.method in {\"GET\" \"HEAD\" \"POST\"}"
      action      = "block"
    },
    {
      ref         = "block_scanner_probes"
      description = "Paths only vulnerability scanners ask for"
      expression = join(" or ", [
        "http.request.uri.path contains \".php\"",
        "http.request.uri.path contains \"/wp-\"",
        "http.request.uri.path contains \"/.env\"",
        "http.request.uri.path contains \"/.git\"",
        "http.request.uri.path contains \"/cgi-bin\"",
        "http.request.uri.path contains \"/.aws\"",
      ])
      action = "block"
    },
    {
      ref         = "block_crawlers"
      description = "Search engines and other verified bots: there's no public content to index"
      expression  = "cf.client.bot"
      action      = "block"
    },
    {
      ref         = "challenge_sign_in_abroad"
      description = "Sign-in and sign-up from outside the listed countries get a managed challenge first"
      expression  = "not ip.src.country in {${local.countries}} and http.request.uri.path in ${local.sign_in_paths}"
      action      = "managed_challenge"
    },
  ]
}

# Rate limiting: the Free plan allows 1 rule, matching on path only, counted per IP over
# 10 seconds, blocking for 10 seconds. This one slows password guessing on the pages that
# take a password.
resource "cloudflare_ruleset" "rate_limit" {
  zone_id     = var.zone_id
  name        = "CredCloud rate limits"
  description = "Rate limits for CredCloud"
  kind        = "zone"
  phase       = "http_ratelimit"

  rules = [
    {
      ref         = "limit_password_pages"
      description = "Sign-in, sign-up and account changes: ${var.password_requests_per_10s} requests per IP per 10s"
      expression  = "http.request.uri.path in ${local.password_paths}"
      action      = "block"
      ratelimit = {
        characteristics     = ["ip.src", "cf.colo.id"]
        period              = 10
        requests_per_period = var.password_requests_per_10s
        mitigation_timeout  = 10
      }
    },
  ]
}
