// Works with CredCloud for Chrome, whose content script marks the page with
// data-credcloud-extension. Shows the install hint when the extension isn't there, and tells
// the connect page when the extension didn't take its token.
(() => {
  const root = document.documentElement;
  const show = (selector, visible) => document.querySelectorAll(selector).forEach(e => { e.hidden = !visible; });
  const check = () => {
    const present = root.dataset.credcloudExtension !== undefined;
    show('[data-credcloud-present]', present);
    show('[data-credcloud-absent]', !present);
    show('[data-credcloud-missing]', !present);
    const status = document.getElementById('credcloud-extension-status');
    if (status && document.getElementById('credcloud-extension-token')) {
      status.textContent = present ? 'Connecting…' : 'CredCloud for Chrome isn’t in this browser, so it wasn’t connected.';
    }
  };
  // The content script runs once the page has loaded; give it a moment.
  setTimeout(check, 1500);
})();
