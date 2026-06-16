/* ───────────────────────── Detail / streaming ─────────────────────────
   The full-bleed detail page: hero (enriched from TMDB), action buttons
   (row 0), and — for series — sticky season tabs (row 1) + episode list
   (row 2). A TMDB title is bridged to a streamable ShowBox id via
   /api/resolve, then the share-key → files → links flow produces playable
   streams. Keyboard nav is a flat (row, col) grid.                         */

import { $, $$, clamp, paintIcons, escapeHtml } from './dom.js';
import { tmdbFetch, backdropURL, posterURL } from './tmdb.js';
import { api } from './api.js';
import { isSaved, toggleWatchlist } from './watchlist.js';
import { showPage, getLastBrowsePage } from './router.js';
import { openQualityModal } from './player.js';

const detailView = $('#detailView');

/* Detail keyboard state — a (row, col) grid:
     row 0 = action buttons (Play / Resume / My List)
     row 1 = season tabs        (series only)
     row 2 = episode list       (series only)
   For a movie only row 0 exists. Season episodes load on demand. */
const dnav = {
  isTv: false,
  item: null,           // the TMDB item being shown
  row: 0,
  col: 0,
  cols: {},             // remembered column per row
  playFid: null,        // fid the Play button targets (movie file / first episode)
  shareKey: null,
  seasonIdx: 0,
  seasons: [],          // [{ fid, label }]  (single synthetic entry for flat shows)
  flatEpisodes: null,   // pre-fetched episodes when a show has no season folders
  episodeCount: 0,      // episodes currently rendered (row-2 length)
};

function resetDetailNav() {
  Object.assign(dnav, {
    isTv: false, item: null, row: 0, col: 0, cols: {}, playFid: null,
    shareKey: null, seasonIdx: 0, seasons: [], flatEpisodes: null, episodeCount: 0,
  });
}

/* ---- Hero markup (re-rendered once TMDB credits resolve) ---- */
// % match, year, runtime / season count — all from real TMDB data. The decorative
// cert/4K/HDR tags in the mock are intentionally dropped.
function metaRowHtml(item, detail) {
  const bits = [];
  if (item.rating && item.voteCount >= 50) {
    bits.push(`<span class="match">${Math.round(item.rating * 10)}% Match</span>`);
  }
  if (item.year) bits.push(`<span>${escapeHtml(item.year)}</span>`);
  const runtime = detail?.runtime || detail?.episode_run_time?.[0];
  if (runtime) bits.push(`<span>${runtime} min</span>`);
  if (detail?.number_of_seasons) {
    bits.push(`<span>${detail.number_of_seasons} Season${detail.number_of_seasons > 1 ? 's' : ''}</span>`);
  }
  return bits.join('');
}

function heroInfoHtml(item, detail) {
  const genres = (detail?.genres || []).map((g) => g.name);
  const cast = (detail?.credits?.cast || []).slice(0, 3).map((c) => c.name);
  const director = (detail?.credits?.crew || []).find((c) => c.job === 'Director')?.name;
  const facts = [];
  if (cast.length) facts.push(`<b>Cast:</b> ${escapeHtml(cast.join(', '))}`);
  if (director) facts.push(`<b>Director:</b> ${escapeHtml(director)}`);
  if (genres.length) facts.push(`<b>Genres:</b> ${escapeHtml(genres.join(', '))}`);
  return `
    <h1 class="series-logo">${escapeHtml(item.title)}</h1>
    <div class="meta-row">${metaRowHtml(item, detail)}</div>
    ${item.overview ? `<p class="synopsis">${escapeHtml(item.overview)}</p>` : ''}
    ${facts.length ? `<div class="genres">${facts.join('&nbsp;&nbsp;&nbsp;')}</div>` : ''}
  `;
}

// Action buttons (row 0). Play state fills in once the stream resolves; Resume is
// a visual placeholder per the design.
function actionRowHtml(item) {
  const isTv = item.mediaType === 'tv';
  const later = isSaved(item);
  return `
    <div class="action-row">
      <div class="btn primary focusable" data-action="play" data-row="0" data-col="0">
        <i data-lucide="play" class="icon"></i> <span class="btn-label">Play</span>
      </div>
      ${isTv ? `
      <div class="btn focusable" data-action="resume" data-row="0" data-col="1">
        <i data-lucide="rotate-ccw" class="icon"></i> Resume
      </div>` : ''}
      <div class="btn focusable ${later ? 'saved' : ''}" data-action="list"
           data-row="0" data-col="${isTv ? 2 : 1}">
        <i data-lucide="${later ? 'check' : 'plus'}" class="icon"></i> My List
      </div>
    </div>`;
}

