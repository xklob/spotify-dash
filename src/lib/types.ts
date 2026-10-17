export type Kind = 'music' | 'podcast' | 'audiobook' | 'unknown';
export type Flag = boolean | null;
export interface HistoryEvent {
  id: string; ts: number; ms: number; kind: Kind; media: 'audio' | 'video';
  title: string; artist: string; album: string; uri: string;
  platform: string; country: string; shuffle: Flag; skipped: Flag;
  offline: Flag; incognito: Flag; reasonStart: string; reasonEnd: string;
}
export interface DatasetMeta {
  version: 1; source: 'import' | 'demo'; files: string[]; importedAt: string;
  rawRows: number; duplicates: number; invalidRows: number; events: number;
  first: string; last: string; totalMs: number; warnings: string[];
}
export interface Dataset { meta: DatasetMeta; events: HistoryEvent[] }
export interface Filters {
  start: string; end: string; kind: Kind | 'all'; minMs: number;
  search: string; artist: string; platform: string; country: string;
  timezone: string; shuffle: 'all' | 'yes' | 'no';
}
export const DEFAULT_FILTERS: Filters = {
  start: '', end: '', kind: 'all', minMs: 0, search: '', artist: '',
  platform: '', country: '', timezone: 'America/New_York', shuffle: 'all',
};
export interface Ranking {
  key: string; name: string; artist: string; album: string; uri: string;
  plays: number; ms: number; skipRate: number | null; skipKnown: number;
  first: string; last: string;
}
export interface PeriodPoint {
  period: string; hours: number; plays: number; artists: number;
  newArtists: number; skipRate: number | null; familiarShare: number | null;
}
export interface DailyPoint {
  date: string; hours: number; minutes: number; plays: number;
  tracks: number; artists: number; uniqueShare: number | null;
}
export interface GroupPoint {
  name: string; hours: number; plays: number; skipRate: number | null; skipKnown: number;
}
export interface QueryResult {
  summary: { events: number; qualifiedPlays: number; ms: number; tracks: number; artists: number;
    activeDays: number; calendarDays: number; skipRate: number | null; skipKnown: number;
    shuffleRate: number | null; shuffleKnown: number; averageMs: number | null;
    newArtists: number; longestStreak: number; topDay: { date: string; hours: number } | null;
    first: string; last: string; topArtistShare: number | null };
  monthly: PeriodPoint[];
  yearly: (PeriodPoint & { topArtist: string; partial: boolean; overlap: number | null })[];
  daily: DailyPoint[];
  heatmap: { day: number; hour: number; hours: number; plays: number }[];
  hours: GroupPoint[]; weekdays: (GroupPoint & { averageMinutes: number; calendarDays: number })[];
  artists: Ranking[]; tracks: Ranking[]; albums: Ranking[];
  platforms: GroupPoint[]; countries: GroupPoint[]; kinds: GroupPoint[];
  reasons: GroupPoint[]; shuffleComparison: GroupPoint[];
  composition: { period: string; [key: string]: string | number }[];
  compositionKeys: string[];
  rediscoveries: { name: string; artist: string; uri: string; gapDays: number; before: string; after: string }[];
  obsessions: { name: string; artist: string; month: string; plays: number; hours: number; uri: string }[];
  correlation: { r: number | null; n: number; label: string; explanation: string };
  options: { years: string[]; platforms: string[]; countries: string[]; artists: string[] };
  scope: { start: string; end: string; timezone: string };
}
export interface HistoryPage { rows: (HistoryEvent & { date: string; time: string })[]; total: number; page: number; pages: number }
