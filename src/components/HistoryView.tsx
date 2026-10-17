import { useEffect, useState } from 'react';
import { Download, RotateCcw } from 'lucide-react';
import * as client from '../lib/client';
import type { Filters, HistoryPage, QueryResult } from '../lib/types';
import { errorMessage, fmtClock, fmtDate, fmtInt } from './format';
import { useCompact } from './hooks';
import { KindBadge, Notice, Pager, Segmented, Spinner, SpotifyLink } from './ui';

export interface HistoryRequest { page: number; pageSize: number; order: 'newest' | 'oldest' }
type Row = HistoryPage['rows'][number];

// The latest page outlives tab switches, so coming back to History does not repeat the same request.
let cache: { key: string; data: HistoryPage } | null = null;

const skipText = (v: boolean | null) => (v === null ? 'Not recorded' : v ? 'Skipped' : 'No');

export function HistoryView({ filters, scope, version, request, onRequest, onArtist, notify, refreshing }: {
  filters: Filters; scope: QueryResult['scope']; version: number; request: HistoryRequest;
  onRequest: (update: (r: HistoryRequest) => HistoryRequest) => void; onArtist: (artist: string) => void;
  notify: (tone: 'info' | 'error' | 'success', text: string) => void;
  /** The summary query for the current filters is still running, so the count and scope may be stale. */
  refreshing: boolean;
}) {
  const key = JSON.stringify([filters, request, version]);
  const [data, setData] = useState<HistoryPage | null>(cache?.data ?? null);
  const [loading, setLoading] = useState(cache?.key !== key);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [exporting, setExporting] = useState(false);
  const compact = useCompact();
  const tz = filters.timezone.replace(/_/g, ' ');

  useEffect(() => {
    if (cache?.key === key) { setData(cache.data); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    setError(null);
    client.history(filters, request.page, request.pageSize, request.order)
      .then(page => { if (!cancelled) { cache = { key, data: page }; setData(page); } })
      .catch(e => { if (!cancelled) setError(errorMessage(e)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [key, attempt]);

  async function exportCsv() {
    setExporting(true);
    try {
      const csv = await client.exportCsv(filters);
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `spotify-dash-history_${scope.start || 'start'}_to_${scope.end || 'end'}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
      notify('success', `CSV ready with ${data ? fmtInt(data.total) : 'all'} filtered events.`);
    } catch (e) {
      notify('error', `CSV export failed: ${errorMessage(e)}`);
    } finally {
      setExporting(false);
    }
  }

  const setPage = (page: number) => { onRequest(r => ({ ...r, page })); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const rows: Row[] = data?.rows ?? [];

  return (
    <div className="stack">
      <section className="card history" aria-labelledby="history-title">
        <header className="card-head">
          <div className="card-titles">
            <h2 id="history-title">Playback records</h2>
            <p className="card-sub" aria-live="polite">
              {data ? `${fmtInt(data.total)} events match the filters` : 'Loading records'}. Times show when playback ended, {tz}.
            </p>
          </div>
          <div className="card-controls">
            <Segmented label="Order" value={request.order} onChange={order => onRequest(r => ({ ...r, order, page: 0 }))}
              options={[{ value: 'newest', label: 'Newest first' }, { value: 'oldest', label: 'Oldest first' }]} />
            <label className="field field-inline">
              <span className="sr-only">Rows per page</span>
              <select value={request.pageSize} onChange={e => onRequest(r => ({ ...r, pageSize: Number(e.target.value), page: 0 }))}>
                {[25, 50, 100, 200].map(n => <option key={n} value={n}>{n} per page</option>)}
              </select>
            </label>
            <button type="button" className="btn btn-primary" onClick={exportCsv} disabled={exporting || loading || refreshing || !data?.total}
              aria-label={`Export all ${data ? fmtInt(data.total) : ''} filtered events as CSV`}>
              {exporting ? <Spinner size={15} /> : <Download size={15} aria-hidden="true" />} Export CSV
            </button>
          </div>
        </header>

        {error && (
          <Notice tone="error" title="Records could not be loaded" action={
            <button type="button" className="btn btn-sm" onClick={() => { cache = null; setAttempt(a => a + 1); }}><RotateCcw size={14} aria-hidden="true" /> Try again</button>
          }>{error}</Notice>
        )}

        {!data && loading ? (
          <div className="loading-inline" role="status"><Spinner /> Loading playback records</div>
        ) : rows.length === 0 ? (
          <p className="muted">No playback records match the current filters.</p>
        ) : compact ? (
          <ol className={`hist-list${loading ? ' is-refreshing' : ''}`}>
            {rows.map(r => (
              <li key={r.id}>
                <div className="hist-top">
                  <span className="hist-title">{r.title}</span>
                  <SpotifyLink uri={r.uri} label={r.title} />
                </div>
                <div className="hist-sub">
                  <button type="button" className="link-btn" onClick={() => onArtist(r.artist)} aria-label={`Filter every view to ${r.artist}`}>{r.artist}</button>
                  {r.album && <span> · {r.album}</span>}
                </div>
                <div className="hist-meta">
                  <span>{fmtDate(r.date)} {r.time}</span>
                  <span>{fmtClock(r.ms)}</span>
                  <span>{r.platform}</span>
                  <span>Skip: {skipText(r.skipped)}</span>
                  <KindBadge kind={r.kind} />
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className={`table-scroll${loading ? ' is-refreshing' : ''}`}>
            <table className="data-table history-table">
              <caption className="sr-only">Playback records, page {data ? data.page + 1 : 1}</caption>
              <thead>
                <tr>
                  <th scope="col">Ended</th>
                  <th scope="col">Type</th>
                  <th scope="col">Title</th>
                  <th scope="col">Artist / show</th>
                  <th scope="col" className="num">Played</th>
                  <th scope="col">Device</th>
                  <th scope="col">Skip flag</th>
                  <th scope="col"><span className="sr-only">Spotify link</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.id}>
                    <td className="nowrap"><span className="cell-date">{fmtDate(r.date)}</span> <span className="cell-time">{r.time}</span></td>
                    <td><KindBadge kind={r.kind} />{r.media === 'video' && <span className="tag">video</span>}</td>
                    <td className="cell-name"><span className="cell-title">{r.title}</span>{r.album && <span className="cell-sub">{r.album}</span>}</td>
                    <td className="cell-artist">
                      <button type="button" className="link-btn" onClick={() => onArtist(r.artist)} aria-label={`Filter every view to ${r.artist}`}>{r.artist}</button>
                    </td>
                    <td className="num">{fmtClock(r.ms)}</td>
                    <td className="nowrap">{r.platform}</td>
                    <td className={r.skipped === null ? 'muted nowrap' : 'nowrap'}>{skipText(r.skipped)}</td>
                    <td className="cell-link"><SpotifyLink uri={r.uri} label={r.title} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && <Pager page={data.page} pages={data.pages} onPage={setPage} label="Playback record pages" />}
        <p className="card-caption">
          Search, date range, type, minimum played, artist, device, country, and shuffle filters all apply here.
          Export CSV writes every matching event, not just this page, with UTC and local timestamps.
        </p>
      </section>
    </div>
  );
}
