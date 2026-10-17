/// <reference lib="webworker" />
import { Analytics } from './analytics';
import { ImportBuilder, validateDataset } from './normalize';
import { demoDataset } from './demo';
import { eventsCsv } from './csv';
import { readSaved, localDismissed, saveDataset, deleteSaved } from './storage';
import type { Dataset, Filters } from './types';

let engine: Analytics | null = null;
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
      result = await retain(builder.finish());
    } else if (method === 'loadDemo') result = await retain(demoDataset());
    else if (method === 'clearData') { await deleteSaved(); engine = null; result = null; }
    else {
      if (!engine) throw new Error('Import your listening history first.');
      if (method === 'query') result = engine.query(args[0] as Filters);
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
