variable "account_id" {
  description = "Cloudflare account ID (dashboard: any zone's Overview page, right-hand column)."
  type        = string
}

variable "zone_id" {
  description = "Zone ID for credcloud.app (same place as the account ID)."
  type        = string
}

variable "hostname" {
  description = "The hostname the tunnel serves CredCloud on."
  type        = string
  default     = "credcloud.app"
}

variable "superuser_emails" {
  description = "Emails allowed through Cloudflare Access to the superuser pages (/admin/user-groups). Each gets a one-time code by email."
  type        = list(string)

  validation {
    condition     = length(var.superuser_emails) > 0
    error_message = "List at least one superuser email, or nobody can reach the superuser pages."
  }
}

variable "sign_in_countries" {
  description = "Countries (ISO codes) that reach the sign-in and register pages without a challenge. Everyone else gets a Cloudflare managed challenge first, not a block."
  type        = list(string)
  default     = ["US"]
}

variable "password_requests_per_10s" {
  description = "Requests per IP per 10 seconds to /login, /register and /account before that IP is blocked for 10 seconds. A person signing in uses 2 or 3."
  type        = number
  default     = 5
}

variable "access_session_duration" {
  description = "How long a superuser stays signed in to Access before the next one-time code."
  type        = string
  default     = "12h"
}

variable "create_one_time_pin_login" {
  description = "Create the one-time PIN login method (a code emailed at each sign-in). Set true unless Zero Trust > Integrations > Identity providers already lists 'One-time PIN'; new accounts don't have it, and an account can only have one."
  type        = bool
  default     = false
}
