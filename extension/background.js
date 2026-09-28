// CredCloud for Chrome, the background half. It keeps each CredCloud server's device token,
// fetches jobs when a CredCloud page says one is ready, opens the portal in a new tab, and
// holds that tab's job while the coordinator works through the portal's pages.
//
// Jobs live in chrome.storage.session, keyed by tab, because Chrome stops this worker when
// it's idle. Tokens live in chrome.storage.local, keyed by the CredCloud origin that issued
// them, so production, staging and a local server each have their own.

// CredCloud's own sites, from the manifest. Ports don't count, as in Chrome's match patterns.
const CREDCLOUD = new Set(chrome.runtime.getManifest().content_scripts
  .flatMap(script => script.matches).map(match => new URL(match.replace('/*', '/')).origin));

function isCredCloud(origin) {
  try {
    const url = new URL(origin);
    return CREDCLOUD.has(`${url.protocol}//${url.hostname}`);
  } catch (e) {
    return false;
  }
}

// --- Storage ---

async function tokenFor(server) {
  const { tokens = {} } = await chrome.storage.local.get('tokens');
  return tokens[server];
}

async function setToken(server, token) {
  const { tokens = {} } = await chrome.storage.local.get('tokens');
  if (token) tokens[server] = token; else delete tokens[server];
  await chrome.storage.local.set({ tokens });
}

async function sessionFor(tabId) {
  const key = 'tab-' + tabId;
  return (await chrome.storage.session.get(key))[key];
}

async function saveSession(tabId, session) {
  await chrome.storage.session.set({ ['tab-' + tabId]: session });
}

async function dropSession(tabId) {
  await chrome.storage.session.remove('tab-' + tabId);
}

// --- Talking to CredCloud ---

async function api(server, method, path, body) {
  const token = await tokenFor(server);
  const headers = { Authorization: 'Bearer ' + token };
  if (body) headers['Content-Type'] = 'application/json';
  // credentials: on staging, Cloudflare Access lets the request through on its cookie.
  return fetch(server + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined, credentials: 'include', cache: 'no-store'
  });
}

async function post(server, path, body) {
  const response = await api(server, 'POST', path, body);
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.error || 'CredCloud didn’t accept that (' + response.status + '). Try again.');
  }
  return response.status === 204 ? {} : response.json();
}

function openConnect(server, near) {
  return chrome.tabs.create({ url: server + '/extension/connect', index: near ? near.index + 1 : undefined });
}

/** Takes the next job for this browser, if there is one, and opens its portal. */
async function claim(server, near) {
  if (!(await tokenFor(server))) {
    await openConnect(server, near);
    return;
  }
  const response = await api(server, 'GET', '/runner/api/jobs/next');
  if (response.status === 401) {
    await setToken(server, null);
    await openConnect(server, near);
    return;
  }
  if (response.status !== 200) return;
  const job = await response.json();
  await openJob(server, job, near);
}

// --- Portals ---

function isAddress(host) {
  return /^[\d.]+$/.test(host) || host.includes(':') || !host.includes('.');
}

/** The last two labels of a host: portal.payer.com and login.payer.com are one site. */
function site(host) {
  if (isAddress(host)) return host;
  return host.toLowerCase().split('.').slice(-2).join('.');
}

function samePortal(pageUrl, startUrl) {
  try {
    return site(new URL(pageUrl).hostname) === site(new URL(startUrl).hostname);
  } catch (e) {
    return false;
  }
}

/** What to ask Chrome for: the portal's whole site, so its sign-in pages work too. */
function portalOrigins(startUrl) {
  const url = new URL(startUrl);
  const host = site(url.hostname);
  return isAddress(host) ? [`${url.protocol}//${host}/*`] : [`${url.protocol}//${host}/*`, `${url.protocol}//*.${host}/*`];
}

