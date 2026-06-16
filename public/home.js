/* ───────────────────────── Home ─────────────────────────
   The home screen: a stack of horizontal poster rows driven by arrow keys
   (Left/Right within a row, Up/Down between rows, Up from the top row reaches
   the nav pills). The focused card previews in the persistent hero; Enter
   opens it. A "Watch Later" row is prepended when the list is non-empty.    */

import { $, clamp } from './dom.js';
import { tmdbFetch, loadGenreMap, normItem } from './tmdb.js';
import { scroller } from './scroller.js';
import { setHero } from './hero.js';
import { createCard } from './cards.js';
import { getWatchlist, consumeHomeDirty } from './watchlist.js';
import {
  getCurrentPage, showPage, onEnterHome, navPills,
} from './router.js';
import { openTitle } from './detail.js';

const contentEl = $('#content');

const ROWS = [
  { title: 'Trending This Week', path: '/trending/all/week' },
  { title: 'Popular Movies', path: '/movie/popular', type: 'movie' },
  { title: 'Popular Series', path: '/tv/popular', type: 'tv' },
  { title: 'Top Rated Movies', path: '/movie/top_rated', type: 'movie' },
  { title: 'Action', path: '/discover/movie?with_genres=28&sort_by=popularity.desc', type: 'movie' },
  { title: 'Comedy', path: '/discover/movie?with_genres=35&sort_by=popularity.desc', type: 'movie' },
  { title: 'Sci-Fi & Fantasy', path: '/discover/movie?with_genres=878&sort_by=popularity.desc', type: 'movie' },
  { title: 'Horror', path: '/discover/movie?with_genres=27&sort_by=popularity.desc', type: 'movie' },
  { title: 'Animation', path: '/discover/movie?with_genres=16&sort_by=popularity.desc', type: 'movie' },
];

let homeRows = []; // cached TMDB rows (Watch Later is prepended at render time)

// Keyboard-nav state. area is 'nav' (the pills) or a numeric row index.
export const nav = { area: 'nav', col: 0, rows: [], movies: [] };
let navLastTime = 0;

/* ---- Focus ---- */
export function focusCurrent() {
  if (getCurrentPage() !== 'home') return;
  if (nav.area === 'nav') {
    nav.col = clamp(nav.col, 0, navPills.length - 1);
    // Just focus the pill; arrows pass through it. Activation happens on Enter.
    navPills[nav.col]?.focus({ preventScroll: true });
    return;
  }
  const row = nav.rows[nav.area] || [];
  if (!row.length) return;
  nav.col = clamp(nav.col, 0, row.length - 1);
  const card = row[nav.col];
  if (!card) return;
  card.focus({ preventScroll: true });
  scrollRowIntoView(card);
  scrollCardIntoView(card);
  const item = (nav.movies[nav.area] || [])[nav.col];
  if (item) setHero(item);
}

function scrollRowIntoView(card) {
  const rowEl = card.closest('.row');
  if (rowEl) scroller.scrollTo(contentEl, null, Math.max(0, rowEl.offsetTop - 12));
}
function scrollCardIntoView(card) {
  const scroll = card.closest('.row-scroll');
  if (!scroll) return;
  const target = card.offsetLeft - (scroll.clientWidth / 2) + (card.offsetWidth / 2);
  scroller.scrollTo(scroll, Math.max(0, target), null);
}

/* ---- Keyboard ---- */
function navEnter() {
  if (nav.area === 'nav') {
    const page = navPills[nav.col]?.dataset.page;
    if (!page) return;
    showPage(page);
    if (page === 'search') $('#searchInput').focus();
    return;
  }
  const item = (nav.movies[nav.area] || [])[nav.col];
  if (item) openTitle(item);
}

function processNavKey(key) {
  if (key === 'Enter') { navEnter(); return; }

  if (nav.area === 'nav') {
    if (key === 'ArrowRight') nav.col = clamp(nav.col + 1, 0, navPills.length - 1);
    else if (key === 'ArrowLeft') nav.col = clamp(nav.col - 1, 0, navPills.length - 1);
    else if (key === 'ArrowDown') { nav.area = 0; nav.col = 0; }
    focusCurrent();
    return;
  }

  switch (key) {
    case 'ArrowRight': nav.col = clamp(nav.col + 1, 0, (nav.rows[nav.area] || []).length - 1); break;
    case 'ArrowLeft':  nav.col = clamp(nav.col - 1, 0, (nav.rows[nav.area] || []).length - 1); break;
    case 'ArrowDown':  if (nav.area < nav.rows.length - 1) { nav.area++; nav.col = 0; } break;
    case 'ArrowUp':
      if (nav.area === 0) {
        nav.area = 'nav';
        nav.col = Math.max(0, navPills.findIndex((p) => p.classList.contains('active')));
      } else {
        nav.area--;
        nav.col = 0;
      }
      break;
  }
  focusCurrent();
}

