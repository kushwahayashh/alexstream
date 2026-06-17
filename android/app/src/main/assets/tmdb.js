/* ───────────────────────── TMDB client ─────────────────────────
   Browse/discovery data comes from TMDB, proxied through our own
   backend (/api/tmdb/*) so the app talks to a single origin and the
   api_key stays server-side. Responses are cached in localStorage.
   Streaming is handled separately (ShowBox/FebBox); a TMDB title is
   bridged to a ShowBox id at click time via /api/resolve.
   ---------------------------------------------------------------- */

// Base for all backend calls. Defaults to same-origin (plain website); the
// Android app overrides it by setting window.__BACKEND_BASE before load.
const API_BASE = (typeof window !== 'undefined' && window.__BACKEND_BASE)
  ? String(window.__BACKEND_BASE).replace(/\/$/, '')
  : '';

// Prefix a backend-relative path (e.g. "/api/subtitle?url=…") with the
// configured backend base. Absolute URLs pass through unchanged.
export function backendUrl(path) {
  if (/^https?:\/\//.test(path)) return path;
  return `${API_BASE}${path}`;
}

const TMDB = {
  img: 'https://image.tmdb.org/t/p',
};

const TMDB_CACHE_PREFIX = 'tmdb_cache_v1:';
const TTL = {
  trending: 60 * 60 * 1000,          // 1h
  popular: 6 * 60 * 60 * 1000,       // 6h
  genres: 24 * 60 * 60 * 1000,       // 24h
  default: 3 * 60 * 60 * 1000,       // 3h
};

function cacheTtl(path) {
  if (path.startsWith('/trending/')) return TTL.trending;
  if (path.includes('/popular') || path.includes('/top_rated')) return TTL.popular;
  if (path.startsWith('/genre/')) return TTL.genres;
  return TTL.default;
}

function readCache(path) {
  const key = TMDB_CACHE_PREFIX + path;
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const entry = JSON.parse(raw);
    if (entry && entry.expiresAt > Date.now()) return entry.data;
    localStorage.removeItem(key);
  } catch {
    /* storage disabled / quota — ignore */
  }
  return null;
}

function writeCache(path, data, ttlMs) {
  try {
    localStorage.setItem(
      TMDB_CACHE_PREFIX + path,
      JSON.stringify({ expiresAt: Date.now() + ttlMs, data }),
    );
  } catch {
    /* ignore */
  }
}

// Fetch a TMDB path (must start with "/"). Routed through our backend proxy,
// which injects the api_key server-side.
export async function tmdbFetch(path) {
  const cached = readCache(path);
  if (cached) return cached;

  const res = await fetch(`${API_BASE}/api/tmdb${path}`);
  if (!res.ok) throw new Error(`TMDB HTTP ${res.status}`);
  const data = await res.json();
  writeCache(path, data, cacheTtl(path));
  return data;
}

export function posterURL(path, size = 'w342') {
  return path ? `${TMDB.img}/${size}${path}` : '';
}

export function backdropURL(path, size = 'w1280') {
  return path ? `${TMDB.img}/${size}${path}` : '';
}

// Normalize a raw TMDB result (movie or tv) into the shape the UI uses.
export function normItem(raw, forcedType) {
  const mediaType = forcedType || raw.media_type || (raw.first_air_date ? 'tv' : 'movie');
  const date = raw.release_date || raw.first_air_date || '';
  return {
    tmdbId: raw.id,
    mediaType,
    title: raw.title || raw.name || '',
    year: date ? date.slice(0, 4) : '',
    poster: raw.poster_path || null,
    backdrop: raw.backdrop_path || null,
    rating: raw.vote_average || 0,
    voteCount: raw.vote_count || 0,
    overview: raw.overview || '',
    genreIds: raw.genre_ids || [],
  };
}

// Genre id -> name maps (movie + tv), loaded once and cached.
let genreMapPromise = null;
export function loadGenreMap() {
  if (!genreMapPromise) {
    genreMapPromise = Promise.all([
      tmdbFetch('/genre/movie/list'),
      tmdbFetch('/genre/tv/list'),
    ]).then(([m, t]) => {
      const map = {};
      [...(m.genres || []), ...(t.genres || [])].forEach((g) => { map[g.id] = g.name; });
      return map;
    }).catch(() => ({}));
  }
  return genreMapPromise;
}

/* ───────────────────────── Backend API helper ─────────────────────────
   Thin wrapper over fetch for our own /api/* endpoints (ShowBox/FebBox
   streaming bridge). Distinct from tmdbFetch above: no caching, and it
   surfaces the backend's JSON { error } message on failure.              */
export async function api(url) {
  const target = /^https?:\/\//.test(url) ? url : `${API_BASE}${url}`;
  const res = await fetch(target);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Request failed (${res.status})`);
  }
  return res.json();
}
