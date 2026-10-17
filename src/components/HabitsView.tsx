import { useMemo, type ReactElement } from 'react';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { GroupPoint, QueryResult } from '../lib/types';
import { ChartCard, DataTable, TipBox, tipDatum, type TipProps } from './ChartCard';
import { countryName, fmtCompact, fmtHours, fmtHoursValue, fmtInt, fmtMinutes, fmtPct, reasonLabel } from './format';
import { CalendarHeatmap, hourName, WeekHourHeatmap } from './Heatmaps';
import { useCompact, useReducedMotion, useSticky } from './hooks';
import { ACCENT, axisLine, barCursor, BLUE, CORAL, GRID, lineCursor, MUTED, SURFACE, tick, VIOLET } from './theme';
import { Segmented } from './ui';

type Labeled = GroupPoint & { label: string };
const truncate = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1)}…` : s);

function groupColumns(nameLabel: string) {
  return [
    { key: 'n', label: nameLabel, render: (r: Labeled) => r.label },
    { key: 'h', label: 'Hours', numeric: true, render: (r: Labeled) => fmtHoursValue(r.hours) },
    { key: 'e', label: 'Events', numeric: true, render: (r: Labeled) => fmtInt(r.plays) },
    { key: 's', label: 'Skip rate', numeric: true, render: (r: Labeled) => fmtPct(r.skipRate) },
    { key: 'k', label: 'Known skip flags', numeric: true, render: (r: Labeled) => fmtInt(r.skipKnown) },
  ];
}

/** Horizontal ranked bars for categorical breakdowns (devices, countries, end reasons). */
function GroupBars({ rows, color, limit = 8, label }: { rows: Labeled[]; color: string; limit?: number; label: string }) {
  const compact = useCompact();
  const reduced = useReducedMotion();
  const shown = rows.slice(0, limit);
  const rest = rows.slice(limit);
  const restHours = rest.reduce((sum, r) => sum + r.hours, 0);
  const labelWidth = compact ? 104 : 140;
  const renderTick = (props: { x?: number | string; y?: number | string; payload?: { value?: unknown } }): ReactElement => {
    const text = String(props.payload?.value ?? '');
    return (
      <text x={Number(props.x)} y={Number(props.y)} dy={4} textAnchor="end" fill={MUTED} fontSize={11}>
        <title>{text}</title>{truncate(text, compact ? 15 : 21)}
      </text>
    );
  };
  return (
    <>
      <div className="chart-box" style={{ height: shown.length * 32 + 30 }} role="presentation">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={shown} layout="vertical" margin={{ top: 0, right: 12, bottom: 0, left: 0 }} barCategoryGap={7}>
            <CartesianGrid stroke={GRID} horizontal={false} />
            <XAxis type="number" tick={tick} axisLine={false} tickLine={false} tickFormatter={v => fmtCompact(Number(v))} />
            <YAxis type="category" dataKey="label" width={labelWidth} tick={renderTick} axisLine={axisLine} tickLine={false} interval={0} />
            <Tooltip cursor={barCursor} content={(p: TipProps) => {
              const d = tipDatum<Labeled>(p);
              return d ? (
                <TipBox title={d.label} rows={[
                  { label: 'listened', value: fmtHours(d.hours), color },
                  { label: 'events', value: fmtInt(d.plays) },
                  { label: `skip rate, n ${fmtInt(d.skipKnown)}`, value: fmtPct(d.skipRate) },
                ]} />
              ) : null;
            }} />
            <Bar dataKey="hours" name={label} fill={color} radius={[0, 4, 4, 0]} maxBarSize={18} isAnimationActive={!reduced} animationDuration={450} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      {rest.length > 0 && <p className="chart-note">Plus {fmtInt(rest.length)} more, {fmtHours(restHours)} combined. The table lists all.</p>}
    </>
  );
}

export function HabitsView({ result }: { result: QueryResult }) {
  const [heatMetric, setHeatMetric] = useSticky<'hours' | 'plays'>('habits.heat', 'hours');
  const [weekMetric, setWeekMetric] = useSticky<'average' | 'total'>('habits.week', 'average');
  const [yearPick, setYearPick] = useSticky<string>('habits.year', '');
  const reduced = useReducedMotion();
  const tz = result.scope.timezone.replace(/_/g, ' ');

  const years = useMemo(() => [...new Set(result.daily.map(d => d.date.slice(0, 4)))].sort(), [result.daily]);
  const year = years.includes(yearPick) ? yearPick : years[years.length - 1] ?? '';
  const hourly = useMemo(() => result.hours.map((h, i) => ({ ...h, hour: i })), [result.hours]);
  const weekdays = useMemo(() => result.weekdays.map(w => ({ ...w, short: w.name.slice(0, 3) })), [result.weekdays]);
  const peakHour = hourly.reduce<(typeof hourly)[number] | null>((b, h) => (!b || h.hours > b.hours ? h : b), null);
  const peakDay = weekdays.reduce<(typeof weekdays)[number] | null>((b, w) => (!b || w.hours > b.hours ? w : b), null);
  const devices = useMemo(() => result.platforms.map(p => ({ ...p, label: p.name })), [result.platforms]);
  const countries = useMemo(() => result.countries.map(c => ({ ...c, label: countryName(c.name) })), [result.countries]);
  const reasons = useMemo(() => result.reasons.map(r => ({ ...r, label: reasonLabel(r.name) })), [result.reasons]);
  const shuffle = useMemo(() => {
    const order = ['Shuffle on', 'Shuffle off', 'Unknown'];
    return [...result.shuffleComparison].sort((a, b) => order.indexOf(a.name) - order.indexOf(b.name))
      .map(g => ({ ...g, label: g.name === 'Unknown' ? 'Shuffle not recorded' : g.name }));
  }, [result.shuffleComparison]);
  const comparable = shuffle.filter(g => g.name !== 'Unknown' && g.skipKnown > 0);
  const weekKey = weekMetric === 'average' ? 'averageMinutes' : 'hours';

  return (
    <div className="stack">
      <ChartCard
        title="Weekday and hour"
        subtitle={`When playback ended, in ${tz}`}
        controls={<Segmented label="Heatmap measure" value={heatMetric} onChange={setHeatMetric} options={[{ value: 'hours', label: 'Hours' }, { value: 'plays', label: 'Events' }]} />}
        summary={peakHour && peakDay ? `Most listening ends around ${hourName(peakHour.hour)}; the busiest weekday is ${peakDay.name}.` : undefined}
        caption="Each event, including all of its playback time, is placed in the hour it ended. Long sessions therefore lean toward later hours."
        table={() => (
          <DataTable caption="Listening by weekday and hour" rows={result.heatmap} rowKey={r => `${r.day}-${r.hour}`} columns={[
            { key: 'd', label: 'Weekday', render: r => weekdays[r.day]?.name ?? r.day },
            { key: 'h', label: 'Hour', render: r => hourName(r.hour) },
            { key: 'hr', label: 'Hours', numeric: true, render: r => fmtHoursValue(r.hours) },
            { key: 'e', label: 'Events', numeric: true, render: r => fmtInt(r.plays) },
          ]} />
        )}>
        <WeekHourHeatmap cells={result.heatmap} metric={heatMetric} />
      </ChartCard>

      <div className="grid-2">
        <ChartCard
          title="Weekdays"
          subtitle={weekMetric === 'average' ? 'Average minutes per calendar day, including days without recorded listening' : 'Total hours per weekday'}
          controls={<Segmented label="Weekday measure" value={weekMetric} onChange={setWeekMetric} options={[{ value: 'average', label: 'Avg / day' }, { value: 'total', label: 'Total' }]} />}
          summary={peakDay ? `${peakDay.name} has the most listening: ${fmtHours(peakDay.hours)} in total, ${fmtMinutes(peakDay.averageMinutes)} per calendar day on average.` : undefined}
          table={() => (
            <DataTable caption="Listening by weekday" rows={result.weekdays} rowKey={r => r.name} columns={[
              { key: 'd', label: 'Weekday', render: r => r.name },
              { key: 'h', label: 'Total hours', numeric: true, render: r => fmtHoursValue(r.hours) },
              { key: 'a', label: 'Avg minutes / day', numeric: true, render: r => fmtInt(r.averageMinutes) },
              { key: 'c', label: 'Calendar days', numeric: true, render: r => fmtInt(r.calendarDays) },
              { key: 'e', label: 'Events', numeric: true, render: r => fmtInt(r.plays) },
            ]} />
          )}>
          <div className="chart-box chart-sm">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={weekdays} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="short" tick={tick} axisLine={axisLine} tickLine={false} />
                <YAxis tick={tick} axisLine={false} tickLine={false} width={40} tickFormatter={v => fmtCompact(Number(v))} />
                <Tooltip cursor={barCursor} content={(p: TipProps) => {
                  const d = tipDatum<(typeof weekdays)[number]>(p);
                  return d ? (
                    <TipBox title={d.name} rows={[
                      { label: 'average per calendar day', value: fmtMinutes(d.averageMinutes), color: weekMetric === 'average' ? ACCENT : undefined },
                      { label: 'in total', value: fmtHours(d.hours), color: weekMetric === 'total' ? ACCENT : undefined },
                      { label: `${d.name}s in range`, value: fmtInt(d.calendarDays) },
                    ]} />
                  ) : null;
                }} />
                <Bar dataKey={weekKey} fill={ACCENT} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={!reduced} animationDuration={450} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>

        <ChartCard
          title="Hour of day"
          subtitle={`Hours of listening ending in each clock hour, ${tz}`}
          summary={peakHour ? `The busiest hour is ${hourName(peakHour.hour)} with ${fmtHours(peakHour.hours)}.` : undefined}
          table={() => (
            <DataTable caption="Listening by hour of day" rows={hourly} rowKey={r => String(r.hour)} columns={[
              { key: 'h', label: 'Hour', render: r => hourName(r.hour) },
              { key: 'hr', label: 'Hours', numeric: true, render: r => fmtHoursValue(r.hours) },
              { key: 'e', label: 'Events', numeric: true, render: r => fmtInt(r.plays) },
              { key: 's', label: 'Skip rate', numeric: true, render: r => fmtPct(r.skipRate) },
            ]} />
          )}>
          <div className="chart-box chart-sm">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={hourly} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <defs>
                  <linearGradient id="fill-hourly" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={ACCENT} stopOpacity={0.24} />
                    <stop offset="100%" stopColor={ACCENT} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="hour" type="number" domain={[0, 23]} ticks={[0, 3, 6, 9, 12, 15, 18, 21]} tickFormatter={h => hourName(Number(h)).replace(':00', '')}
                  tick={tick} axisLine={axisLine} tickLine={false} />
                <YAxis tick={tick} axisLine={false} tickLine={false} width={40} tickFormatter={v => fmtCompact(Number(v))} />
                <Tooltip cursor={lineCursor} content={(p: TipProps) => {
                  const d = tipDatum<(typeof hourly)[number]>(p);
                  return d ? (
                    <TipBox title={`${hourName(d.hour)} to ${hourName((d.hour + 1) % 24)}`} rows={[
                      { label: 'listened', value: fmtHours(d.hours), color: ACCENT },
                      { label: 'events', value: fmtInt(d.plays) },
                    ]} />
                  ) : null;
                }} />
                <Area type="monotone" dataKey="hours" stroke={ACCENT} strokeWidth={2} fill="url(#fill-hourly)" isAnimationActive={!reduced} animationDuration={450}
                  activeDot={{ r: 4, fill: ACCENT, stroke: SURFACE, strokeWidth: 2 }} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>
      </div>

      {year && (
        <ChartCard
          title="Daily calendar"
          subtitle={`Minutes listened per day in ${year}, ${tz}`}
          controls={years.length > 1 ? (
            <label className="field field-inline">
              <span className="sr-only">Calendar year</span>
              <select value={year} onChange={e => setYearPick(e.target.value)}>
                {[...years].reverse().map(y => <option key={y} value={y}>{y}</option>)}
              </select>
            </label>
          ) : undefined}
          caption="Colors split the year's active days into equal-sized groups by minutes. Outlined squares fall outside the selected range."
          table={() => (
            <DataTable caption={`Daily listening in ${year}`} rows={result.daily.filter(d => d.date.startsWith(year))} rowKey={r => r.date} columns={[
              { key: 'd', label: 'Date', render: r => r.date },
              { key: 'm', label: 'Minutes', numeric: true, render: r => fmtInt(r.minutes) },
              { key: 'e', label: 'Events', numeric: true, render: r => fmtInt(r.plays) },
              { key: 't', label: 'Titles', numeric: true, render: r => fmtInt(r.tracks) },
            ]} />
          )}>
          <CalendarHeatmap days={result.daily} year={year} />
        </ChartCard>
      )}

      <div className="grid-3">
        <ChartCard title="Devices" subtitle="Hours by platform family"
          table={() => <DataTable caption="Listening by device" rows={devices} rowKey={r => r.name} columns={groupColumns('Device')} />}>
          <GroupBars rows={devices} color={ACCENT} label="Hours" />
        </ChartCard>
        <ChartCard title="Countries" subtitle="Hours by connection country"
          table={() => <DataTable caption="Listening by country" rows={countries} rowKey={r => r.name} columns={groupColumns('Country')} />}>
          <GroupBars rows={countries} color={BLUE} label="Hours" />
        </ChartCard>
        <ChartCard title="How playback ended" subtitle="Hours by recorded end reason"
          caption="Spotify's reason_end codes, relabeled. Hover a bar for the exact counts."
          table={() => <DataTable caption="Listening by end reason" rows={reasons} rowKey={r => r.name} columns={[
            ...groupColumns('End reason'), { key: 'c', label: 'Code', render: (r: Labeled) => <code>{r.name}</code> },
          ]} />}>
          <GroupBars rows={reasons} color={VIOLET} label="Hours" />
        </ChartCard>
      </div>

      <ChartCard
        title="Skip flag with shuffle on and off"
        subtitle="Share of events Spotify flagged as skipped, among events with a recorded skip flag"
        caption="This compares recorded rates only. Shuffle sessions can differ in context, such as playlists versus albums, so the gap does not show that shuffle causes skipping."
        table={() => <DataTable caption="Skip rate by shuffle state" rows={shuffle} rowKey={r => r.name} columns={groupColumns('Shuffle')} />}>
        {comparable.length ? (
          <div className="compare">
            {shuffle.map(g => (
              <div className="compare-row" key={g.name}>
                <div className="compare-label">
                  <strong>{g.label}</strong>
                  <span>{fmtInt(g.plays)} events · {fmtHours(g.hours)}</span>
                </div>
                <div className="compare-bar" aria-hidden="true">
                  <span style={{ width: `${(g.skipRate ?? 0) * 100}%`, background: g.name === 'Unknown' ? MUTED : CORAL }} />
                </div>
                <div className="compare-value">
                  <strong>{fmtPct(g.skipRate)}</strong>
                  <span>n {fmtInt(g.skipKnown)}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="muted">No events in this range have both a recorded shuffle state and a recorded skip flag.</p>
        )}
      </ChartCard>
    </div>
  );
}
