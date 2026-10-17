package com.caleb.spotifydash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.DashboardState
import java.time.temporal.ChronoUnit

const val SpotifyPrivacyUrl = "https://www.spotify.com/account/privacy/"
const val SpotifyDataUrl = "https://support.spotify.com/us/article/understanding-your-data/"
const val SpotifyHelpUrl = "https://support.spotify.com/us/article/data-rights-and-privacy-settings/"

private val ExportSteps = listOf(
    "Open your Spotify Account Privacy page in a browser, sign in, and find Download your data.",
    "Select Extended streaming history and submit the request. The Account data and Technical log information packages are not needed here.",
    "Confirm the request from Spotify's email if asked. Check spam or junk if the email is missing.",
    "Wait for Spotify's email saying the download is ready. Allow around 30 days.",
    "Download the ZIP and extract it. In the Files app, open the ZIP and choose Extract. Open the extracted Spotify Extended Streaming History folder.",
    "In this app, choose that folder, or select every Streaming_History JSON file. Include every numbered or yearly part; audio and video files can be added together. The ZIP and the PDF readme are not needed.",
)

private val PrivacyPoints = listOf(
    "Your files are read on this device and never uploaded. Spotify Dash has no internet access at all.",
    "IP addresses, usernames, and device details are discarded while importing.",
    "The cleaned history stays in this app's private storage, outside device backups, until you delete it.",
    "Imported catalog metadata is also stored privately and cleared with your history. No local audio files are used.",
    "Spotify opens only when you tap a Spotify link, in its own app or your browser.",
)

