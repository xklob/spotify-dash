const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const WEEKDAYS_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const HOUR_MS = 3_600_000;

const intFmt = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const oneFmt = new Intl.NumberFormat('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const compactFmt = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });

export const fmtInt = (n: number | null | undefined) => (n == null || !Number.isFinite(n) ? 'n/a' : intFmt.format(n));
export const fmtCompact = (n: number) => (Number.isFinite(n) ? compactFmt.format(n) : 'n/a');
export const plural = (n: number, one: string, many = `${one}s`) => `${fmtInt(n)} ${n === 1 ? one : many}`;
export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** Hours without a unit: 12,345 / 345 / 34.5 / 3.4 / <0.1 */
export function fmtHoursValue(h: number): string {
  if (!Number.isFinite(h)) return 'n/a';
  if (h === 0) return '0';
  if (h < 0.1) return '<0.1';
  return h >= 100 ? intFmt.format(h) : oneFmt.format(h);
}
export const fmtHours = (h: number) => `${fmtHoursValue(h)} h`;
export const fmtMsHours = (ms: number) => fmtHours(ms / HOUR_MS);

export function fmtMinutes(min: number): string {
  if (!Number.isFinite(min)) return 'n/a';
  if (min === 0) return '0 min';
  if (min < 1) return '<1 min';
  if (min < 60) return `${Math.round(min)} min`;
  let h = Math.floor(min / 60), m = Math.round(min - h * 60);
  if (m === 60) { h += 1; m = 0; }
  return m ? `${h} h ${m} min` : `${h} h`;
}

/** Clock-style playback length: 0:42, 3:05, 1:02:09 */
export function fmtClock(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return 'n/a';
  const total = Math.round(ms / 1000);
  const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
  const ss = String(s).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

export const fmtPct = (frac: number | null | undefined, digits = 1) =>
  frac == null || !Number.isFinite(frac) ? 'n/a' : `${(frac * 100).toFixed(digits)}%`;

// Calendar strings are parsed by hand so labels never shift with the browser's own timezone.
export function parseYmd(s: string): [number, number, number] | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}
export function weekdayIndex(s: string): number {
  const p = parseYmd(s);
  return p ? (new Date(Date.UTC(p[0], p[1] - 1, p[2])).getUTCDay() + 6) % 7 : 0;
}
export function addDays(s: string, n: number): string {
  const p = parseYmd(s);
  return p ? new Date(Date.UTC(p[0], p[1] - 1, p[2] + n)).toISOString().slice(0, 10) : s;
}
export const daysInMonth = (year: number, month: number) => new Date(Date.UTC(year, month, 0)).getUTCDate();

export function fmtDate(s: string | null | undefined): string {
  if (!s) return 'n/a';
  const p = parseYmd(s);
  return p ? `${MONTHS[p[1] - 1]} ${p[2]}, ${p[0]}` : s;
}
export function fmtDateLong(s: string): string {
  const p = parseYmd(s);
  return p ? `${WEEKDAYS[weekdayIndex(s)]}, ${MONTHS_LONG[p[1] - 1]} ${p[2]}, ${p[0]}` : s;
}
export function fmtDayMonth(s: string): string {
  const p = parseYmd(s);
  return p ? `${MONTHS[p[1] - 1]} ${p[2]}` : s;
}
export function fmtMonth(period: string, long = false): string {
  const m = /^(\d{4})-(\d{2})/.exec(period);
  return m ? `${(long ? MONTHS_LONG : MONTHS)[Number(m[2]) - 1]} ${m[1]}` : period;
}
export function fmtMonthTick(period: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(period);
  if (!m) return period;
  return m[2] === '01' ? m[1] : `${MONTHS[Number(m[2]) - 1]} '${m[1].slice(2)}`;
}
export const monthShort = (index: number) => MONTHS[index];

/** Calendar date (YYYY-MM-DD) of an ISO timestamp in a given IANA zone. */
export function isoToYmd(iso: string, timeZone: string): string {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return '';
  try {
    const parts: Record<string, string> = {};
    for (const p of new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(t)) parts[p.type] = p.value;
    return `${parts.year}-${parts.month}-${parts.day}`;
  } catch {
    return new Date(t).toISOString().slice(0, 10);
  }
}
export function fmtDateTime(iso: string, timeZone: string): string {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return 'n/a';
  try {
    return new Intl.DateTimeFormat('en-US', { timeZone, dateStyle: 'medium', timeStyle: 'short' }).format(t);
  } catch {
    return new Date(t).toISOString();
  }
}
export function fmtSpan(days: number): string {
  if (days >= 365) {
    const y = days / 365.25;
    return `${y.toFixed(y >= 10 ? 0 : 1)} years`;
  }
  return `${Math.round(days / 30.44)} months`;
}

export function spotifyUrl(uri: string | null | undefined): string | null {
  const m = /^spotify:(track|episode|show|album|artist|playlist|audiobook|chapter):([A-Za-z0-9]+)$/.exec(uri ?? '');
  return m ? `https://open.spotify.com/${m[1]}/${m[2]}` : null;
}

const REASONS: Record<string, string> = {
  trackdone: 'Played to the end',
  fwdbtn: 'Forward button',
  backbtn: 'Back button',
  endplay: 'Playback ended',
  clickrow: 'Another item chosen',
  playbtn: 'Play button',
  remote: 'Remote device',
  logout: 'Logged out',
  trackerror: 'Playback error',
  'unexpected-exit': 'App exited unexpectedly',
  'unexpected-exit-while-paused': 'App exited while paused',
  appload: 'App loaded',
  popup: 'Popup',
  uriopen: 'Link opened',
  unknown: 'Not recorded',
};
export const reasonLabel = (code: string) => REASONS[code] ?? code.replace(/[-_]+/g, ' ');

let regions: Intl.DisplayNames | null | undefined;
export function countryName(code: string): string {
  if (!/^[A-Z]{2}$/.test(code)) return code === 'Unknown' ? 'Not recorded' : code;
  if (regions === undefined) {
    try { regions = new Intl.DisplayNames(['en'], { type: 'region' }); } catch { regions = null; }
  }
  try { return regions?.of(code) ?? code; } catch { return code; }
}

const KINDS: Record<string, string> = { music: 'Music', podcast: 'Podcast', audiobook: 'Audiobook', unknown: 'Unknown' };
export const kindLabel = (kind: string) => KINDS[kind] ?? kind;
