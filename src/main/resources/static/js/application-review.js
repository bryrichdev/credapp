// Keeps an SSN hidden on the review page until someone chooses to see it.
(function () {
    document.querySelectorAll('[data-secret-toggle]').forEach(button => {
        const input = button.parentElement.querySelector('[data-secret]');
        button.addEventListener('click', () => {
            const hidden = input.type === 'password';
            input.type = hidden ? 'text' : 'password';
            button.textContent = hidden ? 'Hide' : 'Show';
        });
    });
})();
