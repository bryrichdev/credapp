(() => {
    const role = document.getElementById('accountRole');
    const update = () => {
        const admin = role.value === 'ADMIN';
        document.getElementById('adminFields').hidden = !admin;
        document.getElementById('coordinatorFields').hidden = admin;
        document.getElementById('groupName').required = admin;
        document.getElementById('joinCode').required = !admin;
        document.getElementById('registerButton').textContent = admin ? 'Create account & user group' : 'Request to join';
    };
    role.addEventListener('change', update);
    update();
})();
