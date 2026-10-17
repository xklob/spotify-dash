import type { Dataset, HistoryEvent } from './types';

export const FEATURE_KEYS = ['energy', 'valence', 'danceability', 'acousticness', 'instrumentalness', 'speechiness', 'liveness', 'tempo', 'loudness'] as const;
export type Feature = typeof FEATURE_KEYS[number];
export type AudioFeatures = Record<Feature, number | null>;
export interface TrackMetadata {
  uri: string; status: 'found' | 'missing'; fetchedAt: string; providerId: string;
  title: string; artists: string[]; durationMs: number | null; isrc: string;
  features: AudioFeatures; key: number | null; mode: number | null;
}
export interface Metadata {
  format: 'spotify-dash-metadata'; version: 1; provider: 'reccobeats' | 'demo';
  modelVersion: string | null; generatedAt: string; tracks: TrackMetadata[];
}
export const emptyFeatures = (): AudioFeatures => Object.fromEntries(FEATURE_KEYS.map(k => [k, null])) as AudioFeatures;
export const emptyMetadata = (provider: Metadata['provider'] = 'reccobeats'): Metadata => ({ format: 'spotify-dash-metadata', version: 1, provider, modelVersion: null, generatedAt: new Date().toISOString(), tracks: [] });
export const spotifyId = (uri: string) => /^spotify:track:([A-Za-z0-9]{22})$/.exec(uri)?.[1] ?? null;
export const moodKey = (e: HistoryEvent, demo = false) => demo ? `demo:${JSON.stringify([e.artist, e.title, e.album])}` : e.uri || `unmatched:${JSON.stringify([e.kind, e.artist, e.title, e.album])}`;
export const paired = (t: TrackMetadata | undefined) => t?.status === 'found' && t.features.energy !== null && t.features.valence !== null;
const object = (v: unknown): Record<string, unknown> => v !== null && typeof v === 'object' && !Array.isArray(v) ? v as Record<string, unknown> : {};
const text = (v: unknown, max = 500) => typeof v === 'string' ? v.slice(0, max) : '';
export function featureValue(key: Feature, v: unknown): number | null {
  const min = key === 'loudness' ? -100 : 0, max = key === 'tempo' ? 400 : key === 'loudness' ? 10 : 1;
  return typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max ? v : null;
}
export function validateMetadata(value: unknown): Metadata {
  const m = object(value);
  if (m.format !== 'spotify-dash-metadata' || m.version !== 1 || !['reccobeats', 'demo'].includes(String(m.provider)) || !Array.isArray(m.tracks) || m.tracks.length > 100000) throw new Error('Choose a Spotify Dash metadata JSON file (format version 1).');
  const date = (v: unknown) => { if (typeof v !== 'string' || !Number.isFinite(Date.parse(v))) throw new Error('Metadata has an invalid retrieval date.'); return new Date(v).toISOString(); };
  const seen = new Set<string>();
  const tracks = m.tracks.map(raw => {
    const r = object(raw), uri = text(r.uri, 1600), f = object(r.features);
    if (!(m.provider === 'demo' ? uri.startsWith('demo:') : spotifyId(uri)) || seen.has(uri) || !['found', 'missing'].includes(String(r.status))) throw new Error('Metadata has an invalid or duplicate track identifier.');
    seen.add(uri);
    const features = emptyFeatures();
    for (const k of FEATURE_KEYS) {
      features[k] = featureValue(k, f[k]);
      if (f[k] != null && features[k] === null) throw new Error(`Metadata has an invalid ${k} score.`);
    }
    if (r.status === 'missing' && FEATURE_KEYS.some(k => features[k] !== null)) throw new Error('Missing tracks cannot contain scores.');
    const integer = (v: unknown, min: number, max: number): number | null => {
      if (v == null) return null;
      if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < min || v > max) throw new Error('Metadata has an invalid numeric field.');
      return v;
    };
    return { uri, status: r.status as TrackMetadata['status'], fetchedAt: date(r.fetchedAt), providerId: text(r.providerId), title: text(r.title),
      artists: Array.isArray(r.artists) ? r.artists.slice(0, 100).map(a => text(a)) : [], durationMs: integer(r.durationMs, 0, 86400000), isrc: text(r.isrc, 32),
      features, key: integer(r.key, -1, 11), mode: integer(r.mode, 0, 1) };
  });
  return { format: 'spotify-dash-metadata', version: 1, provider: m.provider as Metadata['provider'], modelVersion: typeof m.modelVersion === 'string' ? text(m.modelVersion) : null, generatedAt: date(m.generatedAt), tracks };
}
export function mergeMetadata(previous: Metadata, next: Metadata): Metadata {
  if (previous.provider !== next.provider || previous.modelVersion !== next.modelVersion) throw new Error('Metadata from different providers or model versions cannot be combined.');
  const tracks = new Map(previous.tracks.map(t => [t.uri, t]));
  for (const t of next.tracks) {
    const old = tracks.get(t.uri);
    if (!old || t.fetchedAt >= old.fetchedAt) tracks.set(t.uri, t);
  }
  return { ...next, tracks: [...tracks.values()] };
}
/** Stable synthetic scores. Never used for imported history or sent to any API. */
export function demoMetadata(data: Dataset): Metadata {
  const m = emptyMetadata('demo'); m.generatedAt = '2026-01-01T00:00:00.000Z'; m.modelVersion = 'fictional-v1';
  const seen = new Set<string>();
  for (const e of data.events) {
    const uri = moodKey(e, true); if (e.kind !== 'music' || seen.has(uri)) continue; seen.add(uri);
    let seed = 7; for (const c of uri) seed = (Math.imul(seed, 31) + c.charCodeAt(0)) >>> 0;
    const rand = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
    const features = emptyFeatures(); for (const k of FEATURE_KEYS) features[k] = k === 'tempo' ? 70 + rand() * 100 : k === 'loudness' ? -15 + rand() * 10 : rand();
    // Deliberately incomplete so the demo also illustrates missing coverage.
    if (seen.size % 7 === 0) continue;
    m.tracks.push({ uri, status: 'found', fetchedAt: m.generatedAt, providerId: '', title: e.title, artists: [e.artist], durationMs: 240000, isrc: '', features, key: null, mode: null });
  }
  return m;
}
