/* ───────────────────────── Home hero ─────────────────────────
   The home page has one persistent hero; focusing/hovering a card
   crossfades its backdrop and meta here. A short debounce + image
   preload keeps fast scrolling smooth (no flashes).                */

import { $, escapeHtml } from './dom.js';
import { backdropURL, posterURL } from './tmdb.js';

const heroBackdrop = $('#hero-backdrop');
const heroTitle = $('#hero-title');
const heroMeta = $('#hero-meta');
const heroOverview = $('#hero-overview');

let heroCurrentId = null;
let heroTimer = null;
let heroPreload = null;

export function setHero(item) {
  if (!item) return;
  const id = item.tmdbId ?? item.title;
  if (id === heroCurrentId && !heroTimer) return;

  clearTimeout(heroTimer);
  heroTimer = setTimeout(() => {
    heroTimer = null;
    heroCurrentId = id;
    heroTitle.textContent = item.title;
    heroOverview.textContent = item.overview || '';

    // Match the detail design's meta row: green "% Match", year, type label.
    const parts = [];
    if (item.rating && item.voteCount >= 50) {
      parts.push(`<span class="match">${Math.round(item.rating * 10)}% Match</span>`);
    }
    if (item.year) parts.push(`<span>${escapeHtml(item.year)}</span>`);
    parts.push(`<span>${item.mediaType === 'tv' ? 'Series' : 'Movie'}</span>`);
    heroMeta.innerHTML = parts.join('');

    const url = backdropURL(item.backdrop) || posterURL(item.poster, 'w780');
    if (!url) { heroBackdrop.classList.remove('fade-in'); return; }
    const img = new Image();
    heroPreload = img;
    img.onload = () => {
      if (heroPreload !== img) return;        // a newer hero won the race
      heroBackdrop.classList.remove('fade-in');
      requestAnimationFrame(() => {
        heroBackdrop.style.backgroundImage = `url(${url})`;
        requestAnimationFrame(() => heroBackdrop.classList.add('fade-in'));
      });
    };
    img.src = url;
  }, 110);
}
