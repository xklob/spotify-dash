package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.GroupPoint

@Composable
fun HabitsScreen(env: ScreenEnv) {
    val r = env.result
    val listState = rememberLazyListState()
    val key = dataKey(env)
    val tz = Fmt.zoneLabel(r.scope.timezone)
    ScreenList(listState, env.gutter, env.header) {
        item(key = "heat") {
            var byHours by rememberSaveable { mutableStateOf(true) }
            ChartCard(
                title = "Weekday and hour",
                subtitle = "When playback ended, in $tz",
                caption = "Each event, including all of its playback time, is placed in the hour it ended. Long sessions therefore lean toward later hours.",
                controls = { Segmented(listOf(true to "Hours", false to "Events"), byHours, { byHours = it }, label = "Heatmap measure") },
                table = {
                    val cells = r.heatmap
                    DataTable(
                        listOf(TableCol("Weekday", 110.dp), TableCol("Hour", 150.dp), TableCol("Hours", 80.dp, true), TableCol("Events", 84.dp, true)),
                        cells.size,
                        { i, c ->
                            val h = cells[i]
                            when (c) { 0 -> Fmt.WEEKDAYS.getOrElse(h.day) { "${h.day}" }; 1 -> Fmt.hourSpan(h.hour); 2 -> Fmt.hoursValue(h.hours); else -> Fmt.int(h.plays) }
                        },
                        stateKey = "heat",
                    )
                },
            ) {
                WeekHourHeatmap(r.heatmap, byHours)
            }
        }
        pair(env.wide, "rhythm", { WeekdayCard(env, key) }, { HourCard(env, key, tz) })
        item(key = "calendar") { CalendarCard(env, tz) }
        pair(
            env.wide,
            "groups-1",
            {
                GroupCard(
                    "Devices", "Hours by platform family", r.platforms, Palette.Accent, "Device", { it },
                    onPick = { name -> env.actions.setFilters(env.filters.copy(platform = name)) }, pickLabel = { "Filter every view to $it" },
                )
            },
            {
                GroupCard(
                    "Countries", "Hours by connection country", r.countries, Palette.Blue, "Country", { Fmt.country(it) },
                    onPick = { code -> if (code != "Unknown") env.actions.setFilters(env.filters.copy(country = code)) }, pickLabel = { "Filter every view to $it" },
                )
            },
        )
        pair(
            env.wide,
            "groups-2",
            {
                GroupCard(
                    "Content types", "Hours by type of recording", r.kinds, Palette.VioletText, "Type", { Fmt.kind(it) },
                    onPick = { kind -> env.actions.setFilters(env.filters.copy(kind = kind)) }, pickLabel = { "Filter every view to $it" },
                )
            },
            {
                GroupCard(
                    "How playback ended", "Hours by recorded end reason", r.reasons, Palette.Violet, "End reason", { Fmt.reason(it) },
                    caption = "Spotify's end-reason codes, relabeled. The table lists the original codes.",
                    showCode = true,
                )
            },
        )
        item(key = "shuffle") { ShuffleCard(r.shuffleComparison) }
    }
}

@Composable
private fun WeekdayCard(env: ScreenEnv, key: Any) {
    val days = env.result.weekdays
    var average by rememberSaveable { mutableStateOf(true) }
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val selected = sel?.let { days.getOrNull(it) }
    val peak = remember(Ref(days)) { days.maxByOrNull { it.hours } }
    ChartCard(
        title = "Weekdays",
        subtitle = if (average) "Average minutes per calendar day, including days without recorded listening" else "Total hours per weekday",
        controls = { Segmented(listOf(true to "Avg per day", false to "Total"), average, { average = it }, label = "Weekday measure") },
        table = {
            DataTable(
                listOf(TableCol("Weekday", 110.dp), TableCol("Total hours", 96.dp, true), TableCol("Avg min / day", 108.dp, true), TableCol("Calendar days", 112.dp, true), TableCol("Events", 84.dp, true)),
                days.size,
                { i, c ->
                    val d = days[i]
                    when (c) { 0 -> d.name; 1 -> Fmt.hoursValue(d.hours); 2 -> Fmt.int(d.averageMinutes); 3 -> Fmt.int(d.calendarDays); else -> Fmt.int(d.plays) }
                },
                stateKey = "weekdays",
            )
        },
    ) {
        ChartReadout(
            title = selected?.name,
            detail = selected?.let { "${Fmt.minutes(it.averageMinutes)} per calendar day · ${Fmt.hours(it.hours)} in total · ${Fmt.int(it.calendarDays)} ${it.name}s in range" },
            hint = peak?.let { "${it.name} has the most listening: ${Fmt.hours(it.hours)} in total, ${Fmt.minutes(it.averageMinutes)} per calendar day on average." } ?: "",
            onClear = { sel = null },
        )
        SeriesChart(
            count = days.size,
            value = { if (average) days[it].averageMinutes else days[it].hours },
            style = SeriesStyle.Bars,
            description = "Bar chart of ${if (average) "average minutes per calendar day" else "total hours"} by weekday." +
                (peak?.let { " ${it.name} is highest by total time." } ?: ""),
            yUnit = if (average) "Minutes per day" else "Hours",
            yFormat = { Fmt.compact(it) },
            xLabel = { days[it].name.take(3) },
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "weekday",
            height = 190.dp,
        )
    }
}

