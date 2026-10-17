import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Search } from 'lucide-react';
import type { Filters, QueryResult, Ranking } from '../lib/types';
import { fmtDate, fmtHoursValue, fmtInt, fmtMsHours, fmtPct, HOUR_MS, plural } from './format';
import { useCompact, useSticky } from './hooks';
import { Pager, Segmented, SpotifyLink } from './ui';

type View = 'artists' | 'tracks' | 'albums';
type Sort = 'time' | 'events';
const PAGE = 50;
const VIEW_OPTIONS = [
  { value: 'artists', label: 'Artists / shows' },
  { value: 'tracks', label: 'Titles' },
  { value: 'albums', label: 'Albums' },
] as const;
const NOUN: Record<View, [string, string]> = { artists: ['artist or show', 'artists and shows'], tracks: ['title', 'titles'], albums: ['album', 'albums'] };

export function FavoritesView({ result, filters, onArtist, onTitle }: {
  result: QueryResult; filters: Filters; onArtist: (artist: string) => void; onTitle: (title: string, artist: string) => void;
}) {
  const [view, setView] = useSticky<View>('favorites.view', 'artists');
  const [sort, setSort] = useSticky<Sort>('favorites.sort', 'time');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const compact = useCompact();

  const source = view === 'artists' ? result.artists : view === 'tracks' ? result.tracks : result.albums;
  const ranked = useMemo(() => (sort === 'time' ? source
    : [...source].sort((a, b) => b.plays - a.plays || b.ms - a.ms || a.name.localeCompare(b.name))), [source, sort]);
  const rows = useMemo(() => {
    const q = query.trim().toLocaleLowerCase();
    const all = ranked.map((r, i) => ({ r, rank: i + 1 }));
    if (!q) return all;
    return all.filter(({ r }) => r.name.toLocaleLowerCase().includes(q)
      || (view !== 'artists' && r.artist.toLocaleLowerCase().includes(q))
      || (view === 'tracks' && r.album.toLocaleLowerCase().includes(q)));
  }, [ranked, query, view]);
  useEffect(() => { setPage(0); }, [source, sort, query, view]);

  const metric = (r: Ranking) => (sort === 'time' ? r.ms : r.plays);
  const top = ranked.length ? metric(ranked[0]) : 0;
  const barWidth = (r: Ranking) => (top && metric(r) > 0 ? Math.max(0.6, (metric(r) / top) * 100) : 0);
  const pages = Math.ceil(rows.length / PAGE);
  const current = Math.min(page, Math.max(0, pages - 1));
  const visible = rows.slice(current * PAGE, current * PAGE + PAGE);
  const [one, many] = NOUN[view];

  const openRow = (r: Ranking) => {
    if (view === 'artists') { onArtist(r.name); setView('tracks'); setQuery(''); }
    else if (view === 'albums') { onTitle(r.name, r.artist); setView('tracks'); setQuery(''); }
    else onTitle(r.name, r.artist);
  };
  const openLabel = (r: Ranking) => (view === 'artists' ? `Filter every view to ${r.name} and list their titles`
    : view === 'albums' ? `Filter every view to ${r.artist} and search their records for ${r.name}` : `Filter every view to ${r.name} by ${r.artist}`);
  const sub = (r: Ranking) => (view === 'tracks' ? [r.artist, r.album].filter(Boolean).join(' · ') : view === 'albums' ? r.artist : '');
  const skip = (r: Ranking) => (r.skipKnown ? fmtPct(r.skipRate) : 'n/a');
  const sortHeader = (key: Sort, label: string) => (
    <th scope="col" className="num" aria-sort={sort === key ? 'descending' : undefined}>
      <button type="button" className={`th-sort${sort === key ? ' is-active' : ''}`} onClick={() => setSort(key)}>{label}</button>
    </th>
  );

  return (
    <div className="stack">
      {filters.artist && (
        <section className="card drill" aria-label="Artist drill-down">
          <button type="button" className="btn btn-sm" onClick={() => { onArtist(''); setView('artists'); setQuery(''); }}>
            <ArrowLeft size={15} aria-hidden="true" /> All artists
          </button>
          <div className="drill-title">
            <span className="eyebrow">Filtered to</span>
            <strong>{filters.artist}</strong>
          </div>
          <div className="drill-stats">
            <span>{fmtMsHours(result.summary.ms)}</span>
            <span>{fmtInt(result.summary.events)} events</span>
            <span>{plural(result.summary.tracks, 'title')}</span>
          </div>
        </section>
      )}

      <section className="card favorites" aria-labelledby="favorites-title">
        <header className="card-head">
          <div className="card-titles">
            <h2 id="favorites-title">Rankings</h2>
            <p className="card-sub">{fmtInt(ranked.length)} {ranked.length === 1 ? one : many} in the current filters, ranked by {sort === 'time' ? 'listening time' : 'event count'}</p>
          </div>
          <div className="card-controls">
            <Segmented label="Ranking type" value={view} onChange={v => { setView(v); setQuery(''); }} options={VIEW_OPTIONS} />
            <Segmented label="Rank by" value={sort} onChange={setSort} options={[{ value: 'time', label: 'Time' }, { value: 'events', label: 'Events' }]} />
          </div>
        </header>

        <div className="table-tools">
          <label className="field field-search field-inline">
            <span className="sr-only">Filter these rankings by name</span>
            <span className="input-wrap">
              <Search size={15} aria-hidden="true" className="input-icon" />
              <input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder={`Find in ${many}`} autoComplete="off" spellCheck={false} />
            </span>
          </label>
          <span className="table-count" aria-live="polite">
            {rows.length ? `Showing ${fmtInt(current * PAGE + 1)} to ${fmtInt(current * PAGE + visible.length)} of ${fmtInt(rows.length)}` : 'No names match'}
          </span>
        </div>

        {compact ? (
          <ol className="fav-list">
            {visible.map(({ r, rank }) => (
              <li key={r.key}>
                <span className="rank-n">{rank}</span>
                <div className="fav-main">
                  <button type="button" className="link-btn fav-name" onClick={() => openRow(r)} aria-label={openLabel(r)}>{r.name}</button>
                  {view === 'tracks'
                    ? <button type="button" className="link-btn fav-sub" onClick={() => onArtist(r.artist)} aria-label={`Filter every view to ${r.artist}`}>{r.artist}</button>
                    : sub(r) && <span className="fav-sub">{sub(r)}</span>}
                  <div className="bar-track" aria-hidden="true"><span style={{ width: `${barWidth(r)}%` }} /></div>
                  <span className="fav-meta">{fmtInt(r.plays)} events · skip {skip(r)} · {fmtDate(r.first)} to {fmtDate(r.last)}</span>
                </div>
                <div className="fav-side">
                  <span className="fav-value">{fmtHoursValue(r.ms / HOUR_MS)} h</span>
                  {view === 'tracks' && <SpotifyLink uri={r.uri} label={r.name} />}
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className="table-scroll">
            <table className="data-table fav-table">
              <caption className="sr-only">Ranked {many}</caption>
              <thead>
                <tr>
                  <th scope="col" className="num">#</th>
                  <th scope="col">{view === 'artists' ? 'Artist / show' : view === 'tracks' ? 'Title' : 'Album'}</th>
                  <th scope="col" className="col-bar"><span className="sr-only">Relative {sort === 'time' ? 'time' : 'events'}</span></th>
                  {sortHeader('events', 'Events')}
                  {sortHeader('time', 'Hours')}
                  <th scope="col" className="num">Skip rate</th>
                  <th scope="col">First</th>
                  <th scope="col">Last</th>
                  {view === 'tracks' && <th scope="col"><span className="sr-only">Spotify link</span></th>}
                </tr>
              </thead>
              <tbody>
                {visible.map(({ r, rank }) => (
                  <tr key={r.key}>
                    <td className="num muted">{rank}</td>
                    <td className="cell-name">
                      <button type="button" className="link-btn" onClick={() => openRow(r)} aria-label={openLabel(r)} title={openLabel(r)}>{r.name}</button>
                      {view === 'tracks'
                        ? <span className="cell-sub"><button type="button" className="link-btn" onClick={() => onArtist(r.artist)} aria-label={`Filter every view to ${r.artist}`}>{r.artist}</button>{r.album && <> · {r.album}</>}</span>
                        : sub(r) && <span className="cell-sub">{sub(r)}</span>}
                    </td>
                    <td className="col-bar"><div className="bar-track" aria-hidden="true"><span style={{ width: `${barWidth(r)}%` }} /></div></td>
                    <td className="num">{fmtInt(r.plays)}</td>
                    <td className="num">{fmtHoursValue(r.ms / HOUR_MS)}</td>
                    <td className="num" title={r.skipKnown ? `${fmtInt(r.skipKnown)} events with a recorded skip flag` : 'No recorded skip flags'}>
                      {skip(r)}<span className="cell-n">{r.skipKnown ? `n ${fmtInt(r.skipKnown)}` : ''}</span>
                    </td>
                    <td className="nowrap">{fmtDate(r.first)}</td>
                    <td className="nowrap">{fmtDate(r.last)}</td>
                    {view === 'tracks' && <td className="cell-link"><SpotifyLink uri={r.uri} label={r.name} /></td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pager page={current} pages={pages} onPage={setPage} label="Rankings pages" />
        <p className="card-caption">
          Events count every recorded playback of any length in the current filters, so they are not the 30 second play measure.
          Hours sum <code>ms_played</code>. Skip rate divides recorded skips by events with a known skip flag (n).
          {view === 'artists' ? ' Selecting a name filters every view to that artist and lists their titles.'
            : view === 'albums' ? ' Selecting an album filters every view to its artist and searches their titles and albums for its name, so similarly named titles can also match.'
            : ' Selecting a title filters every view to it with a search and an artist filter.'}
          {view !== 'tracks' ? ' Spotify links appear on titles only, because the export has no artist or album identifiers.' : ''}
        </p>
      </section>
    </div>
  );
}
