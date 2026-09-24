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

# Bot Fight Mode: challenges known bot networks, and blocks AI scrapers and crawlers.
# Nothing in CredCloud is public content, so there's nothing for them to read.
#
# Bot Fight Mode needs JavaScript detections: small inline scripts Cloudflare injects into
# pages. The app's Content-Security-Policy carries a fresh nonce on every response, and
# Cloudflare stamps that nonce on the scripts it injects, so they run under the policy.
resource "cloudflare_bot_management" "this" {
  zone_id            = var.zone_id
  enable_js          = true
  fight_mode         = true
  ai_bots_protection = "block"
}