@Composable
private fun HourCard(env: ScreenEnv, key: Any, tz: String) {
    val hours = env.result.hours
    var sel by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    val selected = sel?.let { hours.getOrNull(it) }
    val peakIndex = remember(Ref(hours)) { hours.indices.maxByOrNull { hours[it].hours } }
    ChartCard(
        title = "Hour of day",
        subtitle = "Hours of listening ending in each clock hour, $tz",
        table = {
            DataTable(
                listOf(TableCol("Hour", 150.dp), TableCol("Hours", 80.dp, true), TableCol("Events", 84.dp, true), TableCol("Skip rate", 88.dp, true), TableCol("Known flags", 100.dp, true)),
                hours.size,
                { i, c ->
                    val h = hours[i]
                    when (c) { 0 -> Fmt.hourSpan(i); 1 -> Fmt.hoursValue(h.hours); 2 -> Fmt.int(h.plays); 3 -> Fmt.pct(h.skipRate); else -> Fmt.int(h.skipKnown) }
                },
                stateKey = "hours",
            )
        },
    ) {
        ChartReadout(
            title = sel?.let { Fmt.hourSpan(it) },
            detail = selected?.let { "${Fmt.hours(it.hours)} · ${Fmt.plural(it.plays, "event")} · skip ${Fmt.pct(it.skipRate)} (n ${Fmt.int(it.skipKnown)})" },
            hint = peakIndex?.let { "Busiest hour: ${Fmt.hourSpan(it)}, ${Fmt.hours(hours[it].hours)}. Tap or drag for any hour." } ?: "",
            onClear = { sel = null },
        )
        SeriesChart(
            count = hours.size,
            value = { hours[it].hours },
            style = SeriesStyle.Bars,
            description = "Bar chart of listening hours by hour of day." + (peakIndex?.let { " The busiest hour is ${Fmt.hourSpan(it)} with ${Fmt.hours(hours[it].hours)}." } ?: ""),
            yUnit = "Hours",
            yFormat = { Fmt.compact(it) },
            xLabel = { Fmt.hourTick(it) },
            xPriority = { if (it % 6 == 0) 3 else if (it % 3 == 0) 2 else 1 },
            selected = sel,
            onSelect = { sel = it },
            stepLabel = "hour",
            height = 190.dp,
        )
    }
}

@Composable
private fun CalendarCard(env: ScreenEnv, tz: String) {
    val daily = env.result.daily
    val years = remember(Ref(daily)) { daily.map { it.date.take(4) }.distinct().sorted() }
    if (years.isEmpty()) return
    var pick by rememberSaveable { mutableStateOf("") }
    val year = if (pick in years) pick else years.last()
    val inYear = remember(Ref(daily), year) { daily.filter { it.date.startsWith(year) } }
    ChartCard(
        title = "Daily calendar",
        subtitle = "Minutes listened per day in $year, $tz",
        caption = "Colors split the year's active days into equal-sized groups by minutes. Outlined squares fall outside the selected range. A zero day means no events in this export and selection, not proof of silence.",
        controls = if (years.size > 1) {
            {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.asReversed().forEach { y -> ChoiceChip(y, y == year, onClick = { pick = y }) }
                }
            }
        } else null,
        table = {
            DataTable(
                listOf(TableCol("Date", 150.dp), TableCol("Minutes", 84.dp, true), TableCol("Events", 80.dp, true), TableCol("Titles", 80.dp, true), TableCol("Artists", 80.dp, true)),
                inYear.size,
                { i, c ->
                    val d = inYear[i]
                    when (c) { 0 -> Fmt.date(d.date); 1 -> Fmt.int(d.minutes); 2 -> Fmt.int(d.plays); 3 -> Fmt.int(d.tracks); else -> Fmt.int(d.artists) }
                },
                pageSize = 31,
                stateKey = "calendar-$year",
            )
        },
    ) {
        CalendarHeatmap(daily, year)
    }
}

