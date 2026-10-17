import { useId, useState, type ReactNode } from 'react';
import { ChartColumn, Table2 } from 'lucide-react';

/**
 * Card wrapper for every chart. The Table toggle swaps the plot for an HTML table of the same
 * values, so no number is reachable only through hover.
 */
export function ChartCard({ title, subtitle, caption, summary, controls, table, children, className = '' }: {
  title: string; subtitle?: ReactNode; caption?: ReactNode; summary?: string; controls?: ReactNode;
  table?: () => ReactNode; children: ReactNode; className?: string;
}) {
  const [showTable, setShowTable] = useState(false);
  const titleId = useId();
  return (
    <section className={`card chart-card ${className}`} aria-labelledby={titleId}>
      <header className="card-head">
        <div className="card-titles">
          <h2 id={titleId}>{title}</h2>
          {subtitle && <p className="card-sub">{subtitle}</p>}
        </div>
        {(controls || table) && (
          <div className="card-controls">
            {controls}
            {table && (
              <button type="button" className="ghost-btn" aria-pressed={showTable} onClick={() => setShowTable(v => !v)}
                aria-label={showTable ? `Show ${title} as chart` : `Show ${title} as table`}>
                {showTable ? <ChartColumn size={14} aria-hidden="true" /> : <Table2 size={14} aria-hidden="true" />}
                <span>{showTable ? 'Chart' : 'Table'}</span>
              </button>
            )}
          </div>
        )}
      </header>
      {summary && <p className="sr-only">{summary}</p>}
      <div className="card-body">
        {showTable && table ? <div className="table-scroll table-tall">{table()}</div> : children}
      </div>
      {caption && <p className="card-caption">{caption}</p>}
    </section>
  );
}

export interface Column<R> {
  key: string; label: string; numeric?: boolean; className?: string;
  render: (row: R, index: number) => ReactNode;
}
export function DataTable<R>({ columns, rows, caption, rowKey }: {
  columns: Column<R>[]; rows: readonly R[]; caption: string; rowKey?: (row: R, index: number) => string;
}) {
  return (
    <table className="data-table">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>{columns.map(c => <th key={c.key} scope="col" className={c.numeric ? 'num' : c.className}>{c.label}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={rowKey ? rowKey(r, i) : i}>
            {columns.map(c => <td key={c.key} className={[c.numeric ? 'num' : '', c.className ?? ''].join(' ').trim() || undefined}>{c.render(r, i)}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export interface TipRow { label: string; value: string; color?: string }
export function TipBox({ title, rows, note }: { title: string; rows: TipRow[]; note?: string }) {
  return (
    <div className="chart-tip">
      <div className="tip-title">{title}</div>
      {rows.map((r, i) => (
        <div className="tip-row" key={i}>
          {r.color && <span className="tip-key" style={{ background: r.color }} />}
          <span className="tip-value">{r.value}</span>
          <span className="tip-label">{r.label}</span>
        </div>
      ))}
      {note && <div className="tip-note">{note}</div>}
    </div>
  );
}

/** The subset of Recharts tooltip props these charts read. */
export type TipProps = {
  active?: boolean;
  payload?: ReadonlyArray<{ payload?: unknown; value?: unknown; name?: unknown; dataKey?: unknown; color?: string }>;
  label?: string | number;
};
export function tipDatum<T>(props: TipProps): T | null {
  return props.active && props.payload?.length ? (props.payload[0].payload as T) : null;
}
