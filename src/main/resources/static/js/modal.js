/*
 * Short forms in a dialog over the page instead of a page of their own.
 *
 * A link marked data-modal loads its page in the background and shows the part marked
 * data-modal-content in a <dialog>, titled with the page's data-modal-title. The form is
 * submitted in the background too:
 *   - saved: the server answers 204 with X-Modal-Redirect, and we go where it would have
 *     redirected, so the "Saved" message shows on the page underneath as usual;
 *   - not valid: the server answers with the form again, errors and all, and it replaces
 *     the dialog's contents;
 *   - anything else: we say so in the dialog, and nothing is lost.
 * Without JavaScript, or opened in a new tab, the link is still an ordinary page.
 */
(function () {
    if (!('fetch' in window) || typeof HTMLDialogElement !== 'function') {
        return;
    }

    let dialog = null;
    let opener = null;

    function build() {
        const element = document.createElement('dialog');
        element.className = 'modal';
        element.setAttribute('aria-labelledby', 'modal-title');
        element.innerHTML =
            '<div class="modal__header">' +
            '  <div><h2 class="modal__title" id="modal-title"></h2><p class="modal__subtitle"></p></div>' +
            '  <button class="modal__close" type="button" aria-label="Close">&times;</button>' +
            '</div>' +
            '<div class="modal__error alert alert--error" role="alert" hidden></div>' +
            '<div class="modal__body"></div>';
        element.querySelector('.modal__close').addEventListener('click', close);
        element.addEventListener('close', () => {
            element.remove();
            dialog = null;
            if (opener && document.contains(opener)) {
                opener.focus();
            }
        });
        element.addEventListener('click', event => {
            if (event.target.closest('[data-modal-cancel]')) {
                event.preventDefault();
                close();
            }
        });
        element.addEventListener('submit', submit);
        return element;
    }

    function close() {
        if (dialog && dialog.open) {
            dialog.close();
        }
    }

    function navigate(response) {
        const location = response.headers.get('X-Modal-Redirect');
        if (response.status === 204 && location) {
            window.location.assign(location);
            return true;
        }
        return false;
    }

    /** Puts the page's form into the dialog. False when the page has none to show. */
    function show(html) {
        const page = new DOMParser().parseFromString(html, 'text/html');
        const content = page.querySelector('[data-modal-content]');
        if (!content) {
            return false;
        }
        if (!dialog) {
            dialog = build();
            document.body.appendChild(dialog);
        }
        const title = page.querySelector('[data-modal-title]');
        const subtitle = page.querySelector('.page__header .page__subtitle');
        dialog.querySelector('.modal__title').textContent = title ? title.textContent.trim() : '';
        const subtitleElement = dialog.querySelector('.modal__subtitle');
        subtitleElement.textContent = subtitle ? subtitle.textContent.trim() : '';
        subtitleElement.hidden = !subtitle;
        dialog.querySelector('.modal__error').hidden = true;
        dialog.querySelector('.modal__body').replaceChildren(document.importNode(content, true));
        if (!dialog.open) {
            dialog.showModal();
        }
        const first = dialog.querySelector('.field__input--invalid, .modal__body input:not([type=hidden]), .modal__body select, .modal__body textarea');
        if (first) {
            first.focus();
        }
        return true;
    }

    function problem(message) {
        const error = dialog.querySelector('.modal__error');
        error.textContent = message;
        error.hidden = false;
    }

    async function open(link) {
        opener = link;
        link.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(link.href, {headers: {'X-Modal': '1'}, credentials: 'same-origin'});
            if (navigate(response)) {
                return;
            }
            if (!response.ok || !show(await response.text())) {
                window.location.assign(link.href);
            }
        } catch (error) {
            window.location.assign(link.href);
        } finally {
            link.removeAttribute('aria-busy');
        }
    }

    async function submit(event) {
        const form = event.target;
        if (form.hasAttribute('data-confirm') || event.defaultPrevented) {
            return;
        }
        event.preventDefault();
        const buttons = form.querySelectorAll('button[type=submit], button:not([type])');
        buttons.forEach(button => { button.disabled = true; });
        try {
            const body = new FormData(form, event.submitter || undefined);
            const response = await fetch(form.action, {
                method: (form.getAttribute('method') || 'post').toUpperCase(),
                body: body,
                headers: {'X-Modal': '1'},
                credentials: 'same-origin'
            });
            if (navigate(response)) {
                return;
            }
            if (response.ok && show(await response.text())) {
                return;
            }
            problem(response.status === 403
                ? "That wasn't allowed. Reload the page, check you're still signed in, and try again."
                : "Something went wrong saving that. Nothing was changed; try again in a moment.");
        } catch (error) {
            problem("Couldn't reach CredCloud. Check your connection and try again.");
        } finally {
            buttons.forEach(button => { button.disabled = false; });
        }
    }

    document.addEventListener('click', event => {
        const link = event.target.closest && event.target.closest('a[data-modal]');
        if (!link || event.defaultPrevented || event.button !== 0
            || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        event.preventDefault();
        open(link);
    });
})();
