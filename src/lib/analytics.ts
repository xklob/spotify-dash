import type { Dataset, Filters, GroupPoint, HistoryEvent, HistoryPage, QueryResult, Ranking } from './types';

const DAY = 86400000;
const HOUR = 3600000;
const ratio = (a: number, b: number) => b ? a / b : null;
const itemKey = (e: HistoryEvent) => e.uri || JSON.stringify([e.kind, e.artist, e.title, e.album]);
const searchText = (s: string) => s.normalize('NFKD').replace(/\p{M}/gu, '').toLocaleLowerCase();
type Prepared = HistoryEvent & { date: string; time: string; month: string; year: string; hour: number; day: number; item: string; search: string };
type Bucket = { ms: number; plays: number; skipped: number; known: number; artists: Set<string>; items: Set<string>; newArtists: Set<string>; familiarMs: number; artistMs: Map<string, number> };
const bucket = (): Bucket => ({ ms: 0, plays: 0, skipped: 0, known: 0, artists: new Set(), items: new Set(), newArtists: new Set(), familiarMs: 0, artistMs: new Map() });
function get<K, V>(map: Map<K, V>, key: K, create: () => V): V {
  let value = map.get(key); if (value === undefined) { value = create(); map.set(key, value); } return value;
}
function increment(map: Map<string, number>, key: string, amount: number) { map.set(key, (map.get(key) || 0) + amount); }
function add(b: Bucket, e: Prepared, isNewArtist: boolean, familiar: boolean) {
  b.ms += e.ms; b.plays++; b.artists.add(e.artist); b.items.add(e.item);
  if (e.skipped !== null) { b.known++; if (e.skipped) b.skipped++; }
  if (isNewArtist) b.newArtists.add(e.artist);
  if (familiar) b.familiarMs += e.ms;
  increment(b.artistMs, e.artist, e.ms);
}
const asGroup = (name: string, b: Bucket): GroupPoint => ({ name, hours: b.ms / HOUR, plays: b.plays, skipRate: ratio(b.skipped, b.known), skipKnown: b.known });
const asPeriod = (period: string, b: Bucket) => ({ period, hours: b.ms / HOUR, plays: b.plays, artists: b.artists.size, newArtists: b.newArtists.size, skipRate: ratio(b.skipped, b.known), familiarShare: ratio(b.familiarMs, b.ms) });
const dateNumber = (d: string) => Date.parse(d + 'T00:00:00Z');
const isoDay = (ts: number) => new Date(ts).toISOString().slice(0, 10);

export function pearson(points: [number, number][]): number | null {
  if (points.length < 3) return null;
  const n = points.length, mx = points.reduce((s, p) => s + p[0], 0) / n, my = points.reduce((s, p) => s + p[1], 0) / n;
  let xx = 0, yy = 0, xy = 0;
  for (const [x, y] of points) { xx += (x - mx) ** 2; yy += (y - my) ** 2; xy += (x - mx) * (y - my); }
  return xx && yy ? Math.max(-1, Math.min(1, xy / Math.sqrt(xx * yy))) : null;
}

export class Analytics {
  readonly dataset: Dataset;
  private zone = '';
  private prepared: Prepared[] = [];
  private firstArtist = new Map<string, string>();
  private firstItem = new Map<string, string>();
  constructor(dataset: Dataset) { this.dataset = dataset; }

  private prepare(timezone: string) {
    if (this.zone === timezone) return;
    const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
    this.firstArtist = new Map(); this.firstItem = new Map();
    this.prepared = this.dataset.events.map(e => {
      const p: Record<string, string> = {};
      for (const part of fmt.formatToParts(e.ts)) if (part.type !== 'literal') p[part.type] = part.value;
      const date = `${p.year}-${p.month}-${p.day}`, item = itemKey(e);
      if (!this.firstArtist.has(e.artist)) this.firstArtist.set(e.artist, date);
      if (!this.firstItem.has(item)) this.firstItem.set(item, date);
      return { ...e, date, time: `${p.hour}:${p.minute}:${p.second}`, month: date.slice(0, 7), year: p.year,
        hour: Number(p.hour) % 24, day: (new Date(date + 'T12:00:00Z').getUTCDay() + 6) % 7,
        item, search: searchText(`${e.title}\n${e.artist}\n${e.album}`) };
    });
    this.zone = timezone;
  }

