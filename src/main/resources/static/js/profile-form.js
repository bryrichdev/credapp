/*
 * Repeating rows for the single provider and group forms.
 *
 * Markup contract (see provider/form.html and group/form.html):
 *   [data-section="licenses"]   one per list; the value is the form property name
 *     [data-rows]               holds the rows
 *       [data-row]              one row; [data-remove-row] inside removes it
 *     template[data-row-template]  a blank row, named licenses[0].*
 *     [data-add-row]            appends a copy of the template
 *     [data-count], [data-empty]   row count pill and "nothing yet" line
 *
 * Spring binds licenses[0], licenses[1], ... so every add and remove renumbers the
 * section's names to stay contiguous.
 *
 * Cross-section links, all optional:
 *   [data-key]                  hidden key on a row other rows can point at. New rows get
 *                               a "new-" key here so a link works before anything is saved.
 *   [data-link-source="owners"] marks a section whose rows can be linked to. Each row's
 *                               label is built from its [data-label-part] fields,
 *                               joined with data-link-separator (default a space).
 *   select[data-link-target="owners"]  options are rebuilt from that section's rows;
 *                               options marked data-static are kept as they are.
 *   select[data-location-select] options carry data-group and are limited to groups
 *                               picked in any select[data-group-select].
 *   [data-primary]              checkboxes in one section; ticking one clears the rest.
 *   [data-mode] + [data-mode-show="x"]  inside a row, shows a block only while the
 *                               row's mode control has value x.
 */
(function () {
    const form = document.querySelector('[data-profile-form]');
    if (!form) {
        return;
    }

    let newKeyCounter = 0;

    function sections() {
        return Array.from(form.querySelectorAll('[data-section]'));
    }

    function rowsOf(section) {
        return Array.from(section.querySelector('[data-rows]').children)
            .filter(row => row.matches('[data-row]'));
    }

    function renumber(section) {
        const name = section.dataset.section;
        const pattern = new RegExp('^' + name + '\\[\\d+\\]');
        const rows = rowsOf(section);
        rows.forEach((row, index) => {
            row.querySelectorAll('[name]').forEach(input => {
                input.name = input.name.replace(pattern, name + '[' + index + ']');
            });
        });
        const count = section.querySelector('[data-count]');
        if (count) {
            count.textContent = rows.length;
        }
        const empty = section.querySelector('[data-empty]');
        if (empty) {
            empty.hidden = rows.length > 0;
        }
    }

    // ---------- linked selects ----------

    function labelFor(row, fallback, separator) {
        const modeControl = row.querySelector('[data-mode]');
        const mode = modeControl ? currentMode(modeControl) : null;
        const parts = Array.from(row.querySelectorAll('[data-label-part]'))
            .filter(part => !mode || !part.closest('[data-mode-show]')
                || part.closest('[data-mode-show]').dataset.modeShow === mode)
            .map(part => part.tagName === 'SELECT'
                ? (part.value ? part.options[part.selectedIndex].text : '')
                : part.value.trim())
            .filter(text => text !== '');
        return parts.length ? parts.join(separator) : fallback;
    }

    function refreshLinks() {
        form.querySelectorAll('[data-link-source]').forEach(source => {
            const name = source.dataset.linkSource;
            const fallback = source.dataset.linkFallback || 'New item (not saved yet)';
            const separator = source.dataset.linkSeparator || ' ';
            const choices = rowsOf(source)
                .map(row => {
                    const key = row.querySelector('[data-key]');
                    return key && key.value ? {value: key.value, label: labelFor(row, fallback, separator)} : null;
                })
                .filter(Boolean);

            form.querySelectorAll('select[data-link-target="' + name + '"]').forEach(select => {
                const current = select.value;
                Array.from(select.options)
                    .filter(option => !option.hasAttribute('data-static'))
                    .forEach(option => option.remove());
                choices.forEach(choice => select.add(new Option(choice.label, choice.value)));
                const stillThere = Array.from(select.options).some(option => option.value === current);
                select.value = stillThere ? current : '';
            });
        });
    }

    // ---------- locations limited to picked groups ----------

    function refreshLocations() {
        const groupIds = new Set(Array.from(form.querySelectorAll('select[data-group-select]'))
            .map(select => select.value)
            .filter(value => value !== ''));
        form.querySelectorAll('select[data-location-select]').forEach(select => {
            Array.from(select.options).forEach(option => {
                if (!option.dataset.group) {
                    return;
                }
                const allowed = groupIds.has(option.dataset.group);
                // A saved pick stays visible so the server can explain what's wrong with it.
                option.hidden = !allowed && !option.selected;
                option.disabled = !allowed && !option.selected;
            });
        });
    }

    // ---------- row modes (pick existing / add new) ----------

    function currentMode(control) {
        if (control.type === 'radio') {
            const checked = control.closest('[data-row]')
                .querySelector('[data-mode]:checked');
            return checked ? checked.value : null;
        }
        return control.value;
    }

    function refreshModes(scope) {
        scope.querySelectorAll('[data-row]').forEach(row => {
            const control = row.querySelector('[data-mode]');
            if (!control) {
                return;
            }
            const mode = currentMode(control);
            row.querySelectorAll('[data-mode-show]').forEach(block => {
                const show = block.dataset.modeShow === mode;
                block.hidden = !show;
                // Hidden fields still submit, so switch them off to keep the other mode's
                // values out of the request.
                block.querySelectorAll('input, select, textarea').forEach(input => {
                    input.disabled = !show;
                });
            });
        });
    }

    function refreshAll() {
        refreshModes(form);
        refreshLinks();
        refreshLocations();
    }

    // ---------- add / remove ----------

    function addRow(section) {
        const template = section.querySelector('template[data-row-template]');
        const holder = section.querySelector('[data-rows]');
        holder.appendChild(template.content.cloneNode(true));
        const row = rowsOf(section).pop();
        row.querySelectorAll('[data-key]').forEach(key => {
            if (!key.value) {
                newKeyCounter += 1;
                key.value = 'new-' + Date.now() + '-' + newKeyCounter;
            }
        });
        renumber(section);
        refreshAll();
        const first = row.querySelector('input:not([type="hidden"]):not([disabled]), select:not([disabled])');
        if (first) {
            first.focus();
        }
        markDirty();
    }

    form.addEventListener('click', event => {
        const add = event.target.closest('[data-add-row]');
        if (add) {
            addRow(add.closest('[data-section]'));
            return;
        }
        const remove = event.target.closest('[data-remove-row]');
        if (remove) {
            const section = remove.closest('[data-section]');
            remove.closest('[data-row]').remove();
            renumber(section);
            refreshAll();
            markDirty();
        }
    });

    form.addEventListener('change', event => {
        const box = event.target.closest('[data-primary]');
        if (box && box.checked) {
            box.closest('[data-section]').querySelectorAll('[data-primary]').forEach(other => {
                if (other !== box) {
                    other.checked = false;
                }
            });
        }
        refreshAll();
    });

    form.addEventListener('input', event => {
        if (event.target.closest('[data-label-part]')) {
            refreshLinks();
        }
    });

    // ---------- unsaved changes ----------

    let dirty = false;

    function markDirty() {
        dirty = true;
    }

    form.addEventListener('input', markDirty);
    form.addEventListener('change', markDirty);

    form.addEventListener('submit', () => {
        sections().forEach(renumber);
        dirty = false;
    });

    window.addEventListener('beforeunload', event => {
        if (dirty) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    sections().forEach(renumber);
    refreshAll();
})();
