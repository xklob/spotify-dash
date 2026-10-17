import { useEffect, useRef, useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import * as client from '../lib/client';
import { DEFAULT_MOOD, type MoodBucket, type MoodOptions, type MoodResult } from '../lib/mood';
import type { Feature } from '../lib/metadata';
import type { Filters } from '../lib/types';
import { ChartCard, DataTable, TipBox, tipDatum, type TipProps } from './ChartCard';
import { errorMessage, fmtHours, fmtInt, fmtPct } from './format';
import { ACCENT, VIOLET, GRID, tick, HEAT } from './theme';
import { Notice, Segmented, Spinner, Stat } from './ui';

const score = (v: number | null | undefined, digits = 2) => v == null ? 'Unknown' : v.toFixed(digits);
const coverage = (r: MoodBucket) => `${fmtPct(r.coverage)} of listening time · ${fmtInt(r.coveredTracks)} / ${fmtInt(r.tracks)} tracks`;
function BucketTable({ rows, title }: { rows: MoodBucket[]; title: string }) {
  return <DataTable caption={title} rows={rows} rowKey={r => r.label} columns={[
    { key: 'label', label: 'Period / group', render: r => r.label },
    { key: 'energy', label: 'Energy', numeric: true, render: r => score(r.values.energy) },
    { key: 'valence', label: 'Valence', numeric: true, render: r => score(r.values.valence) },
    { key: 'coverage', label: 'Time coverage', numeric: true, render: r => fmtPct(r.coverage) },
    { key: 'tracks', label: 'Scored / all tracks', render: r => `${fmtInt(r.coveredTracks)} / ${fmtInt(r.tracks)}` },
    { key: 'events', label: 'Scored / all events', render: r => `${fmtInt(r.coveredPlays)} / ${fmtInt(r.plays)}` },
  ]} />;
}
function download(text: string, name: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function MoodView({ filters, version, onFilters, onTitle }: { filters: Filters; version: number; onFilters: (patch: Partial<Filters>) => void; onTitle: (title: string, artist: string) => void }) {
  const [options, setOptions] = useState<MoodOptions>(DEFAULT_MOOD);
  const [data, setData] = useState<MoodResult | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(false);
  const [revision, setRevision] = useState(0), [smoothing, setSmoothing] = useState(false), [heatMetric, setHeatMetric] = useState<'energy' | 'valence'>('energy');
  const [fetching, setFetching] = useState(false), [progress, setProgress] = useState(''), [working, setWorking] = useState(false);
  const [metadataOpen, setMetadataOpen] = useState(false);
  const [pickedHeat, setPickedHeat] = useState<number | null>(null), [pickedCell, setPickedCell] = useState<number | null>(null);
  const stop = useRef(false), file = useRef<HTMLInputElement>(null);
  useEffect(() => () => { stop.current = true; }, []);
  useEffect(() => {
    let cancelled = false; setLoading(true); setError('');
    client.mood(filters, options).then(r => { if (!cancelled) setData(r); }).catch(e => { if (!cancelled) setError(errorMessage(e)); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [filters, options, version, revision]);
  async function importFile(f: File) {
    setWorking(true); setError('');
    try { const n = await client.importMetadata(f); setProgress(`${fmtInt(n)} catalog records saved on this device.`); setRevision(v => v + 1); }
    catch (e) { setError(errorMessage(e)); } finally { setWorking(false); }
  }
  async function fetchNext() {
    stop.current = false; setFetching(true); setError(''); let count = 0;
    try {
      for (let i = 0; i < 10 && !stop.current; i++) {
        setProgress(`Fetching catalog metadata… ${fmtInt(count)} tracks checked this run.`);
        const r = await client.enrichBatch(); count += r.completed; setRevision(v => v + 1);
        if (!r.remaining) break;
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
      setProgress(`${fmtInt(count)} tracks checked. Completed batches are saved; you can continue later.`);
    } catch (e) { setError(errorMessage(e)); } finally { setFetching(false); }
  }
  async function exportFile() {
    try { download(await client.exportMetadata(), 'spotify-dash-metadata.json'); } catch (e) { setError(errorMessage(e)); }
  }
  const rows = (smoothing ? data?.rolling : data?.periods) ?? [];
  const showMetadata = metadataOpen || !data?.cached || fetching || working;
  const points = rows.map(r => ({ ...r, energy: r.values.energy, valence: r.values.valence }));
  const series = (label: string, values: typeof points, keys: ('energy' | 'valence')[]) => <div className="chart-box" style={{ height: 270 }} role="img" aria-label={label}>
    <ResponsiveContainer width="100%" height="100%"><LineChart data={values} margin={{ top: 12, right: 12, bottom: 4, left: 0 }}>
      <CartesianGrid stroke={GRID} vertical={false} /><XAxis dataKey="label" tick={tick} minTickGap={40} tickFormatter={v => String(v).slice(0, options.period === 'month' ? 7 : 10)} />
      <YAxis domain={[0, 1]} ticks={[0, .25, .5, .75, 1]} tick={tick} width={36} />
      <Tooltip content={(p: TipProps) => { const r = tipDatum<MoodBucket>(p); return r ? <TipBox title={r.label} rows={keys.map(k => ({ label: k, value: score(r.values[k]), color: k === 'energy' ? ACCENT : VIOLET }))} note={coverage(r)} /> : null; }} />
      {keys.map(k => <Line key={k} dataKey={k} stroke={k === 'energy' ? ACCENT : VIOLET} strokeWidth={2} strokeDasharray={k === 'valence' ? '5 3' : undefined} dot={values.length < 35 ? { r: 2 } : false} connectNulls={false} isAnimationActive={false} />)}
    </LineChart></ResponsiveContainer>
  </div>;
  return <div className="stack mood-view" aria-busy={loading}>
    <section className="card mood-tools">
      <div className="mood-manager-head"><strong>Catalog metadata</strong>{!!data?.cached && <button className="ghost-btn" aria-expanded={showMetadata} onClick={() => setMetadataOpen(v => !v)}>Manage metadata</button>}</div>
      {showMetadata && <p className="muted">Import a shared metadata file, or fetch by Spotify track ID. Fetching sends track IDs to ReccoBeats; your listening timestamps and files stay here.</p>}
        <input ref={file} type="file" accept=".json,application/json" className="sr-only" aria-label="Import metadata JSON" onChange={e => { const f = e.target.files?.[0]; if (f) void importFile(f); e.target.value = ''; }} />
      <div className="mood-actions" style={{ display: showMetadata ? 'flex' : 'none' }}>
        <button className="btn" disabled={fetching || working} onClick={() => file.current?.click()}>Import metadata</button>
        <button className="btn" disabled={!data?.cached || working} onClick={() => void exportFile()}>Export for Android</button>
        {data?.provider !== 'demo' && <button className="btn btn-primary" disabled={fetching || working || !data?.pending} onClick={() => void fetchNext()}>Fetch next {Math.min(400, data?.pending ?? 400)} tracks</button>}
        {fetching && <button className="btn" onClick={() => { stop.current = true; setProgress('Stopping after the current batch…'); }}>Stop after this batch</button>}
      </div>
      {progress && <p role="status" className="muted">{progress}</p>}
      {data && <small className="muted">{data.provider === 'demo' ? 'Fictional demo scores' : `ReccoBeats · ${fmtInt(data.cached)} cached IDs · ${fmtInt(data.pending)} unchecked IDs across your archive`}{data.retrievedAt && ` · latest retrieval ${data.retrievedAt.slice(0, 10)}`}</small>}
    </section>
    {error && <Notice tone="error" title="Metadata operation could not finish">{error}</Notice>}
    {!data ? <p role="status"><Spinner /> Calculating mood and energy…</p> : <>
      <div className="mood-controls">
        <Segmented label="Weight mood scores by" value={options.weight} onChange={weight => setOptions(o => ({ ...o, weight }))} options={[{ value: 'time', label: 'Listening time' }, { value: 'plays', label: 'Events' }, { value: 'tracks', label: 'Distinct tracks' }]} />
        <Segmented label="Mood period" value={options.period} onChange={period => setOptions(o => ({ ...o, period }))} options={[{ value: 'month', label: 'Monthly' }, { value: 'week', label: 'Weekly' }]} />
      </div>
      <p className="muted">Energy measures musical intensity; valence estimates how positive the music sounds. Both run from 0 to 1. These describe the recordings, not your emotional state.</p>
      <div className="grid-3">
        <Stat label="Musical energy" value={score(data.summary.values.energy)} detail="0: gentle · 1: intense" />
        <Stat label="Musical valence" value={score(data.summary.values.valence)} detail="0: darker · 1: more positive" />
        <Stat label="Time with mood scores" value={fmtPct(data.summary.coverage)} detail={`${fmtHours(data.summary.coveredMs / 3600000)} of ${fmtHours(data.summary.ms / 3600000)} · ${fmtInt(data.summary.coveredTracks)} / ${fmtInt(data.summary.tracks)} tracks`} />
      </div>
      {data.summary.coveredTracks === 0 && <Notice title="No mood scores for this selection">Import metadata or fetch catalog matches to populate these charts. Unmatched tracks remain unknown. Only music is eligible.</Notice>}
      <ChartCard title="Mood over time" subtitle={<><span style={{ color: ACCENT }}>● Energy</span> · <span style={{ color: VIOLET }}>┄ Valence</span> · {smoothing ? 'Trailing 3 periods, reweighted from events' : 'Each period separately'}</>}
        controls={<label className="mood-checkbox"><input type="checkbox" checked={smoothing} onChange={e => setSmoothing(e.target.checked)} /> Smooth 3 periods</label>}
        caption="Averages use available scores only. Gaps mean no weighted scores; changing catalog coverage can change the trend. Inspect coverage below before comparing periods."
        table={() => <BucketTable rows={rows} title="Mood trend values" />}>
        {series('Energy and valence over time, each from zero to one', points, ['energy', 'valence'])}
        <label className="field mood-drill"><span>Explore a period’s tracks</span><select value="" onChange={e => { const p = data.periods.find(p => p.label === e.target.value); if (p) onFilters({ start: p.start < filters.start ? filters.start : p.start, end: filters.end && p.end > filters.end ? filters.end : p.end }); }}><option value="">Choose a period…</option>{data.periods.filter(p => p.plays).map(p => <option key={p.label} value={p.label}>{p.label} · {fmtPct(p.coverage)} coverage</option>)}</select></label>
      </ChartCard>
      <div className="grid-2">
        <ChartCard title="Mood distribution" subtitle={`All scored tracks · weighted by ${options.weight === 'time' ? 'listening hours' : options.weight === 'plays' ? 'events' : 'distinct tracks'}`} caption="Horizontal: darker → more positive. Vertical: gentle → intense. Cells group scores into 0.1-wide bins; brighter cells carry more weight.">
          <div className="mood-grid-frame"><span className="mood-axis-label">Energy ↑</span><div className="mood-density" role="group" aria-label="Energy and valence distribution">{Array.from({ length: 100 }, (_, position) => {
            const i = (9 - Math.floor(position / 10)) * 10 + position % 10, cell = data.distribution[i], max = Math.max(...data.distribution.map(c => c.weight));
            const label = `Energy ${(cell.y / 10).toFixed(1)}–${((cell.y + 1) / 10).toFixed(1)}, valence ${(cell.x / 10).toFixed(1)}–${((cell.x + 1) / 10).toFixed(1)}: ${cell.tracks} tracks, ${fmtHours(cell.ms / 3600000)}`;
            return <button key={i} aria-label={label} title={label} onClick={() => setPickedCell(i)} style={{ background: cell.weight > 0 ? HEAT[Math.max(1, Math.ceil(Math.sqrt(cell.weight / max) * 6))] : HEAT[0] }} />;
          })}</div><span className="mood-axis-label">Valence →</span></div>
          <p className="chart-note" role="status">{pickedCell === null ? `0–1 on both axes · ${coverage(data.summary)}` : `${data.distribution[pickedCell].tracks} tracks · ${fmtHours(data.distribution[pickedCell].ms / 3600000)} · energy ${data.distribution[pickedCell].y / 10}–${((data.distribution[pickedCell].y + 1) / 10).toFixed(1)}, valence ${data.distribution[pickedCell].x / 10}–${((data.distribution[pickedCell].x + 1) / 10).toFixed(1)}`}</p>
        </ChartCard>
        <ChartCard title="Earlier and later" subtitle="The selected calendar range split in half" caption="Compare the dates and coverage before reading the difference as a shift in listening taste.">
          {data.comparison.map(p => <div className="mood-comparison" key={p.label}><strong>{p.label}</strong><span>{p.start > p.end ? 'Not enough days for a second half' : `${p.start} to ${p.end}`}</span><div>Energy <b>{score(p.values.energy)}</b> · Valence <b>{score(p.values.valence)}</b></div><small>{coverage(p)}</small></div>)}
          <p className="chart-note">Energy Δ {data.comparison.every(p => p.values.energy !== null) ? (data.comparison[1].values.energy! - data.comparison[0].values.energy!).toFixed(3) : 'Unknown'} · Valence Δ {data.comparison.every(p => p.values.valence !== null) ? (data.comparison[1].values.valence! - data.comparison[0].values.valence!).toFixed(3) : 'Unknown'}</p>
        </ChartCard>
      </div>
      <ChartCard title="Mood by weekday and hour" subtitle={`Playback end time · ${filters.timezone}`} controls={<Segmented label="Mood heatmap metric" value={heatMetric} onChange={setHeatMetric} options={[{ value: 'energy', label: 'Energy' }, { value: 'valence', label: 'Valence' }]} />} table={() => <BucketTable rows={data.heatmap} title="Weekday and hour mood" />} caption="Each playback is assigned to its end hour. Gray means unknown; color measures the average score, not listening volume.">
        <div className="mood-hour-scroll"><div className="mood-hour-grid"><span />{Array.from({ length: 24 }, (_, h) => <span key={h}>{h % 3 === 0 ? h : ''}</span>)}{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day, d) => <div className="mood-hour-row" key={day}><span>{day}</span>{data.heatmap.slice(d * 24, d * 24 + 24).map((p, h) => <button key={h} style={{ background: p.values[heatMetric] == null ? '#30302d' : HEAT[Math.max(1, Math.ceil(p.values[heatMetric]! * 6))] }} aria-label={`${p.label}: ${heatMetric} ${score(p.values[heatMetric])}, ${coverage(p)}`} title={`${p.label}: ${score(p.values[heatMetric])} · ${coverage(p)}`} onClick={() => setPickedHeat(d * 24 + h)} />)}</div>)}</div></div>
        <p role="status" className="chart-note">{pickedHeat === null ? 'Select a cell for its score and coverage. Scale 0–1.' : `${data.heatmap[pickedHeat].label}: ${heatMetric} ${score(data.heatmap[pickedHeat].values[heatMetric])} · ${coverage(data.heatmap[pickedHeat])}`}</p>
      </ChartCard>
      <div className="grid-2">
        <ChartCard title="Sound and texture" subtitle="Separate characteristics of the scored recordings" caption="BPM may reflect half-time or double-time estimates. Feature scores are provider estimates; model version is not supplied.">
          {(['tempo', 'danceability', 'acousticness', 'instrumentalness', 'speechiness', 'liveness', 'loudness'] as Feature[]).map(k => <div className="mood-feature" key={k}><span>{k === 'tempo' ? 'Tempo' : k.charAt(0).toUpperCase() + k.slice(1)}</span><strong>{score(data.summary.values[k], k === 'tempo' || k === 'loudness' ? 1 : 2)}{k === 'tempo' ? ' BPM' : k === 'loudness' ? ' dB' : ''}</strong><small>{fmtPct(data.summary.featureCoverage[k])} time coverage</small></div>)}
        </ChartCard>
        <ChartCard title="Energy and playback behavior" subtitle={`Catalog matches · ${fmtPct(data.summary.coverage)} time coverage`} caption="Skip correlation uses tracks with at least 5 known skip flags; repeats use log(1 + events) per track. These associations do not establish cause or personal mood.">
          <p>Energy ↔ skip rate: <b>{score(data.correlations.energySkip)}</b> · {fmtInt(data.correlations.skipTracks)} tracks</p>
          <p>Energy ↔ repeat events: <b>{score(data.correlations.energyRepeats)}</b> · {fmtInt(data.correlations.repeatTracks)} tracks</p>
          <DataTable caption="Skip rate by energy band" rows={data.energyBands} columns={[{ key: 'band', label: 'Energy', render: p => p.label }, { key: 'skip', label: 'Skip rate', render: p => fmtPct(p.skipRate) }, { key: 'n', label: 'Known flags', render: p => fmtInt(p.skipKnown) }]} />
        </ChartCard>
      </div>
      <ChartCard title="New and familiar tracks" subtitle="First recorded day versus later listening days" caption="First recorded means first in this archive, not the first time you ever heard a track. Both groups use the active filters."><BucketTable rows={data.familiarity} title="Mood of first-day and returning listening" /></ChartCard>
      <ChartCard title="Catalog coverage by year" subtitle="How much of each year supports these comparisons" table={() => <BucketTable rows={data.years} title="Annual catalog coverage" />}>
        <div className="mood-coverage">{data.years.map(y => <div key={y.label}><span>{y.label}</span><progress value={y.coverage ?? 0} max={1} aria-label={`${y.label} time coverage`} /><strong>{fmtPct(y.coverage)}</strong><small>{fmtInt(y.coveredTracks)} / {fmtInt(y.tracks)} tracks</small></div>)}</div>
      </ChartCard>
      <ChartCard title="Tracks behind the scores" subtitle={`${fmtInt(data.trackCount)} scored tracks in this selection · showing the top ${Math.min(200, data.trackCount)} by listening time`} caption="Matched only by exact Spotify ID. Different releases, remixes and remasters keep their own identifiers. Open a track to filter the dashboard; catalog names are retained for comparison.">
        <div className="table-scroll table-tall"><DataTable caption="Track metadata and contributions" rows={data.tracks} rowKey={r => r.uri} columns={[
          { key: 'title', label: 'Track / artist', render: r => <button className="link-btn" onClick={() => onTitle(r.title, r.artist)}>{r.title}<small className="muted" style={{ display: 'block' }}>{r.artist}</small></button> },
          { key: 'time', label: 'Listened', render: r => fmtHours(r.ms / 3600000) },
          { key: 'energy', label: 'Energy', render: r => score(r.metadata.features.energy) },
          { key: 'valence', label: 'Valence', render: r => score(r.metadata.features.valence) },
          { key: 'tempo', label: 'BPM', render: r => score(r.metadata.features.tempo, 1) },
          { key: 'catalog', label: 'Catalog details', render: r => <details><summary>{r.metadata.durationMs == null ? 'Metadata' : `${Math.round(r.metadata.durationMs / 1000)} sec`} · {r.metadata.isrc || 'No ISRC'}</summary><p>{r.metadata.title} · {r.metadata.artists.join(', ')}</p><p>Estimated key: {r.metadata.key == null || r.metadata.key < 0 ? 'Unknown' : `${['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'][r.metadata.key]} ${r.metadata.mode === 0 ? 'minor' : r.metadata.mode === 1 ? 'major' : '(mode unknown)'}`}</p><small>{r.metadata.uri}<br />Retrieved {r.metadata.fetchedAt.slice(0, 10)}</small></details> },
        ]} /></div>
      </ChartCard>
      <p className="chart-note">{data.provider === 'demo' ? 'All demo scores are synthetic.' : <>Scores: <a href="https://reccobeats.com/docs/documentation/terms-of-service" target="_blank" rel="noreferrer">ReccoBeats</a>, a third-party catalog. Model revision: {data.modelVersion ?? 'not published'}. These are not Spotify’s Audio Features API scores.</>} Energy and valence require both scores; other features use their own available values. Missing values never become zero. Distinct-track weighting counts each ID once per chart bucket.</p>
    </>}
  </div>;
}
