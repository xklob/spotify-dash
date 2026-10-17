import type { Dataset, DatasetMeta, Flag, HistoryEvent, Kind } from './types';

const text = (v: unknown): string => typeof v === 'string' ? v.trim() : '';
const flag = (v: unknown): Flag => typeof v === 'boolean' ? v : null;
export function platformFamily(value: unknown): string {
  const s = text(value).toLowerCase();
  if (/android/.test(s)) return 'Android';
  if (/windows/.test(s)) return 'Windows';
  if (/ios|iphone|ipad/.test(s)) return 'iOS';
  if (/osx|macintosh|macos|mac os/.test(s)) return 'macOS';
  if (/cast|chromecast/.test(s)) return 'Chromecast';
  if (/sonos/.test(s)) return 'Sonos';
  if (/web_player|web player|webplayer|browser/.test(s)) return 'Web player';
  if (/linux/.test(s)) return 'Linux';
  if (/playstation/.test(s)) return 'PlayStation';
  if (/xbox/.test(s)) return 'Xbox';
  if (/amazon|alexa|echo/.test(s)) return 'Alexa';
  return s ? 'Other' : 'Unknown';
}

// Two independent 32-bit hashes. Used only as a compact identifier, never for
// security decisions; duplicate removal compares the complete identity string.
function hash(s: string): string {
  let a = 2166136261, b = 5381;
  for (let i = 0; i < s.length; i++) {
    a = Math.imul(a ^ s.charCodeAt(i), 16777619);
    b = Math.imul(b, 33) ^ s.charCodeAt(i);
  }
  return (a >>> 0).toString(36) + '-' + (b >>> 0).toString(36);
}

function normalize(raw: unknown, filename: string): { event: HistoryEvent; identity: string } | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const r = raw as Record<string, unknown>;
  if (typeof r.ts !== 'string' || typeof r.ms_played !== 'number' || !Number.isSafeInteger(r.ms_played) || r.ms_played < 0) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})?$/.exec(r.ts);
  if (!match || Number(match[4]) > 23 || Number(match[5]) > 59 || Number(match[6]) > 59 ||
    new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))).toISOString().slice(0, 10) !== r.ts.slice(0, 10)) return null;
  // Spotify's documented timestamps are UTC. Accept older space-separated UTC.
  const stamp = r.ts.includes('T') ? r.ts : r.ts.replace(' ', 'T');
  const ts = Date.parse(/Z$|[+-]\d\d:\d\d$/.test(stamp) ? stamp : stamp + 'Z');
  if (!Number.isFinite(ts) || ts < Date.UTC(2006, 0, 1) || ts > Date.now() + 86400000) return null;
  let kind: Kind = 'unknown', title = '', artist = '', album = '', uri = '';
  if (text(r.master_metadata_track_name) || text(r.spotify_track_uri)) {
    kind = 'music'; title = text(r.master_metadata_track_name); artist = text(r.master_metadata_album_artist_name);
    album = text(r.master_metadata_album_album_name); uri = text(r.spotify_track_uri);
  } else if (text(r.episode_name) || text(r.spotify_episode_uri)) {
    kind = 'podcast'; title = text(r.episode_name); artist = text(r.episode_show_name); uri = text(r.spotify_episode_uri);
  } else if (text(r.audiobook_title) || text(r.audiobook_uri)) {
    kind = 'audiobook'; title = text(r.audiobook_chapter_title) || text(r.audiobook_title);
    artist = text(r.audiobook_title); album = text(r.audiobook_title);
    uri = text(r.audiobook_chapter_uri) || text(r.audiobook_uri);
  }
  if (!/^spotify:(track|episode|show|audiobook|chapter):[a-zA-Z0-9]+$/.test(uri)) uri = '';
  const event: HistoryEvent = {
    id: '', ts, ms: r.ms_played, kind, media: /video/i.test(filename) ? 'video' : 'audio',
    title: title || 'Unknown title', artist: artist || 'Unknown artist', album, uri,
    platform: platformFamily(r.platform), country: /^[A-Za-z]{2}$/.test(text(r.conn_country)) ? text(r.conn_country).toUpperCase() : 'Unknown',
    shuffle: flag(r.shuffle), skipped: flag(r.skipped), offline: flag(r.offline),
    incognito: flag(r.incognito_mode), reasonStart: text(r.reason_start) || 'unknown', reasonEnd: text(r.reason_end) || 'unknown',
  };
  // Compare the whole original record, with stable field ordering, so records
  // differing only in details omitted from the dashboard remain distinct.
  // This string is transient. Only a timestamp + compact fingerprint survives;
  // IP addresses, usernames and user agents are never written to storage.
  const identity = JSON.stringify([event.media, Object.keys(r).sort().map(k => [k, r[k]])]);
  event.id = ts.toString(36) + '-' + hash(identity);
  return { event, identity };
}

