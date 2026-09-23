const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/caqh-password-reveal.js'), 'utf8');

function setup(fetchResult) {
    const events = {};
    const button = {
        textContent: 'Show password', disabled: false, dataset: { url: '/providers/1/caqh-password' },
        addEventListener(name, handler) { events[name] = handler; }
    };
    const badge = { textContent: 'On file, encrypted', className: 'badge badge--active secret-value' };
    const error = { hidden: true, textContent: '' };
    const elements = { revealCaqhPassword: button, caqhPasswordBadge: badge, caqhPasswordError: error };
    const timers = new Map();
    const calls = [];
    let nextTimer = 0;
    const document = {
        hidden: false,
        getElementById: id => elements[id],
        querySelector: selector => ({ content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'test-csrf' }),
        addEventListener(name, handler) { events[name] = handler; }
    };
    vm.runInNewContext(source, {
        document,
        window: { addEventListener(name, handler) { events[name] = handler; } },
        AbortController,
        setTimeout(handler, ms) { timers.set(++nextTimer, { handler, ms }); return nextTimer; },
        clearTimeout(id) { timers.delete(id); },
        async fetch(url, options) {
            calls.push({ url, options });
            return fetchResult ? fetchResult() : { ok: true, json: async () => ({ password: '<b> synthetic secret </b>' }) };
        }
    });
    return { button, badge, error, events, document, timers, calls };
}

test('reveals only on a CSRF-protected uncached POST, then auto-hides after 30 seconds', async () => {
    const ui = setup();
    assert.equal(ui.calls.length, 0);
    await ui.events.click();
    assert.equal(ui.calls[0].url, '/providers/1/caqh-password');
    assert.equal(ui.calls[0].options.method, 'POST');
    assert.equal(ui.calls[0].options.headers['X-CSRF-TOKEN'], 'test-csrf');
    assert.equal(ui.calls[0].options.cache, 'no-store');
    assert.equal(ui.calls[0].options.credentials, 'same-origin');
    assert.equal(ui.badge.textContent, '<b> synthetic secret </b>');
    assert.equal(ui.badge.innerHTML, undefined);
    assert.equal(ui.button.textContent, 'Hide');
    const timer = [...ui.timers.values()][0];
    assert.equal(timer.ms, 30000);
    timer.handler();
    assert.equal(ui.badge.textContent, 'On file, encrypted');
    assert.equal(ui.button.textContent, 'Show password');
});

test('manual hide, tab hiding, navigation and window blur remove the secret', async () => {
    for (const event of ['click', 'visibilitychange', 'pagehide', 'blur']) {
        const ui = setup();
        await ui.events.click();
        if (event === 'visibilitychange') ui.document.hidden = true;
        await ui.events[event]();
        assert.equal(ui.badge.textContent, 'On file, encrypted', event);
        assert.equal(ui.timers.size, 0, event);
        assert.equal(ui.calls.length, 1, event);
    }
});

test('a late response cannot reveal a password after the user leaves the page', async () => {
    let resolve;
    const response = new Promise(done => { resolve = done; });
    const ui = setup(() => response);
    const click = ui.events.click();
    assert.equal(ui.button.disabled, true);
    ui.events.pagehide();
    assert.equal(ui.calls[0].options.signal.aborted, true);
    resolve({ ok: true, json: async () => ({ password: 'late secret' }) });
    await click;
    assert.equal(ui.badge.textContent, 'On file, encrypted');
    assert.equal(ui.button.disabled, false);
    assert.equal(ui.timers.size, 0);
});

test('failed, missing and login-page responses leave the value concealed and allow retry', async () => {
    for (const response of [
        { ok: false },
        { ok: true, json: async () => ({ password: null }) },
        { ok: true, json: async () => { throw new Error('unexpected HTML with secret'); } }
    ]) {
        const ui = setup(() => response);
        await ui.events.click();
        assert.equal(ui.badge.textContent, 'On file, encrypted');
        assert.equal(ui.error.hidden, false);
        assert.equal(ui.error.textContent.includes('unexpected HTML'), false);
        assert.equal(ui.button.disabled, false);
        assert.equal(ui.timers.size, 0);
    }
});
