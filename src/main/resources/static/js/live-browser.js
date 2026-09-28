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
  // While teaching and picking, a click goes to CredCloud (which box is this?), not the portal.
  let picker = null;
  const swallowed = new Set();

  keys.addEventListener('pointerdown', e => {
    e.preventDefault();
    keys.focus({ preventScroll: true });
    const at = point(e);
    if (picker) {
      swallowed.add(e.pointerId);
      picker(at);
      return;
    }
    if (e.pointerType === 'touch') {
      touch = { x: e.clientX, y: e.clientY, start: at, moved: false };
      return;
    }
    keys.setPointerCapture(e.pointerId);
    send({ type: 'down', ...at, button: BUTTONS[e.button] || 'left', clicks: e.detail || 1 });
  });

  keys.addEventListener('pointermove', e => {
    if (swallowed.has(e.pointerId)) return;
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
    if (swallowed.delete(e.pointerId)) return;
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
    picker = null;
    document.querySelectorAll('#live-fill-page, #live-pick, #live-save').forEach(button => { button.disabled = true; });
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

  // --- Filling a provider's answers ---

  const fillSection = document.getElementById('live-fill');
  if (fillSection) {
    const button = document.getElementById('live-fill-page');
    const progress = document.getElementById('live-progress');
    const note = document.getElementById('live-fill-message');
    button.addEventListener('click', async () => {
      button.disabled = true;
      note.classList.remove('is-problem');
      note.textContent = 'Filling…';
      try {
        const response = await fetch(fillSection.dataset.fill, {
          method: 'POST', credentials: 'same-origin', headers: headers({})
        });
        if (response.status === 404) {
          ended('This browser has closed.');
          note.textContent = '';
          return;
        }
        if (!response.ok) throw new Error(String(response.status));
        const result = await response.json();
        note.textContent = result.message;
        note.classList.toggle('is-problem', result.problem);
        progress.textContent = `${result.done} of ${result.total} boxes filled`;
        result.filled.forEach(index => {
          const row = fillSection.querySelector(`[data-index="${index}"]`);
          if (row) row.classList.add('is-filled');
        });
      } catch (e) {
        note.textContent = 'That didn’t go through. Press Fill this page again.';
        note.classList.add('is-problem');
      } finally {
        button.disabled = over;
        keys.focus({ preventScroll: true });
      }
    });
  }

  // --- Teaching CredCloud the portal ---

  let unsaved = false;
  const teachSection = document.getElementById('live-teach');
  if (teachSection) {
    const pickButton = document.getElementById('live-pick');
    const saveButton = document.getElementById('live-save');
    const form = document.getElementById('live-teach-form');
    const teachNote = document.getElementById('live-teach-message');
    const list = document.getElementById('live-teach-boxes');
    const count = document.getElementById('live-teach-count');
    const fixedLabel = document.getElementById('live-fixed-label');
    let pending = null;

    const tell = (text, problem) => {
      teachNote.textContent = text;
      teachNote.classList.toggle('is-problem', Boolean(problem));
    };

    async function call(url, body) {
      const options = { method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin', headers: headers({}) };
      if (body !== undefined && body !== null) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
      }
      const response = await fetch(url, options);
      if (response.status === 404) {
        ended('This browser has closed.');
        return null;
      }
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        tell(data.error || 'That didn’t go through. Try again.', true);
        return null;
      }
      return data;
    }

    // Built with textContent: box names come from the portal's pages.
    function render(state) {
      list.replaceChildren(...state.boxes.map((box, index) => {
        const row = document.createElement('li');
        const label = document.createElement('span');
        label.className = 'live__answer-label';
        label.textContent = box.label;
        const detail = document.createElement('span');
        detail.className = 'live__answer';
        detail.textContent = box.detail;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'live__remove';
        remove.textContent = 'Remove';
        remove.setAttribute('aria-label', 'Remove ' + box.label);
        remove.addEventListener('click', async () => {
          const next = await call(teachSection.dataset.remove + '?index=' + index, null);
          if (next) render(next);
        });
        row.append(label, detail, ' ', remove);
        return row;
      }));
      count.textContent = state.boxes.length === 1 ? '1 box' : state.boxes.length + ' boxes';
      unsaved = state.unsaved;
      if (state.message) tell(state.message);
    }

    function setPicking(on) {
      picker = on ? pickAt : null;
      pickButton.setAttribute('aria-pressed', String(on));
      pickButton.textContent = on ? 'Stop picking' : 'Pick a box';
      root.classList.toggle('is-picking', on);
      if (on) tell('Click a box in the portal. Clicks go to CredCloud, not the portal, until you stop picking.');
    }

    async function pickAt(at) {
      const found = await call(teachSection.dataset.pick, { x: at.x, y: at.y });
      if (!found) return;
      if (found.problem) {
        tell(found.problem, true);
        return;
      }
      pending = found.box;
      setPicking(false);
      form.hidden = false;
      form.elements.label.value = pending.label;
      form.elements.source.value = '';
      form.elements.format.value = 'AS_SAVED';
      form.elements.defaultValue.value = '';
      fixedLabel.textContent = 'Fixed answer';
      tell('Say what goes in this box, then press Add box.');
      form.elements.source.focus();
    }

    form.elements.source.addEventListener('change', () => {
      fixedLabel.textContent = form.elements.source.value ? 'If there’s no data, use' : 'Fixed answer';
    });

    form.addEventListener('submit', async e => {
      e.preventDefault();
      if (!pending) return;
      const state = await call(teachSection.dataset.add, {
        ...pending,
        label: form.elements.label.value,
        source: form.elements.source.value,
        format: form.elements.format.value,
        defaultValue: form.elements.defaultValue.value
      });
      if (!state) return;
      pending = null;
      form.hidden = true;
      render(state);
      setPicking(true);
      tell(state.message);
    });

    document.getElementById('live-discard').addEventListener('click', () => {
      pending = null;
      form.hidden = true;
      tell('Discarded. Press Pick a box to pick another.');
    });

    pickButton.addEventListener('click', () => {
      form.hidden = true;
      pending = null;
      setPicking(!picker);
      if (!picker) tell('Stopped picking. Clicks go to the portal again.');
    });

    saveButton.addEventListener('click', async () => {
      saveButton.disabled = true;
      const state = await call(teachSection.dataset.save, null);
      saveButton.disabled = over;
      if (state) {
        setPicking(false);
        render(state);
      }
    });

    call(teachSection.dataset.state).then(state => { if (state) render(state); });
  }

  const done = document.getElementById('live-done');
  if (done) {
    done.addEventListener('submit', e => {
      if (over) return;
      const question = done.dataset.confirm
        || (unsaved ? 'Close CredCloud’s browser without saving the boxes you changed?' : '');
      if (question && !window.confirm(question)) e.preventDefault();
    });
  }

  keys.focus({ preventScroll: true });
})();