function renderDetailShell(item) {
  const bg = backdropURL(item.backdrop) || posterURL(item.poster, 'w780');
  const isTv = item.mediaType === 'tv';

  detailView.innerHTML = `
    <section class="hero">
      <div class="hero-bg" style="${bg ? `background-image:url(${bg})` : ''}"></div>
      <div class="hero-content" id="heroContent">${heroInfoHtml(item, null)}</div>
    </section>
    <section class="section action-section">
      <div id="actionRow">${actionRowHtml(item)}</div>
    </section>
    ${isTv ? `
    <section class="section">
      <div class="section-head"><div class="season-tabs" id="seasonTabs"></div></div>
      <div class="episode-list" id="episodeList"></div>
    </section>` : ''}
  `;
  bindActionRow(item);
  paintIcons();
}

function streamError(message) {
  // Series errors land in the episode list; movie errors replace the Play button.
  const el = $('#episodeList');
  if (el) { el.innerHTML = `<div class="detail-error">${escapeHtml(message)}</div>`; return; }
  const play = $('#actionRow [data-action="play"] .btn-label');
  if (play) play.textContent = 'Unavailable';
  const btn = $('#actionRow [data-action="play"]');
  if (btn) btn.dataset.disabled = '1';
}

/* ---- Action buttons ---- */
function bindActionRow(item) {
  $$('#actionRow .focusable').forEach((el) => {
    el.addEventListener('click', () => activateAction(el.dataset.action, item));
  });
}

function activateAction(action, item) {
  if (action === 'play') {
    if (dnav.playFid) openQualityModal(dnav.playFid, item.title);
  } else if (action === 'list') {
    toggleWatchlist(item);
    repaintListButton(item);
  }
  // 'resume' is a visual placeholder for now.
}

function repaintListButton(item) {
  const btn = $('#actionRow [data-action="list"]');
  if (!btn) return;
  const saved = isSaved(item);
  btn.classList.toggle('saved', saved);
  // Swap the icon: lucide replaced the original <i> with an <svg> on first paint,
  // so drop in a fresh <i> with the new glyph and let lucide render it again.
  const ico = btn.querySelector('.icon');
  if (ico) {
    const next = document.createElement('i');
    next.className = 'icon';
    next.setAttribute('data-lucide', saved ? 'check' : 'plus');
    ico.replaceWith(next);
    paintIcons();
  }
}

// Toggle the Play button between a spinner label and its ready label.
function setPlayLoading(loading) {
  const label = $('#actionRow [data-action="play"] .btn-label');
  if (label) label.textContent = loading ? 'Loading…' : 'Play';
}

/* ---- ShowBox id resolution (cached on the item) ---- */
async function resolveShowboxId(item) {
  if (item._showboxId !== undefined) return item._showboxId;
  try {
    const data = await api(
      `/api/resolve?title=${encodeURIComponent(item.title)}&year=${item.year}&type=${item.mediaType}`,
    );
    item._showboxId = data.id;
  } catch {
    item._showboxId = null;
  }
  return item._showboxId;
}

/* ---- Entry point ---- */
export function openTitle(item) {
  showPage('detail');
  resetDetailNav();
  dnav.isTv = item.mediaType === 'tv';
  dnav.item = item;
  renderDetailShell(item);
  applyDetailFocus();

  // Enrich the hero (genres, runtime, cast) without blocking the stream lookup.
  const detailPath = item.mediaType === 'tv' ? `/tv/${item.tmdbId}` : `/movie/${item.tmdbId}`;
  tmdbFetch(`${detailPath}?append_to_response=credits`)
    .then((detail) => {
      const info = $('#heroContent');
      if (info) info.innerHTML = heroInfoHtml(item, detail);
    })
    .catch(() => {});

  return item.mediaType === 'tv' ? openSeries(item) : openMovie(item);
}

async function openMovie(item) {
  setPlayLoading(true);
  try {
    const id = await resolveShowboxId(item);
    if (!id) return streamError("This title isn't available to stream right now.");

    const shareData = await api(`/api/share-key?id=${id}&type=1`);
    const filesData = await api(`/api/files?shareKey=${shareData.shareKey}`);
    const vids = filesData.videoFiles;
    if (!vids.length) return streamError('No video files found for this title.');

    dnav.playFid = vids[0].fid;
    setPlayLoading(false);
  } catch (err) {
    streamError(err.message);
  }
}

