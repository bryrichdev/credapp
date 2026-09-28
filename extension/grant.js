// The page a job opens on when CredCloud doesn't yet have Chrome's permission for the portal.
// Chrome only asks after a click, so the button is what asks.
(async () => {
  const info = await chrome.runtime.sendMessage({ type: 'grant-info' });
  const message = document.getElementById('message');
  if (!info || !info.origins) {
    message.textContent = 'This job has ended. Start it again from CredCloud.';
    document.getElementById('allow').disabled = true;
    return;
  }
  document.getElementById('host').textContent = info.host;
  document.getElementById('allow').addEventListener('click', async () => {
    const granted = await chrome.permissions.request({ origins: info.origins });
    if (granted) {
      await chrome.runtime.sendMessage({ type: 'granted' });
    } else {
      message.textContent = 'Without this, CredCloud can’t fill this portal. You can use Show answers to copy in CredCloud instead.';
    }
  });
  document.getElementById('cancel').addEventListener('click', () => window.close());
})();
