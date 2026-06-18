/* ───────────────────────── In-app updater ─────────────────────────
   The "Update" nav pill pulls the rolling `latest` GitHub release APK and hands
   it to the native installer (see MainActivity.updateApp / DownloadManager). The
   button shows "Downloading…" while the native download runs; native fires
   window.__onUpdateStatus when it finishes so we can reset or show "Failed".
   In a plain browser there is no native bridge, so we just flash "Unavailable". */

import { $ } from './dom.js';

// The APK always lives at this stable rolling-release URL.
const RELEASE_APK_URL = 'https://github.com/kushwahayashh/alexstream/releases/download/latest/AlexStream-TV.apk';

let downloading = false;

function setLabel(text) {
  const btn = $('#updateBtn');
  if (btn) btn.textContent = text;
}

function reset() {
  downloading = false;
  const btn = $('#updateBtn');
  if (btn) {
    btn.textContent = 'Update';
    btn.classList.remove('is-downloading');
  }
  window.__updateStatusListener = null;
}

function flash(text) {
  setLabel(text);
  setTimeout(reset, 1500);
}

export function triggerUpdate() {
  if (downloading) return;

  const btn = $('#updateBtn');
  if (!window.__hasNativeUpdate) {
    // Plain browser (or older app without the native hook) — nothing to install.
    flash('Unavailable');
    return;
  }

  downloading = true;
  if (btn) {
    btn.textContent = 'Downloading…';
    btn.classList.add('is-downloading');
  }

  // Native fires this when the download finishes. On success the system installer
  // takes over (button just resets); on failure we surface it briefly.
  window.__updateStatusListener = (ok) => {
    if (ok) reset();
    else flash('Failed');
  };

  window.__updateApp(RELEASE_APK_URL);
}
