package com.caleb.spotifydash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.PeriodPoint
import com.caleb.spotifydash.core.QueryResult
import com.caleb.spotifydash.core.Scope

/** Identity-based key so large lists never get compared element by element during recomposition. */
class Ref(private val value: Any?) {
    override fun equals(other: Any?) = other is Ref && other.value === value
    override fun hashCode() = System.identityHashCode(value)
}

class PartialInfo(val notes: Map<String, String>, val caption: String)

/** First and last months are partial when the range starts after the 1st or ends before month end. */
fun partialMonths(scope: Scope): PartialInfo {
    val s = Fmt.ymd(scope.start)
    val e = Fmt.ymd(scope.end)
    val first = if (s != null && s.dayOfMonth != 1) scope.start.take(7) else null
    val last = if (e != null && e.dayOfMonth != e.lengthOfMonth()) scope.end.take(7) else null
    val notes = HashMap<String, String>()
    if (first != null) notes[first] = "Partial month: from ${Fmt.dayMonth(scope.start)}"
    if (last != null) notes[last] = if (first == last) "Partial month: ${Fmt.dayMonth(scope.start)} to ${Fmt.dayMonth(scope.end)}" else "Partial month: through ${Fmt.dayMonth(scope.end)}"
    val listed = listOfNotNull(first, last).distinct().map { Fmt.month(it) }
    val caption = if (listed.isEmpty()) "" else "${listed.joinToString(" and ")} ${if (listed.size > 1) "are partial months" else "is a partial month"} in this range."
    return PartialInfo(notes, caption)
}

fun dataKey(env: ScreenEnv): Any = listOf(env.filters, env.state.meta?.importedAt, env.state.meta?.events, Ref(env.result))

@Composable
fun OverviewScreen(env: ScreenEnv) {
    val r = env.result
    val listState = rememberLazyListState()
    val key = dataKey(env)
    ScreenList(listState, env.gutter, env.header) {
        item(key = "kpis") { OverviewKpis(r, env.wide) }
        item(key = "monthly") { MonthlyCard(r, key) }
        item(key = "mix") { ArtistMixCard(env, key) }
        pair(
            env.wide,
            "tops",
            {
                Panel {
                    PanelHeader("Top artists and shows", "By recorded listening time")
                    val top = r.artists.firstOrNull()?.ms ?: 0L
                    Column {
                        r.artists.take(10).forEachIndexed { i, a ->
                            RankRow(
                                rank = i + 1, primary = a.name, secondary = null,
                                fraction = if (top > 0) a.ms.toDouble() / top else 0.0,
                                value = Fmt.msHours(a.ms), detail = Fmt.plural(a.plays, "event"),
                                onClick = { env.actions.filterArtist(a.name) }, clickLabel = "Filter every view to ${a.name}",
                            )
                        }
                    }
                    QuietButton("All rankings", onClick = { env.actions.navigate(Destination.Favorites) })
                }
            },
            {
                Panel {
                    PanelHeader("Top titles", "By recorded listening time")
                    val top = r.tracks.firstOrNull()?.ms ?: 0L
                    Column {
                        r.tracks.take(10).forEachIndexed { i, t ->
                            RankRow(
                                rank = i + 1, primary = t.name, secondary = t.artist,
                                fraction = if (top > 0) t.ms.toDouble() / top else 0.0,
                                value = Fmt.msHours(t.ms), detail = Fmt.plural(t.plays, "event"),
                                onClick = { env.actions.filterTitle(t.name, t.artist) }, clickLabel = "Filter every view to ${t.name} by ${t.artist}",
                                uri = t.uri,
                            )
                        }
                    }
                    QuietButton("Playback records", onClick = { env.actions.navigate(Destination.History) })
                }
            },
        )
        item(key = "yearly") { YearlyCard(r, key) }
        item(key = "highlights") { Highlights(r, env.wide) }
        item(key = "note") {
            Caption("Listening time sums recorded playback, so overlapping sessions can count twice. Every event is placed in the hour, day, and month its playback ended, in ${Fmt.zoneLabel(r.scope.timezone)}.")
        }
    }
}

