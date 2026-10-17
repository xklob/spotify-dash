import test from 'node:test';
import assert from 'node:assert/strict';
import { ImportBuilder, platformFamily } from '../src/lib/normalize';
import { Analytics, pearson } from '../src/lib/analytics';
import { DEFAULT_FILTERS } from '../src/lib/types';
import { eventsCsv } from '../src/lib/csv';

function raw(ts: string, overrides: Record<string, unknown> = {}) {
  return { ts, ms_played: 60000, master_metadata_track_name: 'Track A', master_metadata_album_artist_name: 'Artist A', master_metadata_album_album_name: 'Album A', spotify_track_uri: 'spotify:track:AAA', platform: 'Windows 11', conn_country: 'US', skipped: false, shuffle: false, offline: false, reason_start: 'trackdone', reason_end: 'trackdone', ...overrides };
}
function build(rows: unknown[], file = 'Streaming_History_Audio_test.json') { const b = new ImportBuilder(); b.add(rows, file); return b.finish(); }
const utc = { ...DEFAULT_FILTERS, timezone: 'UTC' };

test('deduplicates playback identity, preserves distinct plays, strips personal fields', () => {
  const a = raw('2024-01-01T00:00:00Z', { ip_addr: '192.0.2.44', username: 'private-person', user_agent_decrypted: 'private-agent' });
  const ds = build([a, { ...a }, { ...a, ts: '2024-01-01T00:01:00Z' }, { ...a, platform: 'Windows 10' }]);
  assert.equal(ds.meta.events, 3); assert.equal(ds.meta.duplicates, 1); assert.equal(ds.meta.rawRows, 4);
  assert.equal(ds.meta.totalMs, 180000);
  assert(!JSON.stringify(ds).includes('192.0.2.44')); assert(!JSON.stringify(ds).includes('private-person')); assert(!JSON.stringify(ds).includes('private-agent'));
  const again = new ImportBuilder(ds); again.add([a], 'again.json');
  assert.equal(again.finish().meta.events, 3);
});

test('rejects wrong exports without silently displaying an empty successful import', () => {
  assert.throws(() => build([{ endTime: '2024-01-01', msPlayed: 123 }]), /not an Extended/);
  assert.throws(() => build([]), /No valid/);
  const ds = build([raw('not-a-date'), raw('2024-02-31T00:00:00Z'), raw('2024-01-01T00:00:00Z', { ms_played: -1 }), raw('2024-01-02T00:00:00Z')]);
  assert.equal(ds.meta.invalidRows, 3); assert.equal(ds.meta.events, 1);
});

test('source details distinguish records even when the dashboard discards them', () => {
  const a = raw('2024-01-01T00:00:00Z', { ip_addr: '192.0.2.1' });
  const b = { ...a, ip_addr: '192.0.2.2' };
  const ds = build([a, b, a]);
  assert.equal(ds.meta.events, 2); assert.equal(ds.meta.duplicates, 1);
  assert(!JSON.stringify(ds).includes('192.0.2.'));
});

test('weighted skips exclude unknown flags and >=30s convention includes boundary', () => {
  const ds = build([
    raw('2024-01-01T01:00:00Z', { skipped: true, ms_played: 1000 }),
    raw('2024-01-01T02:00:00Z', { skipped: false, ms_played: 30000 }),
    raw('2024-01-01T03:00:00Z', { skipped: null, ms_played: 90000 }),
    raw('2024-01-01T04:00:00Z', { skipped: null, ms_played: 0 }),
  ]);
  const q = new Analytics(ds).query(utc);
  assert.equal(q.summary.events, 4); assert.equal(q.summary.qualifiedPlays, 2);
  assert.equal(q.summary.skipKnown, 2); assert.equal(q.summary.skipRate, 0.5);
  assert.equal(q.summary.ms, 121000); assert.equal(q.tracks[0].skipRate, 0.5);
});

test('ending calendar day, DST and inclusive ranges are applied in selected timezone', () => {
  const ds = build([raw('2024-03-10T04:30:00Z'), raw('2024-03-10T06:59:00Z'), raw('2024-03-10T07:01:00Z')]);
  const a = new Analytics(ds);
  const q = a.query({ ...DEFAULT_FILTERS, start: '2024-03-10', end: '2024-03-10' });
  assert.equal(q.summary.events, 2); assert.equal(q.summary.ms, 120000); assert.equal(q.hours[2].plays, 0);
  assert.equal(q.hours[1].plays, 1); assert.equal(q.hours[3].plays, 1);
  assert.equal(a.history({ ...DEFAULT_FILTERS }).rows.at(-1)?.date, '2024-03-09');
  assert.equal(a.query({ ...utc, start: '2024-03-10', end: '2024-03-10' }).summary.events, 3);
});

