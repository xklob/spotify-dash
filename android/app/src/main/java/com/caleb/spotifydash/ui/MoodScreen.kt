package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.*
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

private fun score(v: Double?, digits: Int = 2) = v?.let { String.format(Locale.ROOT, "%.${digits}f", it) } ?: "Unknown"
private fun coverage(r: MoodBucket) = "${Fmt.pct(r.coverage)} of time · ${Fmt.int(r.coveredTracks)} / ${Fmt.int(r.tracks)} tracks"

@Composable
private fun MoodTable(rows: List<MoodBucket>, key: String) {
    DataTable(listOf(TableCol("Period / group", 148.dp), TableCol("Energy", 82.dp, true), TableCol("Valence", 82.dp, true), TableCol("Time coverage", 112.dp, true), TableCol("Scored / all tracks", 145.dp), TableCol("Scored / all events", 145.dp)), rows.size,
        { i, c -> val p = rows[i]; when (c) { 0 -> p.label; 1 -> score(p.values.energy); 2 -> score(p.values.valence); 3 -> Fmt.pct(p.coverage); 4 -> "${Fmt.int(p.coveredTracks)} / ${Fmt.int(p.tracks)}"; else -> "${Fmt.int(p.coveredPlays)} / ${Fmt.int(p.plays)}" } }, stateKey = key)
}

