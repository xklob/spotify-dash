import { useMemo } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { Filters, PeriodPoint, QueryResult } from '../lib/types';
import { ChartCard, DataTable, TipBox, tipDatum, type TipProps } from './ChartCard';
import {
  daysInMonth, fmtClock, fmtCompact, fmtDate, fmtDateLong, fmtDayMonth, fmtHours, fmtHoursValue, fmtInt, fmtMonth, fmtMonthTick,
  fmtMsHours, fmtPct, HOUR_MS, parseYmd, plural,
} from './format';
import { useReducedMotion, useSticky } from './hooks';
import type { TabId } from './Shell';
import { ACCENT, axisLine, GRID, lineCursor, OTHER, SERIES, SURFACE, tick } from './theme';
import { Fact, RankList, Segmented, Stat } from './ui';

/** Year starts for long spans, quarters for medium spans, every month for short ones. */
export function monthTicks(periods: string[]): string[] {
  if (periods.length <= 14) return periods;
  if (periods.length <= 40) return periods.filter(p => /-(01|04|07|10)$/.test(p));
  const januaries = periods.filter(p => p.endsWith('-01'));
  const step = Math.max(1, Math.ceil(januaries.length / 12));
  return januaries.filter((_, i) => i % step === 0);
}

/** First/last months are partial when the selected range starts after the 1st or ends before month end. */
export function partialMonths(scope: QueryResult['scope']) {
  const s = parseYmd(scope.start), e = parseYmd(scope.end);
  const first = s && s[2] !== 1 ? scope.start.slice(0, 7) : null;
  const last = e && e[2] !== daysInMonth(e[0], e[1]) ? scope.end.slice(0, 7) : null;
  const notes: Record<string, string> = {};
  if (first) notes[first] = `Partial month: from ${fmtDayMonth(scope.start)}`;
  if (last) notes[last] = first === last ? `Partial month: ${fmtDayMonth(scope.start)} to ${fmtDayMonth(scope.end)}` : `Partial month: through ${fmtDayMonth(scope.end)}`;
  const listed = [...new Set([first, last].filter((m): m is string => !!m))].map(m => fmtMonth(m));
  const caption = listed.length ? `${listed.join(' and ')} ${listed.length > 1 ? 'are partial months' : 'is a partial month'} in this range.` : '';
  return { notes, caption };
}

type Metric = 'hours' | 'plays';
type Mix = 'hours' | 'share';

