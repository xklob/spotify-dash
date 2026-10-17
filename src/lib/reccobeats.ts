import { emptyFeatures, emptyMetadata, featureValue, FEATURE_KEYS, spotifyId, type Metadata, type TrackMetadata } from './metadata';

const BASE = 'https://api.reccobeats.com/v1/';
export const BATCH_SIZE = 40;
let nextRequestAt = 0;
export class CatalogError extends Error { constructor(message: string, public retryAfter = 0) { super(message); } }
export async function lookupMetadata(uris: string[], fetcher: typeof fetch = fetch): Promise<Metadata> {
  if (!uris.length || uris.length > BATCH_SIZE || uris.some(u => !spotifyId(u)) || new Set(uris).size !== uris.length) throw new Error('Catalog requests require 1–40 distinct Spotify track IDs.');
  const ids = uris.map(spotifyId).join(',');
  async function get(path: string): Promise<Record<string, unknown>[]> {
    await new Promise(resolve => setTimeout(resolve, Math.max(0, nextRequestAt - Date.now())));
    nextRequestAt = Date.now() + 1000;
    const response = await fetcher(`${BASE}${path}?ids=${ids}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(25000), credentials: 'omit', referrerPolicy: 'no-referrer' });
    if (!response.ok) {
      const h = response.headers.get('retry-after');
      const seconds = h ? (/^\d+$/.test(h) ? Number(h) : Math.max(0, (Date.parse(h) - Date.now()) / 1000)) : 0;
      throw new CatalogError(`Catalog lookup paused (HTTP ${response.status}). Completed batches are saved. Try again later.`, Number.isFinite(seconds) ? seconds : 0);
    }
    const body = await response.json();
    if (!Array.isArray(body.content)) throw new Error('The catalog returned an unexpected response. Completed batches are saved.');
    return body.content;
  }
  const catalog = await get('track');
  // Request starts are spaced by at least a second, including across batches.
  const features = await get('audio-features');
  const requested = new Set(uris);
  function index(rows: Record<string, unknown>[]) {
    const map = new Map<string, Record<string, unknown>>();
    for (const r of rows) {
      const id = typeof r.href === 'string' ? /^https:\/\/open\.spotify\.com\/track\/([A-Za-z0-9]{22})(?:\?.*)?$/.exec(r.href)?.[1] : null;
      const uri = id ? `spotify:track:${id}` : '';
      if (requested.has(uri)) {
        if (map.has(uri)) throw new Error('The catalog returned duplicate identifiers. This batch was not saved.');
        map.set(uri, r);
      }
    }
    return map;
  }
  const byTrack = index(catalog), byFeature = index(features), m = emptyMetadata();
  m.tracks = uris.map(uri => {
    const t = byTrack.get(uri), f = byFeature.get(uri), values = emptyFeatures();
    for (const key of FEATURE_KEYS) values[key] = featureValue(key, f?.[key]);
    const boundedInt = (v: unknown, min: number, max: number) => typeof v === 'number' && Number.isInteger(v) && v >= min && v <= max ? v : null;
    return { uri, status: t || f ? 'found' : 'missing', fetchedAt: m.generatedAt,
      providerId: String(t?.id ?? f?.id ?? ''), title: typeof t?.trackTitle === 'string' ? t.trackTitle : '',
      artists: Array.isArray(t?.artists) ? t.artists.map(a => typeof a?.name === 'string' ? a.name : '').filter(Boolean) : [],
      durationMs: boundedInt(t?.durationMs, 0, 86400000), isrc: String(t?.isrc ?? f?.isrc ?? ''), features: values,
      key: boundedInt(f?.key, -1, 11), mode: boundedInt(f?.mode, 0, 1) } satisfies TrackMetadata;
  });
  return m;
}
