// The live view of a browser CredCloud runs on the server. Pictures of the page arrive as
// server-sent events and are drawn on a canvas. A transparent textarea over the canvas takes
// her clicks, keys and pastes (a textarea, so phone keyboards, accents and pasting all work),
// and they go back in order, one request at a time, batched while one is in flight.
(() => {
  const root = document.getElementById('live');
  if (!root) return;

  const width = Number(root.dataset.width);
  const height = Number(root.dataset.height);
  const canvas = document.getElementById('live-canvas');
  const draw2d = canvas.getContext('2d');
  const keys = document.getElementById('live-keys');
  const cover = document.getElementById('live-cover');
  const address = document.getElementById('live-address');
  const message = document.getElementById('live-message');

  const NAMED = new Set(['Enter', 'Tab', 'Backspace', 'Delete', 'Escape', 'ArrowUp', 'ArrowDown', 'ArrowLeft',
    'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown']);
  let over = false;

  // --- Sending what she does ---

  function headers(extra) {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    const all = Object.assign({}, extra);
    if (token && header && header.content) all[header.content] = token.content;
    return all;
  }

  const queue = [];
  let sending = false;

  function send(event) {
    if (over) return;
    const last = queue[queue.length - 1];
    if (event.type === 'move' && last && last.type === 'move') {
      queue[queue.length - 1] = event;
    } else {
      queue.push(event);
    }
    flush();
  }

  async function flush() {
    if (sending || !queue.length || over) return;
    sending = true;
    const batch = queue.splice(0, 500);
    try {
      const response = await fetch(root.dataset.input, {
        method: 'POST', credentials: 'same-origin', body: JSON.stringify(batch),
        headers: headers({ 'Content-Type': 'application/json' })
      });
      if (response.status === 404) ended('This browser has closed.');
      else if (!response.ok) say('CredCloud didn’t take that (' + response.status + '). Reload this page if it keeps happening.');
    } catch (e) {
      say('Lost touch with CredCloud. Check your connection.');
    } finally {
      sending = false;
      if (queue.length) flush();
    }
  }

  function post(url) {
    return fetch(url, { method: 'POST', credentials: 'same-origin', headers: headers({}) }).catch(() => {});
  }

  function point(e) {
    const box = keys.getBoundingClientRect();
    return {
      x: Math.round((e.clientX - box.left) * width / box.width),
      y: Math.round((e.clientY - box.top) * height / box.height)
    };
  }

  // --- Mouse and touch ---

  const BUTTONS = ['left', 'middle', 'right'];
  let lastMove = 0;
  let touch = null;

  keys.addEventListener('pointerdown', e => {
    e.preventDefault();
    keys.focus({ preventScroll: true });
    const at = point(e);
    if (e.pointerType === 'touch') {
      touch = { x: e.clientX, y: e.clientY, start: at, moved: false };
      return;
    }
    keys.setPointerCapture(e.pointerId);
    send({ type: 'down', ...at, button: BUTTONS[e.button] || 'left', clicks: e.detail || 1 });
  });

  keys.addEventListener('pointermove', e => {
    if (e.pointerType === 'touch') {
      if (!touch) return;
      // A finger drag scrolls the portal rather than selecting text.
      const box = keys.getBoundingClientRect();
      const dx = (touch.x - e.clientX) * width / box.width;
      const dy = (touch.y - e.clientY) * height / box.height;
      if (Math.abs(dx) + Math.abs(dy) < 2) return;
      touch.moved = touch.moved || Math.abs(dx) + Math.abs(dy) > 8;
      touch.x = e.clientX;
      touch.y = e.clientY;
      send({ type: 'wheel', ...touch.start, dx, dy });
      return;
    }
    const now = performance.now();
    if (e.buttons || now - lastMove > 60) {
      lastMove = now;
      send({ type: 'move', ...point(e) });
    }
  });

  keys.addEventListener('pointerup', e => {
    if (e.pointerType === 'touch') {
      if (touch && !touch.moved) {
        send({ type: 'down', ...touch.start, button: 'left', clicks: 1 });
        send({ type: 'up', ...touch.start, button: 'left', clicks: 1 });
      }
      touch = null;
      return;
    }
    send({ type: 'up', ...point(e), button: BUTTONS[e.button] || 'left', clicks: e.detail || 1 });
  });

  keys.addEventListener('pointercancel', () => { touch = null; });
  keys.addEventListener('contextmenu', e => e.preventDefault());

  keys.addEventListener('wheel', e => {
    e.preventDefault();
    const scale = e.deltaMode === 1 ? 40 : e.deltaMode === 2 ? height : 1;
    send({ type: 'wheel', ...point(e), dx: e.deltaX * scale, dy: e.deltaY * scale });
  }, { passive: false });

  // --- Keys, typing and pasting ---

  function modifiers(e) {
    const held = [];
    if (e.shiftKey) held.push('Shift');
    if (e.ctrlKey) held.push('Control');
    if (e.altKey) held.push('Alt');
    if (e.metaKey) held.push('Meta');
    return held;
  }

  keys.addEventListener('keydown', e => {
    if (e.isComposing || over) return;
    const shortcut = (e.ctrlKey || e.metaKey) && e.key.length === 1;
    if (shortcut && e.key.toLowerCase() === 'v') return; // becomes a paste event below
    if (NAMED.has(e.key) || shortcut) {
      e.preventDefault();
      send({ type: 'key', key: e.key, modifiers: modifiers(e) });
    }
    // Anything else is a character: the textarea gets it and the input event sends it.
  });

  function typed() {
    const text = keys.value;
    keys.value = '';
    if (text) send({ type: 'type', text });
  }

  keys.addEventListener('input', e => {
    if (!e.isComposing) typed();
  });
  keys.addEventListener('compositionend', typed);

  keys.addEventListener('paste', e => {
    e.preventDefault();
    const text = e.clipboardData ? e.clipboardData.getData('text/plain') : '';
    if (text) send({ type: 'paste', text });
  });

  document.getElementById('live-back').addEventListener('click', () => {
    post(root.dataset.back);
    keys.focus({ preventScroll: true });
  });
  document.getElementById('live-reload').addEventListener('click', () => {
    post(root.dataset.reload);
    keys.focus({ preventScroll: true });
  });

  // --- The picture and the status ---

  let pending = null;
  let drawing = false;

  async function draw() {
    if (drawing || !pending) return;
    drawing = true;
    const data = pending;
    pending = null;
    try {
      const binary = atob(data);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      const picture = await createImageBitmap(new Blob([bytes], { type: 'image/jpeg' }));
      draw2d.drawImage(picture, 0, 0, width, height);
      picture.close();
      if (!over) cover.hidden = true;
    } catch (e) {
      // A bad frame; the next one replaces it.
    } finally {
      drawing = false;
      if (pending) draw();
    }
  }

  function say(text) {
    message.textContent = text || '';
    message.hidden = !text;
  }

  function ended(text) {
    over = true;
    queue.length = 0;
    cover.textContent = text;
    cover.hidden = false;
    keys.disabled = true;
    stream.close();
  }

  const stream = new EventSource(root.dataset.stream);
  stream.addEventListener('frame', e => {
    pending = e.data;
    draw();
  });
  stream.addEventListener('status', e => {
    const status = JSON.parse(e.data);
    address.textContent = status.url || '…';
    say(status.message);
    if (status.state === 'failed') ended(status.message || 'CredCloud’s browser stopped.');
    else if (status.state === 'ended') ended('This browser has closed. ' + (status.message || ''));
  });
  stream.addEventListener('error', () => {
    if (over) return;
    if (stream.readyState === EventSource.CLOSED) {
      ended('This browser has closed, or CredCloud can’t reach it. Open the portal again to start a new one.');
    } else {
      say('Reconnecting to CredCloud’s browser…');
    }
  });
  stream.addEventListener('open', () => {
    if (!over && message.textContent.startsWith('Reconnecting')) say('');
  });

  keys.focus({ preventScroll: true });
})();
