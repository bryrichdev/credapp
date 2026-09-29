# Zone-wide TLS and security settings. All available on the Free plan.
locals {
  zone_settings = {
    # HTTPS only, modern TLS.
    always_use_https         = "on"
    automatic_https_rewrites = "on"
    min_tls_version          = "1.2"
    tls_1_3                  = "on"
    # The tunnel connects to the app itself, so this only matters if a DNS record ever
    # points at an origin directly: then it must present a valid certificate.
    ssl = "strict"

    # Challenge visitors with a poor reputation, and those whose browsers fail basic checks.
    security_level = "medium"
    browser_check  = "on"

    # Never serve cached copies of pages when the app is down: they're private.
    always_online = "off"
  }
}

resource "cloudflare_zone_setting" "this" {
  for_each = local.zone_settings

  zone_id    = var.zone_id
  setting_id = each.key
  value      = each.value
}

# AI scrapers and crawlers are blocked: nothing in CredCloud is public content.
#
# Bot Fight Mode is off. It challenges programs that aren't browsers, and CredCloud Helper,
# the program on coordinators' computers, is one: it calls /runner/api every couple of
# seconds. On the Free plan Bot Fight Mode can't be skipped for some paths (a WAF skip rule
# doesn't apply to it), so it's off for the whole zone. Signing in is still behind the
# managed challenge and rate limit in waf.tf, and everything else needs a session or a
# helper's token.
#
# JavaScript detections stay on: small inline scripts Cloudflare injects into pages, which
# the app's Content-Security-Policy lets run by the nonce Cloudflare stamps on them.
resource "cloudflare_bot_management" "this" {
  zone_id            = var.zone_id
  enable_js          = true
  fight_mode         = false
  ai_bots_protection = "block"
}

# CredCloud Helper calls /runner/api, and Terminal and PowerShell fetch its install scripts
# and builds from /helper/install and /helper/download. None of them is a browser that can
# answer a challenge, so the browser integrity check and security level challenges are off
# there. The API needs a helper's token; a script needs a live one-time code.
resource "cloudflare_ruleset" "helper_config" {
  zone_id     = var.zone_id
  name        = "CredCloud Helper"
  description = "No browser challenges for CredCloud Helper and its installer"
  kind        = "zone"
  phase       = "http_config_settings"

  rules = [
    {
      ref         = "helper_no_browser_checks"
      description = "CredCloud Helper's API, install scripts and builds aren't fetched by a browser"
      expression  = "starts_with(http.request.uri.path, \"/runner/api/\") or starts_with(http.request.uri.path, \"/helper/install/\") or starts_with(http.request.uri.path, \"/helper/download/\")"
      action      = "set_config"
      action_parameters = {
        bic            = false
        security_level = "essentially_off"
      }
    },
  ]
}
