# CredCloud Runner

Fills payer portals in the coordinator's own Chrome. CredCloud sends it a job; it opens the
portal in a new tab, she signs in herself, and a small CredCloud panel on the page does the rest.

- **Fill this page** types the provider's answers into the boxes on the current page. She checks
  the page, goes to the next one, and fills again. **Done** reports which boxes were filled.
- **The runner never submits.** It can type into text boxes, pick dropdown options and tick
  checkboxes or radio buttons. Before touching an element it checks what the element is, so a
  template pointing at a button does nothing. She presses submit herself.
- **Portal passwords stay on her computer.** The runner uses its own Chrome profile, where her
  portal sign-ins live. CredCloud never sees them.
- **Learn mode** teaches it a portal: she clicks each box and picks what goes in it. Saving makes
  a new version of the portal template, so earlier fills keep the version they used.

It only types into pages on the portal the job was sent for (the same site, such as
`login.payer.com` for `portal.payer.com`).

## Run it

Needs Java 25 and Google Chrome. From the repository root:

```sh
./mvnw -f runner/pom.xml package -DskipTests
java -jar runner/target/credcloud-runner.jar                                  # credcloud.app
java -jar runner/target/credcloud-runner.jar --server https://staging.credcloud.app
```

Chrome opens on the CredCloud Runner page. In CredCloud, go to **My account > Runners > Connect
a runner**, and type the code into that page. On staging, sign in to Cloudflare Access in that
tab first. Close Chrome to stop the runner.

The token and the Chrome profile are in `~/Library/Application Support/CredCloud Runner`.
Revoking the runner in CredCloud stops it at its next request.

## How it talks to CredCloud

Every API call is made from the CredCloud Runner tab with `fetch`, not from Java. The requests
come from real Chrome, so Cloudflare's bot checks and, on staging, Cloudflare Access treat
them like any visit.

## Tests

`./mvnw -f runner/pom.xml verify` runs them in headless Chromium against a fake portal and a
fake CredCloud, served from inside the browser. Playwright downloads Chromium the first time;
set `CHROMIUM_PATH` to use one already installed.
