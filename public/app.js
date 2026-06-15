const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const resultsGrid = document.getElementById('results');
const emptyState = document.getElementById('emptyState');
const browseView = document.getElementById('browseView');
const detailView = document.getElementById('detailView');

async function api(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Request failed (${res.status})`);
  }
  return res.json();
}

/* ---- Search ---- */

async function searchMovies(query) {
  const data = await api(`/api/search?q=${encodeURIComponent(query)}`);
  return data.results;
}

function renderMovieCard(movie) {
  const card = document.createElement('div');
  card.className = 'movie-card';

  const badge = document.createElement('span');
  badge.className = `type-badge ${movie.media_type === 'tv' ? 'is-tv' : 'is-movie'}`;
  badge.textContent = movie.media_type === 'tv' ? 'Series' : 'Movie';
  card.appendChild(badge);

  if (movie.poster) {
    const img = document.createElement('img');
    img.className = 'poster';
    img.src = movie.poster;
    img.alt = movie.title;
    img.loading = 'lazy';
    card.appendChild(img);
  } else {
    const placeholder = document.createElement('div');
    placeholder.className = 'poster-placeholder';
    placeholder.textContent = 'No Poster';
    card.appendChild(placeholder);
  }

  const info = document.createElement('div');
  info.className = 'info';

  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = movie.title;
  info.appendChild(title);

  const meta = document.createElement('div');
  meta.className = 'meta';

  if (movie.year) {
    const year = document.createElement('span');
    year.textContent = movie.year;
    meta.appendChild(year);
  }

  if (movie.imdb_rating) {
    const rating = document.createElement('span');
    rating.className = 'rating';
    rating.textContent = `★ ${movie.imdb_rating}`;
    meta.appendChild(rating);
  }

  info.appendChild(meta);
  card.appendChild(info);
  card.addEventListener('click', () => openTitle(movie));
  return card;
}

function showLoading(msg = 'Searching...') {
  resultsGrid.innerHTML = '';
  emptyState.classList.add('hidden');
  const loading = document.createElement('div');
  loading.className = 'loading';
  loading.id = 'loadingState';
  loading.innerHTML = `<div class="spinner"></div><p>${msg}</p>`;
  resultsGrid.appendChild(loading);
}

function showResults(movies) {
  resultsGrid.innerHTML = '';
  if (movies.length === 0) {
    emptyState.classList.remove('hidden');
    emptyState.innerHTML = '<p>No movies found. Try a different search.</p>';
    return;
  }
  emptyState.classList.add('hidden');
  movies.forEach((movie) => {
    resultsGrid.appendChild(renderMovieCard(movie));
  });
}

async function handleSearch() {
  const query = searchInput.value.trim();
  if (!query) return;

  searchBtn.disabled = true;
  showLoading();

  try {
    const movies = await searchMovies(query);
    showDetail(false);
    showResults(movies);
  } catch (err) {
    resultsGrid.innerHTML = '';
    emptyState.classList.remove('hidden');
    emptyState.innerHTML = `<p>Error: ${err.message}</p>`;
  } finally {
    searchBtn.disabled = false;
  }
}

searchBtn.addEventListener('click', handleSearch);
searchInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') handleSearch();
});

/* ---- Detail / Play ---- */

function showDetail(show) {
  stopPlayer();
  browseView.classList.toggle('hidden', show);
  detailView.classList.toggle('hidden', !show);
}

function showDetailLoading() {
  detailView.innerHTML = `
    <button class="detail-back" id="backBtn">← Back to results</button>
    <div class="loading"><div class="spinner"></div><p>Loading stream details...</p></div>
  `;
  document.getElementById('backBtn').addEventListener('click', () => showDetail(false));
}

let activePlayer = null;

function stopPlayer() {
  if (activePlayer) {
    activePlayer.destroy?.();
    activePlayer = null;
  }
  const modal = document.getElementById('playerModal');
  if (modal) modal.remove();
}

function heroHtml(movie) {
  const cats = movie.cats ? movie.cats.split(',').map(c => c.trim()).filter(Boolean) : [];

  let posterHtml;
  if (movie.poster_org) {
    posterHtml = `<img class="detail-poster" src="${movie.poster_org}" alt="${movie.title}">`;
  } else if (movie.poster) {
    posterHtml = `<img class="detail-poster" src="${movie.poster}" alt="${movie.title}">`;
  } else {
    posterHtml = `<div class="detail-poster-placeholder">No Poster</div>`;
  }

  return `
    <div class="detail-hero">
      ${posterHtml}
      <div class="detail-info">
        <div class="detail-title">${movie.title}</div>
        <div class="detail-meta">
          ${movie.year ? `<span>${movie.year}</span>` : ''}
          ${movie.runtime ? `<span>${movie.runtime} min</span>` : ''}
          ${movie.imdb_rating ? `<span class="rating">★ ${movie.imdb_rating}</span>` : ''}
        </div>
        ${cats.length ? `<div class="detail-cats">${cats.map(c => `<span>${c}</span>`).join('')}</div>` : ''}
        ${movie.description ? `<div class="detail-desc">${movie.description}</div>` : ''}
        ${movie.actors ? `<div class="detail-actors">${movie.actors}</div>` : ''}
      </div>
    </div>
  `;
}

function qualityGridHtml(links) {
  if (!links.length) {
    return '<p style="color:#888">No stream links available.</p>';
  }
  return '<div class="quality-grid">' +
    links.map(l => `
      <button class="quality-btn" data-url="${l.proxiedUrl}" data-ext="${l.ext}">
        <div class="qlabel">${l.quality} <span class="qext">${l.ext}</span></div>
        <div class="qmeta">${l.size || ''} ${l.speed || ''}</div>
      </button>
    `).join('') +
  '</div>';
}

function bindQualityButtons(root) {
  root.querySelectorAll('.quality-btn').forEach(btn => {
    btn.addEventListener('click', () => playStream(btn.dataset.url, btn.dataset.ext));
  });
}

function renderDetail(movie, links) {
  detailView.innerHTML = `
    <button class="detail-back" id="backBtn">← Back to results</button>
    ${heroHtml(movie)}
    <div class="detail-section-title">Available Quality</div>
    ${qualityGridHtml(links)}
  `;
  document.getElementById('backBtn').addEventListener('click', () => showDetail(false));
  bindQualityButtons(detailView);
}

async function loadHlsJs() {
  if (window.Hls) return;
  await new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/hls.js@latest';
    s.onload = resolve;
    s.onerror = reject;
    document.head.appendChild(s);
  });
}

async function playStream(url, ext) {
  stopPlayer();

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.id = 'playerModal';
  overlay.innerHTML = `
    <div class="modal-content">
      <video id="videoPlayer" controls autoplay playsinline></video>
    </div>
  `;
  document.body.appendChild(overlay);

  const close = () => { stopPlayer(); overlay.remove(); };
  overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });

  const video = document.getElementById('videoPlayer');

  if (ext === 'm3u8') {
    try {
      await loadHlsJs();
      if (Hls.isSupported()) {
        const hls = new Hls();
        hls.loadSource(url);
        hls.attachMedia(video);
        activePlayer = hls;
        hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = url;
      }
    } catch {
      video.src = url;
    }
  } else {
    video.src = url;
  }
}

function showDetailError(message) {
  detailView.innerHTML = `
    <button class="detail-back" id="backBtn">← Back to results</button>
    <div class="empty-state"><p>Error: ${message}</p></div>
  `;
  document.getElementById('backBtn').addEventListener('click', () => showDetail(false));
}

function openTitle(movie) {
  return movie.media_type === 'tv' ? openSeries(movie) : openMovie(movie);
}

async function openMovie(movie) {
  showDetail(true);
  showDetailLoading();

  try {
    const shareData = await api(`/api/share-key?id=${movie.id}&type=1`);
    const filesData = await api(`/api/files?shareKey=${shareData.shareKey}`);

    const vids = filesData.videoFiles;
    if (!vids.length) throw new Error('No video files found');

    const target = vids[0];
    const linksData = await api(`/api/links?fid=${target.fid}`);
    renderDetail(movie, linksData.links);
  } catch (err) {
    showDetailError(err.message);
  }
}

/* ---- Series ---- */

// A real season folder is named like "season 1", "Season 01", or "S1" — not
// junk folders ("Documents") or wrapper folders that some shares include.
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

// Sort ascending by number, but push Specials (season 0) to the end.
function seasonOrder(a, b) {
  const na = seasonNumber(a) || Infinity;
  const nb = seasonNumber(b) || Infinity;
  return na - nb;
}

async function openSeries(movie) {
  showDetail(true);
  showDetailLoading();

  try {
    const shareData = await api(`/api/share-key?id=${movie.id}&type=2`);
    const filesData = await api(`/api/files?shareKey=${shareData.shareKey}`);

    const dirs = (filesData.files || []).filter((f) => f.is_dir === 1);
    // Prefer folders that actually look like seasons; fall back to all folders
    // only if none match (so oddly-named shares still work).
    const seasonDirs = dirs.filter(isSeasonFolder);
    const seasons = (seasonDirs.length ? seasonDirs : dirs).sort(seasonOrder);

    if (!seasons.length) {
      // Some series shares hold episodes at the top level (no season folders).
      renderSeries(movie, shareData.shareKey, [], filesData.videoFiles);
      return;
    }
    renderSeries(movie, shareData.shareKey, seasons, null);
  } catch (err) {
    showDetailError(err.message);
  }
}

function renderSeries(movie, shareKey, seasons, flatEpisodes) {
  const seasonTabs = seasons.length
    ? '<div class="season-tabs">' +
      seasons.map((s, i) => `
        <button class="season-tab${i === 0 ? ' active' : ''}" data-fid="${s.fid}">
          ${seasonLabel(s)}
        </button>
      `).join('') +
      '</div>'
    : '';

  detailView.innerHTML = `
    <button class="detail-back" id="backBtn">← Back to results</button>
    ${heroHtml(movie)}
    <div class="detail-section-title">Episodes</div>
    ${seasonTabs}
    <div id="episodeList" class="episode-list"></div>
  `;
  document.getElementById('backBtn').addEventListener('click', () => showDetail(false));

  const tabs = detailView.querySelectorAll('.season-tab');
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      loadEpisodes(shareKey, tab.dataset.fid);
    });
  });

  if (flatEpisodes) {
    renderEpisodes(flatEpisodes);
  } else if (seasons.length) {
    loadEpisodes(shareKey, seasons[0].fid);
  }
}

function resRank(resLabel) {
  return parseInt(resLabel, 10) || 0; // "2160p" -> 2160, null -> 0
}

function episodeSort(a, b) {
  return (a.episode ?? 1e9) - (b.episode ?? 1e9) ||
    resRank(b.resLabel) - resRank(a.resLabel);
}

// Collapse multiple source files of the same episode down to the highest
// resolution. Files we couldn't parse an episode number for are kept as-is.
function bestPerEpisode(episodes) {
  const best = new Map();
  const unknown = [];
  for (const ep of episodes) {
    if (ep.episode == null) {
      unknown.push(ep);
      continue;
    }
    const current = best.get(ep.episode);
    if (!current || resRank(ep.resLabel) > resRank(current.resLabel)) {
      best.set(ep.episode, ep);
    }
  }
  return [...best.values(), ...unknown];
}

async function loadEpisodes(shareKey, parentId) {
  const list = document.getElementById('episodeList');
  list.innerHTML = '<div class="loading"><div class="spinner"></div><p>Loading episodes...</p></div>';
  try {
    const data = await api(`/api/files?shareKey=${shareKey}&parentId=${parentId}`);
    renderEpisodes(data.videoFiles);
  } catch (err) {
    list.innerHTML = `<p style="color:#888">Error: ${err.message}</p>`;
  }
}

function renderEpisodes(episodes) {
  const list = document.getElementById('episodeList');
  if (!episodes || !episodes.length) {
    list.innerHTML = '<p style="color:#888">No episodes found.</p>';
    return;
  }

  const sorted = bestPerEpisode(episodes).sort(episodeSort);
  list.innerHTML = sorted.map((ep) => {
    const label = ep.episode != null ? `E${String(ep.episode).padStart(2, '0')}` : '';
    const res = ep.resLabel ? `<span class="ep-res">${ep.resLabel}</span>` : '';
    return `
      <div class="episode-row" data-fid="${ep.fid}">
        <div class="ep-main">
          ${label ? `<span class="ep-num">${label}</span>` : ''}
          <span class="ep-name">${ep.file_name}</span>
          ${res}
        </div>
        <div class="ep-qualities"></div>
      </div>
    `;
  }).join('');

  list.querySelectorAll('.episode-row').forEach(row => {
    row.querySelector('.ep-main').addEventListener('click', () => toggleEpisode(row));
  });
}

async function toggleEpisode(row) {
  const box = row.querySelector('.ep-qualities');
  if (row.classList.contains('open')) {
    row.classList.remove('open');
    box.innerHTML = '';
    return;
  }
  row.classList.add('open');
  box.innerHTML = '<div class="loading"><div class="spinner"></div><p>Loading qualities...</p></div>';
  try {
    const data = await api(`/api/links?fid=${row.dataset.fid}`);
    box.innerHTML = qualityGridHtml(data.links);
    bindQualityButtons(box);
  } catch (err) {
    box.innerHTML = `<p style="color:#888">Error: ${err.message}</p>`;
  }
}
