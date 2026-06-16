/* ───────────────────────── AlexTV entry point ─────────────────────────
   Browse/discovery comes from TMDB (tmdb.js, client-side). Streaming comes
   from our backend: a TMDB title is resolved to a ShowBox id (/api/resolve),
   then the share-key → files → links flow produces playable stream URLs.

   This module wires the cross-module lifecycle hooks, attaches the keyboard
   router, and kicks off the home load.                                     */

import { $ } from './dom.js';
import { initChrome, onLeave, getCurrentPage } from './router.js';
import { stopPlayer, closeQualityModal, handleModalKey } from './player.js';
import { loadHome, handleHomeKey } from './home.js';
import { handleSearchKey } from './search.js';
import { handleDetailKey } from './detail.js';

// Tear down any open player/quality modal when navigating away from detail.
// (Modals only open from the detail page; staying within it keeps them.)
onLeave((nextPage) => {
  if (nextPage !== 'detail') {
    stopPlayer();
    closeQualityModal();
  }
});

// Keyboard router: one document-level keydown listener that dispatches by
// context — an open modal (quality / player) gets first dibs, otherwise the
// active page's handler runs.
document.addEventListener('keydown', (e) => {
  if (handleModalKey(e)) return;            // modal consumed it

  switch (getCurrentPage()) {
    case 'detail': handleDetailKey(e); break;
    case 'search': handleSearchKey(e); break;
    case 'home':   handleHomeKey(e); break;
    // library / settings have no keyboard nav yet
  }
});

initChrome({ onSearchPage: () => $('#searchInput').focus() });

loadHome();
