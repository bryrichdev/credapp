// A portal page following the job it just queued for CredCloud Helper: it says when the helper
// has opened it, or why it couldn't. The helper starts when she logs in, so it's normally
// running; if it isn't, the page offers its credcloud:// link, which starts it. That's a
// button, because browsers only open an app's link from a click.
(() => {
  const status = async url => {
    const response = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error(response.status);
    return response.json();
  };

  const box = document.querySelector('[data-helper-job]');
  if (!box) return;
  const message = box.querySelector('[data-helper-message]');
  const reveal = (selector, shown) => box.querySelectorAll(selector).forEach(e => { e.hidden = !shown; });
  const started = Date.now();

  const tick = async () => {
    const waited = Date.now() - started;
    if (waited > 10 * 60 * 1000) return;
    let now;
    try {
      now = await status(box.dataset.status);
    } catch (e) {
      setTimeout(tick, 5000);
      return;
    }
    const job = now.job || {};
    if (job.status === 'claimed') {
      message.textContent = 'Opened in CredCloud Helper. Look for its Chrome window.';
      reveal('[data-helper-setup], [data-helper-start]', false);
      return;
    }
    if (job.status === 'done') {
      message.textContent = 'Finished in CredCloud Helper.';
      reveal('[data-helper-setup], [data-helper-start]', false);
      return;
    }
    if (job.status === 'cancelled') {
      message.textContent = job.error || 'Cancelled.';
      box.classList.add('helper-job--problem');
      reveal('[data-helper-setup], [data-helper-start]', false);
      return;
    }
    if (!now.connected) {
      message.textContent = 'Waiting for CredCloud Helper.';
      reveal('[data-helper-setup]', true);
    } else if (!now.running) {
      message.textContent = 'CredCloud Helper isn’t running. Start it, and it opens the portal:';
      reveal('[data-helper-setup]', false);
      reveal('[data-helper-start]', true);
    } else {
      message.textContent = waited > 15000
        ? 'CredCloud Helper is running but hasn’t opened it yet. It will as soon as it can.'
        : 'Opening it in CredCloud Helper…';
      reveal('[data-helper-setup], [data-helper-start]', false);
    }
    setTimeout(tick, waited > 60000 ? 5000 : 1000);
  };
  tick();
})();
