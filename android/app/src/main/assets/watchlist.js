/* ───────────────────────── Watch Later ─────────────────────────
   A simple localStorage-backed list. The home page shows it as a
   "Watch Later" row; the detail page toggles membership.            */

const WATCHLIST_KEY = 'watchlater_v1';

// Set when the list changes so home knows to rebuild its row on next show.
let homeDirty = false;
export function markHomeDirty() { homeDirty = true; }
export function consumeHomeDirty() {
  const was = homeDirty;
  homeDirty = false;
  return was;
}

export function getWatchlist() {
  try { return JSON.parse(localStorage.getItem(WATCHLIST_KEY)) || []; }
  catch { return []; }
}

function saveWatchlist(list) {
  try { localStorage.setItem(WATCHLIST_KEY, JSON.stringify(list)); }
  catch { /* storage disabled / quota — ignore */ }
}

function watchKey(item) { return `${item.mediaType}:${item.tmdbId}`; }

export function isSaved(item) {
  return getWatchlist().some((w) => watchKey(w) === watchKey(item));
}

// Returns true if the item is now saved, false if it was removed.
export function toggleWatchlist(item) {
  const list = getWatchlist();
  const k = watchKey(item);
  const idx = list.findIndex((w) => watchKey(w) === k);
  if (idx === -1) {
    list.unshift({
      tmdbId: item.tmdbId, mediaType: item.mediaType, title: item.title, year: item.year,
      poster: item.poster, backdrop: item.backdrop, rating: item.rating,
      voteCount: item.voteCount, overview: item.overview,
    });
  } else {
    list.splice(idx, 1);
  }
  saveWatchlist(list);
  markHomeDirty();
  return idx === -1;
}
