import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { RotateCcw, Trash2 } from 'lucide-react';
import * as client from './lib/client';
import { DEFAULT_FILTERS, type DatasetMeta, type Filters, type QueryResult } from './lib/types';
import { ConfirmDialog } from './components/ConfirmDialog';
import { DataView } from './components/DataView';
import { DiscoveriesView } from './components/DiscoveriesView';
import { FavoritesView } from './components/FavoritesView';
import { activeChips, FilterBar, type DataBounds } from './components/FilterBar';
import { errorMessage, fmtDate, fmtInt, isoToYmd, plural } from './components/format';
import { HabitsView } from './components/HabitsView';
import { HistoryView, type HistoryRequest } from './components/HistoryView';
import type { PickedFiles } from './components/ImportPanel';
import { OverviewView } from './components/OverviewView';
import { isTab, MobileHeader, Sidebar, TABS, type TabId } from './components/Shell';
import { EmptyResult, Notice, Spinner } from './components/ui';
import { Welcome } from './components/Welcome';

const TZ_KEY = 'spotify-dash:timezone';
function initialFilters(): Filters {
  let timezone = DEFAULT_FILTERS.timezone;
  try {
    const saved = localStorage.getItem(TZ_KEY);
    if (saved) {
      new Intl.DateTimeFormat('en-US', { timeZone: saved });
      timezone = saved;
    }
  } catch { /* unknown zone or storage unavailable: keep the default */ }
  return { ...DEFAULT_FILTERS, timezone };
}
function initialTab(): TabId {
  const hash = window.location.hash.replace(/^#\/?/, '');
  return isTab(hash) ? hash : 'overview';
}

// One initialization per page load, even when StrictMode runs effects twice in development.
let boot: Promise<DatasetMeta | null> | null = null;

type Task = { title: string; detail: string };
type Toast = { tone: 'info' | 'error' | 'success'; text: string; id: number };

export default function App() {
  const [meta, setMeta] = useState<DatasetMeta | null>(null);
  const [booting, setBooting] = useState(true);
  const [bootError, setBootError] = useState<string | null>(null);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [searchDraft, setSearchDraft] = useState('');
  const [result, setResult] = useState<QueryResult | null>(null);
  const [querying, setQuerying] = useState(false);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [retry, setRetry] = useState(0);
  const [tab, setTab] = useState<TabId>(initialTab);
  const [task, setTask] = useState<Task | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const [confirm, setConfirm] = useState<'clear' | 'demo' | null>(null);
  const [historyRequest, setHistoryRequest] = useState<HistoryRequest>({ page: 0, pageSize: 50, order: 'newest' });
  const busyRef = useRef(false);

  const notify = useCallback((tone: Toast['tone'], text: string) => setToast({ tone, text, id: Date.now() }), []);

  useEffect(() => {
    let cancelled = false;
    (boot ??= client.initialize())
      .then(m => { if (!cancelled) setMeta(m); })
      .catch(e => { if (!cancelled) setBootError(errorMessage(e)); })
      .finally(() => { if (!cancelled) setBooting(false); });
    return () => { cancelled = true; };
  }, []);

  // Every filter change re-queries the worker; superseded responses are ignored.
  useEffect(() => {
    if (!meta) return;
    let cancelled = false;
    setQuerying(true);
    setQueryError(null);
    client.query(filters)
      .then(r => { if (!cancelled) setResult(r); })
      .catch(e => { if (!cancelled) setQueryError(errorMessage(e)); })
      .finally(() => { if (!cancelled) setQuerying(false); });
    return () => { cancelled = true; };
  }, [meta, filters, version, retry]);

  useEffect(() => {
    if (searchDraft === filters.search) return;
    const timer = window.setTimeout(() => setFilters(f => ({ ...f, search: searchDraft })), 250);
    return () => window.clearTimeout(timer);
  }, [searchDraft, filters.search]);

  useEffect(() => { setHistoryRequest(r => (r.page === 0 ? r : { ...r, page: 0 })); }, [filters]);
  useEffect(() => { window.history.replaceState(null, '', `#${tab}`); }, [tab]);
  useEffect(() => {
    if (!toast || toast.tone === 'error') return;
    const timer = window.setTimeout(() => setToast(t => (t?.id === toast.id ? null : t)), 7000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const updateFilters = useCallback((patch: Partial<Filters>) => {
    setFilters(f => ({ ...f, ...patch }));
    if (patch.search !== undefined) setSearchDraft(patch.search);
    if (patch.timezone) { try { localStorage.setItem(TZ_KEY, patch.timezone); } catch { /* storage unavailable */ } }
  }, []);
  const clearFilters = useCallback(() => {
    setFilters(f => ({ ...DEFAULT_FILTERS, timezone: f.timezone }));
    setSearchDraft('');
  }, []);
  const goTab = useCallback((next: TabId) => {
    setTab(next);
    window.scrollTo({ top: 0 });
  }, []);
  const filterArtist = useCallback((artist: string) => updateFilters({ artist }), [updateFilters]);
  const filterTitle = useCallback((title: string, artist: string) => updateFilters({ artist, search: title }), [updateFilters]);

  async function runTask<T>(title: string, work: (progress: (detail: string) => void) => Promise<T>): Promise<T | undefined> {
    if (busyRef.current) return undefined;
    busyRef.current = true;
    setTask({ title, detail: '' });
    try {
      return await work(detail => setTask(t => (t ? { ...t, detail } : t)));
    } catch (e) {
      notify('error', errorMessage(e));
      return undefined;
    } finally {
      busyRef.current = false;
      setTask(null);
    }
  }

  async function onPick({ accepted, skipped }: PickedFiles) {
    const skippedNote = skipped.length
      ? ` Skipped ${plural(skipped.length, 'non-JSON file')}${skipped.length <= 3 ? ` (${skipped.join(', ')})` : ''}.`
      : '';
    if (!accepted.length) {
      const zip = skipped.some(n => /\.zip$/i.test(n)) ? ' Unzip the Spotify download first.' : '';
      notify('error', `No .json files were selected.${skippedNote}${zip}`);
      return;
    }
    const before = meta;
    const next = await runTask(`Importing ${plural(accepted.length, 'file')}`, progress => client.importFiles(accepted, progress));
    if (!next) return;
    // Filters chosen for the fictional demo would hide real history; extending a real import keeps them.
    if (before?.source !== 'import') {
      clearFilters();
      setResult(null);
      goTab('overview');
    }
    setMeta(next);
    setVersion(v => v + 1);
    setBootError(null);
    const added = before?.source === 'import' ? next.events - before.events : next.events;
    notify('success', `Imported ${plural(accepted.length, 'file')}: ${fmtInt(Math.max(0, added))} new events, ${fmtInt(next.events)} in total.${skippedNote}`);
  }

  async function startDemo() {
    setConfirm(null);
    const next = await runTask('Loading the synthetic demo', () => client.loadDemo());
    if (!next) return;
    clearFilters();
    setResult(null);
    setMeta(next);
    setVersion(v => v + 1);
    setBootError(null);
    goTab('overview');
    notify('info', 'Synthetic demo loaded. Every record in it is fictional.');
  }

  async function clearBrowserData() {
    setConfirm(null);
    const done = await runTask('Deleting the browser copy', async () => { await client.clearData(); return true; });
    if (!done) return;
    setMeta(null);
    setResult(null);
    setBootError(null);
    clearFilters();
    setVersion(v => v + 1);
    goTab('overview');
    notify('success', 'Browser copy deleted. Your original Spotify files were not touched.');
  }

  const bounds: DataBounds = useMemo(() => (meta
    ? { first: isoToYmd(meta.first, filters.timezone), last: isoToYmd(meta.last, filters.timezone) }
    : { first: '', last: '' }), [meta, filters.timezone]);
  const filtered = activeChips(filters, bounds, result?.options.years ?? []).length > 0;
  const busy = task !== null;
  const tabLabel = TABS.find(t => t.id === tab)?.label ?? '';

  let content: ReactNode;
  if (booting) {
    content = (
      <div className="card loading-state" role="status">
        <Spinner size={22} />
        <div><strong>Opening your listening archive</strong><p>Checking this browser for a saved history.</p></div>
      </div>
    );
  } else if (!meta) {
    content = (
      <>
        {bootError && (
          <Notice tone="error" title="The saved history could not be opened" action={
            <button type="button" className="btn btn-sm" onClick={() => setConfirm('clear')}><Trash2 size={14} aria-hidden="true" /> Clear saved data</button>
          }>{bootError}</Notice>
        )}
        <Welcome onPick={onPick} onDemo={startDemo} busy={busy} />
      </>
    );
  } else if (tab === 'data') {
    content = (
      <DataView meta={meta} timezone={filters.timezone} busy={busy} onPick={onPick}
        onDemo={() => (meta.source === 'import' ? setConfirm('demo') : void startDemo())} onClear={() => setConfirm('clear')} />
    );
  } else if (!result) {
    content = queryError ? (
      <Notice tone="error" title="This view could not be calculated" action={
        <button type="button" className="btn btn-sm" onClick={() => setRetry(r => r + 1)}><RotateCcw size={14} aria-hidden="true" /> Try again</button>
      }>{queryError}</Notice>
    ) : (
      <div className="card loading-state" role="status">
        <Spinner size={22} />
        <div>
          <strong>Analyzing {fmtInt(meta.events)} events on this device</strong>
          <p>The first view of a large archive takes a few seconds while every timestamp is placed in {filters.timezone.replace(/_/g, ' ')}. Later filter changes are faster.</p>
        </div>
      </div>
    );
  } else if (result.summary.events === 0) {
    content = <EmptyResult onReset={clearFilters} />;
  } else if (tab === 'overview') {
    content = <OverviewView result={result} filters={filters} onArtist={filterArtist} onTitle={filterTitle} onTab={goTab} />;
  } else if (tab === 'favorites') {
    content = <FavoritesView result={result} filters={filters} onArtist={filterArtist} onTitle={filterTitle} />;
  } else if (tab === 'habits') {
    content = <HabitsView result={result} />;
  } else if (tab === 'discoveries') {
    content = <DiscoveriesView result={result} onArtist={filterArtist} onTitle={filterTitle} />;
  } else {
    content = (
      <HistoryView filters={filters} scope={result.scope} version={version} request={historyRequest} onRequest={setHistoryRequest}
        onArtist={filterArtist} notify={notify} refreshing={querying} />
    );
  }

  return (
    <div className={`app${meta ? '' : ' is-welcome'}`}>
      <a className="skip-link" href="#main">Skip to content</a>
      <Sidebar tab={tab} onTab={goTab} meta={meta} timezone={filters.timezone} />
      <div className="main-col">
        <MobileHeader tab={tab} onTab={goTab} meta={meta} />
        <main id="main" tabIndex={-1}>
          {meta && (
            <header className="page-head">
              <div className="page-titles">
                <p className="eyebrow">{meta.source === 'demo' ? 'Synthetic demo data' : 'Extended streaming history'}</p>
                <h1>{tabLabel}</h1>
              </div>
              <p className="page-range">
                {tab !== 'data' && result ? (
                  <>
                    {filtered && <span className="range-tag">Filtered</span>}
                    <strong>{fmtDate(result.scope.start)} to {fmtDate(result.scope.end)}</strong>
                    <span className="dot-sep" aria-hidden="true">·</span>
                    <span>{filters.timezone.replace(/_/g, ' ')}</span>
                  </>
                ) : (
                  <>
                    <strong>{fmtDate(bounds.first)} to {fmtDate(bounds.last)}</strong>
                    <span className="dot-sep" aria-hidden="true">·</span>
                    <span>{fmtInt(meta.events)} events</span>
                  </>
                )}
              </p>
            </header>
          )}
          {meta?.source === 'demo' && (
            <Notice tone="demo" title="Synthetic demo">
              These listening records are fictional and generated for exploring the interface. Import your own files from Data &amp; methods.
            </Notice>
          )}
          {meta && tab !== 'data' && (
            <FilterBar filters={filters} searchDraft={searchDraft} onSearchDraft={setSearchDraft} onChange={updateFilters}
              onClearAll={clearFilters} options={result?.options ?? null} bounds={bounds} />
          )}
          {meta && tab !== 'data' && queryError && result && (
            <Notice tone="error" title="The latest filters could not be applied" action={
              <button type="button" className="btn btn-sm" onClick={() => setRetry(r => r + 1)}><RotateCcw size={14} aria-hidden="true" /> Try again</button>
            }>{queryError} Showing the previous results.</Notice>
          )}
          <div className={`view${querying && result && tab !== 'data' ? ' is-refreshing' : ''}`} aria-busy={querying || booting || undefined}>
            {querying && result && tab !== 'data' && <div className="refresh-bar" role="status" aria-label="Updating results" />}
            {content}
          </div>
        </main>
      </div>

      {task && (
        <div className="task-panel" role="status" aria-live="polite">
          <Spinner />
          <div><strong>{task.title}</strong>{task.detail && <span>{task.detail}</span>}</div>
        </div>
      )}
      {toast && (
        <div className="toast-region">
          <Notice tone={toast.tone} onDismiss={() => setToast(null)}>{toast.text}</Notice>
        </div>
      )}

      <ConfirmDialog open={confirm === 'clear'} title="Delete the browser copy?" confirmLabel="Delete browser data" danger
        onConfirm={clearBrowserData} onCancel={() => setConfirm(null)}>
        <p>This removes the sanitized history this browser remembers. Your original Spotify files are not touched.</p>
        <p>A dataset prepared on disk with <code>npm run import</code> (<code>.local-data/history.json</code>) stays on disk, and this browser stops loading it automatically. Import files again any time.</p>
      </ConfirmDialog>
      <ConfirmDialog open={confirm === 'demo'} title="Replace your history with the demo?" confirmLabel="Load synthetic demo"
        onConfirm={startDemo} onCancel={() => setConfirm(null)}>
        <p>The synthetic demo replaces the imported history saved in this browser. To return to your own data afterwards, import your JSON files again.</p>
      </ConfirmDialog>
    </div>
  );
}
