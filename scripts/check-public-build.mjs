import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
async function walk(dir) {
  const files = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path)); else files.push(path);
  }
  return files;
}
const paths = await walk('dist');
for (const file of paths) {
  if (/Streaming_History|history\.json|metadata\.json|validation\.json|\.local-data|\.private/i.test(file)) throw new Error(`Private data filename in build: ${file}`);
  const content = await readFile(file, 'utf8');
  // Raw export records must never be bundled. A single schema key in importer
  // code is normal; a source record containing ts + ms_played is not.
  if (/"ts"\s*:\s*"20\d\d-[^"\n]+"\s*,\s*"(?:platform|username|ms_played)"/.test(content)) throw new Error(`Possible raw history bundled in ${file}`);
}
console.log(`Checked ${paths.length} public build assets. No private history or metadata payloads included.`);