// Called by keys.js for the home page. Throttled so holding a key eases rather
// than races; taps stay snappy.
export function handleHomeKey(e) {
  if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter'].includes(e.key)) return;
  e.preventDefault();
  const now = performance.now();
  const throttle = e.repeat ? 90 : 16;
  if (now - navLastTime < throttle) return;
  navLastTime = now;
  processNavKey(e.key);
}

/* ---- Render ---- */
function skeletonRow(title) {
  const row = document.createElement('div');
  row.className = 'row';
  const scroll = '<div class="card skeleton"></div>'.repeat(8);
  row.innerHTML = `<h2 class="row-title"></h2><div class="row-scroll">${scroll}</div>`;
  row.querySelector('.row-title').textContent = title;
  return row;
}

// Build a row element + its card list (used for both TMDB rows and Watch Later).
function buildRow(row) {
  const rowEl = document.createElement('div');
  rowEl.className = 'row';
  const title = document.createElement('h2');
  title.className = 'row-title';
  title.textContent = row.title;
  const scroll = document.createElement('div');
  scroll.className = 'row-scroll';
  const cards = row.items.map((item) => createCard(item, { onOpen: openTitle, onFocus: syncNavToCard }));
  scroll.replaceChildren(...cards);
  rowEl.append(title, scroll);
  return { rowEl, cards };
}

// Mouse focus: point the nav state at whatever card the user focused.
function syncNavToCard(card) {
  for (let r = 0; r < nav.rows.length; r++) {
    const c = nav.rows[r].indexOf(card);
    if (c !== -1) { nav.area = r; nav.col = c; break; }
  }
}

// Render the home rows, prepending a "Watch Later" row when non-empty.
// Rebuilds the keyboard-nav arrays from scratch each time.
function renderHomeRows() {
  const watch = getWatchlist();
  const allRows = watch.length
    ? [{ title: 'Watch Later', items: watch }, ...homeRows]
    : homeRows.slice();

  nav.rows = [];
  nav.movies = [];
  const frag = document.createDocumentFragment();
  allRows.forEach((row, rowIdx) => {
    const { rowEl, cards } = buildRow(row);
    frag.appendChild(rowEl);
    nav.rows[rowIdx] = cards;
    nav.movies[rowIdx] = row.items;
  });
  contentEl.replaceChildren(frag);
}

export async function loadHome() {
  contentEl.replaceChildren(...ROWS.map((r) => skeletonRow(r.title)));
  loadGenreMap(); // warm the genre cache for detail pages

  const results = await Promise.all(ROWS.map(async (row) => {
    try {
      const data = await tmdbFetch(row.path);
      const items = (data.results || [])
        .map((raw) => normItem(raw, row.type))
        .filter((it) => it.poster && (it.mediaType === 'movie' || it.mediaType === 'tv'));
      return { title: row.title, items };
    } catch {
      return { title: row.title, items: [] };
    }
  }));

  homeRows = results.filter((r) => r.items.length);
  if (!homeRows.length) {
    contentEl.innerHTML = '<div class="row-error">Couldn\'t load content. Check your connection.</div>';
    return;
  }
  renderHomeRows();

  setHero(homeRows[0].items.find((it) => it.backdrop) || homeRows[0].items[0]);
  nav.area = 0;
  nav.col = 0;
  focusCurrent();
}

// When returning to home (registered as a router hook): rebuild the Watch Later row if it changed, clamp the
// focused area to the available rows, and restore keyboard focus.
onEnterHome(() => {
  if (consumeHomeDirty() && homeRows.length) renderHomeRows();
  nav.area = clamp(typeof nav.area === 'number' ? nav.area : 0, 0, Math.max(0, nav.rows.length - 1));
  focusCurrent();
});
