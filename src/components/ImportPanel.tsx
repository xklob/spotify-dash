import { useRef, useState, type DragEvent } from 'react';
import { FileJson, FolderOpen, Upload } from 'lucide-react';

export interface PickedFiles { accepted: File[]; skipped: string[] }

const isJson = (name: string) => /\.json$/i.test(name);
function partition(files: File[]): PickedFiles {
  const accepted: File[] = [], skipped: string[] = [];
  for (const f of files) {
    if (isJson(f.name)) accepted.push(f);
    else skipped.push(f.name);
  }
  return { accepted, skipped };
}

// Dropped folders arrive as directory entries; walk them so a whole export folder can be dropped.
function readAll(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  return new Promise((resolve, reject) => {
    const out: FileSystemEntry[] = [];
    const next = () => reader.readEntries(batch => {
      if (!batch.length) resolve(out);
      else { out.push(...batch); next(); }
    }, reject);
    next();
  });
}
async function walk(entry: FileSystemEntry, out: File[]): Promise<void> {
  if (entry.isFile) {
    out.push(await new Promise<File>((resolve, reject) => (entry as FileSystemFileEntry).file(resolve, reject)));
  } else if (entry.isDirectory) {
    for (const child of await readAll((entry as FileSystemDirectoryEntry).createReader())) await walk(child, out);
  }
}
async function fromDrop(dt: DataTransfer): Promise<File[]> {
  const entries = [...dt.items].map(i => (i.kind === 'file' ? i.webkitGetAsEntry?.() : null)).filter((e): e is FileSystemEntry => !!e);
  if (!entries.length) return [...dt.files];
  const out: File[] = [];
  for (const e of entries) await walk(e, out);
  return out;
}

export function ImportPanel({ onPick, disabled, compact = false, replacing = false }: {
  onPick: (picked: PickedFiles) => void; disabled?: boolean; compact?: boolean; replacing?: boolean;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const pickInputs = (list: FileList | null, input: HTMLInputElement) => {
    const files = list ? [...list] : [];
    input.value = '';
    if (files.length) onPick(partition(files));
  };
  const onDrop = async (e: DragEvent) => {
    e.preventDefault();
    setOver(false);
    if (disabled) return;
    try { onPick(partition(await fromDrop(e.dataTransfer))); } catch { onPick(partition([...e.dataTransfer.files])); }
  };
  return (
    <div className={`dropzone${over ? ' is-over' : ''}${compact ? ' is-compact' : ''}${disabled ? ' is-disabled' : ''}`}
      onDragOver={e => { e.preventDefault(); if (!disabled) setOver(true); }}
      onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setOver(false); }}
      onDrop={onDrop}>
      <div className="dropzone-icon" aria-hidden="true"><FileJson size={compact ? 20 : 26} /></div>
      <div className="dropzone-text">
        <strong>Drop Streaming_History JSON files or the export folder</strong>
        <span>{replacing ? 'Real files replace the synthetic demo.' : 'Files are merged with what is already loaded. Exact duplicate records count once.'} Only .json files are read.</span>
      </div>
      <div className="dropzone-actions">
        <button type="button" className="btn btn-primary" disabled={disabled} onClick={() => fileRef.current?.click()}>
          <Upload size={15} aria-hidden="true" /> Choose files
        </button>
        <label className={`btn${disabled ? ' is-disabled' : ''}`}>
          <FolderOpen size={15} aria-hidden="true" /> Choose folder
          {/* React has no typed prop for directory pickers, so the attributes are set on the element. */}
          <input type="file" className="visually-hidden-input" disabled={disabled} multiple
            ref={el => { if (el) { el.setAttribute('webkitdirectory', ''); el.setAttribute('directory', ''); } }}
            onChange={e => pickInputs(e.target.files, e.target)} />
        </label>
        <input ref={fileRef} type="file" className="visually-hidden-input" tabIndex={-1} aria-hidden="true" multiple accept=".json,application/json"
          onChange={e => pickInputs(e.target.files, e.target)} />
      </div>
    </div>
  );
}
