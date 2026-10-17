package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.PeriodPoint
import com.caleb.spotifydash.core.QueryResult
import java.util.Locale
import kotlin.math.abs

private const val SCATTER_CAP = 3000

@Composable
fun DiscoveriesScreen(env: ScreenEnv) {
    val r = env.result
    val listState = rememberLazyListState()
    val key = dataKey(env)
    // Weighted by hours, the monthly shares reproduce the range-wide familiar share exactly.
    val familiar = remember(Ref(r.monthly)) {
        var hours = 0.0
        var weighted = 0.0
        r.monthly.forEach { m -> m.familiarShare?.let { hours += m.hours; weighted += it * m.hours } }
        if (hours > 0) weighted / hours else null
    }
    val peakNew = remember(Ref(r.monthly)) { r.monthly.maxByOrNull { it.newArtists } }
    ScreenList(listState, env.gutter, env.header) {
        item(key = "kpis") {
            val longest = r.rediscoveries.firstOrNull()
            val stats = listOf(
                Triple("New artists and shows", Fmt.int(r.summary.newArtists), "first recorded in this range"),
                Triple("Familiar listening", Fmt.pct(familiar), "of time on titles heard on an earlier day"),
                Triple(
                    "Biggest discovery month",
                    if (peakNew != null && peakNew.newArtists > 0) Fmt.int(peakNew.newArtists) else "n/a",
                    if (peakNew != null && peakNew.newArtists > 0) "new names in ${Fmt.month(peakNew.period, true)}" else "no first appearances",
                ),
                Triple(
                    "Longest return",
                    longest?.let { Fmt.span(it.gapDays) } ?: "n/a",
                    longest?.let { "${it.name}, ${it.artist}" } ?: "no gaps of 180 days or more",
                ),
            )
            AdaptiveGrid(stats, maxColumns = if (env.wide) 4 else 2, minCell = 150.dp) { (label, value, detail), m -> StatTile(label, value, m, detail = detail) }
        }
        pair(env.wide, "monthly", { NewArtistsCard(r, key, peakNew) }, { FamiliarCard(r, key, familiar) })
        item(key = "years") { YearByYear(r, onArtist = env.actions.filterArtist) }
        pair(
            env.wide,
            "lists",
            {
                ItemListCard(
                    title = "Replay obsessions",
                    subtitle = "Most events for a single title within one calendar month",
                    empty = "No title was recorded more than once in a single month.",
                    caption = "Counts are recorded events of any length under the current filters.",
                    count = r.obsessions.size,
                ) { i ->
                    val o = r.obsessions[i]
                    ListItemRow(
                        rank = i + 1, title = o.name, sub = "${o.artist} · ${Fmt.month(o.month, true)}",
                        value = Fmt.int(o.plays), valueNote = "events · ${Fmt.hours(o.hours)}", uri = o.uri,
                        onClick = { env.actions.filterTitle(o.name, o.artist) }, clickLabel = "Filter every view to ${o.name} by ${o.artist}",
                    )
                }
            },
            {
                ItemListCard(
                    title = "Longest returns",
                    subtitle = "Longest gaps between consecutive plays of the same title",
                    empty = "No title returned after a gap of 180 days or more.",
                    caption = "Gaps of at least 180 days between consecutive positive-length records of a title, among the filtered records only. Narrow filters can lengthen gaps.",
                    count = r.rediscoveries.size,
                ) { i ->
                    val g = r.rediscoveries[i]
                    ListItemRow(
                        rank = i + 1, title = g.name, sub = "${g.artist} · ${Fmt.date(g.before)} to ${Fmt.date(g.after)}",
                        value = Fmt.span(g.gapDays), valueNote = "${Fmt.int(g.gapDays)} days", uri = g.uri,
                        onClick = { env.actions.filterTitle(g.name, g.artist) }, clickLabel = "Filter every view to ${g.name} by ${g.artist}",
                    )
                }
            },
        )
        item(key = "correlation") { CorrelationCard(r, key) }
    }
}