/** Definitions behind every number in the app, in plain language. */
private val Methods = listOf(
    "Listening time" to "The sum of Spotify's recorded playback time (ms_played). Sessions can overlap, for example on two devices at once, so totals can exceed elapsed wall-clock time.",
    "Events" to "Every kept playback record, including zero-length ones unless you raise Minimum played. Rankings and charts count events unless they say otherwise.",
    "Plays of 30 s or more" to "Events with at least 30,000 milliseconds played. This is the app's convention for a counted play, not a claim about Spotify's royalty or Wrapped rules.",
    "End timestamps" to "Spotify records when playback ended, in UTC. Each event, with all of its playback time, is placed in the hour, day, month, and year in which it ended, in the selected timezone. Sessions that cross midnight are not split, so hourly patterns lean toward when listening stopped.",
    "Timezone" to "One chosen timezone applies to every year. Travel timezones cannot be recovered from the export.",
    "Titles" to "A title is its Spotify URI, or its type, creator, title, and album when there is no URI. Tracks, podcast episodes, and audiobook chapters are included when selected.",
    "Artists and shows" to "Exported creator names: the artist for music, the show for podcasts, and the book title for audiobooks, because author names are absent. Without artist IDs, creators sharing a name are combined.",
    "Skips" to "Skip rate divides events marked skipped by events with a recorded skip flag. Events without a flag are left out of both, and every rate shows its denominator (n). How playback ended is reported separately.",
    "Shuffle" to "Shuffle rates use only events with a recorded shuffle flag. Comparing skips with shuffle on and off describes the records; it does not show that shuffle causes skipping.",
    "Active days and streaks" to "An active day has positive recorded listening in the selected timezone and filters. A streak is a run of consecutive active days. A zero day means no events in this export and selection, not proof that you listened to nothing.",
    "New and familiar" to "New means the first appearance anywhere in the imported history: first recorded exposure, not necessarily the first time you ever heard it. Familiar listening is the share of time on titles first recorded on an earlier calendar day. First-seen dates always come from the full import, even when the view is filtered.",
    "Yearly overlap" to "The Jaccard overlap of consecutive years' top 20 artists or shows by time: names in both lists divided by names in either. Partial first and last years are marked and are not equivalent to complete years.",
    "Longest returns" to "Gaps of 180 days or more between consecutive positive-length records of the same title, among the filtered records. Filters can lengthen these gaps.",
    "Correlation" to "Pearson r between daily minutes and distinct titles, across days with recorded listening, with its n. It describes association, not cause: more listening naturally gives more chances to hear different titles. Fewer than three days, or no variation, gives no estimate.",
    "Date ranges" to "Recent ranges such as Last 365 days end at the last imported day, not today. Partial first and last months are noted in charts and tables.",
    "Duplicates and invalid records" to "Exact copies of a source record within the audio or video history are kept once, across files and repeated imports. Records that differ in any detail stay separate. Records with invalid timestamps or impossible durations are skipped and counted. Items without a Spotify URI are still included.",
    "Devices and countries" to "Platform strings are grouped into broad device families. Country is the connection country Spotify recorded, not a home address.",
    "Catalog enrichment" to "Tempo (BPM), musical mood, audio features, and full track durations are absent from Spotify's history files. Mood & energy can import ReccoBeats catalog estimates exported by the web dashboard. Exact track IDs join the scores; missing values stay unknown and coverage is shown. These describe the music, not your emotional state. Genre and release dates are not enriched in this version.",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(state: DashboardState, actions: DashActions, gutter: Dp, wide: Boolean, modifier: Modifier) {
    val meta = state.meta ?: return
    val demo = meta.source == "demo"
    val tz = state.filters.timezone
    val busy = state.busy
    val listState = rememberLazyListState()
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "loaded") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PanelHeader(
                            "Loaded data",
                            if (demo) "Fictional demo: generated records, not anyone's real listening" else "Imported Spotify Extended Streaming History",
                        )
                    }
                    if (demo) Tag("Fictional", color = Palette.VioletText, background = Palette.VioletWash)
                }
                val b = DataBounds.of(meta, tz)
                val span = if (b.first != null && b.last != null) ChronoUnit.DAYS.between(b.first, b.last).toInt() + 1 else null
                FactGrid(
                    listOf(
                        FactItem("Events kept", Fmt.int(meta.events)),
                        FactItem("Source records read", Fmt.int(meta.rawRows)),
                        FactItem("Exact duplicates removed", Fmt.int(meta.duplicates)),
                        FactItem("Invalid records skipped", Fmt.int(meta.invalidRows)),
                        FactItem("First event ended", Fmt.dateTime(meta.first, tz)),
                        FactItem("Last event ended", Fmt.dateTime(meta.last, tz)),
                        FactItem("Coverage", span?.let { Fmt.plural(it, "calendar day") } ?: "n/a", span?.let { if (it >= 365) Fmt.span(it) else null }),
                        FactItem("Listening time", Fmt.msHours(meta.totalMs)),
                        FactItem("Last updated", Fmt.dateTime(meta.importedAt, tz)),
                        FactItem("Source files", Fmt.int(meta.files.size)),
                    ),
                    columns = if (wide) 3 else 2,
                )
                if (meta.warnings.isNotEmpty()) {
                    Notice(Tone.Info, "Import notes", meta.warnings.joinToString("\n"))
                }
                if (meta.files.isNotEmpty()) FileList(meta.files)
            }
        }
        item(key = "import") {
            Panel {
                PanelHeader(
                    if (demo) "Import your history" else "Add files",
                    if (demo) "Your files replace the fictional demo." else "Add more files, such as video history or a newer export. Exact duplicate records count once.",
                )
                ImportButtons(actions, busy)
                Caption("Only JSON files are read; other files in the folder are ignored. Nothing leaves this device.")
            }
        }
        item(key = "storage") {
            Panel {
                PanelHeader("On this device", "What the app keeps, and how to remove it")
                BulletList(PrivacyPoints)
                Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        if (demo) "Reload fictional demo" else "Load fictional demo", Icons.Rounded.Science,
                        onClick = actions.requestDemo, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        "Delete saved history", Icons.Rounded.DeleteOutline,
                        onClick = actions.requestClear, enabled = !busy, danger = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Caption(
                    if (demo) "The demo is fictional and generated for exploring the app."
                    else "Loading the demo replaces your imported history in this app. Deleting never touches your original Spotify files.",
                )
            }
        }
        item(key = "request") { ExportRequestCard() }
        item(key = "methods") {
            Panel {
                PanelHeader("How the numbers work", "Definitions and caveats behind every view")
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Methods.forEachIndexed { i, (term, text) ->
                        if (i > 0) HorizontalDivider(color = Palette.Line)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                            Text(term, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
                        }
                    }
                }
                ExternalLink("Spotify: Understanding your data", SpotifyDataUrl)
                Caption("Spotify Dash is independent of Spotify and is not endorsed by Spotify.")
            }
        }
    }
}

