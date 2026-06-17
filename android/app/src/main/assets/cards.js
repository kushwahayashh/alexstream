/* ───────────────────────── Cards ─────────────────────────
   A poster card shared by the home grid and the search results.
   Home cards join the keyboard-nav grid (via the onFocus hook the
   home module passes in) and preview in the hero; search-result
   cards are simply clickable.                                      */

import { posterURL } from './tmdb.js';

// onOpen(item)  — required; what Enter/click does (open detail).
// onFocus(card) — optional; home uses it to sync its (row,col) nav state.
export function createCard(item, { onOpen, onFocus } = {}) {
  const card = document.createElement('div');
  card.className = 'card';
  card.tabIndex = -1;
  card._item = item;

  if (item.poster) {
    const img = document.createElement('img');
    img.className = 'card-poster';
    img.loading = 'lazy';
    img.alt = item.title;
    img.addEventListener('load', () => img.classList.add('is-loaded'), { once: true });
    img.src = posterURL(item.poster);
    card.appendChild(img);
  } else {
    const fb = document.createElement('div');
    fb.className = 'card-fallback';
    fb.textContent = item.title;
    card.appendChild(fb);
  }

  card.addEventListener('click', () => onOpen?.(item));
  if (onFocus) card.addEventListener('focus', () => onFocus(card));
  return card;
}
