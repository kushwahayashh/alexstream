/* ───────────────────────── AlexTV entry point ─────────────────────────
   Browse/discovery comes from TMDB (tmdb.js, client-side). Streaming comes
   from our backend: a TMDB title is resolved to a ShowBox id (/api/resolve),
   then the share-key → files → links flow produces playable stream URLs.

   This module wires the cross-module lifecycle hooks, attaches the keyboard
   router, and kicks off the home load.                                     */

import { $ } from './dom.js';
import { initChrome, onLeave } from './router.js';
import { stopPlayer, closeQualityModal } from './player.js';
import { loadHome } from './home.js';
import './keys.js'; // registers the document keydown listener

// Tear down any open player/quality modal when navigating away from detail.
// (Modals only open from the detail page; staying within it keeps them.)
onLeave((nextPage) => {
  if (nextPage !== 'detail') {
    stopPlayer();
    closeQualityModal();
  }
});

initChrome({ onSearchPage: () => $('#searchInput').focus() });

loadHome();
