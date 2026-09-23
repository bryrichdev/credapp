(function () {
    const button = document.getElementById('revealCaqhPassword');
    const badge = document.getElementById('caqhPasswordBadge');
    const error = document.getElementById('caqhPasswordError');
    if (!button || !badge || !error) return;

    const originalText = badge.textContent;
    const originalClass = badge.className;
    let timer = null;
    let pending = null;
    let generation = 0;
    let visible = false;

    function conceal() {
        generation++;
        clearTimeout(timer);
        if (pending) pending.abort();
        pending = null;
        timer = null;
        visible = false;
        badge.textContent = originalText;
        badge.className = originalClass;
        button.textContent = 'Show password';
        button.disabled = false;
    }

    button.addEventListener('click', async function () {
        if (visible) {
            conceal();
            return;
        }
        const requestGeneration = ++generation;
        pending = new AbortController();
        error.hidden = true;
        button.disabled = true;
        button.textContent = 'Checking…';
        const csrfToken = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        const headers = { Accept: 'application/json' };
        if (csrfToken && csrfHeader && csrfHeader.content) {
            headers[csrfHeader.content] = csrfToken.content;
        }
        try {
            const response = await fetch(button.dataset.url, {
                method: 'POST', headers, credentials: 'same-origin', cache: 'no-store', signal: pending.signal
            });
            if (!response.ok) throw new Error('request');
            const body = await response.json();
            // Leaving the page or hiding it while a request is pending must cancel the reveal.
            if (requestGeneration !== generation || document.hidden) return;
            if (typeof body.password !== 'string' || body.password.length === 0) {
                throw new Error('missing');
            }
            pending = null;
            badge.textContent = body.password;
            badge.className = 'badge badge--alert secret-value';
            button.textContent = 'Hide';
            button.disabled = false;
            visible = true;
            timer = setTimeout(conceal, 30000);
        } catch (failure) {
            if (requestGeneration !== generation) return;
            conceal();
            error.textContent = failure.message === 'missing'
                ? 'No CAQH password is stored for this provider.'
                : 'Could not retrieve the CAQH password. Refresh the page and try again.';
            error.hidden = false;
        }
    });

    window.addEventListener('pagehide', conceal);
    window.addEventListener('blur', conceal);
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) conceal();
    });
})();
