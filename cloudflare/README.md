# Cloudflare for credcloud.app

Everything Cloudflare does in front of CredCloud, as Terraform. Written for the Free plan and
Cloudflare provider 5.25+; works with Terraform or OpenTofu.

## What it sets up

**Zone settings** (`zone.tf`)
- HTTPS only, TLS 1.2 minimum, TLS 1.3 on, SSL mode Full (strict).
- Security level medium and the browser integrity check.
- Always Online off, so Cloudflare never serves a cached copy of a private page.
- Bot Fight Mode on, and AI scrapers and crawlers blocked.

**Firewall rules** (`waf.tf`): all 5 of the Free plan's custom rules, run in this order.

| # | Rule | Action |
|---|------|--------|
| 1 | `/api/*` and `/actuator*`: the JSON API (HTTP Basic, no lockout) and actuator. The browser never uses them. | Block |
| 2 | Any method other than GET, HEAD or POST | Block |
| 3 | Scanner paths: `.php`, `/wp-`, `/.env`, `/.git`, `/cgi-bin`, `/.aws` | Block |
| 4 | Verified bots, such as search engines. There's nothing public to index. | Block |
| 5 | `/login`, `/register` and `/password-reset` from outside `sign_in_countries` (default US) | Managed challenge |

**Rate limit** (`waf.tf`): the Free plan's one rule. More than 5 requests from one IP within
10 seconds to `/login`, `/register`, `/account` or `/password-reset` blocks that IP for 10 seconds. Signing in
normally takes 2 or 3 requests.

**Cloudflare Access** (`access.tf`): a second sign-in, with a one-time code sent by email, in
front of `/admin/user-groups/*`. Those are the superuser-only pages: view another group as
admin, import into it, wipe it and delete it. Only `superuser_emails` get through. Practice users never
reach these paths, so nothing changes for them. After signing in to Access, a superuser is sent
back to the Users page with a note to choose the action again, because Access can't replay the
original click.

## One-time setup

1. **Turn on Zero Trust** if you haven't already. In the dashboard, go to Zero Trust, pick a
   team name, and choose the Free plan.
2. **Check whether the account already has the one-time PIN login.** Despite the name, it's a
   code emailed to you each time you sign in. Look under Zero Trust > Integrations > Identity
   providers. New accounts don't get it automatically; they only have the "Cloudflare" provider,
   which means signing in with a Cloudflare account. If "One-time PIN" isn't listed, set
   `create_one_time_pin_login = true` and Terraform adds it. If it is listed, leave the setting
   false: an account can only have one.
3. **Create an API token.** Go to My Profile > API Tokens > Create Token > Custom, and give it:
   - Zone > Zone Settings > Edit
   - Zone > Zone WAF > Edit
   - Zone > Bot Management > Edit
   - Zone > Zone > Read
   - Account > Access: Apps and Policies > Edit
   - Account > Access: Organizations, Identity Providers, and Groups > Edit

   Scope it to the credcloud.app zone and your account only.

## Apply

```sh
cd cloudflare
cp terraform.tfvars.example terraform.tfvars   # fill in account_id, zone_id, superuser_emails
export CLOUDFLARE_API_TOKEN=...                 # the token from step 3; never commit it
terraform init
terraform plan    # also asks Cloudflare to check each rule expression
terraform apply
```

**If you already made rules in the dashboard.** A zone has only one custom-rules list and one
rate-limit list, so creating them fails with "a similar configuration already exists". Import
the existing ones first. The ruleset IDs are in the rule's URL in the dashboard.

```sh
terraform import cloudflare_ruleset.firewall zones/<zone_id>/<ruleset_id>
terraform import cloudflare_ruleset.rate_limit zones/<zone_id>/<ruleset_id>
```

Zone settings can't be deleted through Terraform. Running `terraform destroy` leaves them as
they were last set, and the plan warns you about this.

## App side (already in the repo)

- `compose.yml` sets `CREDAPP_CLOUDFLARE_TRUST_CLIENT_IP=true`. The SSN and CAQH access logs
  then record the visitor's real address from Cloudflare's `CF-Connecting-IP` header. This is
  only safe because the tunnel is the only way in. Keep the app's `ports:` unpublished.
- The app sends a strict Content-Security-Policy, HSTS, Referrer-Policy, Permissions-Policy
  and `X-Frame-Options: DENY`. The CSP allows forms to post to `*.cloudflareaccess.com`, which
  the Access sign-in needs.

## Check it's working

```sh
curl -s -o /dev/null -w "%{http_code}\n" https://credcloud.app/api/providers        # 403
curl -s -o /dev/null -w "%{http_code}\n" -X PUT https://credcloud.app/login         # 403
curl -s -o /dev/null -w "%{http_code}\n" https://credcloud.app/wp-login.php         # 403
for i in $(seq 30); do curl -s -o /dev/null -w "%{http_code} " https://credcloud.app/login; done; echo  # 200s, then 429s
curl -s -o /dev/null -w "%{http_code}\n" https://credcloud.app/admin/user-groups    # 302 to Access
curl -sI https://credcloud.app/login | grep -i content-security-policy               # the CSP
```

Security > Events in the dashboard shows each block and challenge, and which rule made it.

Cloudflare counts rate-limited requests per data center and starts blocking a few requests
after the threshold, not exactly at it. A short burst of 8 can all get through, which is why
the check above sends 30.
