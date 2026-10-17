import { ArrowUpRight, FlaskConical, Github, Trash2 } from 'lucide-react';
import type { DatasetMeta } from '../lib/types';
import { fmtDateTime, fmtInt, fmtMsHours, plural } from './format';
import { ImportPanel, type PickedFiles } from './ImportPanel';
import { Fact } from './ui';
import { REPO_URL, SPOTIFY_DATA_URL } from './Welcome';

const METHODS: [string, string][] = [
  ['Duplicates', 'Each source record is fingerprinted from its complete original fields before private fields are discarded. Exact matches within the same audio or video category, across files or repeated imports, are kept once. Identical-looking records from different categories and records differing in any field stay separate.'],
  ['End timestamps', 'Spotify\'s ts marks when playback ended, in UTC. Each event, with all of its ms_played, is assigned to the hour, day, month, and year in which it ended, in the selected timezone. Time is not spread over the minutes it actually played, so these are not measured wall-clock patterns.'],
  ['Listening time', 'The sum of ms_played. Recorded sessions can overlap, for example on two devices, so totals can exceed elapsed time.'],
  ['Events and plays', 'Events count every record, including zero-length ones unless you raise Minimum played. Plays of 30 s or more is this app\'s convention for a qualifying play, not Spotify\'s royalty or Wrapped rule. Recent range presets end at the last imported event, not today. Rankings count events.'],
  ['Titles and artists', 'A title is its Spotify URI, or type, creator, title, and album when there is no URI. Artists / shows are exported names: album artist for music, show for podcasts, book title for audiobooks. Without artist IDs, artists sharing a name are combined.'],
  ['Skips', 'Skip rate divides events with skipped = true by events with a recorded skip flag. Events without a flag are excluded from both, and every rate shows its denominator (n). End reasons are reported separately.'],
  ['New and familiar', 'New means the first appearance anywhere in the imported data, not the first time you ever heard it. Familiar listening is the share of time on titles first recorded on an earlier calendar day; first-seen dates always come from the full import.'],
  ['Yearly overlap', 'Jaccard overlap of consecutive years\' top 20 artists or shows by time: names in both lists divided by names in either. Partial years are flagged.'],
  ['Returns and correlation', 'Longest returns are gaps of 180 days or more between consecutive positive-length records of a title, within the filtered records. The daily scatter reports Pearson r and its n across active days; it describes association, not cause.'],
  ['Not in the export', 'Genre, tempo, mood, and audio features are absent from Spotify\'s history files. The app does not fetch or infer them.'],
];

export function DataView({ meta, timezone, busy, onPick, onDemo, onClear }: {
  meta: DatasetMeta; timezone: string; busy: boolean; onPick: (picked: PickedFiles) => void; onDemo: () => void; onClear: () => void;
}) {
  const demo = meta.source === 'demo';
  return (
    <div className="stack">
      <section className="card" aria-labelledby="loaded-title">
        <header className="card-head">
          <div className="card-titles">
            <h2 id="loaded-title">Loaded data</h2>
            <p className="card-sub">{demo ? 'Synthetic demo: fictional records generated in this browser' : 'Imported Spotify Extended Streaming History'}</p>
          </div>
          {demo && <span className="demo-badge">Synthetic</span>}
        </header>
        <dl className="facts facts-grid">
          <Fact label="Events kept" value={fmtInt(meta.events)} />
          <Fact label="Source records read" value={fmtInt(meta.rawRows)} />
          <Fact label="Exact duplicates removed" value={fmtInt(meta.duplicates)} />
          <Fact label="Invalid records skipped" value={fmtInt(meta.invalidRows)} />
          <Fact label="First event ended" value={fmtDateTime(meta.first, timezone)} />
          <Fact label="Last event ended" value={fmtDateTime(meta.last, timezone)} />
          <Fact label="Listening time" value={fmtMsHours(meta.totalMs)} />
          <Fact label="Last updated" value={fmtDateTime(meta.importedAt, timezone)} />
        </dl>
        {meta.warnings.length > 0 && (
          <ul className="warnings" aria-label="Import notes">{meta.warnings.map(w => <li key={w}>{w}</li>)}</ul>
        )}
        <details className="files">
          <summary>{plural(meta.files.length, 'source file')}</summary>
          <ul>{meta.files.map(f => <li key={f}><code>{f}</code></li>)}</ul>
        </details>
      </section>

      <div className="grid-2">
        <section className="card" aria-labelledby="add-title">
          <header className="card-head"><div className="card-titles"><h2 id="add-title">Add files</h2>
            <p className="card-sub">{demo ? 'Import your real history to replace the demo' : 'Add more JSON files, such as video history or a newer export'}</p></div></header>
          <ImportPanel onPick={onPick} disabled={busy} compact replacing={demo} />
        </section>

        <section className="card manage" aria-labelledby="manage-title">
          <header className="card-head"><div className="card-titles"><h2 id="manage-title">Browser storage</h2>
            <p className="card-sub">What this browser keeps, and how to remove it</p></div></header>
          <ul className="plain-list">
            <li>Files are parsed by a background worker in this browser and never uploaded.</li>
            <li>IP addresses, usernames, and user agents are discarded during import.</li>
            <li>The sanitized history stays in this browser&apos;s IndexedDB until you delete it here.</li>
            <li>When run locally after <code>npm run import</code>, the prepared <code>.local-data/history.json</code> loads on first launch. Deleting the browser copy leaves that file and your Spotify download untouched.</li>
          </ul>
          <div className="manage-actions">
            <button type="button" className="btn" onClick={onDemo} disabled={busy}>
              <FlaskConical size={15} aria-hidden="true" /> {demo ? 'Reload synthetic demo' : 'Load synthetic demo'}
            </button>
            <button type="button" className="btn btn-danger" onClick={onClear} disabled={busy}>
              <Trash2 size={15} aria-hidden="true" /> Delete browser copy
            </button>
          </div>
        </section>
      </div>

      <section className="card methods" aria-labelledby="methods-title">
        <header className="card-head"><div className="card-titles"><h2 id="methods-title">How the numbers work</h2>
          <p className="card-sub">Definitions behind every view</p></div></header>
        <dl className="method-list">
          {METHODS.map(([term, text]) => (
            <div key={term}><dt>{term}</dt><dd>{text}</dd></div>
          ))}
        </dl>
        <div className="source-links">
          <a className="text-link" href={SPOTIFY_DATA_URL} target="_blank" rel="noopener noreferrer">
            Spotify: Understanding your data <ArrowUpRight size={14} aria-hidden="true" />
          </a>
          <a className="text-link" href={REPO_URL} target="_blank" rel="noopener noreferrer">
            <Github size={14} aria-hidden="true" /> Source code on GitHub <ArrowUpRight size={14} aria-hidden="true" />
          </a>
        </div>
      </section>
    </div>
  );
}