  private select(f: Filters): Prepared[] {
    this.prepare(f.timezone);
    const query = searchText(f.search.trim());
    return this.prepared.filter(e =>
      (!f.start || e.date >= f.start) && (!f.end || e.date <= f.end) &&
      (f.kind === 'all' || e.kind === f.kind) && e.ms >= f.minMs &&
      (!query || e.search.includes(query)) && (!f.artist || e.artist === f.artist) &&
      (!f.platform || e.platform === f.platform) && (!f.country || e.country === f.country) &&
      (f.shuffle === 'all' || e.shuffle === (f.shuffle === 'yes')));
  }

  history(f: Filters, page = 0, pageSize = 50, order: 'newest' | 'oldest' = 'newest'): HistoryPage {
    const rows = this.select(f); if (order === 'newest') rows.reverse();
    pageSize = Math.max(1, Math.min(500, Math.trunc(pageSize)));
    const pages = Math.ceil(rows.length / pageSize), current = Math.max(0, Math.min(Math.trunc(page), Math.max(0, pages - 1)));
    return { rows: rows.slice(current * pageSize, (current + 1) * pageSize), total: rows.length, page: current, pages };
  }
  all(f: Filters) { return this.select(f); }

  query(f: Filters): QueryResult {
    const selected = this.select(f);
    const dataStart = this.prepared[0]?.date || '', dataEnd = this.prepared.at(-1)?.date || '';
    const start = f.start || dataStart, end = f.end || dataEnd;
    const months = new Map<string, Bucket>(), years = new Map<string, Bucket>(), days = new Map<string, Bucket>();
    const groups = { platforms: new Map<string, Bucket>(), countries: new Map<string, Bucket>(), kinds: new Map<string, Bucket>(), reasons: new Map<string, Bucket>(), shuffle: new Map<string, Bucket>() };
    const hourBuckets = Array.from({ length: 24 }, bucket), weekdayBuckets = Array.from({ length: 7 }, bucket);
    const heatmap = Array.from({ length: 168 }, (_, i) => ({ day: Math.floor(i / 24), hour: i % 24, hours: 0, plays: 0 }));
    const artists = new Map<string, Ranking>(), tracks = new Map<string, Ranking>(), albums = new Map<string, Ranking>();
    const total = bucket(); let qualifiedPlays = 0, shuffleYes = 0, shuffleKnown = 0;
    const obsessions = new Map<string, { name: string; artist: string; month: string; plays: number; hours: number; uri: string }>();
    const previous = new Map<string, Prepared>();
    const gaps: QueryResult['rediscoveries'] = [];
    function rank(map: Map<string, Ranking>, key: string, name: string, e: Prepared) {
      const r = get(map, key, () => ({ key, name, artist: e.artist, album: e.album, uri: e.uri, plays: 0, ms: 0, skipRate: null, skipKnown: 0, first: e.date, last: e.date }));
      r.plays++; r.ms += e.ms; r.last = e.date;
      // Store a count until finalization to avoid rounding/weighting rates.
      if (e.skipped !== null) { r.skipKnown++; r.skipRate = (r.skipRate || 0) + Number(e.skipped); }
    }
    for (const e of selected) {
      const first = this.firstArtist.get(e.artist)!, familiar = this.firstItem.get(e.item)! < e.date;
      const discoveredInRange = first >= start && first <= end;
      add(total, e, discoveredInRange, familiar);
      add(get(months, e.month, bucket), e, discoveredInRange && first.slice(0, 7) === e.month, familiar);
      add(get(years, e.year, bucket), e, discoveredInRange && first.slice(0, 4) === e.year, familiar);
      add(get(days, e.date, bucket), e, first === e.date, familiar);
      add(hourBuckets[e.hour], e, false, familiar); add(weekdayBuckets[e.day], e, false, familiar);
      for (const [group, label] of [[groups.platforms, e.platform], [groups.countries, e.country], [groups.kinds, e.kind], [groups.reasons, e.reasonEnd], [groups.shuffle, e.shuffle === null ? 'Unknown' : e.shuffle ? 'Shuffle on' : 'Shuffle off']] as const) add(get(group, label, bucket), e, false, familiar);
      const heat = heatmap[e.day * 24 + e.hour]; heat.hours += e.ms / HOUR; heat.plays++;
      if (e.ms >= 30000) qualifiedPlays++;
      if (e.shuffle !== null) { shuffleKnown++; if (e.shuffle) shuffleYes++; }
      rank(artists, e.artist, e.artist, e);
      rank(tracks, e.item, e.title, e);
      if (e.album) rank(albums, JSON.stringify([e.artist, e.album]), e.album, e);
      const obsession = get(obsessions, JSON.stringify([e.item, e.month]), () => ({ name: e.title, artist: e.artist, month: e.month, plays: 0, hours: 0, uri: e.uri }));
      obsession.plays++; obsession.hours += e.ms / HOUR;
      const prev = previous.get(e.item);
      if (prev && e.ms > 0 && prev.ms > 0) {
        const gapDays = Math.floor((dateNumber(e.date) - dateNumber(prev.date)) / DAY);
        if (gapDays >= 180) gaps.push({ name: e.title, artist: e.artist, uri: e.uri, gapDays, before: prev.date, after: e.date });
      }
      if (e.ms > 0) previous.set(e.item, e);
    }
    function ranks(map: Map<string, Ranking>) {
      return [...map.values()].map(r => ({ ...r, skipRate: r.skipKnown ? (r.skipRate || 0) / r.skipKnown : null }))
        .sort((a, b) => b.ms - a.ms || b.plays - a.plays || a.name.localeCompare(b.name));
    }
    const artistRanks = ranks(artists), trackRanks = ranks(tracks), albumRanks = ranks(albums);
    const daily: QueryResult['daily'] = [];
    const calendarCounts = [0, 0, 0, 0, 0, 0, 0];
    let longestStreak = 0, streak = 0, activeDays = 0;
    let topDay: QueryResult['summary']['topDay'] = null;
    const from = dateNumber(start), to = dateNumber(end);
    if (Number.isFinite(from) && Number.isFinite(to) && to - from > DAY * 365 * 100) throw new Error('Choose a date range shorter than 100 years.');
    for (let stamp = from; stamp <= to; stamp += DAY) {
      const date = isoDay(stamp), b = days.get(date), ms = b?.ms || 0;
      calendarCounts[(new Date(stamp).getUTCDay() + 6) % 7]++;
      const month = date.slice(0, 7); get(months, month, bucket); get(years, date.slice(0, 4), bucket);
      daily.push({ date, hours: ms / HOUR, minutes: ms / 60000, plays: b?.plays || 0, tracks: b?.items.size || 0, artists: b?.artists.size || 0, uniqueShare: b ? ratio(b.items.size, b.plays) : null });
      if (ms > 0) { activeDays++; streak++; longestStreak = Math.max(streak, longestStreak); } else streak = 0;
      if (ms > 0 && (!topDay || ms / HOUR > topDay.hours)) topDay = { date, hours: ms / HOUR };
    }
    const monthly = [...months].sort(([a], [b]) => a.localeCompare(b)).map(([m, b]) => asPeriod(m, b));
    let prior: Set<string> | null = null;
    const yearly = [...years].sort(([a], [b]) => a.localeCompare(b)).map(([year, b]) => {
      const top = [...b.artistMs].sort((a, c) => c[1] - a[1]).slice(0, 20);
      const current = new Set(top.map(([name]) => name));
      const intersection = prior ? [...current].filter(a => prior!.has(a)).length : 0;
      const union = prior ? new Set([...prior, ...current]).size : 0;
      const overlap = prior?.size && current.size && union ? intersection / union : null;
      prior = current;
      return { ...asPeriod(year, b), topArtist: top[0]?.[0] || '', overlap,
        partial: `${year}-01-01` < start || `${year}-12-31` > end || `${year}-01-01` < dataStart || `${year}-12-31` > dataEnd };
    });
    const compositionKeys = artistRanks.slice(0, 5).map(r => r.name);
    // Avoid colliding with a real artist literally named "Other".
    let otherLabel = 'Other'; while (compositionKeys.includes(otherLabel)) otherLabel += ' artists';
    compositionKeys.push(otherLabel);
    const composition = monthly.map(p => {
      const b = months.get(p.period)!;
      const row: { period: string; [key: string]: string | number } = { period: p.period };
      let assigned = 0;
      for (const name of compositionKeys.slice(0, -1)) { row[name] = (b.artistMs.get(name) || 0) / HOUR; assigned += Number(row[name]); }
      row[otherLabel] = Math.max(0, b.ms / HOUR - assigned);
      return row;
    });
    const groupRows = (g: Map<string, Bucket>) => [...g].map(([name, b]) => asGroup(name, b)).sort((a, b) => b.hours - a.hours);
    const correlationDays = daily.filter(d => d.minutes > 0);
    return {
      summary: { events: total.plays, qualifiedPlays, ms: total.ms, tracks: total.items.size, artists: total.artists.size,
        activeDays, calendarDays: daily.length, skipRate: ratio(total.skipped, total.known), skipKnown: total.known,
        shuffleRate: ratio(shuffleYes, shuffleKnown), shuffleKnown, averageMs: ratio(total.ms, total.plays),
        newArtists: total.newArtists.size, longestStreak, topDay, first: selected[0]?.date || '', last: selected.at(-1)?.date || '',
        topArtistShare: ratio(artistRanks.slice(0, 10).reduce((s, r) => s + r.ms, 0), total.ms) },
      monthly, yearly, daily, heatmap,
      hours: hourBuckets.map((b, hour) => asGroup(String(hour).padStart(2, '0') + ':00', b)),
      weekdays: weekdayBuckets.map((b, day) => ({ ...asGroup(['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][day], b), averageMinutes: calendarCounts[day] ? b.ms / 60000 / calendarCounts[day] : 0, calendarDays: calendarCounts[day] })),
      artists: artistRanks, tracks: trackRanks, albums: albumRanks,
      platforms: groupRows(groups.platforms), countries: groupRows(groups.countries), kinds: groupRows(groups.kinds), reasons: groupRows(groups.reasons), shuffleComparison: groupRows(groups.shuffle),
      composition, compositionKeys,
      rediscoveries: gaps.sort((a, b) => b.gapDays - a.gapDays).slice(0, 30),
      obsessions: [...obsessions.values()].filter(o => o.plays > 1).sort((a, b) => b.plays - a.plays || b.hours - a.hours).slice(0, 30),
      correlation: { r: pearson(correlationDays.map(d => [d.minutes, d.tracks])), n: correlationDays.length,
        label: 'Daily listening time vs. distinct titles', explanation: 'Pearson correlation across days with recorded listening. More listening creates more opportunities to hear distinct titles; this association is descriptive, not causal.' },
      options: { years: [...new Set(this.prepared.map(e => e.year))].sort(), platforms: [...new Set(this.prepared.map(e => e.platform))].sort(), countries: [...new Set(this.prepared.map(e => e.country))].sort(), artists: [...new Set(this.prepared.map(e => e.artist))].sort() },
      scope: { start, end, timezone: f.timezone },
    };
  }
}
