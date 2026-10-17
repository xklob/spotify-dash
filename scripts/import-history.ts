import { readdir, readFile, mkdir, writeFile, rename } from 'node:fs/promises';
import { resolve, basename, join } from 'node:path';
import { ImportBuilder } from '../src/lib/normalize';

const input = process.argv[2];
if (!input) { console.error('Usage: npm run import -- "/path/to/Spotify Extended Streaming History"'); process.exit(1); }
const names = (await readdir(input)).filter(n => /^Streaming_History.*\.json$/i.test(n)).sort();
if (!names.length) throw new Error('No Streaming_History*.json files found in the supplied folder.');
const builder = new ImportBuilder();
for (const name of names) builder.add(JSON.parse(await readFile(join(input, name), 'utf8')), basename(name));
const dataset = builder.finish();
await mkdir(resolve('.local-data'), { recursive: true, mode: 0o700 });
await writeFile(resolve('.local-data/history.json.tmp'), JSON.stringify(dataset), { mode: 0o600 });
await rename(resolve('.local-data/history.json.tmp'), resolve('.local-data/history.json'));
console.log(JSON.stringify({ files: dataset.meta.files.length, rawRows: dataset.meta.rawRows, retainedEvents: dataset.meta.events,
  duplicates: dataset.meta.duplicates, invalidRows: dataset.meta.invalidRows, hours: dataset.meta.totalMs / 3600000,
  first: dataset.meta.first, last: dataset.meta.last, output: '.local-data/history.json (git-ignored)' }, null, 2));
