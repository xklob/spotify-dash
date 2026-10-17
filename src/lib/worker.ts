/// <reference lib="webworker" />
import { Analytics } from './analytics';
import { ImportBuilder, validateDataset } from './normalize';
import { demoDataset } from './demo';
import { eventsCsv } from './csv';
import { readSaved, localDismissed, saveDataset, deleteSaved, readMetadata, saveMetadata } from './storage';
import type { Dataset, Filters } from './types';
import { emptyMetadata, demoMetadata, mergeMetadata, validateMetadata, spotifyId, type Metadata } from './metadata';
import { BATCH_SIZE, lookupMetadata, CatalogError } from './reccobeats';
import type { MoodOptions } from './mood';

let engine: Analytics | null = null;
let metadata: Metadata = emptyMetadata();
let retryAt = 0;
function pendingTracks() {
  if (!engine || engine.dataset.meta.source === 'demo') return [];
  const known = new Set(metadata.tracks.map(t => t.uri));
  const time = new Map<string, number>();
  for (const e of engine.dataset.events) if (e.kind === 'music' && spotifyId(e.uri) && !known.has(e.uri)) time.set(e.uri, (time.get(e.uri) ?? 0) + e.ms);
  return [...time.keys()].sort((a, b) => time.get(b)! - time.get(a)! || a.localeCompare(b));
}
async function retain(dataset: Dataset) {
  try { await saveDataset(dataset); } catch (e) { dataset.meta.warnings.push(e instanceof Error ? e.message : 'History could not be saved in this browser.'); }
  engine = new Analytics(dataset); return dataset.meta;
}
const scope = self as unknown as DedicatedWorkerGlobalScope;
async function handle(message: { id: number; method: string; args: unknown[] }) {
  const { id, method, args } = message;
  try {
    let result: unknown;
    if (method === 'initialize') {
      const saved = await readSaved().catch(() => undefined);
      if (saved) { engine = new Analytics(validateDataset(saved)); result = saved.meta; }
      else {
        result = null;
        if (!(await localDismissed().catch(() => false)) && ['localhost', '127.0.0.1', '[::1]'].includes(scope.location.hostname)) {
          const response = await fetch('/__local/history.json', { cache: 'no-store' });
          if (response.ok && response.headers.get('content-type')?.includes('application/json')) {
            result = await retain(validateDataset(await response.json()));
          }
        }
      }
      if (engine?.dataset.meta.source === 'demo') metadata = demoMetadata(engine.dataset);
      else {
        const savedMetadata = await readMetadata().catch(() => undefined);
        if (savedMetadata) metadata = validateMetadata(savedMetadata);
        if (['localhost', '127.0.0.1', '[::1]'].includes(scope.location.hostname) && !(await localDismissed().catch(() => false))) {
          const r = await fetch('/__local/metadata.json', { cache: 'no-store' }).catch(() => null);
          if (r?.ok && r.headers.get('content-type')?.includes('application/json')) {
            const local = validateMetadata(await r.json());
            if (local.provider === 'reccobeats') metadata = mergeMetadata(metadata, local);
          }
        }
      }
    } else if (method === 'importFiles') {
      const files = args[0] as File[];
      const builder = new ImportBuilder(engine?.dataset.meta.source === 'import' ? engine.dataset : null);
      const jsonFiles = files.filter(f => /\.json$/i.test(f.name));
      if (!jsonFiles.length) throw new Error('Choose one or more Extended Streaming History .json files.');
      let index = 0;
      for (const file of jsonFiles) {
        scope.postMessage({ id, progress: `Reading ${++index} of ${jsonFiles.length}: ${file.name}` });
        let rows;
        try { rows = JSON.parse(await file.text()); } catch { throw new Error(`${file.name} is not valid JSON. Your previous import is unchanged.`); }
        builder.add(rows, file.name);
      }
      scope.postMessage({ id, progress: 'Saving your history in this browser…' });
      const next = builder.finish();
      if (metadata.provider === 'demo') { await saveMetadata(null); metadata = emptyMetadata(); }
      result = await retain(next);
    } else if (method === 'loadDemo') {
      const data = demoDataset(); result = await retain(data); metadata = demoMetadata(data); await saveMetadata(null);
    }
    else if (method === 'clearData') { await deleteSaved(); engine = null; metadata = emptyMetadata(); result = null; }
    else {
      if (!engine) throw new Error('Import your listening history first.');
      if (method === 'query') result = engine.query(args[0] as Filters);
      else if (method === 'mood') result = engine.mood(args[0] as Filters, metadata, args[1] as MoodOptions, pendingTracks().length);
      else if (method === 'importMetadata') {
        const file = args[0] as File;
        if (file.size > 64 * 1024 * 1024) throw new Error('Metadata files must be smaller than 64 MB.');
        const next = validateMetadata(JSON.parse(await file.text()));
        if (next.provider !== (engine.dataset.meta.source === 'demo' ? 'demo' : 'reccobeats')) throw new Error('Demo metadata can only be used with the fictional demo.');
        const merged = mergeMetadata(metadata, next); await saveMetadata(merged); metadata = merged; result = metadata.tracks.length;
      } else if (method === 'exportMetadata') result = JSON.stringify(metadata, null, 2) + '\n';
      else if (method === 'enrichBatch') {
        if (engine.dataset.meta.source === 'demo') throw new Error('The demo uses fictional scores and does not contact the catalog.');
        if (Date.now() < retryAt) throw new Error(`The catalog requested a pause. Retry after ${new Date(retryAt).toLocaleTimeString()}.`);
        const pending = pendingTracks(), batch = pending.slice(0, BATCH_SIZE);
        if (batch.length) {
          try {
            const merged = mergeMetadata(metadata, await lookupMetadata(batch));
            await saveMetadata(merged); metadata = merged;
          } catch (e) { if (e instanceof CatalogError) retryAt = Date.now() + Math.max(60, e.retryAfter) * 1000; throw e; }
        }
        result = { completed: batch.length, remaining: pending.length - batch.length };
      }
      else if (method === 'history') result = engine.history(args[0] as Filters, args[1] as number, args[2] as number, args[3] as 'newest' | 'oldest');
      else if (method === 'exportCsv') result = eventsCsv(engine.all(args[0] as Filters), (args[0] as Filters).timezone);
      else throw new Error('Unknown explorer operation.');
    }
    scope.postMessage({ id, result });
  } catch (error) { scope.postMessage({ id, error: error instanceof Error ? error.message : String(error) }); }
}
// Serialize imports and queries so a filter change never reads a half-imported dataset.
let queue: Promise<void> = Promise.resolve();
scope.onmessage = event => { queue = queue.then(() => handle(event.data)); };
