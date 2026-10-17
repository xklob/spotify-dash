import { useEffect, useId, useMemo, useState, type ChangeEvent, type KeyboardEvent } from 'react';
import { RotateCcw, Search, SlidersHorizontal, X } from 'lucide-react';
import type { Filters, Kind } from '../lib/types';
import { addDays, countryName, fmtDate } from './format';

export interface FilterOptions { years: string[]; platforms: string[]; countries: string[]; artists: string[] }
export interface DataBounds { first: string; last: string }

const KIND_OPTIONS: { value: Kind | 'all'; label: string }[] = [
  { value: 'all', label: 'All types' }, { value: 'music', label: 'Music' }, { value: 'podcast', label: 'Podcasts' },
  { value: 'audiobook', label: 'Audiobooks' }, { value: 'unknown', label: 'Unidentified' },
];
const MIN_OPTIONS = [
  { value: 0, label: 'Any length' }, { value: 10_000, label: '10 s or more' }, { value: 30_000, label: '30 s or more' },
  { value: 60_000, label: '1 min or more' }, { value: 120_000, label: '2 min or more' }, { value: 300_000, label: '5 min or more' },
];
const SUGGESTED_ZONES = ['America/New_York', 'America/Chicago', 'America/Denver', 'America/Phoenix', 'America/Los_Angeles',
  'America/Anchorage', 'Pacific/Honolulu', 'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Asia/Kolkata', 'Asia/Tokyo', 'Australia/Sydney', 'UTC'];
const browserZone = (() => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch { return ''; } })();
let zoneCache: string[] | null = null;
function allZones(): string[] {
  if (!zoneCache) {
    try { zoneCache = Intl.supportedValuesOf('timeZone'); } catch { zoneCache = []; }
  }
  return zoneCache;
}

type Preset = 'all' | 'last30' | 'last90' | 'last365' | 'custom' | `year:${string}`;
const clampRange = (start: string, end: string, b: DataBounds) => ({
  start: b.first && start < b.first ? b.first : start,
  end: b.last && end > b.last ? b.last : end,
});
function presetRange(p: Preset, b: DataBounds): { start: string; end: string } | null {
  if (p === 'all') return { start: '', end: '' };
  if (!b.last) return null;
  if (p === 'last30') return clampRange(addDays(b.last, -29), b.last, b);
  if (p === 'last90') return clampRange(addDays(b.last, -89), b.last, b);
  if (p === 'last365') return clampRange(addDays(b.last, -364), b.last, b);
  if (p.startsWith('year:')) { const y = p.slice(5); return clampRange(`${y}-01-01`, `${y}-12-31`, b); }
  return null;
}
function detectPreset(f: Filters, b: DataBounds, years: string[]): Preset {
  if (!f.start && !f.end) return 'all';
  const candidates: Preset[] = ['last30', 'last90', 'last365', ...years.map(y => `year:${y}` as Preset)];
  for (const p of candidates) {
    const r = presetRange(p, b);
    if (r && r.start === f.start && r.end === f.end) return p;
  }
  return 'custom';
}

interface Chip { key: string; label: string; clear: Partial<Filters> }
export function activeChips(f: Filters, b: DataBounds, years: string[]): Chip[] {
  const chips: Chip[] = [];
  const preset = detectPreset(f, b, years);
  if (f.start || f.end) {
    const label = preset.startsWith('year:') ? preset.slice(5)
      : preset === 'last30' ? 'Last 30 days' : preset === 'last90' ? 'Last 90 days' : preset === 'last365' ? 'Last 365 days'
      : `${f.start ? fmtDate(f.start) : 'Start'} to ${f.end ? fmtDate(f.end) : 'end'}`;
    chips.push({ key: 'range', label, clear: { start: '', end: '' } });
  }
  if (f.search.trim()) chips.push({ key: 'search', label: `Search: "${f.search.trim()}"`, clear: { search: '' } });
  if (f.artist) chips.push({ key: 'artist', label: `Artist: ${f.artist}`, clear: { artist: '' } });
  if (f.kind !== 'all') chips.push({ key: 'kind', label: KIND_OPTIONS.find(k => k.value === f.kind)?.label ?? f.kind, clear: { kind: 'all' } });
  if (f.minMs > 0) chips.push({ key: 'min', label: MIN_OPTIONS.find(m => m.value === f.minMs)?.label ?? `${f.minMs / 1000} s or more`, clear: { minMs: 0 } });
  if (f.platform) chips.push({ key: 'platform', label: `Device: ${f.platform}`, clear: { platform: '' } });
  if (f.country) chips.push({ key: 'country', label: `Country: ${countryName(f.country)}`, clear: { country: '' } });
  if (f.shuffle !== 'all') chips.push({ key: 'shuffle', label: f.shuffle === 'yes' ? 'Shuffle on' : 'Shuffle off', clear: { shuffle: 'all' } });
  return chips;
}

