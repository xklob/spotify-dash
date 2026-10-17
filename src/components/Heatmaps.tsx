import { useMemo, useState, type KeyboardEvent } from 'react';
import type { DailyPoint, QueryResult } from '../lib/types';
import { addDays, fmtDateLong, fmtHours, fmtInt, fmtMinutes, monthShort, weekdayIndex, WEEKDAYS, WEEKDAYS_SHORT } from './format';
import { HEAT } from './theme';

export const hourName = (h: number) => `${h % 12 === 0 ? 12 : h % 12} ${h < 12 ? 'AM' : 'PM'}`;
const hourTick = (h: number) => `${h % 12 === 0 ? 12 : h % 12}${h < 12 ? 'a' : 'p'}`;
const hourSpan = (h: number) => `${hourName(h)} to ${hourName((h + 1) % 24)}`;

function Legend({ labels }: { labels: string[] }) {
  return (
    <div className="heat-legend" aria-hidden="true">
      <span>Less</span>
      <span className="heat-legend-steps">
        {HEAT.map((c, i) => (
          <span key={c} className="heat-legend-step">
            <span className="heat-swatch" style={{ background: c }} />
            <span className="heat-legend-label">{labels[i] ?? ''}</span>
          </span>
        ))}
      </span>
      <span>More</span>
    </div>
  );
}

/** Weekday by hour grid. One tab stop; arrow keys move a highlighted cell whose values are announced. */
export function WeekHourHeatmap({ cells, metric }: { cells: QueryResult['heatmap']; metric: 'hours' | 'plays' }) {
  const grid = useMemo(() => {
    const g: (QueryResult['heatmap'][number] | null)[][] = Array.from({ length: 7 }, () => Array<null>(24).fill(null));
    for (const c of cells) if (c.day >= 0 && c.day < 7 && c.hour >= 0 && c.hour < 24) g[c.day][c.hour] = c;
    return g;
  }, [cells]);
  const max = useMemo(() => cells.reduce((m, c) => Math.max(m, c[metric]), 0), [cells, metric]);
  const steps = HEAT.length - 1;
  const level = (v: number) => (v <= 0 || !max ? 0 : Math.max(1, Math.ceil((v / max) * steps)));
  const [active, setActive] = useState<{ day: number; hour: number } | null>(null);
  const cell = active ? grid[active.day][active.hour] : null;
  const valueText = (v: { hours: number; plays: number } | null) => (v ? `${fmtHours(v.hours)} · ${fmtInt(v.plays)} events` : 'no events');

  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, [number, number]> = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] };
    const cur = active ?? { day: 0, hour: 0 };
    if (moves[e.key]) {
      e.preventDefault();
      const [dd, dh] = moves[e.key];
      setActive({ day: Math.min(6, Math.max(0, cur.day + dd)), hour: Math.min(23, Math.max(0, cur.hour + dh)) });
    } else if (e.key === 'Home') { e.preventDefault(); setActive({ day: cur.day, hour: 0 }); }
    else if (e.key === 'End') { e.preventDefault(); setActive({ day: cur.day, hour: 23 }); }
    else if (e.key === 'Escape') setActive(null);
  };
  const unit = metric === 'hours' ? 'h' : 'events';
  const fmtMax = metric === 'hours' ? fmtHours(max) : `${fmtInt(max)} events`;

  return (
    <div className="heat">
      <p className="heat-readout" aria-live="polite">
        {active
          ? <><strong>{WEEKDAYS[active.day]}, {hourSpan(active.hour)}</strong> {valueText(cell)}</>
          : <span className="muted">Hover a cell, or focus the grid and use arrow keys.</span>}
      </p>
      <div className="heat-scroll">
        <div className="heat-grid" tabIndex={0} role="group" onKeyDown={onKey}
          aria-label={`Listening by weekday and hour, darkest is none and brightest is ${fmtMax}. Use arrow keys to read cells.`}
          onFocus={() => { if (!active) setActive({ day: 0, hour: 0 }); }} onBlur={() => setActive(null)} onMouseLeave={() => setActive(null)}>
          <span className="heat-corner" aria-hidden="true" />
          {Array.from({ length: 24 }, (_, h) => <span key={h} className="heat-hour" aria-hidden="true">{h % 3 === 0 ? hourTick(h) : ''}</span>)}
          {grid.map((row, d) => (
            <div className="heat-row" key={d} aria-hidden="true">
              <span className="heat-day">{WEEKDAYS_SHORT[d]}</span>
              {row.map((c, h) => (
                <span key={h} className={`heat-cell${active?.day === d && active.hour === h ? ' is-active' : ''}`}
                  style={{ background: HEAT[level(c ? c[metric] : 0)] }}
                  onMouseEnter={() => setActive({ day: d, hour: h })} />
              ))}
            </div>
          ))}
        </div>
      </div>
      <Legend labels={['0', '', '', '', '', '', `${metric === 'hours' ? fmtHours(max).replace(' h', '') : fmtInt(max)} ${unit}`]} />
    </div>
  );
}

const shortMinutes = (m: number) => (m < 60 ? `${Math.max(1, Math.round(m))}m` : `${(m / 60).toFixed(m < 600 ? 1 : 0)}h`);

