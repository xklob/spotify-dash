import type { HistoryEvent } from './types';

function cell(value: unknown): string {
  let s = value === null || value === undefined ? '' : String(value);
  // Spreadsheet formula injection protection for user-controlled metadata.
  if (/^[\s]*[=+@-]/.test(s) || /^[\t\r\n]/.test(s)) s = "'" + s;
  return '"' + s.replace(/"/g, '""') + '"';
}
export function eventsCsv(events: (HistoryEvent & { date: string; time: string })[], timezone: string): string {
  const headers = ['ended_utc', 'local_date', 'local_time', 'timezone', 'title', 'artist_or_show', 'album', 'kind', 'media', 'milliseconds_played', 'platform', 'country', 'skipped', 'shuffle', 'offline', 'end_reason', 'spotify_uri'];
  const rows = events.map(e => [new Date(e.ts).toISOString(), e.date, e.time, timezone, e.title, e.artist, e.album, e.kind, e.media, e.ms, e.platform, e.country, e.skipped, e.shuffle, e.offline, e.reasonEnd, e.uri].map(cell).join(','));
  return '\ufeff' + [headers.join(','), ...rows].join('\r\n');
}
