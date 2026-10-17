import { FEATURE_KEYS, emptyFeatures, moodKey, paired, type AudioFeatures, type Metadata, type TrackMetadata } from './metadata';
import { pearson } from './analytics';
import type { HistoryEvent } from './types';

export interface MoodOptions { weight: 'time' | 'plays' | 'tracks'; period: 'month' | 'week' }
export const DEFAULT_MOOD: MoodOptions = { weight: 'time', period: 'month' };
export interface MoodRow extends HistoryEvent { date: string; day: number; hour: number; item: string }
export interface MoodBucket {
  label: string; start: string; end: string; plays: number; ms: number; tracks: number;
  coveredPlays: number; coveredMs: number; coveredTracks: number; coverage: number | null;
  values: AudioFeatures; featureCoverage: AudioFeatures; skipRate: number | null; skipKnown: number;
}
export interface MoodTrack { uri: string; title: string; artist: string; plays: number; ms: number; skipRate: number | null; skipKnown: number; metadata: TrackMetadata }
export interface MoodResult {
  provider: Metadata['provider']; modelVersion: string | null; retrievedAt: string; cached: number; pending: number;
  summary: MoodBucket; periods: MoodBucket[]; rolling: MoodBucket[]; years: MoodBucket[];
  heatmap: MoodBucket[]; distribution: { x: number; y: number; weight: number; tracks: number; plays: number; ms: number }[];
  energyBands: MoodBucket[]; familiarity: MoodBucket[]; comparison: MoodBucket[];
  tracks: MoodTrack[]; trackCount: number;
  correlations: { energySkip: number | null; skipTracks: number; energyRepeats: number | null; repeatTracks: number };
}
const DAY = 86400000;
const ratio = (a: number, b: number) => b > 0 ? a / b : null;
const iso = (n: number) => new Date(n).toISOString().slice(0, 10);
export function periodBounds(date: string, period: MoodOptions['period']) {
  const t = Date.parse(date + 'T00:00:00Z');
  if (period === 'week') {
    const start = t - ((new Date(t).getUTCDay() + 6) % 7) * DAY;
    return [iso(start), iso(start + 6 * DAY)] as const;
  }
  return [date.slice(0, 7) + '-01', iso(Date.UTC(Number(date.slice(0, 4)), Number(date.slice(5, 7)), 0))] as const;
}
class Accumulator {
  plays = 0; ms = 0; coveredPlays = 0; coveredMs = 0; skipKnown = 0; skipped = 0;
  all = new Set<string>(); covered = new Set<string>();
  sums = Object.fromEntries(FEATURE_KEYS.map(k => [k, 0])) as Record<typeof FEATURE_KEYS[number], number>;
  weights = { ...this.sums }; knownMs = { ...this.sums };
  constructor(readonly weight: MoodOptions['weight']) {}
  add(e: MoodRow, t: TrackMetadata | undefined, key: string) {
    const fresh = !this.all.has(key); this.all.add(key); this.plays++; this.ms += e.ms;
    if (paired(t)) { this.coveredPlays++; this.coveredMs += e.ms; this.covered.add(key); }
    if (e.skipped !== null) { this.skipKnown++; this.skipped += Number(e.skipped); }
    if (!t || t.status !== 'found') return;
    const w = this.weight === 'time' ? e.ms : this.weight === 'plays' ? 1 : Number(fresh);
    for (const k of FEATURE_KEYS) {
      const value = t.features[k];
      if (value === null || ((k === 'energy' || k === 'valence') && !paired(t))) continue;
      this.sums[k] += value * w; this.weights[k] += w; this.knownMs[k] += e.ms;
    }
  }
  result(label = '', start = '', end = ''): MoodBucket {
    const values = emptyFeatures(), featureCoverage = emptyFeatures();
    for (const k of FEATURE_KEYS) { values[k] = ratio(this.sums[k], this.weights[k]); featureCoverage[k] = ratio(this.knownMs[k], this.ms); }
    return { label, start, end, plays: this.plays, ms: this.ms, tracks: this.all.size, coveredPlays: this.coveredPlays, coveredMs: this.coveredMs,
      coveredTracks: this.covered.size, coverage: ratio(this.coveredMs, this.ms), values, featureCoverage, skipRate: ratio(this.skipped, this.skipKnown), skipKnown: this.skipKnown };
  }
}
export function analyzeMood(rows: MoodRow[], firstItem: Map<string, string>, metadata: Metadata, options: MoodOptions, start: string, end: string, pending = 0): MoodResult {
  if (!['time', 'plays', 'tracks'].includes(options.weight) || !['month', 'week'].includes(options.period)) throw new Error('Invalid mood chart options.');
  const music = rows.filter(e => e.kind === 'music'), byUri = new Map(metadata.tracks.map(t => [t.uri, t]));
  const make = () => new Accumulator(options.weight), total = make(), periods = new Map<string, Accumulator>(), years = new Map<string, Accumulator>();
  const heat = Array.from({ length: 168 }, make), bands = Array.from({ length: 5 }, make), familiarity = [make(), make()], comparison = [make(), make()];
  const ranks = new Map<string, MoodTrack>(), distribution = Array.from({ length: 100 }, (_, i) => ({ x: i % 10, y: Math.floor(i / 10), weight: 0, tracks: 0, plays: 0, ms: 0 }));
  const midpoint = iso(Math.floor((Date.parse(start + 'T00:00:00Z') + Date.parse(end + 'T00:00:00Z')) / 2 / DAY) * DAY);
  const get = (map: Map<string, Accumulator>, k: string) => { if (!map.has(k)) map.set(k, make()); return map.get(k)!; };
  for (const e of music) {
    const key = moodKey(e, metadata.provider === 'demo'), t = byUri.get(key);
    const month = periodBounds(e.date, options.period)[0];
    for (const a of [total, get(periods, month), get(years, e.date.slice(0, 4)), heat[e.day * 24 + e.hour], familiarity[firstItem.get(e.item)! < e.date ? 1 : 0], comparison[e.date <= midpoint ? 0 : 1]]) a.add(e, t, key);
    if (!paired(t)) continue;
    const matched = t!, energy = matched.features.energy!, valence = matched.features.valence!;
    bands[Math.min(4, Math.floor(energy * 5))].add(e, t, key);
    const cell = distribution[Math.min(9, Math.floor(energy * 10)) * 10 + Math.min(9, Math.floor(valence * 10))];
    const fresh = !ranks.has(key); cell.plays++; cell.ms += e.ms; if (fresh) cell.tracks++;
    cell.weight += options.weight === 'time' ? e.ms / 3600000 : options.weight === 'plays' ? 1 : Number(fresh);
    if (!ranks.has(key)) ranks.set(key, { uri: key, title: e.title, artist: e.artist, plays: 0, ms: 0, skipRate: 0, skipKnown: 0, metadata: matched });
    const r = ranks.get(key)!; r.plays++; r.ms += e.ms;
    if (e.skipped !== null) { r.skipKnown++; r.skipRate! += Number(e.skipped); }
  }
  // Empty calendar periods stay explicit gaps, including periods with music but no scores.
  let cursor = start;
  while (cursor && cursor <= end) {
    const [s, e] = periodBounds(cursor, options.period); get(periods, s); get(years, cursor.slice(0, 4));
    cursor = iso(Date.parse(e + 'T00:00:00Z') + DAY);
  }
  const sortedPeriods = [...periods.keys()].sort();
  const rolling = sortedPeriods.map(() => make()), index = new Map(sortedPeriods.map((s, i) => [s, i]));
  for (const e of music) {
    const i = index.get(periodBounds(e.date, options.period)[0])!, key = moodKey(e, metadata.provider === 'demo');
    for (let j = i; j < Math.min(i + 3, rolling.length); j++) rolling[j].add(e, byUri.get(key), key);
  }
  const tracks = [...ranks.values()].map(r => ({ ...r, skipRate: ratio(r.skipRate ?? 0, r.skipKnown) })).sort((a, b) => b.ms - a.ms || b.plays - a.plays || (a.uri < b.uri ? -1 : a.uri > b.uri ? 1 : 0));
  const skipPoints: [number, number][] = tracks.filter(t => t.skipKnown >= 5).map(t => [t.metadata.features.energy!, t.skipRate!]);
  const repeatPoints: [number, number][] = tracks.map(t => [t.metadata.features.energy!, Math.log1p(t.plays)]);
  return { provider: metadata.provider, modelVersion: metadata.modelVersion, retrievedAt: metadata.tracks.reduce((last, t) => t.fetchedAt > last ? t.fetchedAt : last, ''), cached: metadata.tracks.length, pending,
    summary: total.result('Selected music', start, end), periods: sortedPeriods.map(s => periods.get(s)!.result(s, s, periodBounds(s, options.period)[1])),
    rolling: rolling.map((a, i) => a.result(sortedPeriods[i], sortedPeriods[Math.max(0, i - 2)], periodBounds(sortedPeriods[i], options.period)[1])),
    years: [...years].sort(([a], [b]) => a.localeCompare(b)).map(([y, a]) => a.result(y, y + '-01-01', y + '-12-31')),
    heatmap: heat.map((a, i) => a.result(`${['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][Math.floor(i / 24)]} ${String(i % 24).padStart(2, '0')}:00`)), distribution,
    energyBands: bands.map((a, i) => a.result(`${(i / 5).toFixed(1)}–${((i + 1) / 5).toFixed(1)}`)),
    familiarity: familiarity.map((a, i) => a.result(i ? 'Returning tracks' : 'First recorded day')),
    comparison: [comparison[0].result('Earlier half', start, midpoint), comparison[1].result('Later half', iso(Date.parse(midpoint + 'T00:00:00Z') + DAY), end)],
    tracks: tracks.slice(0, 200), trackCount: tracks.length,
    correlations: { energySkip: pearson(skipPoints), skipTracks: skipPoints.length, energyRepeats: pearson(repeatPoints), repeatTracks: repeatPoints.length } };
}