export function OverviewView({ result, filters, onArtist, onTitle, onTab }: {
  result: QueryResult; filters: Filters; onArtist: (artist: string) => void; onTitle: (title: string, artist: string) => void; onTab: (tab: TabId) => void;
}) {
  const [metric, setMetric] = useSticky<Metric>('overview.metric', 'hours');
  const [mix, setMix] = useSticky<Mix>('overview.mix', 'hours');
  const reduced = useReducedMotion();
  const s = result.summary;
  const hours = s.ms / HOUR_MS;
  const partial = useMemo(() => partialMonths(result.scope), [result.scope]);
  const ticks = useMemo(() => monthTicks(result.monthly.map(m => m.period)), [result.monthly]);
  const animate = !reduced && result.monthly.length < 400;

  const peakMonth = useMemo(() => result.monthly.reduce<PeriodPoint | null>((best, m) => (!best || m.hours > best.hours ? m : best), null), [result.monthly]);
  // The chart note follows the selected measure; Highlights keep the hours-based busiest month.
  const chartPeak = useMemo(() => result.monthly.reduce<PeriodPoint | null>((best, m) => (!best || m[metric] > best[metric] ? m : best), null), [result.monthly, metric]);
  // A one-month range has no line or area to draw, so single points get visible dots.
  const singleMonth = result.monthly.length === 1;
  const peakYear = useMemo(() => result.yearly.reduce<QueryResult['yearly'][number] | null>((best, y) => (!best || y.hours > best.hours ? y : best), null), [result.yearly]);

  const keys = result.compositionKeys;
  const otherKey = keys[keys.length - 1];
  const artistKeys = keys.slice(0, -1);
  const colorOf = (key: string) => (key === otherKey ? OTHER : SERIES[artistKeys.indexOf(key)] ?? OTHER);

  const metricLabel = metric === 'hours' ? 'Hours' : 'Events';
  const fmtMetric = (v: number) => (metric === 'hours' ? fmtHours(v) : `${fmtInt(v)} events`);

  return (
    <div className="stack">
      <section className="kpis" aria-label="Headline totals">
        <Stat hero label="Listening time" value={fmtHoursValue(hours)} unit="hours"
          detail={`${fmtInt(hours / 24)} days of summed playback, ${fmtDate(s.first)} to ${fmtDate(s.last)}`} />
        <Stat label="Plays of 30 s or more" value={fmtInt(s.qualifiedPlays)} detail={`${fmtPct(s.events ? s.qualifiedPlays / s.events : null)} of all events`} />
        <Stat label="Unique titles" value={fmtInt(s.tracks)} detail="Tracks, episodes, and chapters" />
        <Stat label="Artists / shows" value={fmtInt(s.artists)} detail="Distinct creator names" />
      </section>

      <dl className="facts card" aria-label="Context">
        <Fact label="All events" value={fmtInt(s.events)} detail="Every record, any length" />
        <Fact label="Active days" value={`${fmtInt(s.activeDays)} of ${fmtInt(s.calendarDays)}`} detail={fmtPct(s.calendarDays ? s.activeDays / s.calendarDays : null)} />
        <Fact label="Skip flag rate" value={fmtPct(s.skipRate)} detail={`of ${fmtInt(s.skipKnown)} with a recorded flag`} />
        <Fact label="Shuffle on" value={fmtPct(s.shuffleRate)} detail={`of ${fmtInt(s.shuffleKnown)} with a recorded flag`} />
        <Fact label="Average event" value={fmtClock(s.averageMs)} detail="played per record" />
      </dl>

      <ChartCard
        title="Listening by month"
        subtitle={metric === 'hours' ? 'Recorded playback hours per calendar month' : 'Recorded events per calendar month, any length'}
        controls={<Segmented label="Monthly measure" value={metric} onChange={setMetric} options={[{ value: 'hours', label: 'Hours' }, { value: 'plays', label: 'Events' }]} />}
        summary={chartPeak ? `Monthly ${metricLabel.toLowerCase()} across ${result.monthly.length} months. The busiest month by ${metricLabel.toLowerCase()} was ${fmtMonth(chartPeak.period, true)} with ${fmtMetric(chartPeak[metric])}.` : undefined}
        caption={[partial.caption, 'Each event is counted in the month its playback ended.'].filter(Boolean).join(' ')}
        table={() => (
          <DataTable caption="Listening by month" rows={result.monthly} rowKey={r => r.period} columns={[
            { key: 'm', label: 'Month', render: r => <>{fmtMonth(r.period)}{partial.notes[r.period] && <span className="tag">partial</span>}</> },
            { key: 'h', label: 'Hours', numeric: true, render: r => fmtHoursValue(r.hours) },
            { key: 'e', label: 'Events', numeric: true, render: r => fmtInt(r.plays) },
            { key: 'a', label: 'Artists / shows', numeric: true, render: r => fmtInt(r.artists) },
          ]} />
        )}>
        <div className="chart-box chart-lg">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={result.monthly} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="fill-monthly" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={ACCENT} stopOpacity={0.26} />
                  <stop offset="100%" stopColor={ACCENT} stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={GRID} vertical={false} />
              <XAxis dataKey="period" ticks={ticks} tickFormatter={fmtMonthTick} tick={tick} axisLine={axisLine} tickLine={false} minTickGap={18} />
              <YAxis tick={tick} axisLine={false} tickLine={false} width={44} tickFormatter={v => fmtCompact(Number(v))} />
              <Tooltip cursor={lineCursor} content={(p: TipProps) => {
                const d = tipDatum<PeriodPoint>(p);
                return d ? (
                  <TipBox title={fmtMonth(d.period, true)} note={partial.notes[d.period]} rows={[
                    { label: metric === 'hours' ? 'listened' : 'events', value: metric === 'hours' ? fmtHours(d.hours) : fmtInt(d.plays), color: ACCENT },
                    { label: metric === 'hours' ? 'events' : 'listened', value: metric === 'hours' ? fmtInt(d.plays) : fmtHours(d.hours) },
                    { label: 'artists / shows', value: fmtInt(d.artists) },
                  ]} />
                ) : null;
              }} />
              <Area type="monotone" dataKey={metric} name={metricLabel} stroke={ACCENT} strokeWidth={2} fill="url(#fill-monthly)"
                isAnimationActive={animate} animationDuration={450}
                dot={singleMonth ? { r: 5, fill: ACCENT, stroke: SURFACE, strokeWidth: 2 } : false}
                activeDot={{ r: 4, fill: ACCENT, stroke: SURFACE, strokeWidth: 2 }} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
        {chartPeak && (
          <p className="chart-note">Peak month by {metricLabel.toLowerCase()}: <strong>{fmtMonth(chartPeak.period, true)}</strong>, {fmtMetric(chartPeak[metric])}</p>
        )}
      </ChartCard>

      {filters.artist ? (
        <section className="card note-card">
          <h2>Artist mix</h2>
          <p>The mix compares the five most-played artists with everyone else. With the artist filter on, all of this listening belongs to <strong>{filters.artist}</strong>.</p>
          <button type="button" className="btn btn-sm" onClick={() => onArtist('')}>Show all artists</button>
        </section>
      ) : artistKeys.length > 0 && (
        <ChartCard
          title="Artist mix by month"
          subtitle={mix === 'hours' ? 'Hours from the five most-played artists in this range, plus everyone else' : 'Share of each month from the five most-played artists'}
          controls={<Segmented label="Artist mix scale" value={mix} onChange={setMix} options={[{ value: 'hours', label: 'Hours' }, { value: 'share', label: 'Share' }]} />}
          summary={`Stacked monthly listening for ${artistKeys.join(', ')}, and ${otherKey}.`}
          caption="Colors follow rank within the current filters. Select a name to filter every view to that artist."
          table={() => (
            <DataTable caption="Artist mix by month" rows={result.composition} rowKey={r => String(r.period)} columns={[
              { key: 'm', label: 'Month', render: r => fmtMonth(String(r.period)) },
              ...keys.map(k => ({ key: k, label: k, numeric: true, render: (r: QueryResult['composition'][number]) => fmtHoursValue(Number(r[k] ?? 0)) })),
            ]} />
          )}>
          <ul className="legend" aria-label="Artists in the mix">
            {keys.map(k => (
              <li key={k}>
                <span className="swatch" style={{ background: colorOf(k) }} aria-hidden="true" />
                {k === otherKey ? <span>{k}</span> : <button type="button" className="link-btn" onClick={() => onArtist(k)} aria-label={`Filter every view to ${k}`}>{k}</button>}
              </li>
            ))}
          </ul>
          <div className="chart-box chart-md">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={result.composition} stackOffset={mix === 'share' ? 'expand' : 'none'} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="period" ticks={ticks} tickFormatter={fmtMonthTick} tick={tick} axisLine={axisLine} tickLine={false} minTickGap={18} />
                <YAxis tick={tick} axisLine={false} tickLine={false} width={44}
                  tickFormatter={v => (mix === 'share' ? `${Math.round(Number(v) * 100)}%` : fmtCompact(Number(v)))} />
                <Tooltip cursor={lineCursor} content={(p: TipProps) => {
                  const d = tipDatum<QueryResult['composition'][number]>(p);
                  if (!d) return null;
                  const total = keys.reduce((sum, k) => sum + Number(d[k] ?? 0), 0);
                  return (
                    <TipBox title={fmtMonth(String(d.period), true)} note={partial.notes[String(d.period)]} rows={[...keys].reverse().map(k => ({
                      color: colorOf(k), label: k,
                      value: mix === 'share' ? fmtPct(total ? Number(d[k] ?? 0) / total : null, 0) : fmtHours(Number(d[k] ?? 0)),
                    }))} />
                  );
                }} />
                {keys.map(k => (
                  <Area key={k} type="monotone" dataKey={k} stackId="mix" stroke={colorOf(k)} strokeWidth={1.5} fill={colorOf(k)} fillOpacity={k === otherKey ? 0.32 : 0.5}
                    isAnimationActive={animate} animationDuration={450} activeDot={false}
                    dot={singleMonth ? { r: 5, fill: colorOf(k), stroke: SURFACE, strokeWidth: 2 } : false} />
                ))}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>
      )}

      <div className="grid-2">
        <section className="card">
          <header className="card-head">
            <div className="card-titles"><h2>Top artists / shows</h2><p className="card-sub">By recorded listening time</p></div>
            <button type="button" className="ghost-btn" onClick={() => onTab('favorites')}>All rankings</button>
          </header>
          <RankList label="Top artists and shows" items={result.artists.slice(0, 10).map(r => ({
            key: r.key, primary: r.name, value: r.ms, valueLabel: fmtMsHours(r.ms), detail: `${fmtInt(r.plays)} events`,
            onSelect: () => onArtist(r.name), selectLabel: `Filter every view to ${r.name}`,
          }))} />
        </section>
        <section className="card">
          <header className="card-head">
            <div className="card-titles"><h2>Top titles</h2><p className="card-sub">By recorded listening time</p></div>
            <button type="button" className="ghost-btn" onClick={() => onTab('history')}>Playback records</button>
          </header>
          <RankList label="Top titles" items={result.tracks.slice(0, 10).map(r => ({
            key: r.key, primary: r.name, secondary: r.artist, value: r.ms, valueLabel: fmtMsHours(r.ms), detail: `${fmtInt(r.plays)} events`,
            onSelect: () => onTitle(r.name, r.artist), selectLabel: `Filter every view to ${r.name} by ${r.artist}`, uri: r.uri,
          }))} />
        </section>
      </div>

      <section className="card">
        <header className="card-head"><div className="card-titles"><h2>Highlights</h2><p className="card-sub">Computed from the records in this range</p></div></header>
        <dl className="highlights">
          <Fact label="Strongest day" value={s.topDay ? fmtHours(s.topDay.hours) : 'n/a'} detail={s.topDay ? fmtDateLong(s.topDay.date) : undefined} />
          <Fact label="Longest streak" value={plural(s.longestStreak, 'day')} detail="consecutive days with listening" />
          <Fact label="Busiest month" value={peakMonth ? fmtHours(peakMonth.hours) : 'n/a'} detail={peakMonth ? fmtMonth(peakMonth.period, true) : undefined} />
          <Fact label="Busiest year" value={peakYear ? fmtHours(peakYear.hours) : 'n/a'} detail={peakYear ? `${peakYear.period}${peakYear.partial ? ' (partial year)' : ''}` : undefined} />
          <Fact label="New artists / shows" value={fmtInt(s.newArtists)} detail="first recorded anywhere in the import during this range" />
          <Fact label="Top 10 artists' share" value={fmtPct(s.topArtistShare)} detail="of listening time" />
        </dl>
      </section>
    </div>
  );
}
