// Shows <time data-local-time datetime="..."> in the viewer's own time zone and format.
(function () {
    const format = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'});
    document.querySelectorAll('time[data-local-time]').forEach(time => {
        const at = new Date(time.getAttribute('datetime'));
        if (!Number.isNaN(at.getTime())) {
            time.title = time.textContent;
            time.textContent = format.format(at);
        }
    });
})();
