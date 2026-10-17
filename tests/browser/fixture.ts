export function historyFixture() {
  const rows = Array.from({ length: 8 }, (_, i) => ({
    ts: `2024-01-${String(i + 1).padStart(2, '0')}T18:00:00Z`,
    ms_played: (i + 1) * 30000,
    master_metadata_track_name: i % 2 ? 'Silver Morning' : 'Golden Evening',
    master_metadata_album_artist_name: i % 2 ? 'Fictional Artist B' : 'Fictional Artist A',
    master_metadata_album_album_name: 'Synthetic Album',
    spotify_track_uri: i % 2 ? 'spotify:track:BBB' : 'spotify:track:AAA',
    platform: i % 2 ? 'Android' : 'Windows', conn_country: 'US',
    skipped: i % 3 === 0, shuffle: i % 2 === 0, offline: false,
    reason_start: 'trackdone', reason_end: i % 3 === 0 ? 'fwdbtn' : 'trackdone',
    ip_addr: '192.0.2.23', username: 'synthetic-private-field',
  }));
  return [...rows, rows[0]];
}
