import { ImportBuilder } from './normalize';
// Entirely fictional. Deterministic, small enough to explore instantly.
export function demoDataset() {
  let seed = 49;
  const random = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
  const names = ['Glass Atlas', 'Lunar Arcade', 'Velvet Circuit', 'The Daybreaks', 'Neon Orchard', 'Quiet Satellites', 'Paper Comets', 'Echo Garden', 'Low Tide Club', 'Analog Bloom', 'Midnight Relay', 'Soft Geometry'];
  const titles = ['Afterimage', 'Open Water', 'Side Streets', 'Static Bloom', 'Night Drive', 'Golden Hour', 'Small Hours', 'Parallel Lines'];
  const rows = [];
  for (let day = 0; day < 1096; day++) {
    if (random() < 0.14) continue;
    const count = 4 + Math.floor(random() * (day > 720 ? 22 : 14));
    for (let n = 0; n < count; n++) {
      const ai = Math.floor(random() ** 1.8 * Math.min(names.length, 4 + Math.floor(day / 90)));
      const ti = Math.floor(random() * titles.length), skip = random() < 0.16, shuffled = random() < 0.6;
      const ts = Date.UTC(2023, 0, 1 + day, 13 + Math.floor(random() * 10), n * 4);
      rows.push({ ts: new Date(ts).toISOString(), ms_played: skip ? Math.floor(random() * 25000) : 150000 + Math.floor(random() * 150000),
        master_metadata_track_name: titles[ti], master_metadata_album_artist_name: names[ai], master_metadata_album_album_name: `Volume ${1 + ti % 3}`,
        platform: random() < 0.65 ? 'Android' : 'Windows', conn_country: random() < 0.96 ? 'US' : 'CA',
        skipped: skip, shuffle: shuffled, offline: false, incognito_mode: false, reason_start: 'trackdone', reason_end: skip ? 'fwdbtn' : 'trackdone' });
    }
  }
  const builder = new ImportBuilder(); builder.add(rows, 'fictional-demo.json'); return builder.finish('demo');
}
