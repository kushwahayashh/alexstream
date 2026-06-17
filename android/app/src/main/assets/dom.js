/* ───────────────────────── DOM / util helpers ─────────────────────────
   Tiny, dependency-free helpers shared across the app modules.          */

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

// Lucide is loaded from a CDN; guard in case it failed so it never blocks render.
export const paintIcons = () => window.lucide?.createIcons?.();

// Escape text before interpolating into innerHTML. TMDB titles / filenames can
// contain &, <, >, quotes which would otherwise break markup (or worse).
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const escapeHtml = (s = '') => String(s).replace(/[&<>"']/g, (c) => ESC[c]);
