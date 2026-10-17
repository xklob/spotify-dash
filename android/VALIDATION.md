# Android validation

## Version 1.2.0: musical mood and catalog metadata

Version code 2. Built with Java 17 and Android SDK 36. Validation uses disposable Android 16 emulators; no physical-phone result is claimed.

- Eight JVM tests pass, including six shared web/native mood scenarios. Hand-calculated fixtures verify time/event/distinct-track weighting, missing and zero scores, exact-ID joins, coverage denominators, calendar gaps, three-period distinct-track deduplication, DST boundaries, and incompatible or invalid metadata.
- The web suite passes all 17 unit tests and five Chromium browser tests. Metadata/history import, export, persistence, errors, filter changes, smoothing, chart/table switches, desktop/mobile layout, and date drill-downs are covered. A live browser catalog check used only two GET requests containing track IDs; it sent no history or audio.
- The native mood instrumentation flow passes metadata import, all nine chart sections, weighting changes, portable export, activity recreation, and invalid-import recovery. The navigation regression passes with all seven sections and the phone's More menu.
- The signed, minified APK imports history and catalog metadata through Android's system file pickers. Its system Save picker exports a JSON file exactly equal to the five-record input fixture, proving portable field names survive release obfuscation. Scores and metadata survive force-stop and cold relaunch.
- Phone, landscape, tablet, and 200% text layouts were inspected. Mood statistic tiles adapt to available width and text size. Chart selections show scores and coverage; numeric table alternatives remain available.
- Release/debug builds and release lint pass. The APK inspection confirms version/package, the existing signing certificate, 16 KiB alignment, disabled backups, no network or broad storage permission, and the unchanged fictional demo asset. Public web assets contain no personal history or metadata payloads.

The real-archive mood check runs under Android's normal 192 MiB heap and compares all three weightings against an independent Python calculation. It reconciles monthly, weekday/hour, annual, and distribution totals. Private data and detailed reports remain outside Git.

`MoodUiTest` replaces only the disposable debug app's dataset with fictional fixtures. `DataDeviceTest#fullMoodArchiveWhenExplicitlyStaged` is opt-in via `-e moodArchive true`, and consumes an explicitly staged private archive, metadata cache, and independent expected totals. CI does not receive personal data.

## Version 1.1.0 baseline

The following checks established the original history explorer and remain the baseline for unchanged features.

Version 1.1.0, version code 1. Validation uses Java 17, Android SDK 36, and an Android 16 emulator. No physical-phone result is claimed.

## Analysis parity

Six JVM tests compare the native engine with the web engine on shared fictional inputs. They cover five filter scenarios, every returned analytical view, daylight-saving boundaries, exact source deduplication, discarded personal fields, null flags, malformed and wrong-package imports, persistence, pagination, complete CSV export, formula injection protection, and insufficient data for correlation.

An additional opt-in test imported a complete private archive and reconciled 220,033 retained events and 37,144,635,265 milliseconds with the web baseline. It also checked title/artist counts, qualifying plays, active days and streaks, every yearly comparison, Pearson correlation, and the sums behind monthly/daily/hourly/composition charts and rankings. The 2024 music filter with at least 30 seconds returned 19,017 events in both engines. Source files and detailed results remain outside Git.

## Android data handling

The full archive was also imported on the emulator with its normal 192 MiB application heap. A separate cache-reload check reconciled the saved dataset and exported all 19,017 filtered rows. Independent CSV parsing confirmed the exact row count and 3,978,259,978-millisecond total.

Manual checks through the native interface verified:

- A full saved archive opens with the same summary as the web app; filters survive force-stop and relaunch.
- Android's Save picker writes the entire filtered CSV, with the same row count and duration total as the independent baseline.
- Android's JSON picker replaces the fictional demo with imported records. Reimporting the same file keeps the retained event count unchanged and updates duplicate accounting.
- Android's folder picker imports all 28 JSON parts of the full archive, ignores non-JSON files, and reaches the same 220,033-event summary.
- A malformed import keeps the prior dataset intact.
- Deletion requires confirmation and returns to the import screen without deleting the original files.

The fictional demo asset has an instrumentation test that reads the bytes from the installed APK, round-trips the native storage format, and verifies the analysis totals.

The native UI instrumentation flow passes on the emulator. It loads the demo, visits all six sections, applies a global search that produces an empty result, resets filters, reaches the final history page, checks the full CSV action, cancels and then confirms deletion, and verifies the cleared state after activity recreation. It uses native accessibility actions so scrolling and keyboard animation do not make button coordinates unreliable.

Rendered phone, landscape, tablet, and 200% text-size views were inspected, including chart selection readouts, numeric table alternatives, artist drill-down, and timezone changes. Large text switches dense statistic grids to fewer columns and keeps navigation accessible.

## Build and artifact checks

Debug/release builds and Android lint pass. `scripts/check-android-build.py` inspects the actual APK for its package/version, launchable activity, signature, 16 KiB ZIP alignment, disabled backups, and absence of network or broad storage permissions. It checks the bundled demo byte-for-byte against the reviewed fictional source asset and rejects private payload paths.

The web regression suite also passes: 11 unit tests and three Chromium browser tests, plus the production build and public-asset inspection.

## Reproduce

See [the Android guide](README.md#build) for local build and device-test commands. `DashboardUiTest` uses a disposable emulator and replaces the debug app's local dataset with fiction. The full-archive checks are opt-in; they do not run against personal data in CI.

For local full-archive reconciliation, set `SPOTIFY_DASH_VALIDATE_SOURCE` to the extracted export folder and `SPOTIFY_DASH_VALIDATE_EXPECTED` to the ignored report from `scripts/validate-local.ts`. `SPOTIFY_DASH_VALIDATE_REPORT` optionally writes a private native report. Then run:

```sh
cd android
./gradlew testDebugUnitTest --tests com.caleb.spotifydash.FullArchiveTest --rerun-tasks
```

Timing measurements are emulator observations, not performance guarantees for every phone. The app keeps Android and browser datasets separate and supports extracted JSON files, not ZIP import or account sync.