@Composable
private fun OverviewKpis(r: QueryResult, wide: Boolean) {
    val s = r.summary
    val hours = s.ms / Fmt.HOUR_MS
    val stats = listOf(
        Triple("Plays of 30 s or more", Fmt.int(s.qualifiedPlays), "${Fmt.pct(if (s.events > 0) s.qualifiedPlays.toDouble() / s.events else null)} of all events"),
        Triple("Unique titles", Fmt.int(s.tracks), "Tracks, episodes, and chapters"),
        Triple("Artists and shows", Fmt.int(s.artists), "Distinct creator names"),
        Triple("All events", Fmt.int(s.events), "Every record, any length"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Listening time", Fmt.hoursValue(hours), Modifier.fillMaxWidth(), unit = "hours", hero = true,
            detail = "${Fmt.int(hours / 24)} days of summed playback, ${Fmt.date(s.first)} to ${Fmt.date(s.last)}",
        )
        AdaptiveGrid(stats, maxColumns = if (wide) 4 else 2, minCell = 150.dp) { (label, value, detail), m -> StatTile(label, value, m, detail = detail) }
        Panel {
            FactGrid(
                listOf(
                    FactItem("Active days", "${Fmt.int(s.activeDays)} of ${Fmt.int(s.calendarDays)}", Fmt.pct(if (s.calendarDays > 0) s.activeDays.toDouble() / s.calendarDays else null)),
                    FactItem("Skip flag rate", Fmt.pct(s.skipRate), "of ${Fmt.int(s.skipKnown)} with a recorded flag"),
                    FactItem("Shuffle on", Fmt.pct(s.shuffleRate), "of ${Fmt.int(s.shuffleKnown)} with a recorded flag"),
                    FactItem("Average event", Fmt.clock(s.averageMs), "played per record"),
                ),
                columns = if (wide) 4 else 2,
            )
        }
    }
}

@Composable
private fun MonthlyCard(r: QueryResult, key: Any) {
    var metric by rememberSaveable { mutableStateOf("hours") }
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val partial = remember(r.scope) { partialMonths(r.scope) }
    val months = r.monthly
    val byHours = metric == "hours"
    val valueOf: (PeriodPoint) -> Double = { if (byHours) it.hours else it.plays.toDouble() }
    val peak = remember(Ref(months), metric) { months.maxByOrNull(valueOf) }
    val fmtMetric: (PeriodPoint) -> String = { if (byHours) Fmt.hours(it.hours) else Fmt.plural(it.plays, "event") }
    val selected = sel?.let { months.getOrNull(it) }
    ChartCard(
        title = "Listening by month",
        subtitle = if (byHours) "Recorded playback hours per calendar month" else "Recorded events per calendar month, any length",
        caption = listOf(partial.caption, "Each event is counted in the month its playback ended.").filter { it.isNotEmpty() }.joinToString(" "),
        controls = { Segmented(listOf("hours" to "Hours", "plays" to "Events"), metric, { metric = it }, label = "Monthly measure") },
        table = {
            DataTable(
                listOf(TableCol("Month", 96.dp), TableCol("Hours", 72.dp, true), TableCol("Events", 76.dp, true), TableCol("Artists / shows", 104.dp, true)),
                months.size,
                { i, c ->
                    val m = months[i]
                    when (c) { 0 -> Fmt.month(m.period); 1 -> Fmt.hoursValue(m.hours); 2 -> Fmt.int(m.plays); else -> Fmt.int(m.artists) }
                },
                stateKey = "monthly",
                rowNote = { i -> if (partial.notes.containsKey(months[i].period)) "partial" else null },
            )
        },
    ) {
        ChartReadout(
            title = selected?.let { Fmt.month(it.period, long = true) + (partial.notes[it.period]?.let { n -> " ($n)" } ?: "") },
            detail = selected?.let { "${Fmt.hours(it.hours)} · ${Fmt.plural(it.plays, "event")} · ${Fmt.int(it.artists)} artists / shows" },
            hint = peak?.let { "Peak by ${if (byHours) "hours" else "events"}: ${Fmt.month(it.period, true)}, ${fmtMetric(it)}. Tap or drag across the chart to read any month." } ?: "No months in range.",
            onClear = { sel = null },
        )
        SeriesChart(
            count = months.size,
            value = { valueOf(months[it]) },
            style = SeriesStyle.Area,
            description = "Area chart of monthly ${if (byHours) "listening hours" else "events"} across ${Fmt.plural(months.size, "month")}." +
                (peak?.let { " The busiest month was ${Fmt.month(it.period, true)} with ${fmtMetric(it)}." } ?: ""),
            yUnit = if (byHours) "Hours" else "Events",
            yFormat = { Fmt.compact(it) },
            xLabel = { Fmt.monthTick(months[it].period) },
            xPriority = { monthPriority(months[it].period) },
            integer = !byHours,
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "month",
            height = 220.dp,
        )
    }
}

