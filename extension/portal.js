// Runs on a portal page next to panel.js. It passes the panel's presses to the background,
// draws what comes back, and does the typing, since only a script on the page can.
//
// It types into text boxes, picks dropdown options, and ticks checkboxes and radio buttons:
// a radio button or checkbox is ticked when the answer is Yes or names that option, so all the
// options of one question can share one piece of data.
// Before touching an element it checks what the element really is, so a template that points
// at a button, or a page that changed, can't make it press anything. It never submits.
(() => {
  if (window.__credcloudPortal) return;
  window.__credcloudPortal = true;
  const panel = window.__credcloud;

  const YES = new Set(['yes', 'y', 'true', '1', 'x', 'checked', 'on']);
  const NO = new Set(['no', 'n', 'false', '0', 'unchecked', 'off']);
  const clean = text => String(text ?? '').replace(/\s+/g, ' ').replace(/[\s:*]+$/, '').trim().toLowerCase();

  /** An answer as a list: "English, Spanish" or "Cardiology; Internal medicine". */
  const parts = value => String(value).split(/[,;|\n]/).map(clean).filter(Boolean);

  /** What an option is called: its label, and its value. */
  function names(element) {
    return [panel.labelTextOf(element), element.getAttribute('value'), element.getAttribute('aria-label')]
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
   * answer names it, so every option of a question can share one piece of data.
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
        if (checked(element) !== shouldTick(element, value)) element.click();
      } else if (actual === 'radio') {
        // A radio button can't be unticked; ticking another option of the question does that.
        if (shouldTick(element, value) && !checked(element)) element.click();
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
