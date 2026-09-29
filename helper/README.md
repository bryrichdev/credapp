# CredCloud Helper

Fills payer portals with provider data from CredCloud, in Chrome on the coordinator's own
computer. She installs it once; it starts when she logs in, opens each portal she fills or
teaches in CredCloud with the CredCloud panel on every page, and keeps itself up to date. It
never submits a form.

## How it works

- **Chrome over the DevTools protocol.** It starts her installed Chrome (or Edge) with a
  profile of its own (`Browser` in its folder), so portal sign-ins and saved passwords stay on
  her computer and never mix with her everyday Chrome. No extension, and no browser download.
- **The panel.** `page/credcloud.js` runs in every page and frame in an isolated world, a
  JavaScript world of its own: the portal's scripts can't see or call it, and the portal's
  Content-Security-Policy doesn't apply to it. It reaches Go through a binding that exists only
  in that world, draws the panel in a closed shadow root, and only acts on real clicks.
- **Jobs.** It asks CredCloud's `/runner/api/jobs/next` every 2 seconds, with the device token
  it got for a one-time code. `job.go` is the panel's logic (pick, add, save; fill, done,
  cancel); `browser.go` tracks tabs, frames and worlds; `cdp.go` is the protocol client.
- **One at a time.** A lock file keeps one helper running; a second start (a `credcloud://`
  link, the app icon) hands its request to the running one over `127.0.0.1` and exits.
- **Updates.** Each answer from `jobs/next` carries `X-Helper-Sha256`, the checksum of
  CredCloud's current build for this kind of computer. When it differs from its own, between
  jobs, an installed helper downloads the build, checks the checksum, swaps it in and restarts.
  Builds are reproducible (`build.sh`), so that happens only when this code changes.

## Installing (what she does)

My account > **CredCloud Helper** > **Get my install command**, then paste one line:

- **Mac**, in Terminal: `curl -fsSL https://credcloud.app/helper/install/<code>.sh | sh`.
  It builds `~/Applications/CredCloud Helper.app` (an AppleScript applet that passes
  `credcloud://` links on, made with `osacompile` and signed with `codesign -s -`), adds a
  LaunchAgent so it starts at login, and connects with the code. curl doesn't mark files as
  downloaded, so there's no Gatekeeper prompt.
- **Windows**, in PowerShell: `irm https://credcloud.app/helper/install/<code>.ps1 | iex`.
  It installs to `%LOCALAPPDATA%\Programs\CredCloud Helper` with a Start menu shortcut, the
  `credcloud://` handler, a Run entry to start at sign-in and an Installed apps entry. No
  administrator. The plain `.exe` download works too; it installs itself when opened and asks
  her to connect in the browser.

The code works once, for 30 minutes. To remove it: on a Mac, `credcloud-helper uninstall` (in
the app's `Contents/Resources`) or drag the app to the Trash; on Windows, Settings > Apps.

## Developing

```sh
go run . portal                      # the test portal, at http://127.0.0.1:8181/enroll
go run . pair http://localhost:8080 <code>   # the code from an install command on /helper
go run .                             # never updates itself
go run . demo [fill]                 # a fake job against the test portal, no CredCloud
```

The dev profile accepts `http://127.0.0.1` portals, for the test portal. To try the install
commands against a local CredCloud, run `sh build.sh` first: the dev profile serves
`helper/dist`. Set `CREDCLOUD_HELPER_HOME` to keep a development helper apart from an installed
one; otherwise they share settings and only one runs.

## Tests

```sh
go test ./...
```

They run a headless Chrome against the test portal (strict CSP, a frame in its own process,
another site's frame) and a fake CredCloud: teaching and filling with real mouse clicks,
pairing, job pickup, updating, and handing links to the running helper. Set
`CREDCLOUD_BROWSER` to use a Chromium that isn't installed where the helper looks, and
`CREDCLOUD_BROWSER_ARGS="--headless=new --no-sandbox"` where Chrome's sandbox isn't allowed.