@Composable
private fun FileList(files: List<String>) {
    var all by rememberSaveable { mutableStateOf(false) }
    val limit = 6
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Fmt.plural(files.size, "source file"), style = MaterialTheme.typography.labelLarge, color = Palette.Text2)
        (if (all) files else files.take(limit)).forEach { f ->
            Text(f, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (files.size > limit) QuietButton(if (all) "Show fewer" else "Show all ${Fmt.int(files.size)} files", onClick = { all = !all })
    }
}

@Composable
private fun ImportButtons(actions: DashActions, busy: Boolean) {
    Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Choose export folder", Icons.Rounded.FolderOpen, onClick = actions.pickFolder, enabled = !busy, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Choose JSON files", Icons.Rounded.UploadFile, onClick = actions.pickFiles, enabled = !busy, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ExportRequestCard() {
    val open = LocalOpenUrl.current
    Panel {
        PanelHeader("Get your extended streaming history", "Spotify prepares it on request; it covers the lifetime of your account")
        NumberedList(ExportSteps)
        PrimaryButton(
            "Open Spotify privacy page", Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = { open(SpotifyPrivacyUrl) }, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        )
        Caption("Opens in your browser. The separate Account data package covers only the past year, so request Extended streaming history.")
        Column {
            ExternalLink("Spotify: Understanding your data", SpotifyDataUrl)
            ExternalLink("Spotify: Data download help", SpotifyHelpUrl)
        }
    }
}

/** Shown when nothing is loaded: import, the fictional demo, and how to request the export. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(actions: DashActions, busy: Boolean, gutter: Dp, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Wordmark()
                Eyebrow("No history loaded", modifier = Modifier.padding(top = 22.dp))
                Text(
                    "Open your Spotify Extended Streaming History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Spotify Dash turns the Streaming_History files from your Spotify data download into trends, favorites, habits, and a searchable record of every playback. Everything is analyzed on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Text2,
                )
            }
        }
        item(key = "import") {
            Panel(padding = PaddingValues(18.dp)) {
                Text("Import your files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "Choose the extracted export folder, or select every Streaming_History JSON file. Importing more files later merges them; exact duplicates count once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text2,
                )
                ImportButtons(actions, busy)
            }
        }
        item(key = "demo") {
            Panel(padding = PaddingValues(18.dp), color = Palette.VioletWash, border = Palette.Violet.copy(alpha = 0.4f)) {
                Text("Waiting for your export?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "Explore with a fictional demo: generated listening, not anyone's real history. Your own files replace it whenever you import them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text2,
                )
                TonalButton(
                    "Explore the fictional demo", Icons.Rounded.Science,
                    onClick = actions.requestDemo, enabled = !busy, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                )
            }
        }
        item(key = "request") { ExportRequestCard() }
        item(key = "privacy") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.Accent, modifier = Modifier.padding(end = 10.dp))
                    Text("Stays on this device", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                BulletList(PrivacyPoints)
            }
        }
        item(key = "fine") {
            Caption("Spotify Dash is independent of Spotify and is not endorsed by Spotify.", Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}