@Composable
private fun YearlyCard(r: QueryResult, key: Any) {
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val years = r.yearly
    if (years.isEmpty()) return
    val selected = sel?.let { years.getOrNull(it) }
    val peak = remember(Ref(years)) { years.maxByOrNull { it.hours } }
    val anyPartial = years.any { it.partial }
    ChartCard(
        title = "Listening by year",
        subtitle = "Recorded playback hours per calendar year",
        caption = if (anyPartial) "Dimmer bars are partial years: the data or the selected range covers only part of that year, so compare them with care." else null,
        table = {
            DataTable(
                listOf(TableCol("Year", 92.dp), TableCol("Hours", 84.dp, true), TableCol("Events", 92.dp, true), TableCol("Artists / shows", 120.dp, true), TableCol("New", 72.dp, true)),
                years.size,
                { i, c ->
                    val y = years[i]
                    when (c) { 0 -> y.period; 1 -> Fmt.hoursValue(y.hours); 2 -> Fmt.int(y.plays); 3 -> Fmt.int(y.artists); else -> Fmt.int(y.newArtists) }
                },
                stateKey = "yearly",
                rowNote = { i -> if (years[i].partial) "partial" else null },
            )
        },
    ) {
        ChartReadout(
            title = selected?.let { it.period + if (it.partial) " (partial year)" else "" },
            detail = selected?.let { "${Fmt.hours(it.hours)} · ${Fmt.plural(it.plays, "event")} · ${Fmt.int(it.artists)} artists / shows · ${Fmt.int(it.newArtists)} new" },
            hint = peak?.let { "Busiest year: ${it.period}${if (it.partial) " (partial)" else ""}, ${Fmt.hours(it.hours)}. Tap a bar for details." } ?: "",
            onClear = { sel = null },
        )
        SeriesChart(
            count = years.size,
            value = { years[it].hours },
            style = SeriesStyle.Bars,
            description = "Bar chart of listening hours per year, ${years.first().period} to ${years.last().period}." +
                (peak?.let { " The busiest year was ${it.period} with ${Fmt.hours(it.hours)}." } ?: ""),
            yUnit = "Hours",
            yFormat = { Fmt.compact(it) },
            xLabel = { years[it].period },
            xPriority = { if (it == 0 || it == years.lastIndex) 2 else 1 },
            barColor = { if (years[it].partial) Palette.Accent.copy(alpha = 0.45f) else Palette.Accent },
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "year",
            height = 190.dp,
        )
    }
}

