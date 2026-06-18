/* ───────────────────────── Native bridge (Android) ─────────────────────────
   Loaded as a classic script BEFORE the ES modules so the globals it sets are
   available to them. When running inside the Android WebView, AndroidBridge is
   injected by MainActivity. The web UI still uses normal fetch() for API data;
   this bridge provides the backend base URL and hands selected streams to the
   native ExoPlayer. In a plain browser none of this exists and the app falls
   back to any preconfigured backend base / HTML5 video.                    */
(function () {
  const bridge = (typeof window !== 'undefined') ? window.AndroidBridge : null;
  const callbacks = {};
  let seq = 0;

  // Native fetch resolves here (called from Kotlin via evaluateJavascript).
  window.__nativeFetchResolve = function (id, ok, payload) {
    const cb = callbacks[id];
    if (!cb) return;
    delete callbacks[id];
    if (ok) cb.resolve(payload);
    else cb.reject(new Error(payload || 'Native fetch failed'));
  };

  // Promise<string> of the raw response body for an absolute URL.
  window.__nativeFetchText = function (url) {
    return new Promise((resolve, reject) => {
      if (!bridge || typeof bridge.fetchJson !== 'function') {
        reject(new Error('Native bridge unavailable'));
        return;
      }
      const id = 'cb_' + (seq++);
      callbacks[id] = { resolve, reject };
      bridge.fetchJson(url, id);
    });
  };

  window.__hasNativeBridge = !!(bridge && typeof bridge.fetchJson === 'function');

  // Backend base URL (the Modal server) comes from native config so it can be
  // changed without touching the bundled JS. In a plain browser (no bridge) any
  // value pre-set on window.__BACKEND_BASE is preserved, which lets the bundled
  // UI be tested against a hosted backend outside the app.
  const base = (bridge && typeof bridge.backendBase === 'function')
    ? bridge.backendBase()
    : (window.__BACKEND_BASE || '');
  window.__BACKEND_BASE = (base || '').replace(/\/+$/, '');

  // Turn an app-relative "/api/..." path into an absolute backend URL. TMDB and
  // other absolute URLs pass through untouched.
  window.__apiUrl = function (path) {
    if (/^https?:/i.test(path)) return path;
    return window.__BACKEND_BASE + path;
  };

  // Hand a chosen stream off to the native ExoPlayer. Returns false if there's
  // no native player (browser fallback should handle it).
  window.__nativePlay = function (url, ext, title, fid) {
    if (bridge && typeof bridge.play === 'function') {
      bridge.play(url, ext || '', title || '', String(fid || ''));
      return true;
    }
    return false;
  };

  // ── In-app updater ──
  // True only inside the Android WebView where the native downloader exists.
  window.__hasNativeUpdate = !!(bridge && typeof bridge.updateApp === 'function');

  // Kick off the native APK download + install. Returns false in a plain browser
  // so the caller can show an "unavailable" state instead of hanging.
  window.__updateApp = function (url) {
    if (bridge && typeof bridge.updateApp === 'function') {
      bridge.updateApp(url);
      return true;
    }
    return false;
  };

  // Native download-complete callback. update.js registers a single listener here
  // so the Update button can reset to "Update" (success) or show "Failed".
  window.__onUpdateStatus = function (ok, message) {
    if (typeof window.__updateStatusListener === 'function') {
      window.__updateStatusListener(ok, message);
    }
  };
})();
