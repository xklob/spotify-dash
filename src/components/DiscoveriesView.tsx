import { useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, Dot, Line, LineChart, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, ZAxis } from 'recharts';
import type { DailyPoint, PeriodPoint, QueryResult } from '../lib/types';
import { ChartCard, DataTable, TipBox, tipDatum, type TipProps } from './ChartCard';
import { fmtCompact, fmtDate, fmtDateLong, fmtHours, fmtHoursValue, fmtInt, fmtMinutes, fmtMonth, fmtMonthTick, fmtPct, fmtSpan, plural } from './format';
import { useReducedMotion } from './hooks';
import { monthTicks, partialMonths } from './OverviewView';
import { ACCENT, axisLine, barCursor, BLUE, GRID, lineCursor, SURFACE, tick, VIOLET } from './theme';
import { Fact, SpotifyLink } from './ui';

const SCATTER_CAP = 3000;

export function DiscoveriesView({ result, onArtist, onTitle }: {
  result: QueryResult; onArtist: (artist: string) => void; onTitle: (title: string, artist: string) => void;
}) {
  const reduced = useReducedMotion();
  const ticks = useMemo(() => monthTicks(result.monthly.map(m => m.period)), [result.monthly]);
  const partial = useMemo(() => partialMonths(result.scope), [result.scope]);
  const animate = !reduced && result.monthly.length < 400;

  // Weighted by hours, the monthly shares reproduce the range-wide familiar share exactly.
  const familiar = useMemo(() => {
    let hours = 0, weighted = 0;
    for (const m of result.monthly) if (m.familiarShare !== null) { hours += m.hours; weighted += m.familiarShare * m.hours; }
    return hours ? weighted / hours : null;
  }, [result.monthly]);
  // A month with no listening on either side (or a one-month range) has no segment to draw; give it a dot.
  const familiarDot = (props: { cx?: number; cy?: number; index?: number }) => {
    const i = props.index ?? -1;
    const share = (j: number) => result.monthly[j]?.familiarShare ?? null;
    if (share(i) === null || share(i - 1) !== null || share(i + 1) !== null || props.cx == null || props.cy == null) return null;
    return <Dot key={`familiar-${i}`} className="recharts-line-dot" cx={props.cx} cy={props.cy} r={5} fill={BLUE} stroke={SURFACE} strokeWidth={2} />;
  };
  const peakNew = useMemo(() => result.monthly.reduce<PeriodPoint | null>((b, m) => (!b || m.newArtists > b.newArtists ? m : b), null), [result.monthly]);

  const active = useMemo(() => result.daily.filter(d => d.minutes > 0), [result.daily]);
  const scatter = useMemo(() => {
    if (active.length <= SCATTER_CAP) return active;
    const step = active.length / SCATTER_CAP;
    return Array.from({ length: SCATTER_CAP }, (_, i) => active[Math.floor(i * step)]);
  }, [active]);
  const r = result.correlation.r;
  const strength = r === null ? '' : Math.abs(r) >= 0.7 ? 'strong' : Math.abs(r) >= 0.4 ? 'moderate' : Math.abs(r) >= 0.2 ? 'weak' : 'negligible';

  return (
    <div className="stack">
      <section className="kpis kpis-4" aria-label="Discovery summary">
        <div className="stat">
          <div className="stat-label">New artists / shows</div>
          <div className="stat-value">{fmtInt(result.summary.newArtists)}</div>
          <div className="stat-detail">first recorded in this range</div>
        </div>
        <div className="stat">
          <div className="stat-label">Familiar listening</div>
          <div className="stat-value">{fmtPct(familiar)}</div>
          <div className="stat-detail">of time on titles heard on an earlier day</div>
        </div>
        <div className="stat">
          <div className="stat-label">Biggest discovery month</div>
          <div className="stat-value">{peakNew && peakNew.newArtists ? fmtInt(peakNew.newArtists) : 'n/a'}</div>
          <div className="stat-detail">{peakNew && peakNew.newArtists ? `new names in ${fmtMonth(peakNew.period, true)}` : 'no first appearances'}</div>
        </div>
        <div className="stat">
          <div className="stat-label">Longest return</div>
          <div className="stat-value">{result.rediscoveries[0] ? fmtSpan(result.rediscoveries[0].gapDays) : 'n/a'}</div>
          <div className="stat-detail">{result.rediscoveries[0] ? `${result.rediscoveries[0].name}, ${result.rediscoveries[0].artist}` : 'no gaps of 180 days or more'}</div>
        </div>
      </section>

      <div className="grid-2">
        <ChartCard
          title="New artists per month"
          subtitle="Artists and shows whose first record in the whole import falls in that month"
          summary={peakNew ? `The most first appearances came in ${fmtMonth(peakNew.period, true)} with ${fmtInt(peakNew.newArtists)}.` : undefined}
          caption={['First recorded exposure in your imported history, not necessarily the first time you ever heard them.', partial.caption].filter(Boolean).join(' ')}
          table={() => (
            <DataTable caption="New artists per month" rows={result.monthly} rowKey={m => m.period} columns={[
              { key: 'm', label: 'Month', render: m => fmtMonth(m.period) },
              { key: 'n', label: 'New artists / shows', numeric: true, render: m => fmtInt(m.newArtists) },
              { key: 'a', label: 'All artists / shows', numeric: true, render: m => fmtInt(m.artists) },
            ]} />
          )}>
          <div className="chart-box chart-sm">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={result.monthly} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap={1}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="period" ticks={ticks} tickFormatter={fmtMonthTick} tick={tick} axisLine={axisLine} tickLine={false} minTickGap={18} />
                <YAxis tick={tick} axisLine={false} tickLine={false} width={36} allowDecimals={false} tickFormatter={v => fmtCompact(Number(v))} />
                <Tooltip cursor={barCursor} content={(p: TipProps) => {
                  const d = tipDatum<PeriodPoint>(p);
                  return d ? (
                    <TipBox title={fmtMonth(d.period, true)} note={partial.notes[d.period]} rows={[
                      { label: 'new artists / shows', value: fmtInt(d.newArtists), color: VIOLET },
                      { label: 'artists / shows heard', value: fmtInt(d.artists) },
                    ]} />
                  ) : null;
                }} />
                <Bar dataKey="newArtists" fill={VIOLET} radius={[3, 3, 0, 0]} maxBarSize={14} isAnimationActive={animate} animationDuration={450} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>

        <ChartCard
          title="Familiar listening share"
          subtitle="Share of each month's listening time on titles first recorded on an earlier day"
          summary={`Across the range, ${fmtPct(familiar)} of listening time went to titles already heard on an earlier day.`}
          caption="First-seen dates come from the entire import, even when the view is filtered. Months without listening are left blank."
          table={() => (
            <DataTable caption="Familiar listening share by month" rows={result.monthly} rowKey={m => m.period} columns={[
              { key: 'm', label: 'Month', render: m => fmtMonth(m.period) },
              { key: 'f', label: 'Familiar share', numeric: true, render: m => fmtPct(m.familiarShare) },
              { key: 'h', label: 'Hours', numeric: true, render: m => fmtHoursValue(m.hours) },
            ]} />
          )}>
          <div className="chart-box chart-sm">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={result.monthly} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="period" ticks={ticks} tickFormatter={fmtMonthTick} tick={tick} axisLine={axisLine} tickLine={false} minTickGap={18} />
                <YAxis domain={[0, 1]} ticks={[0, 0.25, 0.5, 0.75, 1]} tick={tick} axisLine={false} tickLine={false} width={40} tickFormatter={v => `${Math.round(Number(v) * 100)}%`} />
                <Tooltip cursor={lineCursor} content={(p: TipProps) => {
                  const d = tipDatum<PeriodPoint>(p);
                  return d ? (
                    <TipBox title={fmtMonth(d.period, true)} note={partial.notes[d.period]} rows={[
                      { label: 'familiar share', value: fmtPct(d.familiarShare), color: BLUE },
                      { label: 'listened', value: fmtHours(d.hours) },
                    ]} />
                  ) : null;
                }} />
                <Line type="monotone" dataKey="familiarShare" stroke={BLUE} strokeWidth={2} dot={familiarDot} connectNulls={false}
                  activeDot={{ r: 4, fill: BLUE, stroke: SURFACE, strokeWidth: 2 }} isAnimationActive={animate} animationDuration={450} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>
      </div>

      <section className="card" aria-labelledby="yearly-title">
        <header className="card-head">
          <div className="card-titles">
            <h2 id="yearly-title">Year by year</h2>
            <p className="card-sub">Totals per calendar year, with how much the top 20 artists overlap the previous year</p>
          </div>
        </header>
        <div className="table-scroll">
          <DataTable caption="Yearly summary" rows={[...result.yearly].reverse()} rowKey={y => y.period} columns={[
            { key: 'y', label: 'Year', render: y => <span className="nowrap"><strong>{y.period}</strong>{y.partial && <span className="tag">partial</span>}</span> },
            { key: 'h', label: 'Hours', numeric: true, render: y => fmtHoursValue(y.hours) },
            { key: 'e', label: 'Events', numeric: true, render: y => fmtInt(y.plays) },
            { key: 'a', label: 'Artists / shows', numeric: true, render: y => fmtInt(y.artists) },
            { key: 'n', label: 'New', numeric: true, render: y => fmtInt(y.newArtists) },
            { key: 'f', label: 'Familiar', numeric: true, render: y => fmtPct(y.familiarShare, 0) },
            { key: 't', label: 'Top artist / show', className: 'cell-name', render: y => (y.topArtist
              ? <button type="button" className="link-btn" onClick={() => onArtist(y.topArtist)} aria-label={`Filter every view to ${y.topArtist}`}>{y.topArtist}</button>
              : <span className="muted">n/a</span>) },
            { key: 'o', label: 'Top 20 overlap', numeric: true, render: y => (y.overlap === null ? <span className="muted">n/a</span> : <OverlapCell value={y.overlap} />) },
          ]} />
        </div>
        <p className="card-caption">
          Overlap is the Jaccard index of each year's top 20 artists or shows by time against the previous listed year: shared names divided by all names in either list.
          Partial years cover only part of the calendar year within the data or the selected range, so compare them with care.
        </p>
      </section>

      <div className="grid-2">
        <section className="card" aria-labelledby="obsessions-title">
          <header className="card-head">
            <div className="card-titles">
              <h2 id="obsessions-title">Replay obsessions</h2>
              <p className="card-sub">Most events for a single title within one calendar month</p>
            </div>
          </header>
          {result.obsessions.length ? (
            <ol className="item-list">
              {result.obsessions.slice(0, 12).map((o, i) => (
                <li key={`${o.name}-${o.artist}-${o.month}`}>
                  <span className="rank-n">{i + 1}</span>
                  <div className="item-main">
                    <button type="button" className="link-btn" onClick={() => onTitle(o.name, o.artist)} aria-label={`Filter every view to ${o.name} by ${o.artist}`}>{o.name}</button>
                    <span className="item-sub">{o.artist} · {fmtMonth(o.month, true)}</span>
                  </div>
                  <span className="item-value">{fmtInt(o.plays)}<small>events · {fmtHours(o.hours)}</small></span>
                  <SpotifyLink uri={o.uri} label={o.name} />
                </li>
              ))}
            </ol>
          ) : <p className="muted">No title was recorded more than once in a single month.</p>}
          <p className="card-caption">Counts are recorded events of any length under the current filters.</p>
        </section>

        <section className="card" aria-labelledby="gaps-title">
          <header className="card-head">
            <div className="card-titles">
              <h2 id="gaps-title">Longest returns</h2>
              <p className="card-sub">Longest gaps between consecutive plays of the same title</p>
            </div>
          </header>
          {result.rediscoveries.length ? (
            <ol className="item-list">
              {result.rediscoveries.slice(0, 12).map((g, i) => (
                <li key={`${g.name}-${g.artist}-${g.after}`}>
                  <span className="rank-n">{i + 1}</span>
                  <div className="item-main">
                    <button type="button" className="link-btn" onClick={() => onTitle(g.name, g.artist)} aria-label={`Filter every view to ${g.name} by ${g.artist}`}>{g.name}</button>
                    <span className="item-sub">{g.artist} · {fmtDate(g.before)} to {fmtDate(g.after)}</span>
                  </div>
                  <span className="item-value">{fmtSpan(g.gapDays)}<small>{fmtInt(g.gapDays)} days</small></span>
                  <SpotifyLink uri={g.uri} label={g.name} />
                </li>
              ))}
            </ol>
          ) : <p className="muted">No title returned after a gap of 180 days or more.</p>}
          <p className="card-caption">Gaps of at least 180 days between consecutive positive-length records of a title, among the filtered records only. Narrow filters can lengthen gaps.</p>
        </section>
      </div>

      <ChartCard
        title="Listening time and variety"
        subtitle="Each dot is an active day: minutes listened against distinct titles played"
        summary={`Pearson r ${r === null ? 'is not available' : `= ${r.toFixed(2)}`} across ${fmtInt(result.correlation.n)} active days.`}
        caption={<>{result.correlation.explanation}{active.length > scatter.length ? ` The plot shows ${fmtInt(scatter.length)} evenly spaced days of ${fmtInt(active.length)}; r uses all of them.` : ''}</>}
        table={() => (
          <DataTable caption="Active days: minutes and distinct titles" rows={active} rowKey={d => d.date} columns={[
            { key: 'd', label: 'Date', render: d => d.date },
            { key: 'm', label: 'Minutes', numeric: true, render: d => fmtInt(d.minutes) },
            { key: 't', label: 'Distinct titles', numeric: true, render: d => fmtInt(d.tracks) },
            { key: 'e', label: 'Events', numeric: true, render: d => fmtInt(d.plays) },
          ]} />
        )}>
        <div className="corr">
          <div className="corr-stat">
            <span className="stat-label">Pearson r</span>
            <span className="corr-value">{r === null ? 'n/a' : r.toFixed(2)}</span>
            <span className="stat-detail">{r === null ? 'Needs at least 3 active days with variation' : `${strength} ${r >= 0 ? 'positive' : 'negative'} association`}</span>
            <span className="stat-detail">n = {plural(result.correlation.n, 'active day')}</span>
          </div>
          <div className="chart-box chart-md corr-plot">
            <ResponsiveContainer width="100%" height="100%">
              <ScatterChart margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
                <CartesianGrid stroke={GRID} />
                <XAxis type="number" dataKey="minutes" name="Minutes" tick={tick} axisLine={axisLine} tickLine={false} tickFormatter={v => fmtCompact(Number(v))}
                  label={{ value: 'minutes listened', position: 'insideBottomRight', offset: -4, fill: '#8c887e', fontSize: 11 }} />
                <YAxis type="number" dataKey="tracks" name="Distinct titles" tick={tick} axisLine={false} tickLine={false} width={40} allowDecimals={false}
                  label={{ value: 'titles', angle: -90, position: 'insideLeft', offset: 12, fill: '#8c887e', fontSize: 11 }} />
                <ZAxis range={[18, 18]} />
                <Tooltip cursor={false} content={(p: TipProps) => {
                  const d = tipDatum<DailyPoint>(p);
                  return d ? (
                    <TipBox title={fmtDateLong(d.date)} rows={[
                      { label: 'listened', value: fmtMinutes(d.minutes), color: ACCENT },
                      { label: 'distinct titles', value: fmtInt(d.tracks) },
                      { label: 'events', value: fmtInt(d.plays) },
                    ]} />
                  ) : null;
                }} />
                <Scatter data={scatter} fill={ACCENT} fillOpacity={0.45} isAnimationActive={false} />
              </ScatterChart>
            </ResponsiveContainer>
          </div>
        </div>
      </ChartCard>
    </div>
  );
}

function OverlapCell({ value }: { value: number }) {
  return (
    <span className="overlap">
      <span className="overlap-bar" aria-hidden="true"><span style={{ width: `${value * 100}%` }} /></span>
      {fmtPct(value, 0)}
    </span>
  );
}
