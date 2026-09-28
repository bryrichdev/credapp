// CredCloud for Chrome, end to end: the real extension in Chromium, against a fake CredCloud
// on localhost and a fake payer portal on 127.0.0.1 with a strict Content-Security-Policy.
//
//   npm test                          downloads nothing if CHROMIUM_PATH names a Chromium
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const STRICT_CSP = "default-src 'self'; script-src 'self'; style-src 'self'";

// --- Fake CredCloud ---

const jobs = [];
const posts = [];
const TOKEN = 'token-for-tests-0123456789';

function page(body) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>CredCloud</title></head><body>${body}</body></html>`;
}

const credcloud = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/extension/connect') {
    // What CredCloud shows after Connect this browser: the token, for the extension to take.
    res.end(page(`<div id="credcloud-extension-token" data-token="${TOKEN}" hidden></div><p id="credcloud-extension-status">Connecting…</p>`));
  } else if (url.pathname === '/fills') {
    res.end(page('<div data-credcloud-job-ready hidden></div><p>Opening…</p>'));
  } else if (url.pathname === '/elsewhere') {
    res.writeHead(200, { 'Content-Type': 'text/html', 'Content-Security-Policy': STRICT_CSP });
    res.end(fs.readFileSync(path.join(here, 'enroll.html')));
  } else if (url.pathname.startsWith('/runner/api/')) {
    if (req.headers.authorization !== 'Bearer ' + TOKEN) {
      res.writeHead(401).end('{}');
      return;
    }
    if (url.pathname === '/runner/api/jobs/next') {
      const job = jobs.shift();
      if (!job) {
        res.writeHead(204).end();
      } else {
        res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(job));
      }
      return;
    }
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      posts.push({ path: url.pathname, body: body ? JSON.parse(body) : null });
      res.writeHead(200, { 'Content-Type': 'application/json' })
        .end(url.pathname.endsWith('/learned') ? '{"revision":2}' : '{}');
    });
  } else {
    res.writeHead(404).end();
  }
});

// --- Fake portal ---

const portal = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/enroll') {
    res.writeHead(200, { 'Content-Type': 'text/html', 'Content-Security-Policy': STRICT_CSP });
    res.end(fs.readFileSync(path.join(here, 'enroll.html')));
  } else if (url.pathname === '/count-submits.js') {
    res.writeHead(200, { 'Content-Type': 'application/javascript' });
    res.end(fs.readFileSync(path.join(here, 'count-submits.js')));
  } else {
    res.writeHead(200, { 'Content-Type': 'text/html' }).end('<p>Submitted</p>');
  }
});

let context;
let worker;
let server;
let portalUrl;

/** The extension as built, plus the test servers as CredCloud and a portal it may use. */
function testCopyOfExtension() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'credcloud-ext-'));
  for (const file of fs.readdirSync(path.join(here, '..'))) {
    if (file === 'test' || file === 'node_modules' || file.startsWith('package')) continue;
    fs.cpSync(path.join(here, '..', file), path.join(dir, file), { recursive: true });
  }
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json')));
  manifest.content_scripts[0].matches.push('http://localhost/*');
  manifest.host_permissions.push('http://localhost/*', 'http://127.0.0.1/*');
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest));
  return dir;
}

const listen = s => new Promise(resolve => s.listen(0, '127.0.0.1', () => resolve(s.address().port)));

before(async () => {
  server = `http://localhost:${await listen(credcloud)}`;
  portalUrl = `http://127.0.0.1:${await listen(portal)}/enroll`;
  const extension = testCopyOfExtension();
  context = await chromium.launchPersistentContext(fs.mkdtempSync(path.join(os.tmpdir(), 'credcloud-profile-')), {
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || undefined,
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`]
  });
  worker = context.serviceWorkers()[0] || await context.waitForEvent('serviceworker');
});

after(async () => {
  await context.close();
  credcloud.close();
  portal.close();
});

beforeEach(() => {
  jobs.length = 0;
  posts.length = 0;
});

// --- Helpers ---

function job(id, kind, fields, answers, startUrl = portalUrl) {
  return {
    id, kind, templateId: 3, templateName: 'Enrollment', payerName: 'Example Payer', startUrl, revision: 1,
    fields, providerName: kind === 'fill' ? 'Jane Doe' : null, answers,
    sources: kind === 'fill' ? [] : [{ key: 'provider.first_name', label: 'Provider / First name' },
      { key: 'provider.npi', label: 'Provider / NPI' }],
    formats: kind === 'fill' ? {} : { AS_SAVED: 'As saved', DIGITS: 'Digits only' }
  };
}

const field = (label, by, locator, kind) => ({ label, by, locator, kind, source: '', format: '', defaultValue: '', page: '/enroll' });

async function connect() {
  const tab = await context.newPage();
  await tab.goto(server + '/extension/connect');
  await tab.getByText('Connected.').waitFor();
  await tab.close();
}

/** Opens the page that says a job is ready, and returns the tab the extension opens for it. */
async function startJob(expectUrl) {
  const tab = await context.newPage();
  const opened = context.waitForEvent('page', { predicate: p => p !== tab });
  await tab.goto(server + '/fills');
  const next = await opened;
  await next.waitForURL(u => u.toString().startsWith(expectUrl));
  await tab.close();
  return next;
}

const button = (tab, name) => tab.locator('credcloud-panel').getByRole('button', { name, exact: true });

// --- Tests ---

test('connects from the CredCloud page, then fills a job and reports it, without submitting', async () => {
  await connect();
  jobs.push(job(7, 'fill', [
    field('First name', 'label', 'First name *', 'text'),
    field('State', 'label', 'State', 'select'),
    field('Accepting new patients', 'label', 'Accepting new patients', 'checkbox'),
    field('Female', 'label', 'Female', 'radio'),
    field('Submit', 'css', 'button[type=submit]', 'text'),
    field('DEA', 'label', 'DEA number', 'text')
  ], [
    { field: 0, value: 'Jane' }, { field: 1, value: 'Utah' }, { field: 2, value: 'Yes' },
    { field: 3, value: 'Yes' }, { field: 4, value: 'anything' }, { field: 5, value: 'AB1234567' }
  ]));

  const tab = await startJob(portalUrl);
  const panel = tab.locator('credcloud-panel');
  await panel.getByText('Jane Doe').waitFor();
  await button(tab, 'Fill this page').click();
  await panel.getByText('Filled 4 boxes on this page').waitFor();

  assert.equal(await tab.inputValue('#first'), 'Jane');
  assert.equal(await tab.inputValue('#state'), 'UT');
  assert.equal(await tab.isChecked('input[name=accepting]'), true);
  assert.equal(await tab.isChecked('input[value=F]'), true);
  await panel.getByText(/Submit: the page has a button/).waitFor();

  await button(tab, 'Done').click();
  await panel.getByText('submit it yourself').waitFor();
  assert.deepEqual(posts, [{ path: '/runner/api/jobs/7/filled', body: {
    filled: ['First name', 'State', 'Accepting new patients', 'Female'], missed: ['Submit', 'DEA'] } }]);
  assert.equal(await tab.evaluate(() => window.submitted), 0);
  assert.equal(tab.url(), portalUrl, 'still on the form, left for her to check and submit');
});

test('learns the boxes she clicks and saves them as a new version', async () => {
  await connect();
  jobs.push(job(8, 'learn', [field('Old box', 'css', '#gone', 'text')], []));
  const tab = await startJob(portalUrl);
  const panel = tab.locator('credcloud-panel');
  await panel.getByText('1 boxes so far').waitFor();

  await button(tab, '✕').click();
  await panel.getByText('0 boxes so far').waitFor();
  await button(tab, 'Pick a box on the page').click();
  await button(tab, 'Stop picking').waitFor();
  await tab.locator('#first').click();
  await panel.getByText('What goes in this box?').waitFor();
  await panel.locator('select').first().selectOption('provider.first_name');
  await button(tab, 'Add').click();
  await panel.getByText('1 boxes so far').waitFor();

  await tab.locator('#npi').click();
  await panel.getByText('What goes in this box?').waitFor();
  await panel.locator('select').first().selectOption('provider.npi');
  await panel.locator('select').nth(1).selectOption('DIGITS');
  await button(tab, 'Add').click();
  await panel.getByText('2 boxes so far').waitFor();

  await tab.locator('button[type=submit]').click();
  await panel.getByText('isn’t a box').waitFor();
  assert.equal(await tab.evaluate(() => window.submitted), 0, 'clicks go to CredCloud while picking');

  await button(tab, 'Save template').click();
  await panel.getByText('Saved as version 2').waitFor();
  assert.equal(posts.length, 1);
  assert.equal(posts[0].path, '/runner/api/jobs/8/learned');
  const [first, npi] = posts[0].body.fields;
  assert.deepEqual(first, { label: 'First name', by: 'label', locator: 'First name *', kind: 'text',
    source: 'provider.first_name', format: 'AS_SAVED', defaultValue: '', page: '/enroll' });
  assert.equal(npi.by, 'css', 'the NPI box has no label, so it goes by its id');
  assert.equal(npi.locator, '#npi');
  assert.equal(npi.format, 'DIGITS');
});

test('without a connection, a ready job opens the Connect page', async () => {
  await worker.evaluate(() => chrome.storage.local.clear());
  jobs.push(job(9, 'fill', [field('First name', 'label', 'First name *', 'text')], [{ field: 0, value: 'Jane' }]));
  const tab = await startJob(server + '/extension/connect');
  await tab.getByText('Connected.').waitFor();
  // Once connected, it goes on to the job that was waiting.
  let portalTab;
  for (let i = 0; i < 100 && !portalTab; i++) {
    portalTab = context.pages().find(p => p.url().startsWith(portalUrl));
    if (!portalTab) await new Promise(r => setTimeout(r, 100));
  }
  assert.ok(portalTab, 'the portal opened after connecting');
  await portalTab.locator('credcloud-panel').getByText('Jane Doe').waitFor();
});

test('asks Chrome for permission first on a portal it has not been allowed on', async () => {
  await connect();
  const other = portalUrl.replace('127.0.0.1', '127.0.0.2');
  jobs.push(job(10, 'fill', [field('First name', 'label', 'First name *', 'text')], [{ field: 0, value: 'Jane' }], other));
  const tab = await startJob('chrome-extension://');
  assert.match(tab.url(), /grant\.html$/);
  await tab.getByText('Allow CredCloud on 127.0.0.2?').waitFor();
});

test('will not type into a page on another site', async () => {
  await connect();
  jobs.push(job(11, 'fill', [field('First name', 'label', 'First name *', 'text')], [{ field: 0, value: 'Jane' }]));
  const tab = await startJob(portalUrl);
  await tab.locator('credcloud-panel').getByText('Jane Doe').waitFor();
  await tab.goto(server + '/elsewhere');
  await tab.locator('credcloud-panel').getByText('Jane Doe').waitFor();
  await button(tab, 'Fill this page').click();
  await tab.locator('credcloud-panel').getByText('isn’t on the portal').waitFor();
  assert.equal(await tab.inputValue('#first'), '');
});

test('closing the portal tab cancels the job', async () => {
  await connect();
  jobs.push(job(12, 'fill', [field('First name', 'label', 'First name *', 'text')], [{ field: 0, value: 'Jane' }]));
  const tab = await startJob(portalUrl);
  await tab.locator('credcloud-panel').getByText('Jane Doe').waitFor();
  await tab.close();
  for (let i = 0; i < 50 && !posts.length; i++) await new Promise(r => setTimeout(r, 100));
  assert.deepEqual(posts.map(p => p.path), ['/runner/api/jobs/12/cancel']);
});
