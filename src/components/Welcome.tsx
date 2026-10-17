import { ArrowUpRight, FlaskConical, Lock } from 'lucide-react';
import { ImportPanel, type PickedFiles } from './ImportPanel';
import { WaveGlyph } from './ui';

export const SPOTIFY_DATA_URL = 'https://support.spotify.com/us/article/understanding-your-data/';
export const REPO_URL = 'https://github.com/xklob/spotify-dash';

export function Welcome({ onPick, onDemo, busy }: { onPick: (picked: PickedFiles) => void; onDemo: () => void; busy: boolean }) {
  return (
    <div className="welcome">
      <section className="welcome-main" aria-labelledby="welcome-title">
        <div className="welcome-glyph"><WaveGlyph size={34} /></div>
        <p className="eyebrow">No history loaded</p>
        <h1 id="welcome-title">Open your Spotify Extended Streaming History</h1>
        <p className="welcome-lede">
          Spotify Dash reads the <code>Streaming_History_*.json</code> files from your Spotify data download and turns them into
          trends, favorites, habits, and a searchable record of every playback. Everything runs in this browser.
        </p>
        <ImportPanel onPick={onPick} disabled={busy} />
        <div className="welcome-alt">
          <button type="button" className="btn" onClick={onDemo} disabled={busy}>
            <FlaskConical size={15} aria-hidden="true" /> Explore a synthetic demo
          </button>
          <span>Fictional, generated listening. Not anyone's real history.</span>
        </div>
      </section>

      <aside className="welcome-side">
        <section className="card">
          <h2>Getting the files</h2>
          <ol className="steps">
            <li>In your Spotify account privacy settings, request <strong>Extended streaming history</strong>. Spotify emails a download link, which can take several days.</li>
            <li>Unzip the download. The folder holds files such as <code>Streaming_History_Audio_2019-2021_3.json</code>.</li>
            <li>Select every JSON file at once, or choose the whole folder. Other files, like the PDF readme, are skipped.</li>
          </ol>
          <a className="text-link" href={SPOTIFY_DATA_URL} target="_blank" rel="noopener noreferrer">
            Spotify: Understanding your data <ArrowUpRight size={14} aria-hidden="true" />
          </a>
        </section>
        <section className="card">
          <h2><Lock size={15} aria-hidden="true" /> Stays on this device</h2>
          <ul className="plain-list">
            <li>Files are parsed in a background worker and never uploaded.</li>
            <li>IP addresses, usernames, and user agents are discarded during import.</li>
            <li>This browser remembers the sanitized history until you clear it.</li>
            <li>No login, API key, or remote assets. Spotify opens only if you click a link.</li>
          </ul>
        </section>
      </aside>
    </div>
  );
}
