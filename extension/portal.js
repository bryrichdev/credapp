// Runs on a portal page next to panel.js. It passes the panel's presses to the background,
// draws what comes back, and does the typing, since only a script on the page can.
//
// It types into text boxes, picks dropdown options, and ticks checkboxes and radio buttons.
// Before touching an element it checks what the element really is, so a template that points
// at a button, or a page that changed, can't make it press anything. It never submits.
(() => {
  if (window.__credcloudPortal) return;
  window.__credcloudPortal = true;
  const panel = window.__credcloud;

  const YES = new Set(['yes', 'y', 'true', '1', 'x', 'checked', 'on']);
  const yes = value => YES.has(String(value).trim().toLowerCase());

  function locate(field) {
    if (field.by === 'label') {
      return panel.findByLabel(field.locator)[0] || null;
    }
    try {
      const found = document.querySelector(field.locator);
      return found && found.matches(panel.BOXES) ? found : (found ? 'forbidden' : null);
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

  function choose(select, value) {
    const wanted = String(value).trim().toLowerCase();
    const option = Array.from(select.options).find(o => o.text.trim().toLowerCase() === wanted)
      || Array.from(select.options).find(o => o.value.trim().toLowerCase() === wanted);
    if (!option) return false;
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(select, option.value);
    select.dispatchEvent(new Event('input', { bubbles: true }));
    select.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }

  function checked(element) {
    return element.getAttribute('role') ? element.getAttribute('aria-checked') === 'true' : element.checked;
  }

  /** Fills what's on this page. Returns the indexes it filled and what it left alone. */
  function fill(items) {
    const filled = [];
    const problems = [];
    for (const { index, field, value } of items) {
      const element = locate(field);
      if (element === null) continue; // on another page of the form
      const actual = element === 'forbidden' ? null : panel.kindOf(element);
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
        if (checked(element) !== yes(value)) element.click();
      } else if (actual === 'radio') {
        if (yes(value) && !checked(element)) element.click();
      }
      element.style.outline = '2px solid #2f855a';
      element.style.outlineOffset = '1px';
      filled.push(index);
    }
    return { filled, problems };
  }

  async function send(action, data) {
    let reply = await chrome.runtime.sendMessage({ type: 'panel', action, data });
    if (reply && reply.command && reply.command.type === 'fill') {
      reply = await chrome.runtime.sendMessage({ type: 'panel', action: 'filled', data: fill(reply.command.items) });
    }
    if (reply && reply.state) panel.render(reply.state);
  }

  panel.onAction = (action, data) => { send(action, data); };
  send('render', {});
})();
