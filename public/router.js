/* ───────────────────────── Router / page switching ─────────────────────────
   Owns which page is visible and the currentPage / lastBrowsePage state.
   To avoid import cycles with the page modules, router exposes lifecycle
   hooks instead of importing home/player directly:
     - onLeave(fn)      runs whenever we navigate away from any page
     - onEnterHome(fn)  runs when the home page becomes visible
   Page modules register their own enter/cleanup logic via these.          */

import { $, $$ } from './dom.js';

const BROWSE_PAGES = ['home', 'search', 'library', 'settings'];

const topbar = $('#topbar');
const pages = {
  home: $('#homePage'),
  search: $('#searchPage'),
  library: $('#libraryPage'),
  settings: $('#settingsPage'),
  detail: $('#detailView'),
};
const navPills = $$('.nav-pill');

let currentPage = 'home';
let lastBrowsePage = 'home';

export const getCurrentPage = () => currentPage;
export const getLastBrowsePage = () => lastBrowsePage;

const leaveHooks = [];
const enterHomeHooks = [];
export const onLeave = (fn) => leaveHooks.push(fn);
export const onEnterHome = (fn) => enterHomeHooks.push(fn);

export function showPage(page) {
  leaveHooks.forEach((fn) => fn(page));

  currentPage = page;
  Object.entries(pages).forEach(([name, el]) => el.classList.toggle('hidden', name !== page));
  topbar.classList.toggle('hidden', page === 'detail');

  if (BROWSE_PAGES.includes(page)) {
    lastBrowsePage = page;
    navPills.forEach((p) => p.classList.toggle('active', p.dataset.page === page));
  }

  if (page !== 'home') {
    window.scrollTo(0, 0);
  } else {
    enterHomeHooks.forEach((fn) => fn());
  }
}

// Wire the nav pills (click + the scrolled-topbar style). Imported for side effect
// by main.js so the listeners attach once at startup.
export function initChrome({ onSearchPage } = {}) {
  navPills.forEach((pill) => {
    pill.addEventListener('click', () => {
      showPage(pill.dataset.page);
      if (pill.dataset.page === 'search') onSearchPage?.();
    });
  });
  window.addEventListener('scroll', () => {
    topbar.classList.toggle('scrolled', window.scrollY > 30);
  });
}

export { navPills };
