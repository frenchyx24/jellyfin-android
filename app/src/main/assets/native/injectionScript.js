(() => {
    // Nflix: start jellyfin-web with the session opened by the native login
    try {
        const credentials = window.NativeInterface.getNflixCredentials();
        if (credentials) {
            window.localStorage.setItem('jellyfin_credentials', credentials);
        }
    } catch (e) {
        console.error('Nflix credentials injection failed', e);
    }

    const scripts = [
        '/native/nativeshell.js',
        '/native/EventEmitter.js',
        document.currentScript.src.concat('?deferred=true&ts=', Date.now())
    ];
    for (const script of scripts) {
        const scriptElement = document.createElement('script');
        scriptElement.src = script;
        scriptElement.charset = 'utf-8';
        scriptElement.setAttribute('defer', '');
        document.body.appendChild(scriptElement);
    }
    document.currentScript.remove();
})();
