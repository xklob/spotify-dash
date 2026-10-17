// GET-only catalog enrichment. This tool never reads audio files.
import { readFileSync, writeFileSync, renameSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { emptyMetadata, mergeMetadata, validateMetadata, paired, spotifyId, type Metadata } from '../src/lib/metadata';
import { BATCH_SIZE, lookupMetadata, CatalogError } from '../src/lib/reccobeats';
import { validateDataset } from '../src/lib/normalize';

const args = process.argv.slice(2);
const arg = (key: string, fallback: string) => { const i = args.indexOf(key); return i >= 0 ? args[i + 1] ?? fallback : fallback; };
if (args.includes('--help')) {
  console.log('npm run enrich -- --input .local-data/history.json --output .local-data/metadata.json [--limit 500] [--pilot] [--refresh-missing]\nReads a sanitized history from npm run import. Sends only distinct Spotify track IDs. Resumes its output cache; Ctrl+C preserves completed batches. --pilot samples every listening year plus frequent and rare tracks.'); process.exit(0);
}
const input = resolve(arg('--input', '.local-data/history.json')), output = resolve(arg('--output', '.local-data/metadata.json'));
if (input === output) throw new Error('Input and output must be different files.');
const data = validateDataset(JSON.parse(readFileSync(input, 'utf8')));
if (data.meta.source === 'demo') throw new Error('Fictional data cannot be enriched from a real catalog.');
const stats = new Map<string, { ms: number; count: number; years: Set<number> }>();
for (const e of data.events) if (e.kind === 'music' && spotifyId(e.uri)) {
  const t = stats.get(e.uri) ?? { ms: 0, count: 0, years: new Set<number>() };
  t.ms += e.ms; t.count++; t.years.add(new Date(e.ts).getUTCFullYear()); stats.set(e.uri, t);
}
const ranked = [...stats.keys()].sort((a, b) => stats.get(b)!.ms - stats.get(a)!.ms || a.localeCompare(b));
let selected = ranked;
if (args.includes('--pilot')) {
  const chosen = new Set(ranked.slice(0, 80));
  for (const year of [...new Set([...stats.values()].flatMap(t => [...t.years]))].sort()) {
    const group = ranked.filter(id => stats.get(id)!.years.has(year));
    for (let i = 0; i < 16; i++) if (group.length) chosen.add(group[Math.floor(i * (group.length - 1) / 15)]);
  }
  selected = [...chosen];
}
let metadata: Metadata = existsSync(output) ? validateMetadata(JSON.parse(readFileSync(output, 'utf8'))) : emptyMetadata();
if (metadata.provider !== 'reccobeats' || metadata.modelVersion !== null) throw new Error('This cache uses a different provider/model. Choose a new output file.');
const done = new Set(metadata.tracks.filter(t => !args.includes('--refresh-missing') || paired(t)).map(t => t.uri));
const limit = Number(arg('--limit', String(selected.length)));
if (!Number.isSafeInteger(limit) || limit < 1) throw new Error('--limit must be a positive integer.');
selected = selected.filter(id => !done.has(id)).slice(0, limit);
mkdirSync(dirname(output), { recursive: true });
let stopping = false; process.on('SIGINT', () => { stopping = true; console.log('Finishing the current batch before stopping…'); });
const save = () => { writeFileSync(output + '.tmp', JSON.stringify(metadata) + '\n', { mode: 0o600 }); renameSync(output + '.tmp', output); };
console.log(JSON.stringify({ distinctTracks: stats.size, cached: done.size, queued: selected.length, output }));
for (let i = 0; i < selected.length && !stopping; i += BATCH_SIZE) {
  const batch = selected.slice(i, i + BATCH_SIZE);
  let next: Metadata | undefined;
  for (let attempt = 0; attempt < 4; attempt++) {
    try { next = await lookupMetadata(batch); break; }
    catch (error) {
      if (stopping || attempt === 3) throw error;
      const delay = Math.max(error instanceof CatalogError ? error.retryAfter * 1000 : 0, 5000 * 2 ** attempt);
      console.log(`Lookup interrupted; retrying after ${Math.ceil(delay / 1000)} seconds. Completed batches remain saved.`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
  if (next) { metadata = mergeMetadata(metadata, next); save(); }
  console.log(JSON.stringify({ processed: Math.min(i + BATCH_SIZE, selected.length), queued: selected.length, cached: metadata.tracks.length, withMood: metadata.tracks.filter(paired).length }));
}
const matched = new Set(metadata.tracks.filter(paired).map(t => t.uri));
let totalMs = 0, coveredMs = 0;
for (const [uri, s] of stats) { totalMs += s.ms; if (matched.has(uri)) coveredMs += s.ms; }
console.log(JSON.stringify({ tracksWithMood: matched.size, distinctTracks: stats.size, coveredMs, totalMs, timeCoverage: totalMs ? coveredMs / totalMs : null }));
