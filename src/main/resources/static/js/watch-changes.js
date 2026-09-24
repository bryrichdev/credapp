/*
 * Edit-in-place pages (payer, owner): show the save bar once a field in the
 * form[data-watch-changes] differs from how the page loaded, and warn before leaving
 * with unsaved changes.
 */
(function () {
    const form = document.querySelector('form[data-watch-changes]');
    const saveBar = document.getElementById('saveBar');
    if (!form || !saveBar) {
        return;
    }

    // Remember what every field looked like when the page loaded.
    const fields = Array.from(form.querySelectorAll('input, select, textarea'));
    const initial = new Map(fields.map(field => [field, field.value]));
    let dirty = false;

    function check() {
        dirty = fields.some(field => field.value !== initial.get(field));
        saveBar.hidden = !dirty;
    }

    fields.forEach(field => {
        field.addEventListener('input', check);
        field.addEventListener('change', check);
    });

    // Don't warn when the change is being saved on purpose.
    form.addEventListener('submit', () => {
        dirty = false;
    });

    window.addEventListener('beforeunload', event => {
        if (dirty) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
})();