async function openJob(server, job, near) {
  const origins = portalOrigins(job.startUrl);
  const allowed = await chrome.permissions.contains({ origins });
  const session = {
    server, job, fields: job.fields || [], filled: [], pending: null, picking: false, finished: false,
    awaitingPermission: !allowed, tone: '',
    message: job.kind === 'fill' ? 'Sign in, open the form, then press Fill this page.'
      : (job.fields || []).length ? `Starting from version ${job.revision}. Pick more boxes or remove any that changed.`
        : 'Sign in and open the form. Then press Pick a box on the page.'
  };
  const tab = await chrome.tabs.create({
    url: allowed ? job.startUrl : chrome.runtime.getURL('grant.html'),
    index: near ? near.index + 1 : undefined
  });
  await saveSession(tab.id, session);
}

async function inject(tabId, url) {
  const session = await sessionFor(tabId);
  if (!session || session.awaitingPermission || !/^https?:/.test(url)) return;
  if (!(await chrome.permissions.contains({ origins: [new URL(url).origin + '/*'] }))) return;
  await chrome.scripting.executeScript({ target: { tabId }, files: ['panel.js', 'portal.js'] }).catch(() => {});
}

chrome.tabs.onUpdated.addListener((tabId, change, tab) => {
  if (change.status === 'complete' && tab.url) inject(tabId, tab.url);
});

chrome.tabs.onRemoved.addListener(async tabId => {
  const session = await sessionFor(tabId);
  if (!session) return;
  await dropSession(tabId);
  if (!session.finished) {
    await post(session.server, `/runner/api/jobs/${session.job.id}/cancel`).catch(() => {});
  }
});

// --- The panel ---

function state(session) {
  const job = session.job;
  const base = {
    mode: job.kind, title: `${job.payerName} / ${job.templateName}`, provider: job.providerName || '',
    message: session.message, tone: session.tone, finished: session.finished, picking: session.picking
  };
  if (job.kind === 'fill') return base;
  const labels = Object.fromEntries((job.sources || []).map(s => [s.key, s.label]));
  return {
    ...base, pending: session.pending, sources: job.sources, formats: job.formats,
    fields: session.fields.map(f => ({
      label: f.label, detail: f.source ? (labels[f.source] || f.source) : `always "${f.defaultValue}"`
    }))
  };
}

/**
 * The answers to type on this page. Each box remembers the page it was taught on. When some
 * boxes were taught on this page's address, only those (and any without a page) are filled,
 * so an "Address" box taught on page 3 doesn't land in page 2's "Address". When none match,
 * as on a portal whose steps all share one address or whose addresses change per
 * application, every unfilled box found on the page is filled.
 */
function forThisPage(session, url) {
  const open = session.job.answers.filter(a => !session.filled.includes(a.field) && a.value);
  let path = '';
  try {
    path = new URL(url).pathname;
  } catch (e) {
    return open;
  }
  const page = a => session.fields[a.field].page || '';
  return open.some(a => page(a) === path) ? open.filter(a => page(a) === path || !page(a)) : open;
}

function say(session, message, tone = '') {
  session.message = message;
  session.tone = tone;
}

