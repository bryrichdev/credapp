// CredCloud Helper's script for portal pages. The helper runs it in every page and frame of its
// Chrome, in an isolated world: a JavaScript world of its own, next to the page's. The portal's
// scripts can't see its variables or call it, and the portal's Content-Security-Policy doesn't
// apply to it. It talks to the helper through credcloudSend, a binding that exists only in
// this world.
//
// The helper tells it what to draw with __credcloud.render(state), and what to type with
// __credcloud.fill(items). On a job's pages it shows the CredCloud panel (top frame only); in
// learn mode it reports the box she clicks, in any frame. It draws the panel in a closed shadow
// root and only acts on real clicks, so a portal page can't read the panel or press it.
//
// Filling types into text boxes, picks dropdown options, and ticks checkboxes and radio
// buttons: a radio button or checkbox is ticked when the answer is Yes or names that option,
// so all the options of one question can share one piece of data. Before touching an element
// it checks what the element really is, so a template that points at a button, or a page that
// changed, can't make it press anything. It never submits.
(() => {
  if (globalThis.__credcloud) return;

  const TOP = window === window.top;
  const BOXES = 'input, select, textarea, [contenteditable="true"], [role="checkbox"], [role="radio"], [role="textbox"]';
  const YES = new Set(['yes', 'y', 'true', '1', 'x', 'checked', 'on']);
  const NO = new Set(['no', 'n', 'false', '0', 'unchecked', 'off']);
  const clean = text => String(text ?? '').replace(/\s+/g, ' ').replace(/[\s:*]+$/, '').trim().toLowerCase();
  const parts = value => String(value).split(/[,;|\n]/).map(clean).filter(Boolean);

  let state = null;

  function send(action, data = {}) {
    if (typeof globalThis.credcloudSend !== 'function') return false;
    globalThis.credcloudSend(JSON.stringify({ action, data, url: location.href, top: TOP }));
    return true;
  }

  // --- Boxes: what one is, what it's called, how to find it again ---

  function labelTextOf(e) {
    const own = e.labels && e.labels.length ? e.labels[0].innerText : '';
    const labelledBy = (e.getAttribute('aria-labelledby') || '').split(/\s+/)
      .map(id => id && document.getElementById(id)).filter(Boolean).map(n => n.innerText).join(' ');
    return (own || e.getAttribute('aria-label') || labelledBy || '').replace(/\s+/g, ' ').trim();
  }

  /** A name for people: the label without a required-field asterisk, or the placeholder. */
  function nameOf(e) {
    return (labelTextOf(e) || e.getAttribute('placeholder') || '').replace(/\s*\*\s*$/, '').trim();
  }

  function kindOf(e) {
    const tag = e.tagName.toLowerCase();
    const type = (e.getAttribute('type') || 'text').toLowerCase();
    if (tag === 'select') return 'select';
    if (tag === 'input' && ['submit', 'button', 'image', 'reset', 'file', 'hidden'].includes(type)) return null;
    if (tag === 'input' && (type === 'checkbox' || type === 'radio')) return type;
    const role = e.getAttribute('role');
    if (role === 'checkbox' || role === 'radio') return role;
    return 'text';
  }

  function unique(selector) {
    try { return document.querySelectorAll(selector).length === 1; } catch (err) { return false; }
  }

  function cssOf(e) {
    if (e.id && unique('#' + CSS.escape(e.id))) return '#' + CSS.escape(e.id);
    const tag = e.tagName.toLowerCase();
    const name = e.getAttribute('name');
    if (name) {
      let selector = `${tag}[name="${CSS.escape(name)}"]`;
      if (e.value && (e.type === 'radio' || e.type === 'checkbox')) selector += `[value="${CSS.escape(e.value)}"]`;
      if (unique(selector)) return selector;
    }
    const steps = [];
    for (let node = e; node && node !== document.body && node !== document.documentElement; node = node.parentElement) {
      if (node.id && unique('#' + CSS.escape(node.id))) { steps.unshift('#' + CSS.escape(node.id)); break; }
      const same = Array.from(node.parentElement ? node.parentElement.children : [])
        .filter(sibling => sibling.tagName === node.tagName);
      const tagName = node.tagName.toLowerCase();
      steps.unshift(same.length > 1 ? `${tagName}:nth-of-type(${same.indexOf(node) + 1})` : tagName);
    }
    return steps.join(' > ');
  }

  function findByLabel(text) {
    return Array.from(document.querySelectorAll(BOXES)).filter(e => labelTextOf(e) === text);
  }

  /**
   * How to find this box next time: by its label when the label names only this box, which
   * survives most redesigns, or by a CSS selector. Null if it isn't a box; 'unclear' if
   * neither pins it down.
   */
  function describe(e) {
    const kind = kindOf(e);
    if (!kind) return null;
    const labelText = labelTextOf(e);
    if (labelText && findByLabel(labelText).length === 1) {
      return { label: nameOf(e), by: 'label', locator: labelText, kind };
    }
    const css = cssOf(e);
    if (css && unique(css)) return { label: nameOf(e), by: 'css', locator: css, kind };
    return 'unclear';
  }

  /** The box a click was on: a click on its label counts. */
  function boxAt(target) {
    if (!(target instanceof Element)) return null;
    let box = target.closest(BOXES);
    const label = target.closest('label');
    if (!box && label && label.control) box = label.control;
    return box;
  }

  // --- Learn mode: picking boxes ---

  const outlined = new Map();

  function outline(e, style) {
    if (!outlined.has(e)) outlined.set(e, [e.style.outline, e.style.outlineOffset]);
    e.style.outline = style;
    e.style.outlineOffset = '1px';
  }

  function restore(e) {
    const saved = outlined.get(e);
    if (!saved) return;
    outlined.delete(e);
    [e.style.outline, e.style.outlineOffset] = saved;
  }

  let hovered = null;
  const picking = () => !!(state && state.picking && !state.finished);
  const inPanel = event => host && event.composedPath().includes(host);

  function onHover(event) {
    if (!picking() || inPanel(event)) return;
    const box = boxAt(event.target);
    if (box === hovered) return;
    if (hovered) restore(hovered);
    hovered = box;
    if (box) outline(box, '2px dashed #14696b');
  }

  // While she's picking, clicks are CredCloud's: the portal never sees them, so a dropdown
  // doesn't open and a link doesn't navigate.
  function swallow(event) {
    if (!picking() || inPanel(event) || !event.isTrusted) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }

  function onPick(event) {
    if (!picking() || inPanel(event) || !event.isTrusted) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    const box = boxAt(event.target);
    const described = box ? describe(box) : null;
    if (!described) return send('notABox');
    if (described === 'unclear') return send('unclear');
    if (hovered) restore(hovered);
    hovered = null;
    outline(box, '2px solid #14696b');
    send('picked', described);
  }

  for (const type of ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'dblclick', 'auxclick']) {
    window.addEventListener(type, swallow, true);
  }
  window.addEventListener('click', onPick, true);
  window.addEventListener('mouseover', onHover, true);

  // --- Filling ---

  function names(element) {
    return [labelTextOf(element), element.getAttribute('value'), element.getAttribute('aria-label')]
      .map(clean).filter(Boolean);
  }

  /** Whether an answer names this option: "Female", or just "F" for it. */
  function namesMatch(optionNames, value) {
    return parts(value).some(part => optionNames.some(name =>
      name === part || (part.length === 1 && name.startsWith(part))));
  }

  /**
   * Whether a radio button or checkbox should be ticked. A Yes or No option follows a yes/no
   * answer. Any option is ticked by a plain Yes, as a fixed answer, and otherwise when the
   * answer names it.
   */
  function shouldTick(element, value) {
    const answer = clean(value);
    const own = names(element);
    if (own.some(n => YES.has(n))) return YES.has(answer);
    if (own.some(n => NO.has(n))) return NO.has(answer);
    if (YES.has(answer)) return true;
    if (NO.has(answer)) return false;
    return namesMatch(own, value);
  }

  function locate(field) {
    if (field.by === 'label') return findByLabel(field.locator)[0] || null;
    try {
      const found = document.querySelector(field.locator);
      return found && found.matches(BOXES) ? found : (found ? 'forbidden' : null);
    } catch (e) {
      return null;
    }
  }

  // Sets a value the way typing does, so pages built with React and the like notice it.
  function type(element, value) {
    element.focus();
    if (element.isContentEditable) {
      element.textContent = value;
    } else {
      const proto = element instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(element, value);
    }
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.blur();
  }

  /** Picks the option the answer names. A list box that takes several picks every one named. */
  function choose(select, value) {
    const options = Array.from(select.options);
    const matches = option => namesMatch([clean(option.text), clean(option.value)], value);
    if (select.multiple) {
      if (!options.some(matches)) return false;
      options.forEach(option => { option.selected = matches(option); });
    } else {
      const wanted = clean(value);
      const option = options.find(o => clean(o.text) === wanted) || options.find(o => clean(o.value) === wanted);
      if (!option) return false;
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(select, option.value);
    }
    select.dispatchEvent(new Event('input', { bubbles: true }));
    select.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }

  function checked(element) {
    return element.getAttribute('role') ? element.getAttribute('aria-checked') === 'true' : element.checked;
  }

  /** Fills what's in this frame. Returns the indexes it filled and what it left alone. */
  function fill(items) {
    const filled = [];
    const problems = [];
    // One box, one answer per press: if two boxes of the template find the same element, the
    // first gets it and the other waits for a later page.
    const used = new Set();
    for (const { index, field, value } of items) {
      const element = locate(field);
      if (element === null) continue; // on another page or frame
      if (used.has(element)) continue;
      if (element !== 'forbidden') used.add(element);
      const actual = element === 'forbidden' ? null : kindOf(element);
      if (actual !== field.kind) {
        problems.push(`${field.label}: the page has ${actual ? 'a ' + actual : 'a button or something else that isn’t a box'} there now`);
        continue;
      }
      if (actual === 'text') {
        type(element, value);
      } else if (actual === 'select') {
        if (!choose(element, value)) {
          problems.push(`${field.label}: no option called "${value}"`);
          continue;
        }
      } else if (actual === 'checkbox') {
        if (checked(element) !== shouldTick(element, value)) element.click();
      } else if (actual === 'radio') {
        // A radio button can't be unticked; ticking another option of the question does that.
        if (shouldTick(element, value) && !checked(element)) element.click();
      }
      outline(element, '2px solid #2f855a');
      filled.push(index);
    }
    return { filled, problems };
  }

  // --- The panel (top frame only) ---

  const STYLE = `
    :host { all: initial; }
    .panel { position: fixed; bottom: 16px; width: 340px; max-height: 72vh; display: flex; flex-direction: column;
      z-index: 2147483647; background: #fff; color: #1a202c; border: 1px solid #cbd5e0; border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,.18); font: 14px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    .panel.right { right: 16px; } .panel.left { left: 16px; }
    .head { display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; background: #14696b; color: #fff;
      border-radius: 9px 9px 0 0; }
    .panel.collapsed .head { border-radius: 9px; }
    .head .title { flex: 1; font-weight: 600; }
    .head small { display: block; font-weight: 400; opacity: .9; }
    .head button { background: transparent; border: 0; color: #fff; padding: 0 4px; font-size: 16px; line-height: 1; }
    .body { padding: 10px 12px; display: grid; gap: 8px; overflow: auto; }
    .panel.collapsed .body { display: none; }
    .msg { padding: 6px 8px; border-radius: 6px; background: #e6f6f5; }
    .msg.error { background: #fff5f5; color: #9b2c2c; }
    .msg.done { background: #f0fff4; color: #22543d; }
    button { font: inherit; padding: 6px 10px; border-radius: 6px; border: 1px solid #14696b; background: #14696b;
      color: #fff; cursor: pointer; }
    button.quiet { background: #fff; color: #14696b; }
    .row { display: flex; gap: 6px; flex-wrap: wrap; }
    ol, ul { margin: 0; padding-left: 20px; }
    li { margin: 2px 0; }
    li.filled::marker { content: '✓ '; color: #2f855a; }
    li button { padding: 0 6px; margin-left: 6px; }
    .value { color: #4a5568; }
    label { display: grid; gap: 2px; font-size: 12px; color: #4a5568; }
    input, select { font: inherit; padding: 4px 6px; border: 1px solid #cbd5e0; border-radius: 4px; color: #1a202c; background: #fff; }
    .muted { color: #718096; font-size: 12px; }
    details summary { cursor: pointer; color: #4a5568; }
    .chooser { padding: 0; overflow: visible; }`;

  let host = null;
  let root = null;
  let panel = null;
  let collapsed = false;
  let side = 'right';
  let confirmCancel = false;

  function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
      if (key === 'onclick') node.addEventListener('click', event => { if (event.isTrusted) value(event); });
      else if (key === 'text') node.textContent = value;
      else node.setAttribute(key, value);
    }
    node.append(...children.filter(c => c !== null && c !== undefined && c !== false));
    return node;
  }

  function button(text, action, quiet) {
    return el('button', { type: 'button', class: quiet ? 'quiet' : '', onclick: e => { e.preventDefault(); action(); } }, text);
  }

  function ensurePanel() {
    if (!host) {
      host = document.createElement('credcloud-panel');
      root = host.attachShadow({ mode: 'closed' });
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(STYLE);
      root.adoptedStyleSheets = [sheet];
      panel = el('div', { class: 'panel right' });
      root.append(panel);
    }
    if (!host.isConnected && document.documentElement) document.documentElement.append(host);
  }

  function removePanel() {
    if (host) host.remove();
  }

  function draw() {
    if (!TOP) return;
    // A new tab is blank for a moment before the portal loads; there's nothing to do there.
    if (!state || location.href === 'about:blank') {
      removePanel();
      return;
    }
    ensurePanel();
    panel.className = 'panel ' + side + (collapsed ? ' collapsed' : '');
    panel.replaceChildren();
    panel.append(el('div', { class: 'head' },
      el('div', { class: 'title' }, 'CredCloud',
        el('small', { text: state.title || '' }),
        state.provider ? el('small', { text: state.provider }) : null),
      button(side === 'right' ? '⇤' : '⇥', () => { side = side === 'right' ? 'left' : 'right'; draw(); }, true),
      button(collapsed ? '▴' : '▾', () => { collapsed = !collapsed; draw(); }, true)));
    const body = el('div', { class: 'body' });
    panel.append(body);
    if (state.message) body.append(el('div', { class: 'msg ' + (state.tone || ''), role: 'status', text: state.message }));
    if (state.finished) return;
    if (state.mode === 'fill') drawFill(body);
    else drawLearn(body);
  }

  function cancelButton() {
    return button(confirmCancel ? 'Yes, cancel' : 'Cancel', () => {
      if (confirmCancel) {
        confirmCancel = false;
        send('cancel');
        return;
      }
      confirmCancel = true;
      draw();
      setTimeout(() => { confirmCancel = false; draw(); }, 4000);
    }, true);
  }

  function drawFill(body) {
    body.append(el('div', { class: 'muted', text:
      'Sign in and open the form. Fill each page, check it, and go on to the next. CredCloud never submits.' }));
    body.append(el('div', { class: 'row' },
      button('Fill this page', () => send('fill')),
      button('Done', () => send('done'), true),
      cancelButton()));
    if (state.fields && state.fields.length) {
      const list = el('ol');
      for (const field of state.fields) {
        list.append(el('li', { class: field.filled ? 'filled' : '' },
          el('span', { text: field.label + ': ' }),
          el('span', { class: 'value', text: field.detail || 'no answer' })));
      }
      body.append(el('details', {}, el('summary', { text: 'Answers · ' + (state.progress || '') }), list));
    }
  }

  // The box chooser she's filling in, kept across redraws of the same box so nothing she
  // typed or picked is lost.
  let chooser = null;
  let chooserFor = '';

  function drawLearn(body) {
    if (state.pending) {
      const key = JSON.stringify(state.pending);
      if (key !== chooserFor) {
        chooser = drawChooser(state.pending);
        chooserFor = key;
      }
      body.append(chooser);
    } else {
      chooser = null;
      chooserFor = '';
      body.append(el('div', { class: 'row' },
        state.picking
          ? button('Stop picking', () => send('stopPicking'), true)
          : button('Pick a box', () => send('startPicking'))));
      if (state.picking) {
        body.append(el('div', { class: 'muted', text:
          'Stop picking to use the portal again, for example to go to the next page of the form.' }));
      }
    }
    const list = el('ul');
    (state.fields || []).forEach((field, index) => list.append(el('li', {},
      el('span', { text: field.label + ' → ' + field.detail }),
      button('✕', () => send('remove', { index }), true))));
    body.append(el('div', { class: 'muted', text: state.progress || '' }), list);
    body.append(el('div', { class: 'row' },
      button('Save template', () => send('save')),
      cancelButton()));
  }

  function drawChooser(pending) {
    const filter = el('input', { type: 'search', placeholder: 'Search data, e.g. NPI' });
    const source = el('select');
    const list = () => {
      const words = filter.value.toLowerCase().split(/\s+/).filter(Boolean);
      source.replaceChildren(el('option', { value: '', text: '(no data: always use the fixed answer)' }));
      for (const s of state.sources || []) {
        if (words.every(w => s.label.toLowerCase().includes(w))) {
          source.append(el('option', { value: s.key, text: s.label }));
        }
      }
      if (source.options.length > 1 && words.length) source.selectedIndex = 1;
    };
    filter.addEventListener('input', list);
    list();
    const format = el('select');
    for (const f of state.formats || []) format.append(el('option', { value: f.name, text: f.label }));
    const choice = pending.kind === 'radio' || pending.kind === 'checkbox';
    const fixed = el('input', { type: 'text', placeholder: pending.kind === 'text' ? '' : 'Yes, No, or the option' });
    const name = el('input', { type: 'text', value: pending.label, maxlength: '200' });
    setTimeout(() => filter.focus(), 0);
    return el('div', { class: 'body chooser' },
      el('div', { text: 'What goes in this ' + (pending.kind === 'text' ? 'box' : pending.kind) + '?' }),
      el('label', {}, 'Name', name),
      el('label', {}, 'Data', filter, source),
      el('label', {}, 'Format', format),
      el('label', {}, 'Fixed answer, used when the data is empty', fixed),
      choice ? el('div', { class: 'muted', text: 'Ticked when the answer is Yes, or names this option. '
        + 'Give every option of a question the same data: only the ones that match get ticked.' }) : null,
      el('div', { class: 'row' },
        button('Add', () => send('add', { label: name.value, source: source.value, format: format.value, defaultValue: fixed.value })),
        button('Skip', () => send('discard'), true)));
  }

  // Portals that rebuild the page can drop the panel; put it back.
  setInterval(() => { if (TOP && state && host && !host.isConnected) ensurePanel(); }, 1000);

  globalThis.__credcloud = {
    render(json) {
      const next = JSON.parse(json);
      // Redraws can arrive out of order; never go back to an older state.
      if (next && state && next.version < state.version) return;
      // Redrawing what's already there would only risk swallowing a click in progress.
      const same = JSON.stringify({ ...next, version: 0 }) === JSON.stringify({ ...state, version: 0 });
      const wasPicking = picking();
      state = next;
      if (same && host && host.isConnected) return;
      if (wasPicking && !picking() && hovered) {
        restore(hovered);
        hovered = null;
      }
      draw();
    },
    fill(json) {
      return JSON.stringify(fill(JSON.parse(json)));
    },
    // Where a panel button is, so tests can click it the way a person does. Nothing in the
    // portal's own world can call this.
    buttonRect(text) {
      if (!root) return null;
      const found = Array.from(root.querySelectorAll('button')).find(b => b.textContent === text);
      if (!found) return null;
      found.scrollIntoView({ block: 'nearest' });
      const r = found.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    },
    // Sets a field in the box chooser, for tests.
    choose(values) {
      if (!root) return false;
      const inputs = root.querySelectorAll('.body label');
      for (const label of inputs) {
        const key = label.firstChild && label.firstChild.textContent;
        const control = label.querySelector('select, input:not([type=search])');
        if (key in values && control) control.value = values[key];
      }
      return true;
    }
  };

  // Ask what to draw. The binding can arrive a moment after this world starts.
  let tries = 0;
  (function hello() {
    if (send('hello')) return;
    if (++tries < 50) setTimeout(hello, 100);
  })();
})();
