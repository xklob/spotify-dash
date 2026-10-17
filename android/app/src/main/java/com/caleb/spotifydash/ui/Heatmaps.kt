package com.caleb.spotifydash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.DailyPoint
import com.caleb.spotifydash.core.HeatPoint
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Weekday by hour grid. Phones get days as columns and hours as rows so cells stay tappable;
 * wide layouts use the familiar days-as-rows orientation.
 */
@Composable
fun WeekHourHeatmap(cells: List<HeatPoint>, byHours: Boolean) {
    val grid = remember(cells) {
        Array(7) { arrayOfNulls<HeatPoint>(24) }.also { g -> cells.forEach { c -> if (c.day in 0..6 && c.hour in 0..23) g[c.day][c.hour] = c } }
    }
    val valueOf: (HeatPoint?) -> Double = { c -> if (c == null) 0.0 else if (byHours) c.hours else c.plays.toDouble() }
    val maxV = cells.maxOfOrNull(valueOf) ?: 0.0
    val steps = Palette.Heat.size - 1
    val level: (Double) -> Int = { v -> if (v <= 0 || maxV <= 0) 0 else max(1, ceil(v / maxV * steps).toInt()).coerceAtMost(steps) }
    var sel by rememberSaveable { mutableStateOf<Int?>(null) }
    val selDay = sel?.div(24)
    val selHour = sel?.rem(24)
    val busiest = cells.maxByOrNull(valueOf)?.takeIf { valueOf(it) > 0 }
    val fmtValue: (HeatPoint?) -> String = { c -> if (c == null) "no events" else "${Fmt.hours(c.hours)} · ${Fmt.plural(c.plays, "event")}" }
    val maxLabel = if (byHours) Fmt.hours(maxV) else "${Fmt.int(maxV)} events"
    val description = buildString {
        append("Heatmap of listening by weekday and hour, ${if (byHours) "hours" else "events"}. ")
        if (busiest != null) append("Busiest: ${Fmt.WEEKDAYS[busiest.day]}, ${Fmt.hourSpan(busiest.hour)}, ${fmtValue(busiest)}. ")
        append("Brightest cells hold up to $maxLabel. Use the actions to step through cells, or switch to the table.")
    }
    val selectCell: (Int?) -> Unit = { sel = it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChartReadout(
            title = if (selDay != null && selHour != null) "${Fmt.WEEKDAYS[selDay]}, ${Fmt.hourSpan(selHour)}" else null,
            detail = if (selDay != null && selHour != null) fmtValue(grid[selDay][selHour]) else null,
            hint = "Tap a cell to read its hours and events.",
            onClear = { sel = null },
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val transposed = maxWidth < 600.dp
            val measurer = rememberTextMeasurer()
            val tick = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = Palette.Muted)
            val density = LocalDensity.current
            val w = constraints.maxWidth.toFloat()
            val pad = with(density) { 8.dp.toPx() }
            val gap = with(density) { 3.dp.toPx() }
            val dayLayouts = Fmt.WEEKDAYS_SHORT.map { measurer.measure(it, tick) }
            if (transposed) {
                val hourLayouts = (0 until 24).map { measurer.measure(Fmt.hourName(it), tick) }
                val labelW = hourLayouts.maxOf { it.size.width } + pad
                val headerH = dayLayouts.maxOf { it.size.height } + pad / 2
                val colW = (w - labelW) / 7f
                val rowH = with(density) { 19.dp.toPx() }
                val labelEvery = listOf(1, 2, 3, 6).firstOrNull { it * rowH >= hourLayouts[0].size.height + gap } ?: 6
                val heightPx = headerH + 24 * rowH
                val currentSel by rememberUpdatedState(sel)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { heightPx.toDp() })
                        .semantics {
                            contentDescription = description
                            customActions = cellActions(currentSel, selectCell)
                        }
                        .pointerInput(transposed) {
                            detectTapGestures { p ->
                                val day = floor((p.x - labelW) / colW).toInt()
                                val hour = floor((p.y - headerH) / rowH).toInt()
                                sel = if (day in 0..6 && hour in 0..23) day * 24 + hour else null
                            }
                        },
                ) {
                    dayLayouts.forEachIndexed { d, lay ->
                        drawText(lay, topLeft = Offset(labelW + colW * d + (colW - lay.size.width) / 2f, 0f))
                    }
                    for (hour in 0 until 24) {
                        if (hour % labelEvery == 0) {
                            val lay = hourLayouts[hour]
                            drawText(lay, topLeft = Offset(labelW - pad - lay.size.width, headerH + rowH * hour + (rowH - lay.size.height) / 2f))
                        }
                        for (day in 0 until 7) {
                            val c = grid[day][hour]
                            drawRoundRect(
                                Palette.Heat[level(valueOf(c))],
                                topLeft = Offset(labelW + colW * day + gap / 2, headerH + rowH * hour + gap / 2),
                                size = Size(colW - gap, rowH - gap),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                    if (selDay != null && selHour != null) {
                        drawRoundRect(
                            Palette.Text,
                            topLeft = Offset(labelW + colW * selDay + gap / 2, headerH + rowH * selHour + gap / 2),
                            size = Size(colW - gap, rowH - gap),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            } else {
                val hourLayouts = (0 until 24).map { measurer.measure(Fmt.hourTick(it), tick) }
                val labelW = dayLayouts.maxOf { it.size.width } + pad
                val headerH = hourLayouts.maxOf { it.size.height } + pad / 2
                val colW = (w - labelW) / 24f
                val rowH = minOf(colW, with(density) { 30.dp.toPx() })
                val widest = hourLayouts.maxOf { it.size.width } + gap * 2
                val labelEvery = listOf(1, 2, 3, 6).firstOrNull { it * colW >= widest } ?: 6
                val heightPx = headerH + 7 * rowH
                val currentSel by rememberUpdatedState(sel)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { heightPx.toDp() })
                        .semantics {
                            contentDescription = description
                            customActions = cellActions(currentSel, selectCell)
                        }
                        .pointerInput(transposed) {
                            detectTapGestures { p ->
                                val hour = floor((p.x - labelW) / colW).toInt()
                                val day = floor((p.y - headerH) / rowH).toInt()
                                sel = if (day in 0..6 && hour in 0..23) day * 24 + hour else null
                            }
                        },
                ) {
                    for (hour in 0 until 24) {
                        if (hour % labelEvery == 0) {
                            val lay = hourLayouts[hour]
                            drawText(lay, topLeft = Offset(labelW + colW * hour + (colW - lay.size.width) / 2f, 0f))
                        }
                    }
                    for (day in 0 until 7) {
                        val lay = dayLayouts[day]
                        drawText(lay, topLeft = Offset(0f, headerH + rowH * day + (rowH - lay.size.height) / 2f))
                        for (hour in 0 until 24) {
                            drawRoundRect(
                                Palette.Heat[level(valueOf(grid[day][hour]))],
                                topLeft = Offset(labelW + colW * hour + gap / 2, headerH + rowH * day + gap / 2),
                                size = Size(colW - gap, rowH - gap),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                    if (selDay != null && selHour != null) {
                        drawRoundRect(
                            Palette.Text,
                            topLeft = Offset(labelW + colW * selHour + gap / 2, headerH + rowH * selDay + gap / 2),
                            size = Size(colW - gap, rowH - gap),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        HeatLegend("Less", "More", listOf("0", "", "", "", "", "", maxLabel))
    }
}

private fun cellActions(current: Int?, select: (Int?) -> Unit) = listOf(
    CustomAccessibilityAction("Next hour") { select(((current ?: -1) + 1).coerceAtMost(167)); true },
    CustomAccessibilityAction("Previous hour") { select(((current ?: 168) - 1).coerceAtLeast(0)); true },
    CustomAccessibilityAction("Next day") { select(((current ?: -24) + 24).coerceAtMost(167)); true },
    CustomAccessibilityAction("Previous day") { select(((current ?: 191) - 24).coerceAtLeast(0)); true },
)

private class CalendarCell(val date: String, val col: Int, val row: Int, val point: DailyPoint?)

private class CalendarModel(
    val cells: List<CalendarCell>,
    val columns: Int,
    val cuts: List<Double>,
    val months: List<Pair<String, Int>>,
    val covered: Int,
    val active: Int,
    val minutes: Double,
    val plays: Int,
)

/** Quantile cut points over active days so each colored level holds a similar number of days. */
private fun cutPoints(values: List<Double>, levels: Int): List<Double> {
    val sorted = values.filter { it > 0 }.sorted()
    if (sorted.isEmpty()) return emptyList()
    val cuts = ArrayList<Double>()
    for (i in 1 until levels) {
        val v = sorted[minOf(sorted.size - 1, floor(i.toDouble() / levels * sorted.size).toInt())]
        if (cuts.isEmpty() || v > cuts.last()) cuts += v
    }
    return cuts
}

private fun shortMinutes(m: Double): String =
    if (m < 60) "${max(1, m.roundToInt())}m" else if (m < 600) String.format(java.util.Locale.US, "%.1fh", m / 60) else "${(m / 60).roundToInt()}h"

/** GitHub-style year grid: weeks as columns, Monday to Sunday as rows. Days outside the range are outlined. */
@Composable
fun CalendarHeatmap(days: List<DailyPoint>, year: String) {
    val model = remember(days, year) {
        val y = year.toIntOrNull() ?: LocalDate.now().year
        val start = LocalDate.of(y, 1, 1)
        val total = start.lengthOfYear()
        val lead = start.dayOfWeek.value - 1
        val byDate = days.filter { it.date.startsWith(year) }.associateBy { it.date }
        val cells = (0 until total).map { i ->
            val d = start.plusDays(i.toLong()).toString()
            CalendarCell(d, (lead + i) / 7, (lead + i) % 7, byDate[d])
        }
        val inYear = cells.mapNotNull { it.point }
        val months = (1..12).map { m -> Fmt.MONTHS[m - 1] to (cells.first { c -> c.date.substring(5, 7).toInt() == m }.col) }
        CalendarModel(
            cells, cells.last().col + 1, cutPoints(inYear.map { it.minutes }, Palette.Heat.size - 1), months,
            inYear.size, inYear.count { it.minutes > 0 }, inYear.sumOf { it.minutes }, inYear.sumOf { it.plays },
        )
    }
    val level: (Double) -> Int = { m ->
        if (m <= 0) 0 else {
            var l = 1
            for (c in model.cuts) if (m >= c) l++
            minOf(Palette.Heat.size - 1, l)
        }
    }
    var sel by rememberSaveable(year) { mutableStateOf<Int?>(null) }
    val cell = sel?.let { model.cells.getOrNull(it) }
    val legend = Palette.Heat.indices.map { i ->
        when (i) {
            0 -> "0"
            1 -> ">0"
            else -> model.cuts.getOrNull(i - 2)?.let { if (i - 2 == model.cuts.lastIndex) "${shortMinutes(it)}+" else shortMinutes(it) } ?: ""
        }
    }
    val summary = "${Fmt.int(model.active)} active days of ${Fmt.int(model.covered)} in range · ${Fmt.minutes(model.minutes)} · ${Fmt.plural(model.plays, "event")}"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChartReadout(
            title = cell?.let { Fmt.dateLong(it.date) },
            detail = cell?.let { c -> c.point?.let { "${Fmt.minutes(it.minutes)} · ${Fmt.plural(it.plays, "event")} · ${Fmt.plural(it.tracks, "title")}" } ?: "Outside the selected range" },
            hint = "$summary. Tap a day for details.",
            onClear = { sel = null },
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val tick = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = Palette.Muted)
            val density = LocalDensity.current
            val pad = with(density) { 6.dp.toPx() }
            val dayLayouts = Fmt.WEEKDAYS_SHORT.map { measurer.measure(it, tick) }
            val labelW = dayLayouts.maxOf { it.size.width } + pad
            val monthLayouts = model.months.map { measurer.measure(it.first, tick) }
            val monthH = monthLayouts.maxOf { it.size.height } + pad
            val available = constraints.maxWidth - labelW
            val pitch = max(with(density) { 17.dp.toPx() }, available / model.columns)
            val gap = with(density) { 3.dp.toPx() }
            val gridW = pitch * model.columns
            val heightPx = monthH + pitch * 7
            val scroll = rememberScrollState()
            val lastActiveCol = model.cells.lastOrNull { it.point != null }?.col ?: 0
            LaunchedEffect(year, scroll.maxValue) {
                if (scroll.maxValue > 0) scroll.scrollTo((scroll.maxValue * (lastActiveCol + 1f) / model.columns).roundToInt().coerceIn(0, scroll.maxValue))
            }
            val currentSel by rememberUpdatedState(sel)
            val selectDay: (Int?) -> Unit = { sel = it }
            Row {
                Canvas(Modifier.width(with(density) { labelW.toDp() }).height(with(density) { heightPx.toDp() })) {
                    dayLayouts.forEachIndexed { r, lay ->
                        if (r % 2 == 0) drawText(lay, topLeft = Offset(0f, monthH + pitch * r + (pitch - lay.size.height) / 2f))
                    }
                }
                Canvas(
                    Modifier
                        .horizontalScroll(scroll)
                        .width(with(density) { gridW.toDp() })
                        .height(with(density) { heightPx.toDp() })
                        .semantics {
                            contentDescription = "Daily listening calendar for $year. $summary. Use the actions to step through days, or switch to the table."
                            customActions = listOf(
                                CustomAccessibilityAction("Next day") { selectDay(((currentSel ?: -1) + 1).coerceAtMost(model.cells.lastIndex)); true },
                                CustomAccessibilityAction("Previous day") { selectDay(((currentSel ?: model.cells.size) - 1).coerceAtLeast(0)); true },
                                CustomAccessibilityAction("Next week") { selectDay(((currentSel ?: -7) + 7).coerceAtMost(model.cells.lastIndex)); true },
                                CustomAccessibilityAction("Previous week") { selectDay(((currentSel ?: (model.cells.size + 6)) - 7).coerceAtLeast(0)); true },
                            )
                        }
                        .pointerInput(model) {
                            detectTapGestures { p ->
                                val col = floor(p.x / pitch).toInt()
                                val row = floor((p.y - monthH) / pitch).toInt()
                                val idx = model.cells.indexOfFirst { it.col == col && it.row == row }
                                sel = idx.takeIf { it >= 0 }
                            }
                        },
                ) {
                    var lastRight = -1f
                    model.months.forEachIndexed { i, (_, col) ->
                        val lay = monthLayouts[i]
                        val x = col * pitch
                        if (x > lastRight) {
                            drawText(lay, topLeft = Offset(x, 0f))
                            lastRight = x + lay.size.width + pad
                        }
                    }
                    model.cells.forEachIndexed { i, c ->
                        val topLeft = Offset(c.col * pitch + gap / 2, monthH + c.row * pitch + gap / 2)
                        val size = Size(pitch - gap, pitch - gap)
                        if (c.point == null) {
                            drawRoundRect(Palette.Line2, topLeft, size, CornerRadius(3.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
                        } else {
                            drawRoundRect(Palette.Heat[level(c.point.minutes)], topLeft, size, CornerRadius(3.dp.toPx()))
                        }
                        if (sel == i) drawRoundRect(Palette.Text, topLeft, size, CornerRadius(3.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
        HeatLegend("Less", "More", legend)
    }
}
