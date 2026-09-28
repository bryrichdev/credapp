// Runs on CredCloud's own pages. It tells the page the extension is here, hands the extension
// the token from the Connect this browser page, and passes on "a job is ready" markers.
(() => {
  document.documentElement.dataset.credcloudExtension = chrome.runtime.getManifest().version;

  const token = document.getElementById('credcloud-extension-token');
  if (token) {
    const status = document.getElementById('credcloud-extension-status');
    const value = token.dataset.token;
    token.remove();
    chrome.runtime.sendMessage({ type: 'token', token: value }).then(reply => {
      if (status) {
        status.textContent = reply && reply.ok
          ? 'Connected. CredCloud for Chrome can fill portals in this browser now. You can close this tab.'
          : 'That didn’t work. Reload this page and try again.';
      }
    });
  }

  if (document.querySelector('[data-credcloud-job-ready]')) {
    chrome.runtime.sendMessage({ type: 'job-ready' });
  }
})();
