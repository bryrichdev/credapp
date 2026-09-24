output "access_application_aud" {
  description = "Access audience tag, needed if the app later verifies Access's signed JWT itself."
  value       = cloudflare_zero_trust_access_application.superuser_pages.aud
}
