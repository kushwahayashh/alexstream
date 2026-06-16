/* ───────────────────────── Backend API helper ─────────────────────────
   Thin wrapper over fetch for our own /api/* endpoints (ShowBox/FebBox
   streaming bridge). TMDB browse data goes through tmdb.js instead.      */

export async function api(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Request failed (${res.status})`);
  }
  return res.json();
}