@Composable
private fun NewArtistsCard(r: QueryResult, key: Any, peak: PeriodPoint?) {
    val months = r.monthly
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val partial = remember(r.scope) { partialMonths(r.scope) }
    val selected = sel?.let { months.getOrNull(it) }
    ChartCard(
        title = "New artists per month",
        subtitle = "Artists and shows whose first record in the whole import falls in that month",
        caption = listOf("First recorded exposure in your imported history, not necessarily the first time you ever heard them.", partial.caption).filter { it.isNotEmpty() }.joinToString(" "),
        table = {
            DataTable(
                listOf(TableCol("Month", 110.dp), TableCol("New", 72.dp, true), TableCol("All artists / shows", 140.dp, true)),
                months.size,
                { i, c -> val m = months[i]; when (c) { 0 -> Fmt.month(m.period); 1 -> Fmt.int(m.newArtists); else -> Fmt.int(m.artists) } },
                stateKey = "new-artists",
                rowNote = { i -> if (partial.notes.containsKey(months[i].period)) "partial" else null },
            )
        },
    ) {
        ChartReadout(
            title = selected?.let { Fmt.month(it.period, true) + (partial.notes[it.period]?.let { n -> " ($n)" } ?: "") },
            detail = selected?.let { "${Fmt.int(it.newArtists)} new · ${Fmt.int(it.artists)} artists and shows heard" },
            hint = peak?.takeIf { it.newArtists > 0 }?.let { "Most first appearances: ${Fmt.month(it.period, true)}, ${Fmt.int(it.newArtists)}. Tap or drag for any month." } ?: "No first appearances in this range.",
            onClear = { sel = null },
        )
        SeriesChart(
            count = months.size,
            value = { months[it].newArtists.toDouble() },
            style = SeriesStyle.Bars,
            color = Palette.Violet,
            description = "Bar chart of new artists and shows per month." + (peak?.let { " The most first appearances came in ${Fmt.month(it.period, true)} with ${Fmt.int(it.newArtists)}." } ?: ""),
            yUnit = "New names",
            yFormat = { Fmt.compact(it) },
            integer = true,
            xLabel = { Fmt.monthTick(months[it].period) },
            xPriority = { monthPriority(months[it].period) },
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "month",
            height = 190.dp,
        )
    }
}

@Composable
private fun FamiliarCard(r: QueryResult, key: Any, familiar: Double?) {
    val months = r.monthly
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val partial = remember(r.scope) { partialMonths(r.scope) }
    val selected = sel?.let { months.getOrNull(it) }
    ChartCard(
        title = "Familiar listening share",
        subtitle = "Share of each month's listening time on titles first recorded on an earlier day",
        caption = "First-seen dates come from the entire import, even when the view is filtered. Months without listening are left blank.",
        table = {
            DataTable(
                listOf(TableCol("Month", 110.dp), TableCol("Familiar", 88.dp, true), TableCol("Hours", 80.dp, true)),
                months.size,
                { i, c -> val m = months[i]; when (c) { 0 -> Fmt.month(m.period); 1 -> Fmt.pct(m.familiarShare); else -> Fmt.hoursValue(m.hours) } },
                stateKey = "familiar",
                rowNote = { i -> if (partial.notes.containsKey(months[i].period)) "partial" else null },
            )
        },
    ) {
        ChartReadout(
            title = selected?.let { Fmt.month(it.period, true) },
            detail = selected?.let { "${Fmt.pct(it.familiarShare)} familiar · ${Fmt.hours(it.hours)} listened" },
            hint = "Across the range, ${Fmt.pct(familiar)} of listening time went to titles already heard on an earlier day.",
            onClear = { sel = null },
        )
        SeriesChart(
            count = months.size,
            value = { months[it].familiarShare },
            style = SeriesStyle.Line,
            color = Palette.Blue,
            description = "Line chart of the monthly familiar listening share, from 0 to 100 percent. Across the range, ${Fmt.pct(familiar)} of listening time was familiar.",
            yUnit = "Share of time",
            yFormat = { shareTick(it) },
            fixedTicks = ShareTicks,
            xLabel = { Fmt.monthTick(months[it].period) },
            xPriority = { monthPriority(months[it].period) },
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "month",
            height = 190.dp,
        )
    }
}

@Composable
private fun YearByYear(r: QueryResult, onArtist: (String) -> Unit) {
    val years = r.yearly.asReversed()
    Panel {
        PanelHeader("Year by year", "Totals per calendar year, with how much the top 20 artists overlap the previous year")
        Column {
            years.forEachIndexed { i, y ->
                if (i > 0) HorizontalDivider(color = Palette.Line)
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(y.period, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        if (y.partial) Tag("partial year")
                        Spacer(Modifier.weight(1f))
                        Text(Fmt.hours(y.hours), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers), color = Palette.Text)
                    }
                    Text(
                        "${Fmt.plural(y.plays, "event")} · ${Fmt.int(y.artists)} artists and shows · ${Fmt.int(y.newArtists)} new · ${Fmt.pct(y.familiarShare, 0)} familiar",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                        color = Palette.Text2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Top:", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        if (y.topArtist.isNotEmpty()) {
                            Text(
                                y.topArtist,
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClickLabel = "Filter every view to ${y.topArtist}", role = Role.Button) { onArtist(y.topArtist) }
                                    .heightIn(min = 36.dp)
                                    .padding(vertical = 8.dp),
                            )
                        } else {
                            Text("n/a", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Top 20 overlap with the previous year", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                        val overlap = y.overlap
                        if (overlap == null) {
                            Text("n/a", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        } else {
                            Box(Modifier.width(72.dp).height(6.dp).clip(CircleShape).background(Palette.Surface3)) {
                                if (overlap > 0) Box(Modifier.fillMaxWidth(overlap.toFloat().coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(Palette.Blue))
                            }
                            Text(Fmt.pct(overlap, 0), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers), color = Palette.Text, modifier = Modifier.widthIn(min = 40.dp))
                        }
                    }
                }
            }
        }
        Caption(
            "Overlap is the Jaccard index of each year's top 20 artists or shows by time against the previous listed year: shared names divided by all names in either list. " +
                "Partial years cover only part of the calendar year within the data or the selected range, so compare them with care.",
        )
    }
}

