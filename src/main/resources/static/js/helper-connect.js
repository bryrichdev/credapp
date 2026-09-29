// Connect CredCloud Helper: one click gets a one-time code and hands it to the helper on this
// computer through a credcloud:// link, then waits until CredCloud sees it connected.
(() => {
  const card = document.getElementById('helper-connect');
  const button = document.getElementById('helper-connect-button');
  const message = document.getElementById('helper-connect-message');
  if (!card || !button) return;
  const csrfName = document.querySelector('meta[name="_csrf_header"]');
  const csrfToken = document.querySelector('meta[name="_csrf"]');
  let pairedBefore = null;

  const status = async () => {
    const response = await fetch(card.dataset.status, { headers: { Accept: 'application/json' }, cache: 'no-store' });
    return response.ok ? response.json() : null;
  };
  status().then(now => { pairedBefore = now ? now.lastPairedAt : null; });

  const watch = async started => {
    const now = await status().catch(() => null);
    if (now && now.lastPairedAt && now.lastPairedAt !== pairedBefore) {
      message.textContent = 'Connected. You can close this tab: portals you fill or teach open in CredCloud Helper now.';
      button.hidden = true;
      return;
    }
    if (Date.now() - started > 30000) {
      message.textContent = 'CredCloud Helper didn’t answer. Make sure it’s installed, then press the button again.';
      button.disabled = false;
      return;
    }
    setTimeout(() => watch(started), 1000);
  };

  button.addEventListener('click', async () => {
    button.disabled = true;
    message.textContent = 'Connecting…';
    try {
      const headers = { Accept: 'application/json' };
      if (csrfName && csrfToken) headers[csrfName.content] = csrfToken.content;
      const response = await fetch(card.dataset.connect, { method: 'POST', headers, credentials: 'same-origin' });
      if (!response.ok) throw new Error(response.status);
      const { link } = await response.json();
      window.location.href = link;
      watch(Date.now());
    } catch (e) {
      message.textContent = 'That didn’t work. Reload this page and try again.';
      button.disabled = false;
    }
  });
})();
