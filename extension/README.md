# CredCloud for Chrome

Fills payer portals with provider data from CredCloud, in the coordinator's own Chrome, with
her own portal sign-ins. It fills and stops. She checks each page and submits it herself.

## How she uses it

1. **Install it once** from the Chrome Web Store.
2. **Fill a portal.** On a provider's Portal fills page she presses **Fill in my browser**. The
   first time, a CredCloud tab asks to **Connect this browser**: one click, with the account
   she's signed in with. After that it goes straight to the portal.
3. **Allow the portal once.** The first time she uses a portal, Chrome asks to let CredCloud
   work on that site.
4. **Fill each page.** She signs in to the portal, opens the form and presses **Fill this
   page** in the CredCloud panel, page by page, then **Done**.

Teaching a portal works the same way from the payer's Portal templates page: **Pick a box**,
click it, choose its data, and **Save template**.

## What it will and won't do

- It types into text boxes, picks dropdown options and ticks checkboxes and radio buttons.
  Before touching an element it checks what the element is, so it never presses a button.
- A radio button or checkbox is ticked when its answer is Yes or names that option. Teach every
  option of a question with the same data: Female and Male both get Provider / Sex, and only
  the one the data names is ticked ("F" is enough). A Yes/No pair follows a yes/no answer, a
  list like "English, Spanish" ticks each checkbox it names, and a list box that takes several
  picks each option named.
- It only types into the site a job was sent for, and only on sites she has allowed.
- Its scripts run apart from the portal's, so a portal page can't read the panel or press it.
- It never sees her portal password. CredCloud only knows which boxes were filled.

## Files

| File | What it does |
|---|---|
| `manifest.json` | Permissions: CredCloud's own sites; each portal only when she allows it |
| `background.js` | Tokens, jobs, and each portal tab's job while she works through it |
| `credcloud.js` | On CredCloud pages: takes the connect token, and passes on "a job is ready" |
| `panel.js`, `portal.js` | On portal pages: the panel, learning boxes, and typing answers |
| `grant.html`, `grant.js` | Asks Chrome for a portal the first time |

## Try it before it's published

`chrome://extensions` > Developer mode > **Load unpacked** > this folder. It works with
credcloud.app and staging.credcloud.app. For a local server, add `http://localhost/*` to
`host_permissions` and the content script's `matches`.

## Publish

1. Register as a Chrome Web Store developer ($5, once).
2. `npm run zip` and upload `credcloud-extension.zip`. Choose **Unlisted** so only people with
   the link can install it. The listing needs a privacy policy, since it handles personal data.
3. Put the listing's address in production's settings, so CredCloud links to it:
   add `CREDAPP_EXTENSION_INSTALL_URL=https://chromewebstore.google.com/detail/...` to
   `~/credcloud/prod/.env`, run `bash deploy/aws/put-secrets.sh`, then promote.

Edge installs Chrome Web Store extensions too.

## Tests

```sh
npm ci
npx playwright-core install chromium   # or set CHROMIUM_PATH to a Chromium you have
npm test
```

They load the extension into Chromium against a fake CredCloud and a fake portal with a strict
Content-Security-Policy, and cover connecting, filling, learning, asking for a portal, refusing
another site, and cancelling when the tab closes.
