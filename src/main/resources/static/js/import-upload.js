/* Shows the whole name of the workbook picked on the import page. */
(function () {
    const input = document.querySelector('[data-import-file]');
    const label = document.querySelector('[data-import-file-name]');
    if (!input || !label) {
        return;
    }
    function show() {
        const file = input.files && input.files[0];
        label.textContent = file ? file.name : 'No file chosen';
        label.classList.toggle('import-upload__name--chosen', Boolean(file));
    }
    input.addEventListener('change', show);
    // A page restored from the back button can come back with a file still chosen.
    show();
})();
