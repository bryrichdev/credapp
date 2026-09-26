// Two-step setup: draws the QR code for the authenticator app, and copies recovery codes.
(function () {
    const holder = document.querySelector('[data-otpauth]');
    if (holder && typeof qrcode === 'function') {
        const qr = qrcode(0, 'M');
        qr.addData(holder.dataset.otpauth);
        qr.make();
        holder.innerHTML = qr.createSvgTag({cellSize: 5, margin: 4, scalable: true});
    }

    const copy = document.querySelector('[data-copy-codes]');
    if (copy && navigator.clipboard) {
        copy.addEventListener('click', () => {
            navigator.clipboard.writeText(copy.dataset.copyCodes).then(() => {
                copy.textContent = 'Copied';
            });
        });
    } else if (copy) {
        copy.hidden = true;
    }
})();