/** Quantile cut points over active days so each colored level holds a similar number of days. */
function cutPoints(values: number[], levels: number): number[] {
  const sorted = values.filter(v => v > 0).sort((a, b) => a - b);
  if (!sorted.length) return [];
  const cuts: number[] = [];
  for (let i = 1; i < levels; i++) {
    const v = sorted[Math.min(sorted.length - 1, Math.floor((i / levels) * sorted.length))];
    if (!cuts.length || v > cuts[cuts.length - 1]) cuts.push(v);
  }
  return cuts;
}

export function CalendarHeatmap({ days, year }: { days: DailyPoint[]; year: string }) {
  const model = useMemo(() => {
    const byDate = new Map(days.map(d => [d.date, d]));
    const start = `${year}-01-01`;
    const lead = weekdayIndex(start);
    const y = Number(year);
    const total = Math.round((Date.UTC(y + 1, 0, 1) - Date.UTC(y, 0, 1)) / 86_400_000);
    const cells = Array.from({ length: total }, (_, i) => {
      const date = addDays(start, i);
      return { date, col: Math.floor((lead + i) / 7) + 1, row: ((lead + i) % 7) + 1, point: byDate.get(date) ?? null };
    });
    const inYear = cells.map(c => c.point).filter((p): p is DailyPoint => !!p);
    const cuts = cutPoints(inYear.map(p => p.minutes), HEAT.length - 1);
    const months = Array.from({ length: 12 }, (_, m) => {
      const first = cells.find(c => Number(c.date.slice(5, 7)) === m + 1);
      return { label: monthShort(m), col: first ? first.col : 1 };
    });
    const totals = inYear.reduce((acc, p) => ({ minutes: acc.minutes + p.minutes, active: acc.active + (p.minutes > 0 ? 1 : 0), plays: acc.plays + p.plays }), { minutes: 0, active: 0, plays: 0 });
    return { cells, cuts, months, columns: cells[cells.length - 1].col, covered: inYear.length, totals };
  }, [days, year]);

  const level = (m: number) => {
    if (m <= 0) return 0;
    let l = 1;
    for (const c of model.cuts) if (m >= c) l++;
    return Math.min(HEAT.length - 1, l);
  };
  const [active, setActive] = useState<number | null>(null);
  const activeCell = active === null ? null : model.cells[active];
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const delta: Record<string, number> = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 };
    const cur = active ?? 0;
    if (delta[e.key] !== undefined) {
      e.preventDefault();
      setActive(Math.min(model.cells.length - 1, Math.max(0, cur + delta[e.key])));
    } else if (e.key === 'Home') { e.preventDefault(); setActive(0); }
    else if (e.key === 'End') { e.preventDefault(); setActive(model.cells.length - 1); }
    else if (e.key === 'Escape') setActive(null);
  };
  // Level k (k >= 2) starts at cuts[k - 2]; the last used level is open-ended.
  const legend = HEAT.map((_, i) => {
    if (i === 0) return '0';
    if (i === 1) return '>0';
    const cut = model.cuts[i - 2];
    if (cut === undefined) return '';
    return i - 2 === model.cuts.length - 1 ? `${shortMinutes(cut)}+` : shortMinutes(cut);
  });

  return (
    <div className="heat">
      <p className="heat-readout" aria-live="polite">
        {activeCell
          ? <><strong>{fmtDateLong(activeCell.date)}</strong> {activeCell.point ? `${fmtMinutes(activeCell.point.minutes)} · ${fmtInt(activeCell.point.plays)} events · ${fmtInt(activeCell.point.tracks)} titles` : 'outside the selected range'}</>
          : <span className="muted">{fmtInt(model.totals.active)} active days of {fmtInt(model.covered)} in range · {fmtMinutes(model.totals.minutes)} · {fmtInt(model.totals.plays)} events</span>}
      </p>
      <div className="cal-scroll">
        <div className="cal" style={{ ['--cal-cols' as string]: model.columns }}>
          <div className="cal-months" aria-hidden="true">
            {model.months.map(m => <span key={m.label} style={{ gridColumn: `${m.col} / span 4` }}>{m.label}</span>)}
          </div>
          <div className="cal-body">
            <div className="cal-days" aria-hidden="true">
              {WEEKDAYS_SHORT.map((d, i) => <span key={d}>{i % 2 === 0 ? d : ''}</span>)}
            </div>
            <div className="cal-grid" tabIndex={0} role="group" onKeyDown={onKey}
              aria-label={`Daily listening in ${year}. Use arrow keys to read days: left and right move by week, up and down by day.`}
              onFocus={() => { if (active === null) setActive(model.cells.findIndex(c => c.point) >= 0 ? model.cells.findIndex(c => c.point) : 0); }}
              onBlur={() => setActive(null)} onMouseLeave={() => setActive(null)}>
              {model.cells.map((c, i) => (
                <span key={c.date} aria-hidden="true"
                  className={`cal-cell${c.point ? '' : ' is-outside'}${active === i ? ' is-active' : ''}`}
                  style={{ gridColumn: c.col, gridRow: c.row, background: c.point ? HEAT[level(c.point.minutes)] : undefined }}
                  onMouseEnter={() => setActive(i)} />
              ))}
            </div>
          </div>
        </div>
      </div>
      <Legend labels={legend} />
    </div>
  );
}