/** Handles a press in the panel. Returns the panel's new state, and for Fill, what to type. */
async function panel(tabId, url, action, data = {}) {
  const session = await sessionFor(tabId);
  if (!session) return { state: null };
  const job = session.job;
  let command = null;
  if (session.finished && action !== 'render') return { state: state(session) };
  try {
    switch (action) {
      case 'render':
        break;
      case 'fill':
        if (job.kind !== 'fill') break;
        if (!samePortal(url, job.startUrl)) {
          say(session, 'This page isn’t on the portal the fill was sent for, so CredCloud won’t type into it.', 'error');
          break;
        }
        command = { type: 'fill', items: forThisPage(session, url).map(a => ({ index: a.field, field: session.fields[a.field], value: a.value })) };
        break;
      case 'filled': {
        const report = data;
        session.filled = [...new Set([...session.filled, ...report.filled])];
        const left = session.fields.length - session.filled.length;
        say(session, `Filled ${report.filled.length} boxes on this page. ${left} not filled yet.`
          + (report.problems.length ? ' Left alone: ' + report.problems.join('; ') + '.' : ''),
          report.problems.length ? 'error' : '');
        break;
      }
      case 'done': {
        if (job.kind !== 'fill') break;
        const filled = session.fields.filter((f, i) => session.filled.includes(i)).map(f => f.label);
        const missed = session.fields.filter((f, i) => !session.filled.includes(i)).map(f => f.label);
        await post(session.server, `/runner/api/jobs/${job.id}/filled`, { filled, missed });
        session.finished = true;
        say(session, `Filled ${filled.length} of ${session.fields.length} boxes.`
          + (missed.length ? ' Not filled: ' + missed.join(', ') + '.' : '')
          + ' Check every page, then submit it yourself.', 'done');
        break;
      }
      case 'cancel':
        await post(session.server, `/runner/api/jobs/${job.id}/cancel`);
        session.finished = true;
        say(session, 'Cancelled. Nothing was saved.', 'done');
        break;
      case 'startPicking':
        session.picking = true;
        say(session, 'Click a box on the page. Clicks go to CredCloud, not the portal, until you stop.');
        break;
      case 'stopPicking':
        session.picking = false;
        say(session, '');
        break;
      case 'notABox':
        say(session, 'That isn’t a box CredCloud can fill. It only types, picks options and ticks boxes.', 'error');
        break;
      case 'unclear':
        say(session, 'CredCloud couldn’t pin that box down. Try clicking its label instead.', 'error');
        break;
      case 'picked':
        if (job.kind === 'fill' || !session.picking) break;
        session.pending = {
          label: data.label || `Box ${session.fields.length + 1}`, by: data.by, locator: data.locator,
          kind: data.kind, page: data.page
        };
        session.picking = false;
        say(session, '');
        break;
      case 'add': {
        if (!session.pending) break;
        const source = (data.source || '').trim();
        const fixed = (data.defaultValue || '').trim();
        if (!source && !fixed) {
          say(session, 'Choose data, or type a fixed answer.', 'error');
          break;
        }
        const p = session.pending;
        session.fields.push({
          label: (data.label || '').trim() || p.label, by: p.by, locator: p.locator, kind: p.kind,
          source, format: data.format || '', defaultValue: fixed, page: p.page
        });
        session.pending = null;
        session.picking = true;
        say(session, 'Added. Click the next box, or stop picking.');
        break;
      }
      case 'discard':
        session.pending = null;
        say(session, '');
        break;
      case 'remove':
        if (data.index >= 0 && data.index < session.fields.length) session.fields.splice(data.index, 1);
        break;
      case 'save': {
        if (job.kind === 'fill') break;
        if (!session.fields.length) {
          say(session, 'Pick at least one box first.', 'error');
          break;
        }
        const saved = await post(session.server, `/runner/api/jobs/${job.id}/learned`, { fields: session.fields });
        session.finished = true;
        session.picking = false;
        say(session, `Saved as version ${saved.revision}. It’s ready to fill in CredCloud.`, 'done');
        break;
      }
      default:
        break;
    }
  } catch (e) {
    say(session, e.message, 'error');
  }
  await saveSession(tabId, session);
  return { state: state(session), command };
}

// --- Messages from CredCloud pages, the portal panel and the permission page ---

async function handle(message, sender) {
  const origin = sender.url ? new URL(sender.url).origin : '';
  switch (message.type) {
    case 'token':
      if (!isCredCloud(origin) || !message.token) return { ok: false };
      await setToken(origin, message.token);
      await claim(origin, sender.tab);
      return { ok: true };
    case 'job-ready':
      if (!isCredCloud(origin)) return {};
      await claim(origin, sender.tab);
      return {};
    case 'panel':
      return panel(sender.tab.id, sender.url, message.action, message.data);
    case 'grant-info': {
      const session = await sessionFor(sender.tab.id);
      return session ? { host: new URL(session.job.startUrl).hostname, origins: portalOrigins(session.job.startUrl) } : {};
    }
    case 'granted': {
      const session = await sessionFor(sender.tab.id);
      if (!session) return {};
      session.awaitingPermission = false;
      await saveSession(sender.tab.id, session);
      await chrome.tabs.update(sender.tab.id, { url: session.job.startUrl });
      return {};
    }
    default:
      return {};
  }
}

chrome.runtime.onMessage.addListener((message, sender, reply) => {
  if (sender.id !== chrome.runtime.id) return false;
  handle(message, sender).then(reply, error => reply({ error: error.message }));
  return true;
});
