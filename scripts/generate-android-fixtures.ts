// Shared, fictional records and web-derived expectations for native parity checks.
import { mkdirSync, writeFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { Analytics } from '../src/lib/analytics';
import { demoDataset } from '../src/lib/demo';
import { ImportBuilder } from '../src/lib/normalize';
import { DEFAULT_FILTERS, type Filters, type QueryResult } from '../src/lib/types';

const track = (ts: string, ms: number, artist: string, title: string, extra: Record<string, unknown> = {}) => ({
  ts, ms_played: ms, master_metadata_track_name: title, master_metadata_album_artist_name: artist,
  master_metadata_album_album_name: 'Volume 1', spotify_track_uri: '', platform: 'Android 14', conn_country: 'US',
  skipped: false, shuffle: false, offline: false, reason_start: 'trackdone', reason_end: 'trackdone', ...extra,
});
const rows = [
  track('2023-12-31T23:40:00Z', 180000, 'Glass Atlas', 'Afterimage'),
  track('2024-01-01T06:00:00Z', 240000, 'Glass Atlas', 'Afterimage', { shuffle: true }),
  track('2024-01-02T15:00:00Z', 300000, 'Lunar Arcade', 'Night Drive', { skipped: null, shuffle: null }),
  track('2024-01-02T15:10:00Z', 5000, 'Glass Atlas', 'Afterimage', { skipped: true, reason_end: 'fwdbtn' }),
  track('2024-01-03T03:00:00Z', 120000, 'Quiet Satellites', 'Open Water', { platform: 'Windows 11', conn_country: 'ca' }),
  track('2024-01-03T16:00:00Z', 0, 'Glass Atlas', 'Afterimage', { skipped: true, shuffle: true }),
  track('2024-01-04T13:00:00Z', 180000, 'Lunar Arcade', 'Night Drive', { shuffle: true }),
  track('2024-02-29T23:59:00Z', 90000, 'Echo Garden', 'Déjà Vu', { skipped: null }),
  track('2024-03-10T06:59:00Z', 180000, 'Glass Atlas', 'Afterimage'),
  track('2024-03-10T07:01:00Z', 210000, 'Glass Atlas', 'Afterimage', { platform: 'iOS 17', shuffle: true }),
  track('2024-07-01T15:00:00Z', 270000, 'Quiet Satellites', 'Open Water'),
  track('2024-09-03T16:00:00Z', 220000, 'Echo Garden', 'Déjà Vu', { platform: 'Sonos', conn_country: 'XX' }),
  track('2024-11-03T05:30:00Z', 140000, 'Lunar Arcade', 'Night Drive', { platform: 'web_player' }),
  track('2024-11-03T06:30:00Z', 130000, 'Lunar Arcade', 'Night Drive', { skipped: null, shuffle: null }),
  track('2025-01-01T06:30:00Z', 400000, 'Other', 'New Horizon'),
  track('2025-01-02T06:30:00Z', 350000, 'Glass Atlas', 'Afterimage', { shuffle: true }),
  { ts: '2024-02-01T09:00:00Z', ms_played: 900000, episode_name: 'A fictional episode', episode_show_name: 'Signal Notes', spotify_episode_uri: 'spotify:episode:fictionalEpisode', platform: 'Linux', conn_country: 'US', skipped: null },
  { ts: '2024-06-01T09:00:00Z', ms_played: 600000, audiobook_title: 'Fictional Journeys', audiobook_chapter_title: 'Chapter One', audiobook_chapter_uri: 'spotify:chapter:fictionalChapter', platform: 'Android', conn_country: 'US', shuffle: false, skipped: false },
  { ts: '2024-07-04T09:00:00Z', ms_played: 1000, platform: null, conn_country: null, skipped: 'false' },
  track('2024-02-30T09:00:00Z', 200000, 'Invalid', 'Invalid date'),
  track('2024-08-01T09:00:00Z', -1, 'Invalid', 'Negative duration'),
  track('2024-08-02T09:00:00Z', 1.5, 'Invalid', 'Fractional duration'),
];
rows.push({ ...rows[0] }, { ...rows[3] });
const builder = new ImportBuilder(); builder.add(rows, 'parity-history.json');
const data = builder.finish(), engine = new Analytics(data);
function project(q: QueryResult) {
  const result = structuredClone(q) as unknown as Record<string, unknown>;
  for (const key of ['artists', 'tracks', 'albums'] as const) {
    result[key] = q[key].map(({ key: _key, ...r }) => r).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
  }
  result.composition = q.composition.map(row => ({ period: row.period, values: q.compositionKeys.map(key => row[key]) }));
  return result;
}
const scenarios: { name: string; filters: Filters }[] = [
  { name: 'all', filters: DEFAULT_FILTERS },
  { name: 'music_2024', filters: { ...DEFAULT_FILTERS, start: '2024-01-01', end: '2024-12-31', kind: 'music', minMs: 30000 } },
  { name: 'artist_android_shuffle', filters: { ...DEFAULT_FILTERS, artist: 'Glass Atlas', platform: 'Android', shuffle: 'yes' } },
  { name: 'utc_search', filters: { ...DEFAULT_FILTERS, timezone: 'UTC', search: 'deja', country: 'US' } },
  { name: 'empty', filters: { ...DEFAULT_FILTERS, start: '2024-01-01', end: '2024-01-07', search: 'never exists' } },
];
mkdirSync('tests/fixtures', { recursive: true });
writeFileSync('tests/fixtures/parity-history.json', JSON.stringify(rows, null, 2) + '\n');
writeFileSync('tests/fixtures/parity-expected.json', JSON.stringify({ meta: {
  rawRows: data.meta.rawRows, duplicates: data.meta.duplicates, invalidRows: data.meta.invalidRows, events: data.meta.events, totalMs: data.meta.totalMs,
}, scenarios: scenarios.map(s => ({ ...s, expected: project(engine.query(s.filters)) })) }, null, 2) + '\n');
const demo = demoDataset();
// Fixed metadata keeps regeneration byte-for-byte reproducible.
demo.meta.importedAt = '2026-01-01T00:00:00.000Z';
mkdirSync('android/app/src/main/assets', { recursive: true });
writeFileSync('android/app/src/main/assets/demo.json.gzip', gzipSync(JSON.stringify({ version: 1, ...demo }), { level: 9 }));
console.log(`Generated ${scenarios.length} web parity cases and ${demo.events.length} fictional demo records.`);
