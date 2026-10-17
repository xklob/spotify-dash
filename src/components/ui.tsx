import type { ReactNode } from 'react';
import { Check, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, ExternalLink, Info, LoaderCircle, RotateCcw, TriangleAlert, X } from 'lucide-react';
import { fmtInt, kindLabel, spotifyUrl } from './format';

export function WaveGlyph({ size = 22 }: { size?: number }) {
  const bars = [7, 13, 20, 11, 16];
  return (
    <svg className="wave-glyph" width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {bars.map((h, i) => <rect key={i} x={1.6 + i * 4.4} y={(24 - h) / 2} width="2.8" height={h} rx="1.4" />)}
    </svg>
  );
}

export function Wordmark() {
  return (
    <span className="wordmark">
      <WaveGlyph />
      <span className="wordmark-text">Spotify <b>Dash</b></span>
    </span>
  );
}

export function Segmented<T extends string>({ label, value, options, onChange }: {
  label: string; value: T; options: readonly { value: T; label: string }[]; onChange: (value: T) => void;
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map(o => (
        <button key={o.value} type="button" aria-pressed={o.value === value} onClick={() => onChange(o.value)}>{o.label}</button>
      ))}
    </div>
  );
}

export function Stat({ label, value, unit, detail, hero = false }: { label: string; value: string; unit?: string; detail?: ReactNode; hero?: boolean }) {
  return (
    <div className={hero ? 'stat stat-hero' : 'stat'}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}{unit && <span className="stat-unit">{unit}</span>}</div>
      {detail && <div className="stat-detail">{detail}</div>}
    </div>
  );
}

export function Fact({ label, value, detail }: { label: string; value: ReactNode; detail?: ReactNode }) {
  return (
    <div className="fact">
      <dt>{label}</dt>
      <dd><span className="fact-value">{value}</span>{detail && <span className="fact-detail">{detail}</span>}</dd>
    </div>
  );
}

export function Notice({ tone = 'info', title, children, action, onDismiss }: {
  tone?: 'info' | 'error' | 'success' | 'demo'; title?: string; children?: ReactNode; action?: ReactNode; onDismiss?: () => void;
}) {
  const Icon = tone === 'error' ? TriangleAlert : tone === 'success' ? Check : Info;
  return (
    <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={16} aria-hidden="true" className="notice-icon" />
      <div className="notice-body">
        {title && <strong>{title}</strong>}
        {children && <div>{children}</div>}
      </div>
      {action}
      {onDismiss && (
        <button type="button" className="icon-btn" onClick={onDismiss} aria-label="Dismiss message"><X size={15} aria-hidden="true" /></button>
      )}
    </div>
  );
}

export const Spinner = ({ size = 18 }: { size?: number }) => <LoaderCircle className="spin" size={size} aria-hidden="true" />;

export function SpotifyLink({ uri, label }: { uri: string; label: string }) {
  const url = spotifyUrl(uri);
  if (!url) return null;
  return (
    <a className="spotify-link" href={url} target="_blank" rel="noopener noreferrer" title="Open in Spotify" aria-label={`Open ${label} in Spotify (new tab)`}>
      <ExternalLink size={14} aria-hidden="true" />
    </a>
  );
}

export function KindBadge({ kind }: { kind: string }) {
  return <span className={`kind kind-${kind}`}>{kindLabel(kind)}</span>;
}

export function Pager({ page, pages, onPage, label }: { page: number; pages: number; onPage: (page: number) => void; label: string }) {
  if (pages <= 1) return null;
  return (
    <nav className="pager" aria-label={label}>
      <button type="button" className="icon-btn" onClick={() => onPage(0)} disabled={page <= 0} aria-label="First page"><ChevronsLeft size={16} aria-hidden="true" /></button>
      <button type="button" className="icon-btn" onClick={() => onPage(page - 1)} disabled={page <= 0} aria-label="Previous page"><ChevronLeft size={16} aria-hidden="true" /></button>
      <span className="pager-status" aria-live="polite">Page {fmtInt(page + 1)} of {fmtInt(pages)}</span>
      <button type="button" className="icon-btn" onClick={() => onPage(page + 1)} disabled={page >= pages - 1} aria-label="Next page"><ChevronRight size={16} aria-hidden="true" /></button>
      <button type="button" className="icon-btn" onClick={() => onPage(pages - 1)} disabled={page >= pages - 1} aria-label="Last page"><ChevronsRight size={16} aria-hidden="true" /></button>
    </nav>
  );
}

export function EmptyResult({ onReset }: { onReset: () => void }) {
  return (
    <div className="card empty-state">
      <h2>No listening matches these filters</h2>
      <p>No recorded events fall inside the current date range and filter combination.</p>
      <button type="button" className="btn btn-primary" onClick={onReset}><RotateCcw size={15} aria-hidden="true" /> Reset filters</button>
    </div>
  );
}

export interface RankItem {
  key: string; primary: string; secondary?: string; value: number; valueLabel: string; detail?: string;
  onSelect?: () => void; selectLabel?: string; uri?: string;
}
/** Ranked rows with a proportional bar; the numeric value is always printed beside it. */
export function RankList({ items, label }: { items: RankItem[]; label: string }) {
  const top = items.reduce((m, i) => Math.max(m, i.value), 0);
  return (
    <ol className="rank-list" aria-label={label}>
      {items.map((it, i) => (
        <li key={it.key}>
          <span className="rank-n">{i + 1}</span>
          <div className="rank-main">
            <div className="rank-text">
              {it.onSelect
                ? <button type="button" className="link-btn" onClick={it.onSelect} title={it.selectLabel} aria-label={it.selectLabel}>{it.primary}</button>
                : <span className="rank-primary">{it.primary}</span>}
              {it.secondary && <span className="rank-sub">{it.secondary}</span>}
            </div>
            <div className="bar-track" aria-hidden="true"><span style={{ width: `${top && it.value > 0 ? Math.max(0.6, (it.value / top) * 100) : 0}%` }} /></div>
          </div>
          <span className="rank-value">{it.valueLabel}{it.detail && <small>{it.detail}</small>}</span>
          {it.uri !== undefined && <span className="rank-link"><SpotifyLink uri={it.uri} label={it.primary} /></span>}
        </li>
      ))}
    </ol>
  );
}