@Composable
fun MoodScreen(env: ScreenEnv) {
    val m = env.state.mood
    val options = env.state.moodOptions
    var smoothing by rememberSaveable { mutableStateOf(false) }
    var metric by rememberSaveable { mutableStateOf("energy") }
    var selectedPeriod by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedHeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedCell by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedTrack by remember { mutableStateOf<MoodTrack?>(null) }
    var metadataOpen by rememberSaveable { mutableStateOf(false) }
    val periods = if (smoothing) m?.rolling.orEmpty() else m?.periods.orEmpty()
    LaunchedEffect(env.filters, options) { selectedPeriod = null; selectedHeat = null; selectedCell = null; selectedTrack = null }
    ScreenList(rememberLazyListState(), env.gutter, env.header) {
        item(key = "metadata") {
            Panel {
                PanelHeader("Catalog metadata", trailing = if ((m?.cached ?: 0) > 0) { { QuietButton("Manage", { metadataOpen = !metadataOpen }) } } else null)
                if (metadataOpen || (m?.cached ?: 0) == 0) {
                    Caption("Import the metadata JSON exported by the web dashboard or desktop tool. The app keeps the file and all calculations offline.")
                    SecondaryButton("Import metadata", null, env.actions.pickMetadata, enabled = !env.state.busy)
                    SecondaryButton("Export metadata", null, env.actions.exportMetadata, enabled = !env.state.busy && (m?.cached ?: 0) > 0)
                }
                if (m != null) Caption(if (m.provider == "demo") "Fictional demo scores" else "ReccoBeats · ${Fmt.int(m.cached)} cached IDs · ${Fmt.int(m.pending)} unchecked IDs across this archive · latest retrieval ${m.retrievedAt.take(10).ifEmpty { "none" }}")
            }
        }
        if (m == null) {
            item { LoadingPanel("Calculating mood and energy…", "Catalog scores are joined by exact Spotify track ID.") }
            return@ScreenList
        }
        item(key = "options") {
            Panel {
                Segmented(listOf("time" to "Time", "plays" to "Events", "tracks" to "Tracks"), options.weight, { env.actions.moodOptions(options.copy(weight = it)) }, "Weight mood scores by")
                Segmented(listOf("month" to "Monthly", "week" to "Weekly"), options.period, { env.actions.moodOptions(options.copy(period = it)) }, "Mood period")
                Caption("Time weights scores by milliseconds actually played. Tracks counts each distinct ID once per bucket. Energy measures musical intensity; valence estimates how positive it sounds. These describe recordings, not your emotional state.")
            }
        }
        item(key = "mood-stats") {
            AdaptiveGrid(listOf(
                Triple("Musical energy", score(m.summary.values.energy), "0: gentle · 1: intense"),
                Triple("Musical valence", score(m.summary.values.valence), "0: darker · 1: more positive"),
            ), maxColumns = 2, minCell = 150.dp) { (label, value, detail), modifier -> StatTile(label, value, modifier, detail = detail) }
        }
        item(key = "coverage-stat") {
            StatTile("Time with mood scores", Fmt.pct(m.summary.coverage), Modifier.fillMaxWidth(), detail = "${Fmt.hours(m.summary.coveredMs / 3600000.0)} of ${Fmt.hours(m.summary.ms / 3600000.0)} · ${Fmt.int(m.summary.coveredTracks)} / ${Fmt.int(m.summary.tracks)} tracks")
        }
        if (m.summary.coveredTracks == 0) item { Panel { PanelHeader("No mood scores for this selection", "Import catalog metadata to populate the charts. Missing scores remain unknown. Only music is eligible.") } }
        item(key = "trend") {
            ChartCard("Mood over time", if (smoothing) "Trailing 3 periods, reweighted from events" else "Each period separately",
                caption = "Energy and valence run from 0 to 1. Gaps mean no weighted scores. Compare coverage before reading changes as a shift in taste.",
                controls = { Row { Checkbox(smoothing, { smoothing = it }); Text("Smooth 3 periods", Modifier.padding(top = 12.dp)) } },
                table = { MoodTable(periods, "mood-trend") }) {
                listOf("energy", "valence").forEach { k ->
                    Text(k.replaceFirstChar { it.uppercase() }, color = if (k == "energy") Palette.Accent else Palette.VioletText, style = MaterialTheme.typography.labelLarge)
                    SeriesChart(periods.size, { periods[it].values[k] }, SeriesStyle.Line, "$k by ${options.period}, zero to one", "Score", { score(it, 2) },
                        { periods[it].label.take(if (options.period == "month") 7 else 10) }, selectedPeriod, { selectedPeriod = it }, color = if (k == "energy") Palette.Accent else Palette.VioletText,
                        height = 170.dp, fixedTicks = listOf(0.0, .25, .5, .75, 1.0))
                }
                val picked = selectedPeriod?.let { periods.getOrNull(it) }
                ChartReadout(picked?.label, picked?.let { "Energy ${score(it.values.energy)} · valence ${score(it.values.valence)} · ${coverage(it)}" }, "Tap a chart to inspect a period and its coverage.", { selectedPeriod = null })
                if (picked != null) QuietButton("Explore these dates", {
                    env.actions.setFilters(env.filters.copy(start = maxOf(env.filters.start, picked.start), end = if (env.filters.end.isEmpty()) picked.end else minOf(env.filters.end, picked.end)))
                })
            }
        }
        item(key = "distribution") {
            ChartCard("Mood distribution", "All scored tracks · weighted by ${if (options.weight == "time") "listening hours" else if (options.weight == "plays") "events" else "distinct tracks"}", caption = "Horizontal: darker to more positive. Vertical: gentle to intense. Both axes run from 0 to 1, in 0.1-wide bins.") {
                Caption("Energy ↑   1.0")
                val max = m.distribution.maxOfOrNull { it.weight } ?: 0.0
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (y in 9 downTo 0) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (x in 0..9) {
                            val i = y * 10 + x; val cell = m.distribution[i]
                            val color = Palette.Heat[if (cell.weight <= 0) 0 else ceil(sqrt(cell.weight / max) * 6).toInt().coerceIn(1, 6)]
                            val description = "Energy ${score(y / 10.0, 1)} to ${score((y + 1) / 10.0, 1)}, valence ${score(x / 10.0, 1)} to ${score((x + 1) / 10.0, 1)}: ${cell.tracks} tracks, ${Fmt.hours(cell.ms / 3600000.0)}"
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(color).clickable { selectedCell = i }.semantics { contentDescription = description })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Caption("0.0"); Caption("Valence →"); Caption("1.0") }
                val picked = selectedCell?.let { m.distribution.getOrNull(it) }
                ChartReadout(picked?.let { "Energy ${score(it.y / 10.0, 1)}–${score((it.y + 1) / 10.0, 1)} · valence ${score(it.x / 10.0, 1)}–${score((it.x + 1) / 10.0, 1)}" }, picked?.let { "${it.tracks} tracks · ${Fmt.hours(it.ms / 3600000.0)}" }, coverage(m.summary), { selectedCell = null })
            }
        }
        item(key = "comparison") {
            ChartCard("Earlier and later", "The selected calendar range split in half", caption = "Compare dates and coverage before interpreting a shift in listening taste.") {
                m.comparison.forEach { p ->
                    Text(p.label, style = MaterialTheme.typography.titleSmall)
                    Caption(if (p.start > p.end) "Not enough days for a second half" else "${p.start} to ${p.end}")
                    Text("Energy ${score(p.values.energy)} · valence ${score(p.values.valence)}")
                    Caption(coverage(p))
                }
                val a = m.comparison[0].values; val b = m.comparison[1].values
                Caption("Energy Δ ${score(if (a.energy != null && b.energy != null) b.energy - a.energy else null, 3)} · valence Δ ${score(if (a.valence != null && b.valence != null) b.valence - a.valence else null, 3)}")
            }
        }
        item(key = "heat") {
            ChartCard("Mood by weekday and hour", "Playback end time · ${Fmt.zoneLabel(env.filters.timezone)}", caption = "Gray means unknown. Color shows the average score, not volume. Each event belongs to its end hour.",
                controls = { Segmented(listOf("energy" to "Energy", "valence" to "Valence"), metric, { metric = it }, "Mood heatmap metric") },
                table = { MoodTable(m.heatmap, "mood-heat") }) {
                Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row { Spacer(Modifier.width(40.dp)); for (h in 0..23) Text(if (h % 3 == 0) "$h" else "", Modifier.width(23.dp), style = MaterialTheme.typography.labelSmall) }
                    for (d in 0..6) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d], Modifier.width(37.dp), style = MaterialTheme.typography.labelSmall)
                        for (h in 0..23) {
                            val i = d * 24 + h; val p = m.heatmap[i]; val v = p.values[metric]
                            val color = if (v == null) Palette.Line else Palette.Heat[ceil(v * 6).toInt().coerceIn(1, 6)]
                            Box(Modifier.width(20.dp).height(28.dp).clip(RoundedCornerShape(3.dp)).background(color).clickable { selectedHeat = i }
                                .semantics { contentDescription = "${p.label}: $metric ${score(v)}, ${coverage(p)}" })
                        }
                    }
                }
                val picked = selectedHeat?.let { m.heatmap.getOrNull(it) }
                ChartReadout(picked?.label, picked?.let { "$metric ${score(it.values[metric])} · ${coverage(it)}" }, "Select a cell for its score and coverage. Scale 0–1.", { selectedHeat = null })
            }
        }
        item(key = "texture") {
            ChartCard("Sound and texture", "Separate characteristics of the scored recordings", caption = "Tempo can reflect half-time or double-time estimates. Feature estimates have their own coverage.") {
                FEATURE_KEYS.drop(2).forEach { k ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                        Text(score(m.summary.values[k], if (k == "tempo" || k == "loudness") 1 else 2) + when (k) { "tempo" -> " BPM"; "loudness" -> " dB"; else -> "" })
                    }
                    Caption("${Fmt.pct(m.summary.featureCoverage[k])} time coverage")
                }
            }
        }
        item(key = "behavior") {
            ChartCard("Energy and playback behavior", "Catalog matches · ${Fmt.pct(m.summary.coverage)} time coverage", caption = "Skip correlation uses tracks with at least 5 known flags. Repeats use log(1 + events) per track. Correlation does not establish cause or personal mood.") {
                Text("Energy ↔ skip rate: ${score(m.correlations.energySkip)} · ${m.correlations.skipTracks} tracks")
                Text("Energy ↔ repeat events: ${score(m.correlations.energyRepeats)} · ${m.correlations.repeatTracks} tracks")
                DataTable(listOf(TableCol("Energy", 90.dp), TableCol("Skip rate", 95.dp, true), TableCol("Known flags", 105.dp, true)), m.energyBands.size,
                    { i, c -> val p = m.energyBands[i]; when (c) { 0 -> p.label; 1 -> Fmt.pct(p.skipRate); else -> Fmt.int(p.skipKnown) } }, stateKey = "mood-skips")
            }
        }
        item(key = "familiarity") { ChartCard("New and familiar tracks", "First recorded day versus later listening days", caption = "First recorded means first in this archive, not the first time you ever heard it.") { MoodTable(m.familiarity, "mood-familiar") } }
        item(key = "years") {
            ChartCard("Catalog coverage by year", "How much listening supports these comparisons", table = { MoodTable(m.years, "mood-years") }) {
                m.years.forEach { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.label); Text(Fmt.pct(p.coverage)) }
                    LinearProgressIndicator(progress = { (p.coverage ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = Palette.Accent, trackColor = Palette.Line)
                    Caption("${Fmt.int(p.coveredTracks)} / ${Fmt.int(p.tracks)} tracks")
                }
            }
        }
        item(key = "tracks") {
            ChartCard("Tracks behind the scores", "${Fmt.int(m.trackCount)} scored tracks · top ${m.tracks.size} by listening time", caption = "Tap a row for catalog details or to filter to that track. Exact Spotify IDs keep remixes and releases separate.") {
                DataTable(listOf(TableCol("Track / artist", 200.dp), TableCol("Hours", 78.dp, true), TableCol("Energy", 78.dp, true), TableCol("Valence", 78.dp, true), TableCol("BPM", 78.dp, true)), m.tracks.size,
                    { i, c -> val t = m.tracks[i]; when (c) { 0 -> "${t.title}\n${t.artist}"; 1 -> Fmt.hoursValue(t.ms / 3600000.0); 2 -> score(t.metadata.features.energy); 3 -> score(t.metadata.features.valence); else -> score(t.metadata.features.tempo, 1) } },
                    stateKey = "mood-tracks", onRowClick = { selectedTrack = m.tracks[it] })
            }
        }
        item(key = "methods") {
            Panel {
                Caption(if (m.provider == "demo") "All demo scores are synthetic." else "Scores: ReccoBeats, a third-party catalog. Model revision: ${m.modelVersion ?: "not published"}. These are not Spotify Audio Features API scores.")
                Caption("Energy and valence require both scores. Other features use their own available values. Missing scores never become zero. Distinct-track weighting counts each ID once per bucket.")
                if (m.provider != "demo") ExternalLink("ReccoBeats source and terms", "https://reccobeats.com/docs/documentation/terms-of-service")
            }
        }
    }
    selectedTrack?.let { t ->
        AlertDialog(onDismissRequest = { selectedTrack = null }, title = { Text(t.title) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${t.artist}\nCatalog: ${t.metadata.title}\n${t.metadata.artists.joinToString(", ")}\n${t.metadata.uri}")
                Text("Duration: ${t.metadata.durationMs?.let { "${it / 1000} sec" } ?: "Unknown"}\nISRC: ${t.metadata.isrc.ifEmpty { "Unknown" }}\nRetrieved: ${t.metadata.fetchedAt.take(10)}")
                val key = t.metadata.key?.takeIf { it >= 0 }?.let { listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")[it] + when (t.metadata.mode) { 0 -> " minor"; 1 -> " major"; else -> " (mode unknown)" } } ?: "Unknown"
                Text("Estimated key: $key")
            }
        }, confirmButton = { TextButton(onClick = { selectedTrack = null; env.actions.filterTitle(t.title, t.artist) }) { Text("Filter title & artist") } }, dismissButton = { TextButton(onClick = { selectedTrack = null }) { Text("Close") } })
    }
}