function ArtistField({ value, artists, onApply }: { value: string; artists: string[]; onApply: (artist: string) => void }) {
  const [draft, setDraft] = useState(value);
  const [listOn, setListOn] = useState(false);
  const [miss, setMiss] = useState(false);
  const listId = useId();
  useEffect(() => { setDraft(value); setMiss(false); }, [value]);
  const index = useMemo(() => new Map(artists.map(a => [a.toLocaleLowerCase(), a])), [artists]);
  // The suggestion list can hold thousands of names, so it only mounts while the field is in use.
  const list = useMemo(() => (listOn ? <datalist id={listId}>{artists.map(a => <option key={a} value={a} />)}</datalist> : null), [listOn, artists, listId]);
  const commit = (text: string) => {
    const t = text.trim();
    if (!t) { setMiss(false); if (value) onApply(''); return; }
    const match = index.get(t.toLocaleLowerCase());
    if (match) { setMiss(false); setDraft(match); if (match !== value) onApply(match); } else setMiss(true);
  };
  const onChange = (e: ChangeEvent<HTMLInputElement>) => {
    setDraft(e.target.value);
    setMiss(false);
    // A pick from the suggestion list arrives as a replacement rather than typing.
    const type = (e.nativeEvent as InputEvent).inputType;
    if ((!type || type === 'insertReplacementText') && index.has(e.target.value.trim().toLocaleLowerCase())) commit(e.target.value);
  };
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') { e.preventDefault(); commit(draft); }
    if (e.key === 'Escape') { setDraft(value); setMiss(false); }
  };
  return (
    <label className="field field-artist">
      <span className="field-label">Artist / show</span>
      <span className="input-wrap">
        <input type="text" value={draft} list={listOn ? listId : undefined} placeholder="Any artist" autoComplete="off" spellCheck={false}
          aria-invalid={miss || undefined} onChange={onChange} onKeyDown={onKey}
          onFocus={() => setListOn(true)} onBlur={() => { commit(draft); }} />
        {value && <button type="button" className="input-clear" onClick={() => onApply('')} aria-label="Clear artist filter"><X size={13} aria-hidden="true" /></button>}
      </span>
      {miss && <span className="field-hint" role="status">No exact match. Pick a name from the list.</span>}
      {list}
    </label>
  );
}

