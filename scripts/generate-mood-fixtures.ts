import { writeFileSync } from 'node:fs';
import { ImportBuilder } from '../src/lib/normalize';
import { Analytics } from '../src/lib/analytics';
import { emptyFeatures, emptyMetadata, type TrackMetadata } from '../src/lib/metadata';
import { DEFAULT_FILTERS } from '../src/lib/types';
import { DEFAULT_MOOD } from '../src/lib/mood';
const uri = (letter: string) => `spotify:track:${letter.repeat(22)}`;
const row = (day: string, letter: string, seconds: number, skipped: boolean | null = false) => ({ ts: day, ms_played: seconds * 1000,
  master_metadata_track_name: `Fictional ${letter || 'no-ID'}`, master_metadata_album_artist_name: 'Test Artist', master_metadata_album_album_name: 'Test Album',
  spotify_track_uri: letter ? uri(letter) : '', platform: 'Android', conn_country: 'US', skipped, shuffle: false });
const rows = [row('2024-01-01T12:00:00Z', 'A', 100), row('2024-01-01T13:00:00Z', 'A', 300, true), row('2024-01-02T12:00:00Z', 'B', 100),
  row('2024-01-03T12:00:00Z', 'C', 900), row('2024-01-04T12:00:00Z', 'D', 200, null), row('2024-01-04T13:00:00Z', 'E', 0, true),
  row('2024-01-05T12:00:00Z', '', 50), { ...row('2024-01-06T12:00:00Z', '', 50), master_metadata_track_name: 'Another no-ID track' },
  row('2024-03-10T06:59:00Z', 'A', 1000), row('2024-03-10T07:01:00Z', 'B', 100),
  { ts: '2024-01-10T12:00:00Z', ms_played: 999999, episode_name: 'Podcast', episode_show_name: 'Test Show', spotify_episode_uri: 'spotify:episode:fixture', platform: 'Android' }];
const metadata = emptyMetadata(); metadata.generatedAt = '2026-01-01T00:00:00.000Z';
metadata.tracks = ['A', 'B', 'C', 'D', 'E'].map((letter, i): TrackMetadata => ({ uri: uri(letter), status: letter === 'C' ? 'missing' : 'found', fetchedAt: metadata.generatedAt,
  providerId: `fixture-${letter}`, title: `Fictional ${letter}`, artists: ['Test Artist'], durationMs: 240000, isrc: '', key: null, mode: null,
  features: { ...emptyFeatures(), energy: [0, 1, null, .9, .2][i], valence: [.2, .8, null, null, .4][i], tempo: [120, 140, null, 100, 80][i] } }));
const b = new ImportBuilder(); b.add(rows, 'mood-history.json'); const engine = new Analytics(b.finish());
const january = { ...DEFAULT_FILTERS, timezone: 'UTC', start: '2024-01-01', end: '2024-01-31' };
const scenarios = [
  ...(['time', 'plays', 'tracks'] as const).map(weight => ({ name: `january-${weight}`, filters: january, options: { ...DEFAULT_MOOD, weight } })),
  { name: 'rolling-distinct', filters: { ...january, end: '2024-03-31' }, options: { ...DEFAULT_MOOD, weight: 'tracks' as const } },
  { name: 'dst-week', filters: { ...DEFAULT_FILTERS, start: '2024-03-10', end: '2024-03-10' }, options: { ...DEFAULT_MOOD, period: 'week' as const } },
  { name: 'podcasts-empty', filters: { ...january, kind: 'podcast' as const }, options: DEFAULT_MOOD },
];
writeFileSync('tests/fixtures/mood-history.json', JSON.stringify(rows, null, 2) + '\n');
writeFileSync('tests/fixtures/mood-metadata.json', JSON.stringify(metadata, null, 2) + '\n');
writeFileSync('tests/fixtures/mood-expected.json', JSON.stringify(scenarios.map(s => ({ ...s, expected: engine.mood(s.filters, metadata, s.options) }))) + '\n');
console.log(`Generated ${scenarios.length} shared mood cases.`);
