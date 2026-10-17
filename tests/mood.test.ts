import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Analytics } from '../src/lib/analytics';
import { ImportBuilder } from '../src/lib/normalize';
import { DEFAULT_FILTERS } from '../src/lib/types';
import { DEFAULT_MOOD } from '../src/lib/mood';
import { emptyMetadata, mergeMetadata, validateMetadata } from '../src/lib/metadata';
import { lookupMetadata, CatalogError } from '../src/lib/reccobeats';
const read = (name: string) => JSON.parse(readFileSync(new URL(`./fixtures/${name}`, import.meta.url), 'utf8'));
const metadata = validateMetadata(read('mood-metadata.json'));
function engine() { const b = new ImportBuilder(); b.add(read('mood-history.json'), 'mood-history.json'); return new Analytics(b.finish()); }
const january = { ...DEFAULT_FILTERS, timezone: 'UTC', start: '2024-01-01', end: '2024-01-31' };
test('mood means, coverage and missing identifiers reconcile with hand calculations', () => {
  const a = engine(), q = a.mood(january, metadata, DEFAULT_MOOD);
  assert.equal(q.summary.plays, 8); assert.equal(q.summary.tracks, 7); assert.equal(q.summary.ms, 1700000);
  assert.equal(q.summary.coveredMs, 500000); assert.equal(q.summary.coveredTracks, 3); assert.equal(q.summary.coverage, 5 / 17);
  assert.equal(q.summary.values.energy, .2); assert.equal(q.summary.values.valence, .32);
  assert.equal(a.mood(january, metadata, { ...DEFAULT_MOOD, weight: 'plays' }).summary.values.energy, .3);
  assert(Math.abs(a.mood(january, metadata, { ...DEFAULT_MOOD, weight: 'tracks' }).summary.values.energy! - .4) < 1e-12);
  assert.equal(q.distribution.reduce((n, c) => n + c.ms, 0), q.summary.coveredMs);
  assert.equal(q.heatmap.reduce((n, c) => n + c.ms, 0), q.summary.ms);
});
test('calendar gaps, distinct smoothing and timezone boundaries retain their meanings', () => {
  const a = engine(), q = a.mood({ ...january, end: '2024-03-31' }, metadata, { ...DEFAULT_MOOD, weight: 'tracks' });
  assert.equal(q.periods.length, 3); assert.equal(q.periods[1].values.energy, null);
  assert.equal(q.rolling[2].coveredTracks, 3); assert(Math.abs(q.rolling[2].values.energy! - .4) < 1e-12);
  const dst = a.mood({ ...DEFAULT_FILTERS, start: '2024-03-10', end: '2024-03-10' }, metadata, { ...DEFAULT_MOOD, period: 'week' });
  assert.equal(dst.heatmap[6 * 24 + 1].values.energy, 0); assert.equal(dst.heatmap[6 * 24 + 2].values.energy, null); assert.equal(dst.heatmap[6 * 24 + 3].values.energy, 1);
  assert.equal(dst.familiarity[1].plays, 2);
});
test('empty or all-missing scores stay null; metadata joins never multiply history', () => {
  const a = engine(), q = a.mood(january, emptyMetadata(), DEFAULT_MOOD);
  assert.equal(q.summary.values.energy, null); assert.equal(q.summary.coverage, 0); assert.equal(q.summary.plays, 8);
  const podcasts = a.mood({ ...january, kind: 'podcast' }, metadata, DEFAULT_MOOD);
  assert.equal(podcasts.summary.plays, 0); assert.equal(podcasts.summary.coverage, null); assert.equal(podcasts.summary.values.energy, null);
  assert.equal(a.mood({ ...january, search: 'Fictional B' }, metadata, DEFAULT_MOOD).summary.values.energy, 1);
});
test('portable imports reject invalid scores, duplicate IDs and incompatible sources; strip unknown fields', () => {
  const raw = read('mood-metadata.json'); raw.tracks[0].features.energy = 2; assert.throws(() => validateMetadata(raw), /energy/);
  const duplicate = read('mood-metadata.json'); duplicate.tracks.push(duplicate.tracks[0]); assert.throws(() => validateMetadata(duplicate), /duplicate/);
  const privateFields = read('mood-metadata.json'); privateFields.tracks[0].ip_addr = '192.0.2.1'; assert(!JSON.stringify(validateMetadata(privateFields)).includes('192.0.2.1'));
  assert.throws(() => mergeMetadata(metadata, { ...metadata, provider: 'demo' }), /different/);
  const old = { ...metadata, tracks: metadata.tracks.map(t => ({ ...t, fetchedAt: '2020-01-01T00:00:00.000Z' })) };
  assert.equal(mergeMetadata(metadata, old).tracks[0].fetchedAt, metadata.tracks[0].fetchedAt);
});
test('catalog results match by exact href, never response order or titles', async () => {
  const ids = metadata.tracks.slice(0, 3).map(t => t.uri);
  const fetcher = (async (url: string | URL | Request) => {
    assert.match(String(url), /https:\/\/api\.reccobeats\.com\/v1\/(track|audio-features)\?ids=/);
    const content = [ids[1], ids[0]].map((u, i) => ({ href: `https://open.spotify.com/track/${u.split(':')[2]}`, id: `provider-${i}`, trackTitle: 'Do not match by title', energy: i, valence: .5 }));
    return new Response(JSON.stringify({ content }), { status: 200 });
  }) as typeof fetch;
  const result = await lookupMetadata(ids, fetcher);
  assert.equal(result.tracks[0].features.energy, 1); assert.equal(result.tracks[1].features.energy, 0); assert.equal(result.tracks[2].status, 'missing');
});
test('rate-limited batches throw without creating false missing records', async () => {
  await assert.rejects(lookupMetadata([metadata.tracks[0].uri], (async () => new Response('', { status: 429, headers: { 'Retry-After': '120' } })) as typeof fetch), e => e instanceof CatalogError && e.retryAfter === 120);
});