test('new artist and familiar listening use the full import even after filtering', () => {
  const ds = build([raw('2023-12-01T12:00:00Z'), raw('2024-01-02T12:00:00Z'), raw('2024-01-02T13:00:00Z', { master_metadata_album_artist_name: 'Artist B', spotify_track_uri: 'spotify:track:BBB' })]);
  const q = new Analytics(ds).query({ ...utc, start: '2024-01-01', end: '2024-01-31' });
  assert.equal(q.summary.newArtists, 1); assert.equal(q.monthly[0].newArtists, 1); assert.equal(q.monthly[0].familiarShare, 0.5);
  const b = new Analytics(build([raw('2024-01-01T12:00:00Z'), raw('2024-01-20T12:00:00Z')]));
  const partialMonth = b.query({ ...utc, start: '2024-01-15', end: '2024-01-31' });
  assert.equal(partialMonth.summary.newArtists, 0); assert.equal(partialMonth.monthly[0].newArtists, 0); assert.equal(partialMonth.yearly[0].newArtists, 0);
});

test('calendar zeroes, streaks, chart composition and table totals reconcile', () => {
  const ds = build([raw('2024-01-01T12:00:00Z'), raw('2024-01-02T12:00:00Z'), raw('2024-01-04T12:00:00Z', { ms_played: 120000 })]);
  const q = new Analytics(ds).query({ ...utc, start: '2024-01-01', end: '2024-01-07' });
  assert.equal(q.daily.length, 7); assert.equal(q.summary.activeDays, 3); assert.equal(q.summary.longestStreak, 2);
  assert.equal(q.daily[2].hours, 0); assert.equal(q.weekdays[0].averageMinutes, 1);
  assert.equal(q.daily.reduce((n, d) => n + d.minutes, 0), 4);
  assert.equal(q.artists.reduce((n, a) => n + a.ms, 0), 240000);
  for (const [i, p] of q.composition.entries()) assert(Math.abs(q.compositionKeys.reduce((n, k) => n + Number(p[k]), 0) - q.monthly[i].hours) < 1e-9);
  assert.equal(q.yearly[0].partial, true);
});

test('all global filters and CSV use the same selected population', () => {
  const ds = build([
    raw('2024-01-01T12:00:00Z'),
    raw('2024-01-02T12:00:00Z', { platform: 'Android', shuffle: true, master_metadata_track_name: '=SUM(1,2)' }),
    raw('2024-01-03T12:00:00Z', { master_metadata_track_name: null, spotify_track_uri: null, episode_name: 'Episode', episode_show_name: 'Show', spotify_episode_uri: 'spotify:episode:EEE' }),
  ]);
  const a = new Analytics(ds), filters = { ...utc, platform: 'Android', shuffle: 'yes' as const, search: 'sum', minMs: 30000 };
  assert.equal(a.query(filters).summary.events, 1);
  const history = a.history(filters); assert.equal(history.total, 1);
  const csv = eventsCsv(a.all(filters), 'UTC');
  assert.equal(csv.split('\r\n').length, 2); assert(csv.includes("'=SUM(1,2)")); assert(!csv.includes('Episode'));
  assert.equal(a.query({ ...utc, kind: 'podcast' }).summary.events, 1);
  const empty = a.query({ ...utc, search: 'nonexistent' }); assert.equal(empty.summary.ms, 0); assert.equal(empty.summary.skipRate, null);
});

test('history pagination clamps after filtering, sort and all-data reset work', () => {
  const a = new Analytics(build([raw('2024-01-01T12:00:00Z'), raw('2024-01-02T12:00:00Z'), raw('2024-01-03T12:00:00Z')]));
  assert.equal(a.history(utc, 0, 1, 'oldest').rows[0].date, '2024-01-01');
  assert.equal(a.history(utc, 99, 1).page, 2);
  assert.equal(a.query({ ...utc, start: '2024-01-02', end: '2024-01-02' }).summary.events, 1);
  assert.equal(a.query(utc).summary.events, 3);
});

test('search matches accents without changing displayed metadata', () => {
  const a = new Analytics(build([raw('2024-01-01T12:00:00Z', { master_metadata_album_artist_name: 'Björk' })]));
  const q = a.query({ ...utc, search: 'bjork' });
  assert.equal(q.summary.events, 1); assert.equal(q.artists[0].name, 'Björk');
});

test('correlation is undefined for tiny or constant populations', () => {
  assert.equal(pearson([[1, 2], [2, 4]]), null);
  assert.equal(pearson([[1, 2], [1, 4], [1, 6]]), null);
  assert.equal(pearson([[1, 2], [2, 4], [3, 6]]), 1);
  assert.equal(pearson([[1, 6], [2, 4], [3, 2]]), -1);
  assert.equal(platformFamily('Partner google cast_tv;device'), 'Chromecast');
});