/* ───────────── Series ───────────── */
// A real season folder is named like "season 1", "Season 01", or "S1".
function isSeasonFolder(folder) {
  return /^\s*(season\s*\d{1,2}|s\d{1,2})\s*$/i.test(folder.file_name || '');
}
function seasonNumber(folder) {
  const m = (folder.file_name || '').match(/\d+/);
  return m ? Number(m[0]) : 0;
}
function seasonLabel(folder) {
  const n = seasonNumber(folder);
  return n === 0 ? 'Specials' : `Season ${n}`;
}
function seasonOrder(a, b) {
  return (seasonNumber(a) || Infinity) - (seasonNumber(b) || Infinity);
}

async function openSeries(item) {
  setPlayLoading(true);
  const list = $('#episodeList');
  if (list) list.innerHTML = '<div class="loading"><div class="spinner"></div><p>Finding stream…</p></div>';
  try {
    const id = await resolveShowboxId(item);
    if (!id) return streamError('This series isn’t available to stream right now.');

    const shareData = await api(`/api/share-key?id=${id}&type=2`);
    const filesData = await api(`/api/files?shareKey=${shareData.shareKey}`);

    const dirs = (filesData.files || []).filter((f) => f.is_dir === 1);
    const seasonDirs = dirs.filter(isSeasonFolder);
    const seasons = (seasonDirs.length ? seasonDirs : dirs).sort(seasonOrder);

    dnav.shareKey = shareData.shareKey;
    if (!seasons.length) {
      // No season folders: treat the whole share as one flat "Episodes" list.
      dnav.seasons = [{ fid: null, label: 'Episodes' }];
      dnav.flatEpisodes = filesData.videoFiles;
    } else {
      dnav.seasons = seasons.map((s) => ({ fid: s.fid, label: seasonLabel(s) }));
      dnav.flatEpisodes = null;
    }

    dnav.playFid = null; // set to the first episode once a season renders
    setPlayLoading(false);
    renderSeasonTabs();
    selectSeason(0);
  } catch (err) {
    streamError(err.message);
  }
}

// Inline, sticky season tabs (row 1).
function renderSeasonTabs() {
  const host = $('#seasonTabs');
  if (!host) return;
  host.innerHTML = dnav.seasons.map((s, i) =>
    `<div class="season-tab focusable ${i === dnav.seasonIdx ? 'active' : ''}"
          data-action="season" data-row="1" data-col="${i}">${escapeHtml(s.label)}</div>`,
  ).join('');
  host.querySelectorAll('.season-tab').forEach((el) => {
    el.addEventListener('click', () => {
      dnav.row = 1; dnav.col = Number(el.dataset.col);
      selectSeason(dnav.col);
      applyDetailFocus();
    });
  });
}

function paintSeasonTabs() {
  $$('#seasonTabs .season-tab').forEach((el, i) => {
    el.classList.toggle('active', i === dnav.seasonIdx);
  });
}

function selectSeason(idx) {
  dnav.seasonIdx = clamp(idx, 0, dnav.seasons.length - 1);
  paintSeasonTabs();
  const season = dnav.seasons[dnav.seasonIdx];
  if (!season) return;
  if (dnav.flatEpisodes) { renderEpisodes(dnav.flatEpisodes); return; }
  loadEpisodes(dnav.shareKey, season.fid);
}

/* ---- Episodes ---- */
function resRank(resLabel) {
  return parseInt(resLabel, 10) || 0;
}
function episodeSort(a, b) {
  return (a.episode ?? 1e9) - (b.episode ?? 1e9) || resRank(b.resLabel) - resRank(a.resLabel);
}
// Collapse multiple source files of the same episode to the highest resolution.
function bestPerEpisode(episodes) {
  const best = new Map();
  const unknown = [];
  for (const ep of episodes) {
    if (ep.episode == null) { unknown.push(ep); continue; }
    const current = best.get(ep.episode);
    if (!current || resRank(ep.resLabel) > resRank(current.resLabel)) best.set(ep.episode, ep);
  }
  return [...best.values(), ...unknown];
}

async function loadEpisodes(shareKey, parentId) {
  const list = $('#episodeList');
  if (!list) return;
  list.innerHTML = '<div class="loading"><div class="spinner"></div><p>Loading episodes…</p></div>';
  try {
    const data = await api(`/api/files?shareKey=${shareKey}&parentId=${parentId}`);
    renderEpisodes(data.videoFiles);
  } catch (err) {
    list.innerHTML = `<div class="detail-error">Error: ${escapeHtml(err.message)}</div>`;
  }
}

// A friendly episode title derived from the filename (backend files have no title
// field), e.g. "...S01E03.The.Reckoning.1080p..." → "The Reckoning", else "Episode N".
function epTitle(ep, num) {
  const raw = ep.file_name || '';
  const m = raw.match(/[sS]\d{1,2}[eE]\d{1,3}[.\s_-]+(.+?)[.\s_-]+(?:\d{3,4}p|web|bluray|hdtv|x26|h26|hevc|aac|ddp|dts)/i);
  if (m && m[1]) {
    const title = m[1].replace(/[.\s_-]+/g, ' ').trim();
    if (title && !/^\d+$/.test(title)) return title;
  }
  return `Episode ${num}`;
}

