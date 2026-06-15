#!/usr/bin/env node
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import CryptoJS from 'crypto-js';
import { customAlphabet } from 'nanoid';
import fetch from 'node-fetch';
import { SHOWBOX, FEBBOX, PORT } from './config.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const nanoid = customAlphabet('1234567890abcdef', 32);

function encrypt(data) {
  return CryptoJS.TripleDES.encrypt(
    data,
    CryptoJS.enc.Utf8.parse(SHOWBOX.key),
    { iv: CryptoJS.enc.Utf8.parse(SHOWBOX.iv) }
  ).toString();
}

function verify(encryptedData) {
  return CryptoJS.MD5(CryptoJS.MD5(SHOWBOX.appKey).toString() + SHOWBOX.key + encryptedData).toString();
}

function expiry() {
  return Math.floor(Date.now() / 1000 + 60 * 60 * 12).toString();
}

async function showboxRequest(module, params = {}) {
  const requestData = { ...SHOWBOX.defaults, expired_date: expiry(), module, ...params };
  const encryptedData = encrypt(JSON.stringify(requestData));
  const body = JSON.stringify({
    app_key: CryptoJS.MD5(SHOWBOX.appKey).toString(),
    verify: verify(encryptedData),
    encrypt_data: encryptedData,
  });
  const formData = new URLSearchParams({
    data: Buffer.from(body).toString('base64'),
    appid: SHOWBOX.defaults.appid,
    platform: SHOWBOX.defaults.platform,
    version: SHOWBOX.defaults.version,
    medium: SHOWBOX.defaults.medium,
    token: nanoid(32),
  });
  const response = await fetch(SHOWBOX.baseUrl, {
    method: 'POST',
    headers: {
      Platform: SHOWBOX.defaults.platform,
      'Content-Type': 'application/x-www-form-urlencoded',
      'User-Agent': 'okhttp/3.2.0',
    },
    body: formData.toString(),
  });
  return response.json();
}

async function fetchWithTimeout(url, options = {}, timeout = 15_000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function retryFetch(url, options = {}, retries = 3) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      return await fetchWithTimeout(url, options);
    } catch (err) {
      if (attempt === retries) throw err;
      const delay = 1_000 * attempt;
      await new Promise((r) => setTimeout(r, delay));
    }
  }
}

async function getShareKey(id, type = 1) {
  const shareLinkUrl = `https://www.showbox.media/index/share_link?id=${id}&type=${type}`;
  const proxyUrl = `${FEBBOX.proxyBase}${encodeURIComponent(shareLinkUrl)}`;
  const response = await retryFetch(proxyUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    },
  });
  const text = await response.text();
  if (!text.trim().startsWith('{')) return null;
  const data = JSON.parse(text);
  const link = data?.data?.link || '';
  return link ? link.split('/').pop() : null;
}

async function febboxJson(url) {
  const res = await retryFetch(url, {
    headers: {
      cookie: `ui=${FEBBOX.cookie}`,
      referer: FEBBOX.baseUrl,
      'x-requested-with': 'XMLHttpRequest',
      'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    },
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json();
}

import { load as cheerioLoad } from 'cheerio';

async function getLinks(fid) {
  const url = `${FEBBOX.baseUrl}/console/video_quality_list?fid=${fid}`;
  const data = await febboxJson(url);
  const html = data.html || '';
  const $ = cheerioLoad(html);
  const links = [];
  $('.file_quality').each((_, el) => {
    const $el = $(el);
    const url = $el.attr('data-url');
    const ext = (url?.match(/\.(mp4|mkv|avi|m3u8)/i)?.[1] || 'm3u8').toLowerCase();
    links.push({
      url,
      quality: $el.attr('data-quality'),
      speed: $el.find('.speed span').text().trim(),
      size: $el.find('.size').text().trim(),
      ext,
    });
  });
  return links;
}

function proxyUrl(url) {
  return `${FEBBOX.proxyBase}${encodeURIComponent(url)}`;
}

function parseEpisode(fileName = '') {
  const m = fileName.match(/s(\d{1,2})[\s._-]*e(\d{1,3})/i);
  if (!m) return { season: null, episode: null };
  return { season: Number(m[1]), episode: Number(m[2]) };
}

function parseResolution(fileName = '') {
  const m = fileName.match(/(\d{3,4})p/i);
  return m ? `${m[1]}p` : null;
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function serveStatic(req, res) {
  let filePath = path.join(__dirname, 'public', req.url === '/' ? 'index.html' : req.url);
  const ext = path.extname(filePath);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  });
}

function mapSearchItem(item, mediaType) {
  return {
    id: item.id,
    media_type: mediaType,
    title: item.title,
    year: item.year,
    poster: item.poster || null,
    poster_min: item.poster_min || null,
    poster_org: item.poster_org || null,
    imdb_rating: item.imdb_rating || null,
    description: item.description || null,
    runtime: item.runtime || null,
    cats: item.cats || null,
    actors: item.actors || null,
  };
}

async function searchType(query, type, mediaType) {
  const data = await showboxRequest('Search5', { type, keyword: query, page: '1', pagelimit: '20' });
  return (data.data || []).map((item) => mapSearchItem(item, mediaType));
}

async function handleSearch(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const query = url.searchParams.get('q') || '';

  if (!query.trim()) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Missing search query' }));
    return;
  }

  try {
    const [movies, series] = await Promise.all([
      searchType(query, 'movie', 'movie'),
      searchType(query, 'tv', 'tv'),
    ]);
    const results = [...movies, ...series];
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ results }));
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
  }
}

async function handleShareKey(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const id = url.searchParams.get('id');
  const type = url.searchParams.get('type') || '1';

  if (!id) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Missing id' }));
    return;
  }

  try {
    const shareKey = await getShareKey(id, Number(type));
    if (!shareKey) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'No share key found' }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ shareKey }));
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
  }
}

async function handleFiles(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const shareKey = url.searchParams.get('shareKey');
  const parentId = url.searchParams.get('parentId') || '0';

  if (!shareKey) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Missing shareKey' }));
    return;
  }

  try {
    const apiUrl = `${FEBBOX.baseUrl}/file/file_share_list?share_key=${shareKey}&pwd=&parent_id=${parentId}&is_html=0`;
    const data = await febboxJson(apiUrl);
    const files = data.data?.file_list || [];
    const videoFiles = files
      .filter((f) => f.is_dir === 0 && /\.(mp4|mkv|avi|m3u8)$/i.test(f.file_name))
      .map((f) => ({ ...f, ...parseEpisode(f.file_name), resLabel: parseResolution(f.file_name) }));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ files, videoFiles }));
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
  }
}

async function handleLinks(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const fid = url.searchParams.get('fid');

  if (!fid) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Missing fid' }));
    return;
  }

  try {
    const links = await getLinks(Number(fid));
    const withProxy = links.map((l) => ({ ...l, proxiedUrl: proxyUrl(l.url) }));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ links: withProxy }));
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
  }
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (url.pathname === '/api/search' && req.method === 'GET') {
    return handleSearch(req, res);
  }
  if (url.pathname === '/api/share-key' && req.method === 'GET') {
    return handleShareKey(req, res);
  }
  if (url.pathname === '/api/files' && req.method === 'GET') {
    return handleFiles(req, res);
  }
  if (url.pathname === '/api/links' && req.method === 'GET') {
    return handleLinks(req, res);
  }

  return serveStatic(req, res);
});

server.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}`);
});
