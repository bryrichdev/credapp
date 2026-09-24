/*
 * The contacts form: add and remove contact blocks, keeping Spring's contacts[0], contacts[1]...
 * indexes contiguous, and only offering "Add" once the last block has its required fields.
 */
(function () {
    const list = document.getElementById('contactList');
    const addButton = document.getElementById('addContact');
    const hint = document.getElementById('addHint');
    const template = document.getElementById('contactTemplate');
    if (!list || !addButton || !template) {
        return;
    }

    function blocks() {
        return Array.from(list.querySelectorAll('[data-contact]'));
    }

    // Spring binds contacts[0], contacts[1], ... so the indexes have to stay
    // contiguous after every add and remove.
    function renumber() {
        const all = blocks();
        all.forEach((block, index) => {
            block.querySelector('[data-contact-title]').textContent = 'Contact ' + (index + 1);
            block.querySelectorAll('[name]').forEach(input => {
                input.name = input.name.replace(/contacts\[\d+\]/, 'contacts[' + index + ']');
            });
            const remove = block.querySelector('[data-remove]');
            if (remove) {
                remove.hidden = all.length === 1;
            }
        });
    }

    function lastBlockComplete() {
        const all = blocks();
        if (all.length === 0) {
            return false;
        }
        const last = all[all.length - 1];
        return Array.from(last.querySelectorAll('[data-required]'))
            .every(input => input.value.trim() !== '');
    }

    function refresh() {
        const ready = lastBlockComplete();
        addButton.disabled = !ready;
        hint.hidden = ready;
    }

    addButton.addEventListener('click', function () {
        if (!lastBlockComplete()) {
            return;
        }
        list.appendChild(template.content.cloneNode(true));
        renumber();
        refresh();
        const added = blocks().pop();
        const first = added.querySelector('input, select');
        if (first) {
            first.focus();
        }
    });

    list.addEventListener('click', function (event) {
        const button = event.target.closest('[data-remove]');
        if (!button || blocks().length === 1) {
            return;
        }
        button.closest('[data-contact]').remove();
        renumber();
        refresh();
    });

    list.addEventListener('input', refresh);
    list.addEventListener('change', refresh);

    renumber();
    refresh();
})();
