# Spotify Dash

Explore your entire Spotify Extended Streaming History in a web browser or the native Android app. Import your JSON files to explore listening trends, favorites, discovery, daily rhythms, and individual playback records. No Spotify API key or login is needed.

**Your imported history stays on your device.** The web app processes data in a browser worker and remembers sanitized records in IndexedDB. Android processes and stores records locally in the app's private storage. Both discard IP addresses, usernames, and user-agent strings. Raw exports and imported datasets are excluded from the repository and production builds. The optional demo is fictional. Web screenshots show the project owner's listening history, shared as examples; Android screenshots use fictional demo data.

![Spotify Dash overview with date and device filters, listening statistics, and a monthly listening chart](docs/screenshots/overview.png)

## Get your extended streaming history

Request **Extended streaming history** to explore the lifetime of your account. The separate **Account data** package includes only the past year's basic streaming history. Spotify explains the difference in [Understanding your data](https://support.spotify.com/us/article/understanding-your-data/).

1. Sign in to your [Spotify Account Privacy page](https://www.spotify.com/account/privacy/) in a browser and find **Download your data**.
2. Select **Extended streaming history** and submit the request. The other packages, **Account data** and **Technical log information**, are not needed for this dashboard.
3. Follow any confirmation prompts and check your account email for a request-confirmation message. Confirm the request if Spotify asks you to, and check spam or junk if the email is missing.
4. Wait for Spotify's email saying the download is ready. Allow around **30 days** for preparation, as described in [Spotify's guidance](https://community.spotify.com/forum/en/discussion/7376480/song-historic-by-date-and-time).
5. Download the ZIP and extract it. On Windows, right-click the ZIP and choose **Extract All**. Open the extracted **Spotify Extended Streaming History** folder.
6. Open the [Android app](#android) or [run the web dashboard](#run-locally), then select all `Streaming_History_*.json` files or choose the extracted folder. The web app also accepts drag and drop. You can import from **Data & methods** after opening either app.

Include every numbered/yearly part to cover the full export; audio and video files can be selected together. Import the extracted JSON files, not the ZIP or the included PDF. Keep the originals for your own backup. While waiting for the export, you can explore the app using its fictional demo.

If the download option is missing or the request does not arrive, see Spotify's [data download help](https://support.spotify.com/us/article/data-rights-and-privacy-settings/) for support and contact options.

## Android

Install **Spotify Dash** through **Klobstore 0.1.5 or newer**, or download the signed APK from [GitHub Releases](https://github.com/xklob/spotify-dash/releases/latest). Requires Android 9 or newer.

The native app includes all six sections, global filters, interactive charts, searchable rankings, playback details, and full filtered CSV export. Import JSON files or an extracted export folder through Android's file picker. It works offline and requests no network or broad storage permission. Each device keeps its own history; there is no account sync.

See the [Android guide](android/README.md) for installation, features, build instructions and release signing.

<p>
  <img src="docs/screenshots/android-overview.png" width="280" alt="Native Android overview showing fictional demo statistics and six navigation tabs">
  <img src="docs/screenshots/android-trends.png" width="280" alt="Native Android monthly trend chart with a selected month's values and artist composition controls">
</p>

The app uses native Kotlin and Jetpack Compose. [Validation results](android/VALIDATION.md) cover parity with the web calculations, a full archive import, Android file selection and CSV export, and emulator UI checks.

## Run locally

Requires Node.js 22.12 or newer.

```sh
npm ci
npm run build
npm start
```

Open **http://localhost:4187** and choose your `Streaming_History_*.json` files. You can select all audio and video files together, or use the folder picker. Reimporting files merges the history and removes duplicate source records. Importing real history replaces the fictional demo.

For development, use `npm run dev` at the same address.

To prepare a local dataset directly from an export folder:

```sh
npm run import -- "/path/to/Spotify Extended Streaming History"
npm run build
npm start
```

On Windows with this project in WSL, a Windows folder is accessible under `/mnt/c`, for example `/mnt/c/Users/YourName/Desktop/my_spotify_data/Spotify Extended Streaming History`. The import command writes only `.local-data/history.json`, which is ignored by Git. The server binds to loopback. On first use the local app loads this file automatically, then uses its browser cache. After a later command-line import, import the updated files through the app or clear this site's browser storage to replace the cache.

Clearing data in the app deletes its browser copy and disables automatic local loading. It does not delete your original Spotify export or `.local-data/history.json`. You can import files again at any time.

## Explore

- **Overview:** listening time, qualifying plays, unique titles and creators, monthly trends, artist composition, and top favorites.
- **Favorites:** artists/shows, titles, and albums; sort by listening time or event count, search the full rankings, and drill into an artist.
- **Habits:** weekday and hourly patterns, activity calendar, devices, countries, playback endings, and skip rates with shuffle on/off.
- **Discoveries:** first-seen artists, familiar listening share, yearly comparisons, repeat obsessions, rediscoveries, and a daily listening correlation.
- **History:** searchable, paginated playback records with Spotify links and CSV export of the full filtered population.
- **Data & methods:** import management, source coverage, duplicate accounting, and definitions.

Date range, content type, minimum playback duration, search, artist, device, country, shuffle, and timezone filters apply across the app. The default timezone is America/New_York and can be changed. No Spotify requests are made unless you explicitly open a catalog link.

## Screenshots

Expand a view below for more examples, or click an image to see it at full size.

<details>
<summary>Artist mix over time and top favorites</summary>

![Monthly listening time by artist, with top artist and title rankings beneath the chart](docs/screenshots/artist-trends.png)

</details>

<details>
<summary>Listening highlights and streaks</summary>

![Listening highlights showing the strongest day, longest streak, busiest month and year, new artists, and top artists' share](docs/screenshots/highlights.png)

</details>

<details>
<summary>Artist and show rankings</summary>

![Searchable artist and show rankings with listening time, event counts, skip rates, and first and last played dates](docs/screenshots/artist-rankings.png)

</details>

<details>
<summary>Title rankings</summary>

![Title rankings with track names, artists, listening time, playback statistics, and Spotify links](docs/screenshots/title-rankings.png)

</details>

<details>
<summary>Album rankings</summary>

![Album rankings sorted by listening time, with search and event-count sorting controls](docs/screenshots/album-rankings.png)

</details>

## How the numbers work

The source is Spotify's [Extended Streaming History schema](https://support.spotify.com/us/article/understanding-your-data/), plus the `ReadMeFirst_ExtendedStreamingHistory.pdf` included with the download.

| Measurement | Definition |
| --- | --- |
| Listening time | Sum of the exported `ms_played` values. Overlapping sessions may count simultaneously, so this is not elapsed wall-clock time. |
| Events | Every retained playback record, including zero-duration events unless filtered out. |
| Plays ≥30 seconds | Events with at least 30,000 milliseconds played. This is an app convention, not a claim about Spotify's billing or Wrapped rules. |
| Distinct titles | Spotify URI, falling back to content type + creator + title + album. Tracks, episodes, and audiobook chapters are included when selected. |
| Artists / shows | Distinct exported creator names. Spotify supplies artist names, not artist IDs; equally named creators may be combined. Audiobooks use the book title because author metadata is absent. |
| Skip rate | Records with `skipped=true` divided by records with a known boolean skip flag. Unknown flags are excluded from the denominator. Playback ending reasons are shown separately. |
| Active day / streak | A calendar day with positive recorded listening time, and consecutive such days, within the selected timezone and filters. |
| New artist | First appearance in the entire imported history falls in the relevant selected period. This is first recorded exposure, not necessarily real-world discovery. |
| Familiar listening | Share of listening time spent on a title recorded on an earlier calendar day. First-seen dates use the full import even when the view is filtered. |
| Yearly taste overlap | Jaccard overlap of each year's top 20 creators by recorded time with the previous year: intersection divided by union. Small or partial years are not equivalent to complete years. |
| Rediscovery | At least 180 calendar days between consecutive positive-duration records of the same title among the filtered records. Filters may lengthen these gaps. |
| Correlation | Pearson r between daily listening minutes and distinct titles, using days with positive recorded time. It is descriptive; more listening naturally provides more opportunities to hear titles. Fewer than three days or constant values produce no estimate. |

Timestamps record when playback **ended**, in UTC. All duration is assigned to that ending hour/day/month after timezone conversion; sessions crossing midnight are not split. Zero-filled calendar days mean no events in this export and selected population, not proof that you listened to nothing. Partial first/last years are marked. Historical travel timezones cannot be reconstructed from the export, so one chosen timezone applies to all years.

Exact source records within the same audio/video category are deduplicated using a stable source-record fingerprint before personal fields are discarded. Records differing in source details are preserved. The web app uses a timestamp plus two compact hashes; Android uses SHA-256. These identifiers serve duplicate detection. Invalid timestamps and non-integer, negative, or out-of-range durations are excluded and counted. URI-less items are supported. Playback platform strings are grouped into broad device families; country codes reflect connection country, not an inferred home address.

Genre, BPM, danceability, mood, track release dates, and true track durations are absent from this export. The app does not invent them or infer completion percentages from playback duration.

## Development and validation

```sh
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

`npm test` checks independent small fixtures for deduplication, privacy, weighted rates, DST/date filtering, discovery, calendar continuity, pagination, CSV injection protection, and correlation. Browser tests cover actual chart rendering and the import/filter/export experience. `npx tsx scripts/validate-local.ts` additionally reconciles your ignored local dataset across summaries, rankings, monthly/daily/hourly charts, and filtered history; its detailed output stays in `.private/`.

The frontend uses React, TypeScript, Vite, Recharts, and locally bundled icons. `src/lib/normalize.ts` owns import validation, `analytics.ts` owns calculations, and `worker.ts` keeps expensive parsing and aggregation off the UI thread. There is no application backend, telemetry, advertising, or third-party runtime asset dependency.

## Static hosting

`npm run build` produces `dist/`, which can be hosted on GitHub Pages or another static host. Publish only `dist/`, never `.local-data`. Each visitor imports their own data in their own browser. For local preloading use `npm start`; a static host intentionally has no local-data endpoint. Browser data belongs to its origin and does not transfer between localhost and hosted versions.

The project is independent of Spotify and is not endorsed by Spotify.
