// The CredCloud panel on a portal page. The extension injects it, with portal.js, on every page
// of a portal that has a job open in its tab. It draws a small box in a shadow root, so the
// portal's styles can't reach it, and builds everything with DOM calls and a constructed
// stylesheet, so a portal with a strict Content-Security-Policy still shows it.
//
// It runs as a content script, apart from the portal's own scripts: the portal can't see it or
// press its buttons. It holds no data of its own. portal.js sends it what to show with
// render(state), and it reports presses through onAction.
(() => {
  if (window.__credcloud) return;

  const STYLE = `
    :host { all: initial; }
    .panel { position: fixed; right: 16px; bottom: 16px; width: 340px; max-height: 70vh; overflow: auto;
      z-index: 2147483647; background: #fff; color: #1a202c; border: 1px solid #cbd5e0; border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,.18); font: 14px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    .head { padding: 10px 12px; background: #1f3a5f; color: #fff; border-radius: 10px 10px 0 0; font-weight: 600; }
    .head small { display: block; font-weight: 400; opacity: .85; }
    .body { padding: 10px 12px; display: grid; gap: 8px; }
    .msg { padding: 6px 8px; border-radius: 6px; background: #ebf4ff; }
    .msg.error { background: #fff5f5; color: #9b2c2c; }
    .msg.done { background: #f0fff4; color: #22543d; }
    button { font: inherit; padding: 6px 10px; border-radius: 6px; border: 1px solid #1f3a5f; background: #1f3a5f;
      color: #fff; cursor: pointer; }
    button.quiet { background: #fff; color: #1f3a5f; }
    .row { display: flex; gap: 6px; flex-wrap: wrap; }
    ul { margin: 0; padding-left: 18px; }
    li { margin: 2px 0; }
    li button { padding: 0 6px; margin-left: 6px; }
    label { display: grid; gap: 2px; font-size: 12px; color: #4a5568; }
    input, select { font: inherit; padding: 4px 6px; border: 1px solid #cbd5e0; border-radius: 4px; }
    .muted { color: #718096; font-size: 12px; }`;

  const host = document.createElement('credcloud-panel');
  const root = host.attachShadow({ mode: 'open' });
  const sheet = new CSSStyleSheet();
  sheet.replaceSync(STYLE);
  root.adoptedStyleSheets = [sheet];
  const panel = el('div', { class: 'panel' });
  root.append(panel);

  let state = null;

  function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
      if (key === 'onclick') node.addEventListener('click', value);
      else if (key === 'text') node.textContent = value;
      else node.setAttribute(key, value);
    }
    node.append(...children.filter(c => c !== null && c !== undefined));
    return node;
  }

  function send(action, extra = {}) {
    if (typeof api.onAction === 'function') api.onAction(action, extra);
  }

  function button(text, action, quiet) {
    return el('button', { class: quiet ? 'quiet' : '', onclick: e => { e.preventDefault(); action(); } }, text);
  }

  // --- Learn mode: which box was clicked, and how to find it again ---

  const BOXES = 'input, select, textarea, [contenteditable="true"], [role="checkbox"], [role="radio"], [role="textbox"]';

  /** The box's label as the page writes it, which is what CredCloud looks it up by. */
  function labelTextOf(e) {
    const own = e.labels && e.labels.length ? e.labels[0].innerText : '';
    const labelledBy = (e.getAttribute('aria-labelledby') || '').split(/\s+/)
      .map(id => document.getElementById(id)).filter(Boolean).map(n => n.innerText).join(' ');
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
    const parts = [];
    for (let node = e; node && node !== document.body; node = node.parentElement) {
      if (node.id && unique('#' + CSS.escape(node.id))) { parts.unshift('#' + CSS.escape(node.id)); break; }
      const same = Array.from(node.parentElement ? node.parentElement.children : [])
        .filter(sibling => sibling.tagName === node.tagName);
      const tagName = node.tagName.toLowerCase();
      parts.unshift(same.length > 1 ? `${tagName}:nth-of-type(${same.indexOf(node) + 1})` : tagName);
    }
    return parts.join(' > ');
  }

  /** Every box on the page whose label text is exactly this. */
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
    const css = cssOf(e);
    let by;
    let locator;
    if (labelText && findByLabel(labelText).length === 1) {
      by = 'label';
      locator = labelText;
    } else if (css && unique(css)) {
      by = 'css';
      locator = css;
    } else {
      return 'unclear';
    }
    return { label: nameOf(e), by, locator, kind, page: location.pathname };
  }

  function onPick(event) {
    if (!state || !state.picking || event.composedPath().includes(host)) return;
    event.preventDefault();
    event.stopPropagation();
    const target = event.target instanceof Element ? event.target.closest(BOXES) : null;
    const described = target ? describe(target) : null;
    if (!described) {
      send('notABox');
      return;
    }
    if (described === 'unclear') {
      send('unclear');
      return;
    }
    target.style.outline = '2px dashed #1f3a5f';
    send('picked', described);
  }
  document.addEventListener('click', onPick, true);

  // --- Drawing ---

  function render(next) {
    state = next;
    panel.replaceChildren();
    panel.append(el('div', { class: 'head' }, 'CredCloud',
      el('small', { text: state.title || '' }),
      state.provider ? el('small', { text: state.provider }) : null));
    const body = el('div', { class: 'body' });
    panel.append(body);
    if (state.message) body.append(el('div', { class: 'msg ' + (state.tone || ''), text: state.message }));
    if (state.finished) return;
    if (state.mode === 'fill') renderFill(body);
    else renderLearn(body);
  }

  function renderFill(body) {
    body.append(el('div', { class: 'muted', text:
      'Sign in and open the form. Then fill each page, check it, and go on to the next. CredCloud never submits.' }));
    body.append(el('div', { class: 'row' },
      button('Fill this page', () => send('fill')),
      button('Done', () => send('done'), true),
      button('Cancel', () => send('cancel'), true)));
  }

  function renderLearn(body) {
    if (state.pending) {
      body.append(renderChooser(state.pending));
    } else {
      body.append(el('div', { class: 'row' },
        state.picking
          ? button('Stop picking', () => send('stopPicking'), true)
          : button('Pick a box on the page', () => send('startPicking'))));
    }
    const list = el('ul');
    state.fields.forEach((field, index) => list.append(el('li', {},
      el('span', { text: field.label + ' → ' + field.detail }),
      button('✕', () => send('remove', { index }), true))));
    body.append(el('div', { class: 'muted', text: state.fields.length + ' boxes so far' }), list);
    body.append(el('div', { class: 'row' },
      button('Save template', () => send('save')),
      button('Cancel', () => send('cancel'), true)));
  }

  function renderChooser(pending) {
    const filter = el('input', { type: 'search', placeholder: 'Search data, e.g. NPI' });
    const source = el('select');
    const fill = () => {
      const words = filter.value.toLowerCase().split(/\s+/).filter(Boolean);
      source.replaceChildren(el('option', { value: '', text: '(no data: always use the fixed answer)' }));
      for (const s of state.sources) {
        if (words.every(w => s.label.toLowerCase().includes(w))) {
          source.append(el('option', { value: s.key, text: s.label }));
        }
      }
    };
    filter.addEventListener('input', fill);
    fill();
    const format = el('select');
    for (const [name, label] of Object.entries(state.formats)) format.append(el('option', { value: name, text: label }));
    const fixed = el('input', { type: 'text', placeholder: pending.kind === 'text' ? '' : 'Yes, No, or the option' });
    const name = el('input', { type: 'text', value: pending.label });
    return el('div', { class: 'body' },
      el('div', { text: 'What goes in this ' + (pending.kind === 'text' ? 'box' : pending.kind) + '?' }),
      el('label', {}, 'Name', name),
      el('label', {}, 'Data', filter, source),
      el('label', {}, 'Format', format),
      el('label', {}, 'Fixed answer, used when the data is empty', fixed),
      el('div', { class: 'row' },
        button('Add', () => send('add', { label: name.value, source: source.value, format: format.value, defaultValue: fixed.value })),
        button('Skip', () => send('discard'), true)));
  }

  function attach() {
    if (!host.isConnected) (document.body || document.documentElement).append(host);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', attach);
  else attach();

  const api = { render: s => { attach(); render(s); }, describe, findByLabel, kindOf, BOXES, onAction: null };
  window.__credcloud = api;
})();
