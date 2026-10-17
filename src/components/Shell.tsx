import { Clock3, Database, Heart, History, LayoutDashboard, Lock, Sparkles, Activity, type LucideIcon } from 'lucide-react';
import type { DatasetMeta } from '../lib/types';
import { fmtInt, fmtMonth, isoToYmd, plural } from './format';
import { Wordmark } from './ui';

export type TabId = 'overview' | 'favorites' | 'habits' | 'discoveries' | 'mood' | 'history' | 'data';
export const TABS: { id: TabId; label: string; icon: LucideIcon }[] = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'favorites', label: 'Favorites', icon: Heart },
  { id: 'habits', label: 'Habits', icon: Clock3 },
  { id: 'discoveries', label: 'Discoveries', icon: Sparkles },
  { id: 'mood', label: 'Mood & energy', icon: Activity },
  { id: 'history', label: 'History', icon: History },
  { id: 'data', label: 'Data & methods', icon: Database },
];
export const isTab = (value: string): value is TabId => TABS.some(t => t.id === value);

export function NavList({ tab, onTab, variant }: { tab: TabId; onTab: (tab: TabId) => void; variant: 'side' | 'strip' }) {
  return (
    <nav className={variant === 'side' ? 'side-nav' : 'tab-strip'} aria-label="Sections">
      <ul>
        {TABS.map(t => (
          <li key={t.id}>
            <button type="button" aria-current={tab === t.id ? 'page' : undefined} onClick={() => onTab(t.id)}>
              <t.icon size={16} aria-hidden="true" />
              <span>{t.label}</span>
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function DatasetCard({ meta, timezone, onOpen }: { meta: DatasetMeta; timezone: string; onOpen: () => void }) {
  const first = isoToYmd(meta.first, timezone), last = isoToYmd(meta.last, timezone);
  return (
    <button type="button" className={`dataset-card${meta.source === 'demo' ? ' is-demo' : ''}`} onClick={onOpen}
      aria-label={`Dataset details: ${meta.source === 'demo' ? 'synthetic demo' : 'imported archive'}, ${fmtInt(meta.events)} events`}>
      <span className="eyebrow">{meta.source === 'demo' ? 'Synthetic demo' : 'Imported archive'}</span>
      <span className="dataset-range">{fmtMonth(first)} <span aria-hidden="true">→</span><span className="sr-only">to</span> {fmtMonth(last)}</span>
      <span className="dataset-meta">{fmtInt(meta.events)} events · {plural(meta.files.length, 'file')}</span>
    </button>
  );
}

export function Sidebar({ tab, onTab, meta, timezone }: { tab: TabId; onTab: (tab: TabId) => void; meta: DatasetMeta | null; timezone: string }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <Wordmark />
        <p className="sidebar-tag">Extended history explorer</p>
      </div>
      {meta && <NavList tab={tab} onTab={onTab} variant="side" />}
      <div className="sidebar-foot">
        {meta && <DatasetCard meta={meta} timezone={timezone} onOpen={() => onTab('data')} />}
        <p className="privacy-line"><Lock size={13} aria-hidden="true" /> Analyzed on this device. Files are never uploaded.</p>
      </div>
    </aside>
  );
}

export function MobileHeader({ tab, onTab, meta }: { tab: TabId; onTab: (tab: TabId) => void; meta: DatasetMeta | null }) {
  return (
    <div className="mobile-top">
      <header className="mobile-header">
        <Wordmark />
        {meta && <span className={`mobile-source${meta.source === 'demo' ? ' is-demo' : ''}`}>{meta.source === 'demo' ? 'Demo data' : `${fmtInt(meta.events)} events`}</span>}
      </header>
      {meta && <NavList tab={tab} onTab={onTab} variant="strip" />}
    </div>
  );
}