@Composable
private fun ItemListCard(title: String, subtitle: String, empty: String, caption: String, count: Int, row: @Composable (Int) -> Unit) {
    var all by rememberSaveable(title) { mutableStateOf(false) }
    val limit = 12
    Panel {
        PanelHeader(title, subtitle)
        if (count == 0) {
            Caption(empty)
        } else {
            Column {
                for (i in 0 until if (all) count else minOf(limit, count)) row(i)
            }
            if (count > limit) QuietButton(if (all) "Show the top $limit" else "Show all ${Fmt.int(count)}", onClick = { all = !all })
        }
        Caption(caption)
    }
}

@Composable
private fun ListItemRow(rank: Int, title: String, sub: String, value: String, valueNote: String, uri: String, onClick: () -> Unit, clickLabel: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Fmt.int(rank), style = MaterialTheme.typography.labelMedium, color = Palette.Faint, modifier = Modifier.widthIn(min = 22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers), color = Palette.Text)
            Text(valueNote, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
        SpotifyLinkButton(uri, title)
    }
}

@Composable
private fun CorrelationCard(r: QueryResult, key: Any) {
    val active = remember(Ref(r.daily)) { r.daily.filter { it.minutes > 0 } }
    val plotted = remember(Ref(active)) {
        if (active.size <= SCATTER_CAP) active else {
            val step = active.size.toDouble() / SCATTER_CAP
            List(SCATTER_CAP) { i -> active[(i * step).toInt()] }
        }
    }
    val xs = remember(Ref(plotted)) { DoubleArray(plotted.size) { plotted[it].minutes } }
    val ys = remember(Ref(plotted)) { DoubleArray(plotted.size) { plotted[it].tracks.toDouble() } }
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val selected = sel?.let { plotted.getOrNull(it) }
    val c = r.correlation
    val rv = c.r
    val strength = when {
        rv == null -> ""
        abs(rv) >= 0.7 -> "strong"
        abs(rv) >= 0.4 -> "moderate"
        abs(rv) >= 0.2 -> "weak"
        else -> "negligible"
    }
    val rText = rv?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a"
    ChartCard(
        title = "Listening time and variety",
        subtitle = "Each dot is an active day: minutes listened against distinct titles played",
        caption = c.explanation + if (active.size > plotted.size) " The plot shows ${Fmt.int(plotted.size)} evenly spaced days of ${Fmt.int(active.size)}; r uses all of them." else "",
        table = {
            DataTable(
                listOf(TableCol("Date", 150.dp), TableCol("Minutes", 84.dp, true), TableCol("Distinct titles", 120.dp, true), TableCol("Events", 80.dp, true)),
                active.size,
                { i, col -> val d = active[i]; when (col) { 0 -> Fmt.date(d.date); 1 -> Fmt.int(d.minutes); 2 -> Fmt.int(d.tracks); else -> Fmt.int(d.plays) } },
                stateKey = "correlation",
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Pearson r", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text(rText, style = MaterialTheme.typography.displaySmall, color = Palette.Accent)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (rv == null) "Needs at least 3 active days with variation" else "$strength ${if (rv >= 0) "positive" else "negative"} association",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                )
                Text("n = ${Fmt.plural(c.n, "active day")}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Text("Describes association, not cause.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        ChartReadout(
            title = selected?.let { Fmt.dateLong(it.date) },
            detail = selected?.let { "${Fmt.minutes(it.minutes)} · ${Fmt.plural(it.tracks, "distinct title")} · ${Fmt.plural(it.plays, "event")}" },
            hint = "Tap a dot to read that day.",
            onClear = { sel = null },
        )
        ScatterChart(
            xs = xs,
            ys = ys,
            description = "Scatter plot of ${Fmt.int(plotted.size)} active days, minutes listened against distinct titles. Pearson r is $rText across ${Fmt.plural(c.n, "active day")}.",
            xUnit = "Minutes listened per day",
            yUnit = "Distinct titles",
            selected = sel,
            onSelect = { sel = it },
        )
    }
}