@Composable
private fun GroupCard(
    title: String,
    subtitle: String,
    rows: List<GroupPoint>,
    color: Color,
    nameLabel: String,
    label: (String) -> String,
    caption: String? = null,
    onPick: ((String) -> Unit)? = null,
    pickLabel: (String) -> String = { it },
    showCode: Boolean = false,
) {
    var all by rememberSaveable(title) { mutableStateOf(false) }
    val limit = 8
    val shown = if (all) rows else rows.take(limit)
    val top = rows.maxOfOrNull { it.hours } ?: 0.0
    ChartCard(
        title = title,
        subtitle = subtitle,
        caption = caption ?: if (onPick != null) "Select a row to filter every view to it." else null,
        table = {
            val cols = listOf(
                TableCol(nameLabel, 150.dp), TableCol("Hours", 80.dp, true), TableCol("Events", 84.dp, true),
                TableCol("Skip rate", 84.dp, true), TableCol("Known flags", 100.dp, true),
            ) + if (showCode) listOf(TableCol("Code", 170.dp)) else emptyList()
            DataTable(
                cols,
                rows.size,
                { i, c ->
                    val g = rows[i]
                    when (c) { 0 -> label(g.name); 1 -> Fmt.hoursValue(g.hours); 2 -> Fmt.int(g.plays); 3 -> Fmt.pct(g.skipRate); 4 -> Fmt.int(g.skipKnown); else -> g.name }
                },
                stateKey = "group-$title",
            )
        },
    ) {
        if (rows.isEmpty()) {
            Caption("Nothing recorded in the current filters.")
        } else {
            Column {
                shown.forEach { g ->
                    val name = label(g.name)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (onPick != null) Modifier.clickable(onClickLabel = pickLabel(name), role = Role.Button) { onPick(g.name) } else Modifier)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ValueBar(if (top > 0) g.hours / top else 0.0, color = color, height = 6.dp)
                            Text(
                                "${Fmt.plural(g.plays, "event")} · skip ${Fmt.pct(g.skipRate)} (n ${Fmt.int(g.skipKnown)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                                color = Palette.Muted,
                            )
                        }
                        Text(Fmt.hours(g.hours), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers), color = Palette.Text)
                    }
                }
            }
            if (rows.size > limit) {
                val rest = rows.drop(limit)
                QuietButton(
                    if (all) "Show the top $limit" else "Show all ${Fmt.int(rows.size)} (${Fmt.int(rest.size)} more, ${Fmt.hours(rest.sumOf { it.hours })} combined)",
                    onClick = { all = !all },
                )
            }
        }
    }
}

@Composable
private fun ShuffleCard(groups: List<GroupPoint>) {
    val order = listOf("Shuffle on", "Shuffle off", "Unknown")
    val rows = remember(Ref(groups)) { groups.sortedBy { order.indexOf(it.name).let { i -> if (i < 0) 9 else i } } }
    val comparable = rows.any { it.name != "Unknown" && it.skipKnown > 0 }
    ChartCard(
        title = "Skip flag with shuffle on and off",
        subtitle = "Share of events Spotify flagged as skipped, among events with a recorded skip flag",
        caption = "This compares recorded rates only. Shuffle sessions can differ in context, such as playlists versus albums, so the gap does not show that shuffle causes skipping.",
        table = {
            DataTable(
                listOf(TableCol("Shuffle", 170.dp), TableCol("Hours", 80.dp, true), TableCol("Events", 84.dp, true), TableCol("Skip rate", 84.dp, true), TableCol("Known flags", 100.dp, true)),
                rows.size,
                { i, c ->
                    val g = rows[i]
                    when (c) { 0 -> shuffleLabel(g.name); 1 -> Fmt.hoursValue(g.hours); 2 -> Fmt.int(g.plays); 3 -> Fmt.pct(g.skipRate); else -> Fmt.int(g.skipKnown) }
                },
                stateKey = "shuffle",
            )
        },
    ) {
        if (!comparable) {
            Caption("No events in this range have both a recorded shuffle state and a recorded skip flag.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(shuffleLabel(g.name), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                                Text("${Fmt.plural(g.plays, "event")} · ${Fmt.hours(g.hours)}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.pct(g.skipRate), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNumbers), color = Palette.Text)
                                Text("n ${Fmt.int(g.skipKnown)}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.Surface3)) {
                            val rate = g.skipRate ?: 0.0
                            if (rate > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(rate.toFloat().coerceIn(0f, 1f))
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(if (g.name == "Unknown") Palette.Muted else Palette.Coral),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shuffleLabel(name: String) = if (name == "Unknown") "Shuffle not recorded" else name
