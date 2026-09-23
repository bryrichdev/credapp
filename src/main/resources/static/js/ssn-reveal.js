(function () {
        const button = document.getElementById('revealSsn');
        const badge = document.getElementById('ssnBadge');
        const error = document.getElementById('ssnError');
        if (!button || !badge) {
            return;
        }

        const HIDE_AFTER_MS = 30000;
        const originalText = badge.textContent;
        const originalClass = badge.className;
        let hideTimer = null;

        function conceal() {
            badge.textContent = originalText;
            badge.className = originalClass;
            button.textContent = 'Show SSN';
            button.disabled = false;
            hideTimer = null;
        }

        button.addEventListener('click', async function () {
            // A second click while the number is up puts it away early.
            if (hideTimer) {
                clearTimeout(hideTimer);
                conceal();
                return;
            }

            error.hidden = true;
            button.disabled = true;
            button.textContent = 'Checking…';

            const csrfToken = document.querySelector('meta[name="_csrf"]');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
            const headers = { 'Accept': 'application/json' };
            if (csrfToken && csrfHeader && csrfHeader.content) {
                headers[csrfHeader.content] = csrfToken.content;
            }

            try {
                const response = await fetch(button.dataset.url, {
                    method: 'POST',
                    headers: headers,
                    credentials: 'same-origin'
                });
                if (!response.ok) {
                    throw new Error(response.status === 403
                        ? 'You are not allowed to view stored SSNs.'
                        : 'Could not retrieve the SSN.');
                }
                const body = await response.json();
                if (!body.ssn) {
                    throw new Error('No SSN is stored for this record.');
                }

                badge.textContent = body.ssn;
                badge.className = 'badge badge--alert';
                button.textContent = 'Hide';
                button.disabled = false;
                // Put it away on its own, so it can't be left on screen.
                hideTimer = setTimeout(conceal, HIDE_AFTER_MS);
            } catch (failure) {
                error.textContent = failure.message;
                error.hidden = false;
                conceal();
            }
        });
    })();
