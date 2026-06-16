/* ───────────────────────── Keyboard router ─────────────────────────
   One document-level keydown listener that dispatches by context:
     1. an open modal (quality / player) gets first dibs
     2. otherwise the active page's handler runs
   Registering this module (via main.js) wires the listener.            */

import { getCurrentPage } from './router.js';
import { handleModalKey } from './player.js';
import { handleHomeKey } from './home.js';
import { handleSearchKey } from './search.js';
import { handleDetailKey } from './detail.js';

document.addEventListener('keydown', (e) => {
  if (handleModalKey(e)) return;            // modal consumed it

  switch (getCurrentPage()) {
    case 'detail': handleDetailKey(e); break;
    case 'search': handleSearchKey(e); break;
    case 'home':   handleHomeKey(e); break;
    // library / settings have no keyboard nav yet
  }
});
