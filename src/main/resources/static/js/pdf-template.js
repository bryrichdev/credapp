(() => {
    const page = document.querySelector('[data-pdf-page]');
    const image = document.querySelector('[data-pdf-image]');
    const error = document.querySelector('[data-preview-error]');
    if (!page || !image) return;
    const show = value => {
        page.value = value;
        error.hidden = true;
        image.src = image.dataset.previewBase + value;
    };
    image.addEventListener('error', () => { error.hidden = false; });
    page.addEventListener('change', () => show(page.value));
    document.querySelectorAll('[data-show-pdf-page]').forEach(button => {
        button.addEventListener('click', () => {
            show(button.dataset.showPdfPage);
            if (window.matchMedia('(max-width: 1000px)').matches) image.scrollIntoView({behavior: 'smooth'});
        });
    });
})();
