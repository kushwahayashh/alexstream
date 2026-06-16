/* ───────────────────────── Player + quality picker ─────────────────────────
   Netflix-style flow: a single Play button (movie) or an episode row opens a
   minimal quality modal listing the available stream links; selecting one
   closes it and starts the HLS/MP4 player.

   This module owns its modals' keyboard handling: handleModalKey(e) returns
   true if it consumed the event, so main.js's keyboard router can give modals
   first dibs.                                                                */

import { $, $$ } from './dom.js';
import { api } from './tmdb.js';

/* ---- Quality picker modal ---- */

export function closeQualityModal() {
  $('#qModal')?.remove();
}

export async function openQualityModal(fid, title) {
  closeQualityModal();
  const overlay = document.createElement('div');
  overlay.className = 'q-modal-overlay';
  overlay.id = 'qModal';
  overlay.innerHTML = `
    <div class="q-modal" role="dialog" aria-modal="true" aria-label="${title || 'Select quality'}">
      <div class="q-list" id="qList"><div class="loading"><div class="spinner"></div><p>Loading…</p></div></div>
    </div>`;
  document.body.appendChild(overlay);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeQualityModal(); });

  try {
    const data = await api(`/api/links?fid=${fid}`);
    const list = $('#qList');
    if (!list) return;
    if (!data.links || !data.links.length) {
      list.innerHTML = '<div class="detail-error">No stream links available.</div>';
      return;
    }
    list.innerHTML = '';
    data.links.forEach((l) => {
      const btn = document.createElement('button');
      btn.className = 'q-option';
      btn.innerHTML =
        `<span class="q-left">${l.quality}</span>` +
        `<span class="q-right">${[l.size, l.speed].filter(Boolean).join(' • ')}</span>`;
      btn.addEventListener('click', () => {
        closeQualityModal();
        playStream(l.proxiedUrl, l.ext, { fid });
      });
      list.appendChild(btn);
    });
    list.querySelector('.q-option')?.focus();
  } catch (err) {
    const list = $('#qList');
    if (list) list.innerHTML = `<div class="detail-error">Error: ${err.message}</div>`;
  }
}

/* ---- Player ---- */

let activePlayer = null;

export function stopPlayer() {
  if (activePlayer) {
    activePlayer.destroy?.();
    activePlayer = null;
  }
  $('#playerModal')?.remove();
}

async function loadHlsJs() {
  if (window.Hls) return;
  await new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/hls.js@latest';
    s.onload = resolve;
    s.onerror = reject;
    document.head.appendChild(s);
  });
}

export async function playStream(url, ext, subCtx = {}) {
  stopPlayer();

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.id = 'playerModal';
  overlay.innerHTML = `<div class="modal-content"><video id="videoPlayer" controls autoplay playsinline></video></div>`;
  document.body.appendChild(overlay);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) stopPlayer(); });

  const video = $('#videoPlayer');

  if (ext === 'm3u8') {
    try {
      await loadHlsJs();
      if (window.Hls?.isSupported()) {
        const hls = new window.Hls();
        hls.loadSource(url);
        hls.attachMedia(video);
        activePlayer = hls;
        hls.on(window.Hls.Events.MANIFEST_PARSED, () => video.play());
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = url;
      }
    } catch {
      video.src = url;
    }
  } else {
    video.src = url;
  }

  // Subtitles load in the background; failures must never block playback.
  loadSubtitles(video, subCtx).catch(() => {});
}

// Fetch FebBox subtitles for the exact file (by fid) and add them as <track>s.
async function loadSubtitles(video, { fid } = {}) {
  if (!fid) return;
  const data = await api(`/api/subtitles?fid=${fid}`);
  (data.subtitles || []).forEach((sub, i) => {
    const track = document.createElement('track');
    track.kind = 'subtitles';
    track.srclang = sub.lang;
    track.label = sub.langName || sub.lang;
    track.src = sub.url;
    if (i === 0) track.default = true;
    video.appendChild(track);
  });
}

/* ---- Modal keyboard handling ----
   Returns true when an open modal consumed the key (so the router stops).   */
export function handleModalKey(e) {
  const qModal = $('#qModal');
  if (qModal) {
    const opts = $$('.q-option', qModal);
    const idx = opts.indexOf(document.activeElement);
    if (e.key === 'Escape' || e.key === 'Backspace') { e.preventDefault(); closeQualityModal(); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); opts[Math.min(idx + 1, opts.length - 1)]?.focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); opts[Math.max(idx - 1, 0)]?.focus(); }
    else if (e.key === 'Enter') { e.preventDefault(); document.activeElement?.click?.(); }
    return true;
  }

  if ($('#playerModal')) {
    if (e.key === 'Escape' || e.key === 'Backspace') { e.preventDefault(); stopPlayer(); }
    return true;
  }

  return false;
}
