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
    // The extension takes the token off the page and writes its own message; only say
    // something here when it never came.
    const status = document.getElementById('credcloud-extension-status');
    if (status && !present && document.getElementById('credcloud-extension-token')) {
      status.textContent = 'CredCloud for Chrome isn\u2019t in this browser, so it wasn\u2019t connected.';
    }
  };
  // The content script runs once the page has loaded; give it a moment.
  setTimeout(check, 1500);
})();
