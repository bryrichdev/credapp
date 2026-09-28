// What CredCloud does inside a payer portal's page, in CredCloud's own browser. PortalScript
// runs this in one frame at a time with a JSON request, and gets JSON back:
//   {action: 'fill', items}  types the answers into the boxes it finds here
//   {action: 'pick', x, y}   says which box is at that point, and how to find it again
// The logic is the extension's (panel.js and portal.js), so a template taught either way
// fills the same way.
//
// Filling types into text boxes, picks dropdown options, and ticks checkboxes and radio
// buttons: a radio button or checkbox is ticked when the answer is Yes or names that option.
// Before touching an element it checks what the element really is, so a template that points
// at a button, or a page that changed, can't make it press anything. It never submits.
(input) => {
  const request = JSON.parse(input);
  const BOXES = 'input, select, textarea, [contenteditable="true"], [role="checkbox"], [role="radio"], [role="textbox"]';
  const YES = new Set(['yes', 'y', 'true', '1', 'x', 'checked', 'on']);
  const NO = new Set(['no', 'n', 'false', '0', 'unchecked', 'off']);
  const clean = text => String(text ?? '').replace(/\s+/g, ' ').replace(/[\s:*]+$/, '').trim().toLowerCase();
  const parts = value => String(value).split(/[,;|\n]/).map(clean).filter(Boolean);

  function labelTextOf(e) {
    const own = e.labels && e.labels.length ? e.labels[0].innerText : '';
    const labelledBy = (e.getAttribute('aria-labelledby') || '').split(/\s+/)
      .map(id => document.getElementById(id)).filter(Boolean).map(n => n.innerText).join(' ');
    return (own || e.getAttribute('aria-label') || labelledBy || '').replace(/\s+/g, ' ').trim();
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

  /** A name for people: the label without a required-field asterisk, or the placeholder. */
  function nameOf(e) {
    return (labelTextOf(e) || e.getAttribute('placeholder') || '').replace(/\s*\*\s*$/, '').trim();
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

  function findByLabel(text) {
    return Array.from(document.querySelectorAll(BOXES)).filter(e => labelTextOf(e) === text);
  }

  /**
   * How to find this box next time: by its label when the label names only this box, which
   * survives most redesigns, or by a CSS selector.
   */
  function describe(e) {
    const kind = kindOf(e);
    if (!kind) return { result: 'none' };
    const labelText = labelTextOf(e);
    const css = cssOf(e);
    if (labelText && findByLabel(labelText).length === 1) {
      return { result: 'box', label: nameOf(e), by: 'label', locator: labelText, kind };
    }
    if (css && unique(css)) {
      return { result: 'box', label: nameOf(e), by: 'css', locator: css, kind };
    }
    return { result: 'unclear' };
  }

  /** The box at a point: a click on its label counts. A frame there is reported so it can be searched. */
  function pick(x, y) {
    const target = document.elementFromPoint(x, y);
    if (!target) return { result: 'none' };
    if (target.tagName === 'IFRAME' || target.tagName === 'FRAME') {
      const box = target.getBoundingClientRect();
      return {
        result: 'frame', index: Array.from(document.querySelectorAll('iframe, frame')).indexOf(target),
        left: box.left + target.clientLeft, top: box.top + target.clientTop
      };
    }
    let box = target.closest(BOXES);
    const label = target.closest('label');
    if (!box && label && label.control) box = label.control;
    if (!box) return { result: 'none' };
    const described = describe(box);
    if (described.result === 'box') {
      box.style.outline = '2px dashed #14696b';
      box.style.outlineOffset = '1px';
    }
    return described;
  }

  function names(element) {
    return [labelTextOf(element), element.getAttribute('value'), element.getAttribute('aria-label')]
      .map(clean).filter(Boolean);
  }

  function namesMatch(optionNames, value) {
    return parts(value).some(part => optionNames.some(name =>
      name === part || (part.length === 1 && name.startsWith(part))));
  }

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
    if (field.by === 'label') {
      return Array.from(document.querySelectorAll(BOXES)).find(e => labelTextOf(e) === field.locator) || null;
    }
    try {
      const found = document.querySelector(field.locator);
      return found && found.matches(BOXES) ? found : (found ? 'forbidden' : null);
    } catch (e) {
      return null;
    }
  }

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

  /** Fills what's here. Returns the indexes it filled and what it left alone. */
  function fill(items) {
    const filled = [];
    const problems = [];
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
        if (shouldTick(element, value) && !checked(element)) element.click();
      }
      element.style.outline = '2px solid #2f855a';
      element.style.outlineOffset = '1px';
      filled.push(index);
    }
    return { filled, problems };
  }

  return JSON.stringify(request.action === 'pick' ? pick(request.x, request.y) : fill(request.items));
}