@Composable
private fun ArtistMixCard(env: ScreenEnv, key: Any) {
    val r = env.result
    if (env.filters.artist.isNotEmpty()) {
        Panel {
            PanelHeader("Artist mix", null)
            Text(
                buildAnnotatedString {
                    append("The mix compares the five most-played artists with everyone else. With the artist filter on, all of this listening belongs to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Text)) { append(env.filters.artist) }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text2,
            )
            SecondaryButton("Show all artists", null, onClick = { env.actions.filterArtist("") })
        }
        return
    }
    val keys = r.compositionKeys
    if (keys.size < 2 || r.composition.isEmpty()) return
    var mix by rememberSaveable { mutableStateOf("hours") }
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val share = mix == "share"
    val points = r.composition
    val colors = keys.indices.map { i -> if (i == keys.lastIndex) Palette.Other else Palette.Series.getOrElse(i) { Palette.Other } }
    val series = remember(Ref(points), keys.size) { keys.indices.map { k -> DoubleArray(points.size) { i -> points[i].values.getOrElse(k) { 0.0 } } } }
    val selected = sel?.let { points.getOrNull(it) }
    val partial = remember(r.scope) { partialMonths(r.scope) }
    ChartCard(
        title = "Artist mix by month",
        subtitle = if (share) "Share of each month from the five most-played artists" else "Hours from the five most-played artists in this range, plus everyone else",
        caption = "Colors follow rank within the current filters. Select a name to filter every view to that artist.",
        controls = { Segmented(listOf("hours" to "Hours", "share" to "Share"), mix, { mix = it }, label = "Artist mix scale") },
        table = {
            DataTable(
                listOf(TableCol("Month", 110.dp)) + keys.map { TableCol(it.take(18), 110.dp, true) },
                points.size,
                { i, c -> if (c == 0) Fmt.month(points[i].period) else Fmt.hoursValue(points[i].values.getOrElse(c - 1) { 0.0 }) },
                stateKey = "mix",
            )
        },
    ) {
        Legend(keys.zip(colors), onClick = { env.actions.filterArtist(it) }, clickableUntil = keys.lastIndex)
        ChartReadout(
            title = selected?.let { Fmt.month(it.period, true) + (partial.notes[it.period]?.let { n -> " ($n)" } ?: "") },
            detail = selected?.let { p ->
                val total = p.values.sum()
                keys.indices.reversed().joinToString(" · ") { k ->
                    val v = p.values.getOrElse(k) { 0.0 }
                    "${keys[k]} ${if (share) Fmt.pct(if (total > 0) v / total else null, 0) else Fmt.hours(v)}"
                }
            },
            hint = "Tap or drag across the chart to compare artists in any month.",
            onClear = { sel = null },
        )
        StackedChart(
            count = points.size,
            series = series,
            colors = colors,
            share = share,
            description = "Stacked area chart of monthly ${if (share) "listening share" else "listening hours"} for ${keys.dropLast(1).joinToString(", ")}, and ${keys.last()}.",
            yUnit = if (share) "Share" else "Hours",
            xLabel = { Fmt.monthTick(points[it].period) },
            xPriority = { monthPriority(points[it].period) },
            selected = sel,
            onSelect = { sel = it },
            fillAlpha = { k -> if (k == keys.lastIndex) 0.32f else 0.55f },
        )
    }
}

@Composable
private fun Highlights(r: QueryResult, wide: Boolean) {
    val s = r.summary
    val peakMonth = remember(Ref(r.monthly)) { r.monthly.maxByOrNull { it.hours } }
    val peakYear = remember(Ref(r.yearly)) { r.yearly.maxByOrNull { it.hours } }
    Panel {
        PanelHeader("Highlights", "Computed from the records in this range")
        FactGrid(
            listOf(
                FactItem("Strongest day", s.topDay?.let { Fmt.hours(it.hours) } ?: "n/a", s.topDay?.let { Fmt.dateLong(it.date) }),
                FactItem("Longest streak", Fmt.plural(s.longestStreak, "day"), "consecutive days with listening"),
                FactItem("Busiest month", peakMonth?.let { Fmt.hours(it.hours) } ?: "n/a", peakMonth?.let { Fmt.month(it.period, true) }),
                FactItem("Busiest year", peakYear?.let { Fmt.hours(it.hours) } ?: "n/a", peakYear?.let { it.period + if (it.partial) " (partial year)" else "" }),
                FactItem("New artists and shows", Fmt.int(s.newArtists), "first recorded anywhere in the import during this range"),
                FactItem("Top 10 artists' share", Fmt.pct(s.topArtistShare), "of listening time"),
            ),
            columns = if (wide) 3 else 2,
        )
    }
}
