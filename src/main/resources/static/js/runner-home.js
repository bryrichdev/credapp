// The runner's tab (runner/home.html). The runner program calls runnerShow() with its state
// and listens on window.credcloudRunnerHome, a binding only it adds. Pairing happens here, so
// the code goes from this page straight to CredCloud.
(() => {
  const status = document.getElementById('runner-status');
  const missing = document.getElementById('runner-missing');
  const pair = document.getElementById('runner-pair');
  const form = document.getElementById('runner-pair-form');
  const code = document.getElementById('runner-code');
  const error = document.getElementById('runner-pair-error');

  window.runnerShow = state => {
    status.textContent = state.text;
    status.dataset.state = state.state;
    missing.hidden = true;
    pair.hidden = state.state !== 'pairing';
  };

  form.addEventListener('submit', async event => {
    event.preventDefault();
    error.textContent = '';
    try {
      const response = await fetch('/runner/api/pair', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin',
        body: JSON.stringify({ code: code.value })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok || !body.token) {
        error.textContent = body.error || 'That didn’t work. Make a new code in CredCloud.';
        return;
      }
      if (typeof window.credcloudRunnerHome !== 'function') {
        error.textContent = 'Open this page from CredCloud Runner, not a normal browser tab.';
        return;
      }
      window.credcloudRunnerHome(JSON.stringify({ action: 'paired', token: body.token }));
      code.value = '';
      status.textContent = 'Connected. Waiting for a job from CredCloud.';
      pair.hidden = true;
    } catch (e) {
      error.textContent = 'Couldn’t reach CredCloud. Check the connection and try again.';
    }
  });
})();