function renderEpisodes(episodes) {
  const list = $('#episodeList');
  if (!list) return;
  if (!episodes || !episodes.length) {
    list.innerHTML = '<div class="detail-error">No episodes found.</div>';
    dnav.episodeCount = 0;
    return;
  }
  const sorted = bestPerEpisode(episodes).sort(episodeSort);
  dnav.episodeCount = sorted.length;
  // Series Play targets the first episode of the first-loaded season.
  if (dnav.isTv && !dnav.playFid && sorted[0]) dnav.playFid = sorted[0].fid;
  list.innerHTML = sorted.map((ep, i) => {
    const num = ep.episode != null ? ep.episode : i + 1;
    const name = escapeHtml(ep.file_name || '');
    return `
      <div class="episode focusable" data-action="episode" data-row="2" data-col="${i}"
           data-fid="${ep.fid}" data-name="${name}">
        <div class="ep-num">${num}</div>
        <div class="ep-body">
          <div class="ep-title">${escapeHtml(epTitle(ep, num))}</div>
          <div class="ep-file">${name}</div>
        </div>
      </div>
    `;
  }).join('');

  // Clicking an episode opens the same quality picker as a movie, and moves
  // keyboard focus to that row so arrow keys continue from there.
  list.querySelectorAll('.episode').forEach((row, i) => {
    row.addEventListener('click', () => {
      dnav.row = 2; dnav.col = i; applyDetailFocus();
      openQualityModal(row.dataset.fid, row.dataset.name);
    });
  });

  // Keep focus sensible when the season changes while row 2 is active.
  if (dnav.row === 2) { dnav.col = clamp(dnav.col, 0, sorted.length - 1); applyDetailFocus(); }
}

/* ---- Detail keyboard handling ---- */
function detailRowItems(row) {
  return $$(`#detailView .focusable[data-row="${row}"]`);
}
function detailMaxRow() { return dnav.isTv ? 2 : 0; }

function applyDetailFocus() {
  $$('#detailView .focusable.focused').forEach((el) => el.classList.remove('focused'));
  const items = detailRowItems(dnav.row);
  if (!items.length) {
    // Episode list may be empty/loading — fall back to the row above.
    if (dnav.row > 0) { dnav.row--; return applyDetailFocus(); }
    return;
  }
  dnav.col = clamp(dnav.col, 0, items.length - 1);
  dnav.cols[dnav.row] = dnav.col;
  const el = items[dnav.col];
  el.classList.add('focused');
  if (dnav.row === 2) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  else if (dnav.row === 1) el.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
  else {
    // row 0 (action buttons): scroll the detail view to the top so the full hero shows.
    el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    detailView.scrollTo({ top: 0, behavior: 'smooth' });
  }
}

function activateDetailItem() {
  const el = detailRowItems(dnav.row)[dnav.col];
  if (!el) return;
  const action = el.dataset.action;
  if (action === 'season') { selectSeason(Number(el.dataset.col)); }
  else if (action === 'episode') { el.click(); }
  else { activateAction(action, dnav.item); }   // play / resume / list
}

export function handleDetailKey(e) {
  const key = e.key;
  if (key === 'Escape' || key === 'Backspace') { e.preventDefault(); showPage(getLastBrowsePage()); return; }
  if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter'].includes(key)) return;
  e.preventDefault();

  if (key === 'Enter') { activateDetailItem(); return; }

  const inList = dnav.row === 2;       // episode list is vertical
  const rowLen = detailRowItems(dnav.row).length;

  switch (key) {
    case 'ArrowLeft':  if (!inList) dnav.col = clamp(dnav.col - 1, 0, rowLen - 1); break;
    case 'ArrowRight': if (!inList) dnav.col = clamp(dnav.col + 1, 0, rowLen - 1); break;
    case 'ArrowUp':
      if (inList && dnav.col > 0) { dnav.col--; }
      else if (dnav.row > 0) { dnav.row--; dnav.col = dnav.cols[dnav.row] ?? 0; }
      break;
    case 'ArrowDown':
      if (inList) { dnav.col = clamp(dnav.col + 1, 0, rowLen - 1); }
      else if (dnav.row < detailMaxRow()) { dnav.row++; dnav.col = dnav.row === 2 ? 0 : (dnav.cols[dnav.row] ?? 0); }
      break;
  }
  applyDetailFocus();
}
