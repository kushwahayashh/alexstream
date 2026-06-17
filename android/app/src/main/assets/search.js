/* ───────────────────────── Search ─────────────────────────
   Type in the field; Backspace / ↓ / Enter drops focus into the results grid,
   then arrows move between results and Enter opens one. Backspace from a result
   (or ← / ↑ off the edge) returns to the field; Backspace from the field exits
   to home. Results come straight from TMDB multi-search.                      */

import { $, clamp } from './dom.js';
import { tmdbFetch, normItem } from './tmdb.js';
import { createCard } from './cards.js';
import { getCurrentPage, showPage } from './router.js';
import { openTitle } from './detail.js';

const searchInput = $('#searchInput');
const searchResults = $('#searchResults');
const searchEmpty = $('#searchEmpty');

let searchTimer = null;
let searchCards = [];
let searchIndex = 0;

// Clicking or tabbing into the field jumps straight to the search page.
searchInput.addEventListener('focus', () => {
  if (getCurrentPage() !== 'search') showPage('search');
});

searchInput.addEventListener('input', () => {
  const q = searchInput.value.trim();
  clearTimeout(searchTimer);
  if (getCurrentPage() !== 'search') showPage('search');
  if (!q) {
    searchResults.innerHTML = '';
    searchCards = [];
    searchEmpty.textContent = 'Search for a movie or show to get started.';
    searchEmpty.classList.remove('hidden');
    return;
  }
  searchTimer = setTimeout(() => runSearch(q), 350);
});

async function runSearch(q) {
  searchEmpty.classList.add('hidden');
  searchResults.innerHTML = '<div class="loading"><div class="spinner"></div><p>Searching…</p></div>';
  try {
    const data = await tmdbFetch(`/search/multi?query=${encodeURIComponent(q)}&include_adult=false`);
    const items = (data.results || [])
      .filter((r) => (r.media_type === 'movie' || r.media_type === 'tv') && r.poster_path)
      .map((r) => normItem(r));

    if (!items.length) {
      searchResults.innerHTML = '';
      searchCards = [];
      searchEmpty.textContent = `No results for “${q}”.`;
      searchEmpty.classList.remove('hidden');
      return;
    }
    searchResults.replaceChildren(...items.map((it) => createCard(it, { onOpen: openTitle })));
    searchCards = Array.from(searchResults.querySelectorAll('.card'));
    searchIndex = 0;
  } catch (err) {
    searchResults.innerHTML = `<div class="row-error">Error: ${err.message}</div>`;
  }
}

// Number of columns in the results grid (derived from layout, for ↑/↓ paging).
function searchColumns() {
  if (!searchCards.length) return 1;
  const top = searchCards[0].offsetTop;
  let cols = 0;
  for (const c of searchCards) { if (c.offsetTop === top) cols++; else break; }
  return Math.max(1, cols);
}

function focusSearchResult(i) {
  searchIndex = clamp(i, 0, searchCards.length - 1);
  const card = searchCards[searchIndex];
  if (card) { card.focus({ preventScroll: true }); card.scrollIntoView({ block: 'nearest' }); }
}

export function handleSearchKey(e) {
  const key = e.key;

  if (document.activeElement === searchInput) {
    if (key === 'Backspace' || key === 'ArrowDown' || key === 'Enter') {
      e.preventDefault();
      if (searchCards.length) focusSearchResult(0);   // get focus out into results
      else if (key === 'Backspace') showPage('home');
    }
    return;                                            // every other key types normally
  }

  // A result card is focused.
  if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Escape', 'Backspace'].includes(key)) return;
  e.preventDefault();
  const cols = searchColumns();
  switch (key) {
    case 'ArrowRight': focusSearchResult(searchIndex + 1); break;
    case 'ArrowLeft':  (searchIndex % cols === 0) ? searchInput.focus() : focusSearchResult(searchIndex - 1); break;
    case 'ArrowDown':  focusSearchResult(Math.min(searchIndex + cols, searchCards.length - 1)); break;
    case 'ArrowUp':    (searchIndex < cols) ? searchInput.focus() : focusSearchResult(searchIndex - cols); break;
    case 'Enter':      searchCards[searchIndex]?.click(); break;
    case 'Escape':     showPage('home'); break;
    case 'Backspace':  searchInput.focus(); break;
  }
}
