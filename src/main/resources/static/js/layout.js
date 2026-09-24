/*
 * Page-wide behaviour, loaded by the top bar on every signed-in page.
 *
 * 1. The phone menu. Below tablet width the top bar collapses to the brand and a Menu
 *    button; the button opens the nav, My account and Sign out as a panel. The CSS only
 *    collapses the bar once this script has marked it ready, so without the script the
 *    whole bar still shows.
 *
 * 2. Tables that stack into cards on phones. Each cell gets its column's header as a
 *    label, which the phone CSS shows beside the value; the first column becomes the
 *    card's title. Tables marked this way get table--stack, so without the script they
 *    keep their sideways scroll instead.
 *
 * 3. The sticky section nav on the long provider and group forms. It wraps to two rows
 *    on a desktop, so its height isn't fixed; this keeps --section-nav-height in step
 *    with it so a section jumped to lands just below it rather than underneath.
 *
 * 4. Forms marked data-confirm ask before submitting (deletes), and selects marked
 *    data-autosubmit submit their form on change. These replace inline onsubmit and
 *    onchange handlers, which the Content-Security-Policy doesn't allow.
 */
(function () {
    // ---------- menu ----------

    const topbar = document.querySelector('[data-topbar]');
    const toggle = topbar && topbar.querySelector('[data-topbar-toggle]');
    if (topbar && toggle) {
        toggle.hidden = false;
        topbar.classList.add('topbar--menu-ready');

        const setOpen = open => {
            topbar.classList.toggle('topbar--open', open);
            toggle.setAttribute('aria-expanded', String(open));
        };

        toggle.addEventListener('click', () => setOpen(!topbar.classList.contains('topbar--open')));
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && topbar.classList.contains('topbar--open')) {
                setOpen(false);
                toggle.focus();
            }
        });
        document.addEventListener('click', event => {
            if (topbar.classList.contains('topbar--open') && !topbar.contains(event.target)) {
                setOpen(false);
            }
        });
        // Growing past phone width with the menu open shouldn't leave it stuck open.
        window.matchMedia('(min-width: 861px)').addEventListener('change', event => {
            if (event.matches) {
                setOpen(false);
            }
        });
    }

    // ---------- stacking tables ----------

    /*
     * Each cell gets its column's header as a real label element, and its own contents
     * wrapped in one value element. On wide screens the label is hidden and the wrapper is
     * display: contents, so the table lays out exactly as before; on phones the two sit
     * side by side, lined up on the text baseline.
     */
    function label(table) {
        const headers = Array.from(table.querySelectorAll('thead th')).map(th => th.textContent.trim());
        if (headers.length === 0) {
            return;
        }
        table.querySelectorAll('tbody tr, tfoot tr').forEach(row => {
            Array.from(row.children).forEach((cell, index) => {
                if (cell.querySelector(':scope > .table__value')) {
                    return;
                }
                if (!cell.hasAttribute('data-label')) {
                    cell.setAttribute('data-label', headers[index] || '');
                }
                const value = document.createElement('span');
                value.className = 'table__value';
                while (cell.firstChild) {
                    value.appendChild(cell.firstChild);
                }
                const name = document.createElement('span');
                name.className = 'table__label';
                name.textContent = cell.getAttribute('data-label');
                cell.append(name, value);
            });
        });
        table.classList.add('table--stack');
    }

    document.querySelectorAll('table.table').forEach(label);

    // ---------- section nav ----------

    const sectionNav = document.querySelector('.section-nav');
    if (sectionNav) {
        const track = () => document.documentElement.style.setProperty(
            '--section-nav-height', sectionNav.offsetHeight + 'px');
        track();
        if ('ResizeObserver' in window) {
            new ResizeObserver(track).observe(sectionNav);
        } else {
            window.addEventListener('resize', track);
        }
    }

    // ---------- confirmations and auto-submit ----------

    document.addEventListener('submit', event => {
        const message = event.target.getAttribute && event.target.getAttribute('data-confirm');
        if (message && !window.confirm(message)) {
            event.preventDefault();
        }
    });

    document.addEventListener('change', event => {
        const field = event.target;
        if (field.hasAttribute && field.hasAttribute('data-autosubmit') && field.form) {
            if (typeof field.form.requestSubmit === 'function') {
                field.form.requestSubmit();
            } else {
                field.form.submit();
            }
        }
    });
})();