export function FilterBar({ filters, searchDraft, onSearchDraft, onChange, onClearAll, options, bounds }: {
  filters: Filters; searchDraft: string; onSearchDraft: (value: string) => void;
  onChange: (patch: Partial<Filters>) => void; onClearAll: () => void; options: FilterOptions | null; bounds: DataBounds;
}) {
  const [open, setOpen] = useState(false);
  const years = useMemo(() => [...(options?.years ?? [])].sort().reverse(), [options]);
  const preset = detectPreset(filters, bounds, years);
  const chips = activeChips(filters, bounds, years);
  const zones = useMemo(() => {
    const suggested = [...new Set([browserZone, filters.timezone, ...SUGGESTED_ZONES].filter(Boolean))];
    return { suggested, rest: allZones().filter(z => !suggested.includes(z)) };
  }, [filters.timezone]);
  const panelId = useId();

  const setPreset = (p: Preset) => { const r = presetRange(p, bounds); if (r) onChange(r); };
  const setStart = (start: string) => onChange(filters.end && start && start > filters.end ? { start, end: start } : { start });
  const setEnd = (end: string) => onChange(filters.start && end && end < filters.start ? { start: end, end } : { end });

  return (
    <section className="filters" aria-label="Filters">
      <div className="filters-row filters-primary">
        <label className="field field-search">
          <span className="field-label">Search</span>
          <span className="input-wrap">
            <Search size={15} aria-hidden="true" className="input-icon" />
            <input type="search" value={searchDraft} onChange={e => onSearchDraft(e.target.value)} placeholder="Titles, artists, albums" autoComplete="off" spellCheck={false} />
          </span>
        </label>
        <label className="field">
          <span className="field-label">Range</span>
          <select value={preset} onChange={e => setPreset(e.target.value as Preset)}>
            <option value="all">All time</option>
            <optgroup label="Ending at the last imported event">
              <option value="last30">Last 30 days</option>
              <option value="last90">Last 90 days</option>
              <option value="last365">Last 365 days</option>
            </optgroup>
            {years.length > 0 && <optgroup label="Calendar year">{years.map(y => <option key={y} value={`year:${y}`}>{y}</option>)}</optgroup>}
            {preset === 'custom' && <option value="custom">Custom dates</option>}
          </select>
        </label>
        <label className="field field-date">
          <span className="field-label">From</span>
          <input type="date" value={filters.start} min={bounds.first || undefined} max={bounds.last || undefined} onChange={e => setStart(e.target.value)} />
        </label>
        <label className="field field-date">
          <span className="field-label">To</span>
          <input type="date" value={filters.end} min={bounds.first || undefined} max={bounds.last || undefined} onChange={e => setEnd(e.target.value)} />
        </label>
        <button type="button" className="btn filters-toggle" aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(v => !v)}>
          <SlidersHorizontal size={15} aria-hidden="true" /> More filters{chips.length ? <span className="count-pill">{chips.length}</span> : null}
        </button>
      </div>

      <div className={`filters-row filters-secondary${open ? ' is-open' : ''}`} id={panelId}>
        <label className="field">
          <span className="field-label">Type</span>
          <select value={filters.kind} onChange={e => onChange({ kind: e.target.value as Filters['kind'] })}>
            {KIND_OPTIONS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Minimum played</span>
          <select value={filters.minMs} onChange={e => onChange({ minMs: Number(e.target.value) })}>
            {MIN_OPTIONS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
            {!MIN_OPTIONS.some(m => m.value === filters.minMs) && <option value={filters.minMs}>{filters.minMs / 1000} s or more</option>}
          </select>
        </label>
        <ArtistField value={filters.artist} artists={options?.artists ?? []} onApply={artist => onChange({ artist })} />
        <label className="field">
          <span className="field-label">Device</span>
          <select value={filters.platform} onChange={e => onChange({ platform: e.target.value })}>
            <option value="">All devices</option>
            {(options?.platforms ?? []).map(p => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Country</span>
          <select value={filters.country} onChange={e => onChange({ country: e.target.value })}>
            <option value="">All countries</option>
            {(options?.countries ?? []).map(c => <option key={c} value={c}>{countryName(c)}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Shuffle</span>
          <select value={filters.shuffle} onChange={e => onChange({ shuffle: e.target.value as Filters['shuffle'] })}>
            <option value="all">On or off</option>
            <option value="yes">Shuffle on</option>
            <option value="no">Shuffle off</option>
          </select>
        </label>
        <label className="field field-zone">
          <span className="field-label">Timezone</span>
          <select value={filters.timezone} onChange={e => onChange({ timezone: e.target.value })}>
            <optgroup label="Suggested">
              {zones.suggested.map(z => <option key={z} value={z}>{z.replace(/_/g, ' ')}{z === browserZone ? ' (this device)' : ''}</option>)}
            </optgroup>
            {zones.rest.length > 0 && <optgroup label="All timezones">{zones.rest.map(z => <option key={z} value={z}>{z.replace(/_/g, ' ')}</option>)}</optgroup>}
          </select>
        </label>
      </div>

      {chips.length > 0 && (
        <div className="chips" aria-label="Active filters">
          {chips.map(c => (
            <span className="chip" key={c.key}>
              <span>{c.label}</span>
              <button type="button" onClick={() => onChange(c.clear)} aria-label={`Remove filter ${c.label}`}><X size={12} aria-hidden="true" /></button>
            </span>
          ))}
          <button type="button" className="ghost-btn" onClick={onClearAll}><RotateCcw size={13} aria-hidden="true" /> Clear all</button>
        </div>
      )}
    </section>
  );
}