export class ImportBuilder {
  private events: HistoryEvent[];
  private seen: Set<string>;
  private ids: Set<string>;
  private files: Set<string>;
  private rawRows: number;
  private duplicates: number;
  private invalidRows: number;
  constructor(existing?: Dataset | null) {
    this.events = existing ? [...existing.events] : [];
    this.ids = new Set(this.events.map(e => e.id));
    this.seen = new Set();
    this.files = new Set(existing?.meta.files ?? []);
    this.rawRows = existing?.meta.rawRows ?? 0;
    this.duplicates = existing?.meta.duplicates ?? 0;
    this.invalidRows = existing?.meta.invalidRows ?? 0;
  }
  add(rows: unknown, filename: string): void {
    if (!Array.isArray(rows)) throw new Error(`${filename}: expected an array of Spotify extended streaming history records.`);
    if (rows.length && !rows.some(r => r && typeof r === 'object' && 'ts' in r && 'ms_played' in r)) {
      throw new Error(`${filename}: this is not an Extended Streaming History file. Choose the Streaming_History JSON files.`);
    }
    this.files.add(filename.split(/[\\/]/).pop() || 'history.json');
    for (const raw of rows) {
      this.rawRows++;
      const n = normalize(raw, filename);
      if (!n) { this.invalidRows++; continue; }
      // Across incremental imports only compact identities are available.
      if (this.seen.has(n.identity) || this.ids.has(n.event.id)) { this.duplicates++; continue; }
      this.seen.add(n.identity); this.ids.add(n.event.id); this.events.push(n.event);
    }
  }
  finish(source: DatasetMeta['source'] = 'import'): Dataset {
    this.events.sort((a, b) => a.ts - b.ts || a.id.localeCompare(b.id));
    if (!this.events.length) throw new Error('No valid listening records were found. Select your Extended Streaming History JSON files.');
    const warnings: string[] = [];
    if (this.invalidRows) warnings.push(`${this.invalidRows.toLocaleString()} invalid records were excluded.`);
    if (this.duplicates) warnings.push(`${this.duplicates.toLocaleString()} duplicate source records were removed.`);
    return { events: this.events, meta: {
      version: 1, source, files: [...this.files], importedAt: new Date().toISOString(),
      rawRows: this.rawRows, duplicates: this.duplicates, invalidRows: this.invalidRows,
      events: this.events.length, first: new Date(this.events[0].ts).toISOString(),
      last: new Date(this.events[this.events.length - 1].ts).toISOString(),
      totalMs: this.events.reduce((n, e) => n + e.ms, 0), warnings,
    } };
  }
}

export function validateDataset(value: unknown): Dataset {
  const d = value as Dataset;
  if (!d || d.meta?.version !== 1 || !Array.isArray(d.events) || !d.events.length ||
    d.events.some(e => !e || !Number.isFinite(e.ts) || !Number.isFinite(e.ms) || e.ms < 0 || typeof e.id !== 'string' || typeof e.title !== 'string')) {
    throw new Error('The saved history is invalid. Clear it and import your Spotify JSON files again.');
  }
  return d;
}
