package com.caleb.spotifydash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SeriesStyle { Area, Line, Bars }

/** Round axis steps (1, 2, 2.5, 5 x 10^n) from zero to at least [max]. */
fun niceTicks(max: Double, integer: Boolean = false, target: Int = 4): List<Double> {
    if (!(max > 0) || !max.isFinite()) return listOf(0.0, 1.0)
    val raw = max / target
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    var step = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5.0 -> 5.0
        else -> 10.0
    } * mag
    if (integer) step = max(1.0, ceil(step))
    val count = ceil(max / step - 1e-9).toInt().coerceAtLeast(1)
    return (0..count).map { it * step }
}

val ShareTicks = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
fun shareTick(v: Double) = "${(v * 100).roundToInt()}%"

/** Priority for month axis labels: Januaries first, then quarter starts, then any month. */
fun monthPriority(period: String): Int = when (period.takeLast(2)) {
    "01" -> 3
    "04", "07", "10" -> 2
    else -> 1
}

/** Plot heights grow with large text so axis labels never squeeze the plotted area away. */
@Composable
private fun scaledHeight(base: Dp): Dp = base * (1f + (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * 0.4f)

private class PlotBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

@Composable
private fun tickStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.Muted, fontFeatureSettings = TabularNumbers)

/** Greedily keeps the highest-priority labels that do not collide. */
private fun pickLabels(
    count: Int,
    center: (Int) -> Float,
    priority: (Int) -> Int,
    measure: (Int) -> TextLayoutResult,
    minX: Float,
    maxX: Float,
    gap: Float,
): List<Pair<Int, Float>> {
    if (count <= 0) return emptyList()
    val accepted = ArrayList<Triple<Int, Float, Float>>() // index, left, right
    val levels = (0 until count).map(priority).distinct().sortedDescending()
    for (level in levels) {
        for (i in 0 until count) {
            if (priority(i) != level) continue
            val w = measure(i).size.width.toFloat()
            val l = (center(i) - w / 2).coerceIn(minX, max(minX, maxX - w))
            val r = l + w
            if (accepted.none { l < it.third + gap && r + gap > it.second }) accepted += Triple(i, l, r)
        }
    }
    return accepted.sortedBy { it.first }.map { it.first to it.second }
}

/** Monotone cubic interpolation (Fritsch-Carlson): smooth, but never overshoots the data. */
private fun Path.monotoneThrough(points: List<Offset>, moveFirst: Boolean) {
    if (points.isEmpty()) return
    if (moveFirst) moveTo(points[0].x, points[0].y) else lineTo(points[0].x, points[0].y)
    if (points.size == 2) { lineTo(points[1].x, points[1].y); return }
    val n = points.size
    val d = FloatArray(n - 1) { i ->
        val dx = points[i + 1].x - points[i].x
        if (dx == 0f) 0f else (points[i + 1].y - points[i].y) / dx
    }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) {
            val t = 3f / sqrt(s)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    for (i in 0 until n - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val h = (p1.x - p0.x) / 3f
        cubicTo(p0.x + h, p0.y + m[i] * h, p1.x - h, p1.y - m[i + 1] * h, p1.x, p1.y)
    }
}

private fun DrawScope.drawYGrid(ticks: List<Double>, layouts: List<TextLayoutResult>, y: (Double) -> Float, plot: PlotBox, labelGap: Float) {
    ticks.forEachIndexed { k, t ->
        val yy = y(t)
        drawLine(Palette.Grid, Offset(plot.left, yy), Offset(plot.right, yy), strokeWidth = 1.dp.toPx())
        val lay = layouts[k]
        // The zero label sits just above the baseline so it never collides with the first x label.
        val top = (yy - lay.size.height / 2f).coerceAtMost(plot.bottom - lay.size.height)
        drawText(lay, topLeft = Offset(plot.left - labelGap - lay.size.width, top))
    }
    drawLine(Palette.Axis, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
}

/**
 * Time or category series drawn on Canvas. Tap or drag to select a point; the caller shows the
 * selected values in a [ChartReadout]. Accessibility services read [description] and can step
 * through points with the custom actions.
 */
@Composable
fun SeriesChart(
    count: Int,
    value: (Int) -> Double?,
    style: SeriesStyle,
    description: String,
    yUnit: String,
    yFormat: (Double) -> String,
    xLabel: (Int) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    height: Dp = 210.dp,
    fixedTicks: List<Double>? = null,
    integer: Boolean = false,
    xPriority: (Int) -> Int = { 1 },
    barColor: ((Int) -> Color)? = null,
    stepLabel: String = "point",
) {
    val measurer = rememberTextMeasurer()
    val tick = tickStyle()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.fillMaxWidth().height(scaledHeight(height))) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        var maxV = 0.0
        for (i in 0 until count) value(i)?.let { if (it > maxV) maxV = it }
        val ticks = fixedTicks ?: niceTicks(maxV, integer)
        val top = ticks.last().takeIf { it > 0 } ?: 1.0
        val yLayouts = ticks.map { measurer.measure(yFormat(it), tick) }
        val unitLayout = measurer.measure(yUnit, tick.copy(color = Palette.Text2, fontWeight = FontWeight.Medium))
        val pad = with(density) { 8.dp.toPx() }
        val labelH = measurer.measure("0", tick).size.height.toFloat()
        val plot = PlotBox(
            left = (yLayouts.maxOfOrNull { it.size.width } ?: 0).toFloat() + pad,
            top = unitLayout.size.height + with(density) { 12.dp.toPx() },
            right = w - with(density) { 6.dp.toPx() },
            bottom = h - labelH - pad,
        )
        val inset = if (style == SeriesStyle.Bars) 0f else with(density) { 5.dp.toPx() }
        val cx: (Int) -> Float = { i ->
            when {
                style == SeriesStyle.Bars -> plot.left + plot.width * (i + 0.5f) / count
                count <= 1 -> plot.left + plot.width / 2f
                else -> plot.left + inset + (plot.width - 2 * inset) * i / (count - 1)
            }
        }
        val y: (Double) -> Float = { v -> plot.bottom - (v / top).toFloat().coerceIn(0f, 1.05f) * plot.height }
        val indexAt: (Float) -> Int = { x ->
            when {
                count <= 1 -> 0
                style == SeriesStyle.Bars -> ((x - plot.left) / plot.width * count).toInt().coerceIn(0, count - 1)
                else -> (((x - plot.left - inset) / (plot.width - 2 * inset)) * (count - 1)).roundToInt().coerceIn(0, count - 1)
            }
        }
        val xLabelCache = HashMap<Int, TextLayoutResult>()
        val measureX: (Int) -> TextLayoutResult = { i -> xLabelCache.getOrPut(i) { measurer.measure(xLabel(i), tick) } }
        val xLabels = pickLabels(count, cx, xPriority, measureX, 0f, w, with(density) { 10.dp.toPx() })
        val currentIndexAt by rememberUpdatedState(indexAt)
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    if (count > 1) {
                        customActions = listOf(
                            CustomAccessibilityAction("Next $stepLabel") { select(((selected ?: -1) + 1).coerceAtMost(count - 1)); true },
                            CustomAccessibilityAction("Previous $stepLabel") { select(((selected ?: count) - 1).coerceAtLeast(0)); true },
                        )
                    }
                }
                .pointerInput(count, style) { detectTapGestures { select(currentIndexAt(it.x)) } }
                .pointerInput(count, style) {
                    detectHorizontalDragGestures(onDragStart = { select(currentIndexAt(it.x)) }) { change, _ ->
                        change.consume()
                        select(currentIndexAt(change.position.x))
                    }
                },
        ) {
            drawText(unitLayout, topLeft = Offset(0f, 0f))
            drawYGrid(ticks, yLayouts, y, plot, pad * 0.75f)
            xLabels.forEach { (i, left) -> drawText(measureX(i), topLeft = Offset(left, plot.bottom + pad * 0.6f)) }
            val sel = selected?.takeIf { it in 0 until count }
            if (style == SeriesStyle.Bars) {
                val band = plot.width / count
                val bw = min(band * 0.72f, 28.dp.toPx()).coerceAtLeast(min(band, 1.5f))
                if (sel != null) drawRect(Palette.Text.copy(alpha = 0.06f), topLeft = Offset(cx(sel) - band / 2f, plot.top), size = Size(band, plot.height))
                for (i in 0 until count) {
                    val v = value(i) ?: continue
                    if (v <= 0.0) continue
                    val base = barColor?.invoke(i) ?: color
                    val c = if (sel != null && sel != i) base.copy(alpha = base.alpha * 0.55f) else base
                    val left = cx(i) - bw / 2f
                    val r = min(3.dp.toPx(), bw / 2f)
                    val path = Path().apply {
                        addRoundRect(RoundRect(Rect(left, y(v), left + bw, plot.bottom), topLeft = CornerRadius(r), topRight = CornerRadius(r)))
                    }
                    drawPath(path, c)
                }
            } else {
                var segment = ArrayList<Int>()
                val segments = ArrayList<List<Int>>()
                for (i in 0 until count) {
                    if (value(i) == null) {
                        if (segment.isNotEmpty()) { segments += segment; segment = ArrayList() }
                    } else segment += i
                }
                if (segment.isNotEmpty()) segments += segment
                segments.forEach { seg ->
                    val pts = seg.map { Offset(cx(it), y(value(it)!!)) }
                    if (pts.size == 1) {
                        drawCircle(Palette.Surface, 6.dp.toPx(), pts[0])
                        drawCircle(color, 4.5.dp.toPx(), pts[0])
                    } else {
                        if (style == SeriesStyle.Area) {
                            val fill = Path().apply {
                                moveTo(pts.first().x, plot.bottom)
                                monotoneThrough(pts, moveFirst = false)
                                lineTo(pts.last().x, plot.bottom)
                                close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), startY = plot.top, endY = plot.bottom))
                        }
                        val line = Path().apply { monotoneThrough(pts, moveFirst = true) }
                        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                if (sel != null) {
                    val x = cx(sel)
                    drawLine(Palette.Text2.copy(alpha = 0.55f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
                    value(sel)?.let { v ->
                        drawCircle(Palette.Surface, 6.dp.toPx(), Offset(x, y(v)))
                        drawCircle(color, 4.dp.toPx(), Offset(x, y(v)))
                    }
                }
            }
        }
    }
}

/** Stacked areas, bottom series first. In share mode every month is scaled to 100%. */
@Composable
fun StackedChart(
    count: Int,
    series: List<DoubleArray>,
    colors: List<Color>,
    share: Boolean,
    description: String,
    yUnit: String,
    xLabel: (Int) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    xPriority: (Int) -> Int = { 1 },
    height: Dp = 220.dp,
    fillAlpha: (Int) -> Float = { 0.55f },
) {
    val measurer = rememberTextMeasurer()
    val tick = tickStyle()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.fillMaxWidth().height(scaledHeight(height))) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val totals = DoubleArray(count) { i -> series.sumOf { it.getOrElse(i) { 0.0 } } }
        val scaled: List<DoubleArray> = if (share) series.map { s -> DoubleArray(count) { i -> if (totals[i] > 0) s.getOrElse(i) { 0.0 } / totals[i] else 0.0 } } else series
        val maxTotal = if (share) 1.0 else totals.maxOrNull() ?: 0.0
        val ticks = if (share) ShareTicks else niceTicks(maxTotal)
        val top = ticks.last().takeIf { it > 0 } ?: 1.0
        val yLayouts = ticks.map { measurer.measure(if (share) shareTick(it) else Fmt.compact(it), tick) }
        val unitLayout = measurer.measure(yUnit, tick.copy(color = Palette.Text2, fontWeight = FontWeight.Medium))
        val pad = with(density) { 8.dp.toPx() }
        val labelH = measurer.measure("0", tick).size.height.toFloat()
        val plot = PlotBox(
            left = (yLayouts.maxOfOrNull { it.size.width } ?: 0).toFloat() + pad,
            top = unitLayout.size.height + with(density) { 12.dp.toPx() },
            right = w - with(density) { 6.dp.toPx() },
            bottom = h - labelH - pad,
        )
        val inset = with(density) { 5.dp.toPx() }
        val cx: (Int) -> Float = { i -> if (count <= 1) plot.left + plot.width / 2f else plot.left + inset + (plot.width - 2 * inset) * i / (count - 1) }
        val y: (Double) -> Float = { v -> plot.bottom - (v / top).toFloat().coerceIn(0f, 1.05f) * plot.height }
        val indexAt: (Float) -> Int = { x ->
            if (count <= 1) 0 else (((x - plot.left - inset) / (plot.width - 2 * inset)) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        }
        val currentIndexAt by rememberUpdatedState(indexAt)
        val cache = HashMap<Int, TextLayoutResult>()
        val measureX: (Int) -> TextLayoutResult = { i -> cache.getOrPut(i) { measurer.measure(xLabel(i), tick) } }
        val xLabels = pickLabels(count, cx, xPriority, measureX, 0f, w, with(density) { 10.dp.toPx() })
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    if (count > 1) {
                        customActions = listOf(
                            CustomAccessibilityAction("Next month") { select(((selected ?: -1) + 1).coerceAtMost(count - 1)); true },
                            CustomAccessibilityAction("Previous month") { select(((selected ?: count) - 1).coerceAtLeast(0)); true },
                        )
                    }
                }
                .pointerInput(count) { detectTapGestures { select(currentIndexAt(it.x)) } }
                .pointerInput(count) {
                    detectHorizontalDragGestures(onDragStart = { select(currentIndexAt(it.x)) }) { change, _ ->
                        change.consume()
                        select(currentIndexAt(change.position.x))
                    }
                },
        ) {
            drawText(unitLayout, topLeft = Offset(0f, 0f))
            drawYGrid(ticks, yLayouts, y, plot, pad * 0.75f)
            xLabels.forEach { (i, left) -> drawText(measureX(i), topLeft = Offset(left, plot.bottom + pad * 0.6f)) }
            val lower = DoubleArray(count)
            scaled.forEachIndexed { k, s ->
                val upper = DoubleArray(count) { i -> lower[i] + s.getOrElse(i) { 0.0 } }
                val c = colors.getOrElse(k) { Palette.Other }
                if (count == 1) {
                    val bw = 28.dp.toPx()
                    val x = cx(0) - bw / 2f
                    if (upper[0] > lower[0]) drawRect(c.copy(alpha = fillAlpha(k) + 0.2f), topLeft = Offset(x, y(upper[0])), size = Size(bw, y(lower[0]) - y(upper[0])))
                } else {
                    val band = Path().apply {
                        moveTo(cx(0), y(upper[0]))
                        for (i in 1 until count) lineTo(cx(i), y(upper[i]))
                        for (i in count - 1 downTo 0) lineTo(cx(i), y(lower[i]))
                        close()
                    }
                    drawPath(band, c.copy(alpha = fillAlpha(k)))
                    val edge = Path().apply {
                        moveTo(cx(0), y(upper[0]))
                        for (i in 1 until count) lineTo(cx(i), y(upper[i]))
                    }
                    drawPath(edge, c, style = Stroke(width = 1.25.dp.toPx(), join = StrokeJoin.Round))
                }
                for (i in 0 until count) lower[i] = upper[i]
            }
            val sel = selected?.takeIf { it in 0 until count }
            if (sel != null) {
                val x = cx(sel)
                drawLine(Palette.Text.copy(alpha = 0.7f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/** Scatter plot with nearest-point selection on tap. */
@Composable
fun ScatterChart(
    xs: DoubleArray,
    ys: DoubleArray,
    description: String,
    xUnit: String,
    yUnit: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    height: Dp = 260.dp,
) {
    val measurer = rememberTextMeasurer()
    val tick = tickStyle()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val n = min(xs.size, ys.size)
    BoxWithConstraints(modifier.fillMaxWidth().height(scaledHeight(height))) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val xTicks = niceTicks(xs.maxOrNull() ?: 0.0)
        val yTicks = niceTicks(ys.maxOrNull() ?: 0.0, integer = true)
        val xTop = xTicks.last()
        val yTop = yTicks.last()
        val yLayouts = yTicks.map { measurer.measure(Fmt.compact(it), tick) }
        val xLayouts = xTicks.map { measurer.measure(Fmt.compact(it), tick) }
        val unitStyle = tick.copy(color = Palette.Text2, fontWeight = FontWeight.Medium)
        val yUnitLayout = measurer.measure(yUnit, unitStyle)
        val xUnitLayout = measurer.measure(xUnit, unitStyle)
        val pad = with(density) { 8.dp.toPx() }
        val labelH = xLayouts.first().size.height.toFloat()
        val plot = PlotBox(
            left = (yLayouts.maxOfOrNull { it.size.width } ?: 0).toFloat() + pad,
            top = yUnitLayout.size.height + with(density) { 12.dp.toPx() },
            right = w - with(density) { 10.dp.toPx() },
            bottom = h - labelH - xUnitLayout.size.height - pad * 1.5f,
        )
        val px: (Double) -> Float = { v -> plot.left + (v / xTop).toFloat() * plot.width }
        val py: (Double) -> Float = { v -> plot.bottom - (v / yTop).toFloat() * plot.height }
        val nearest: (Offset) -> Int? = { pos ->
            var best = -1
            var bestD = Float.MAX_VALUE
            for (i in 0 until n) {
                val d = hypot(px(xs[i]) - pos.x, py(ys[i]) - pos.y)
                if (d < bestD) { bestD = d; best = i }
            }
            if (best >= 0 && bestD <= with(density) { 32.dp.toPx() }) best else null
        }
        val currentNearest by rememberUpdatedState(nearest)
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(n) { detectTapGestures { select(currentNearest(it)) } },
        ) {
            drawText(yUnitLayout, topLeft = Offset(0f, 0f))
            yTicks.forEachIndexed { k, t ->
                val yy = py(t)
                drawLine(Palette.Grid, Offset(plot.left, yy), Offset(plot.right, yy), strokeWidth = 1.dp.toPx())
                val lay = yLayouts[k]
                drawText(lay, topLeft = Offset(plot.left - pad * 0.75f - lay.size.width, (yy - lay.size.height / 2f).coerceAtMost(plot.bottom - lay.size.height)))
            }
            var lastRight = -1f
            xTicks.forEachIndexed { k, t ->
                val xx = px(t)
                drawLine(Palette.Grid, Offset(xx, plot.top), Offset(xx, plot.bottom), strokeWidth = 1.dp.toPx())
                val lay = xLayouts[k]
                val left = (xx - lay.size.width / 2f).coerceIn(0f, size.width - lay.size.width)
                if (left > lastRight + pad) {
                    drawText(lay, topLeft = Offset(left, plot.bottom + pad * 0.6f))
                    lastRight = left + lay.size.width
                }
            }
            drawLine(Palette.Axis, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
            drawLine(Palette.Axis, Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), strokeWidth = 1.dp.toPx())
            drawText(xUnitLayout, topLeft = Offset(plot.right - xUnitLayout.size.width, plot.bottom + labelH + pad))
            val r = 2.6.dp.toPx()
            val dot = color.copy(alpha = 0.45f)
            for (i in 0 until n) drawCircle(dot, r, Offset(px(xs[i]), py(ys[i])))
            selected?.takeIf { it in 0 until n }?.let { i ->
                val c = Offset(px(xs[i]), py(ys[i]))
                drawLine(Palette.Text2.copy(alpha = 0.4f), Offset(c.x, plot.top), Offset(c.x, plot.bottom), strokeWidth = 1.dp.toPx())
                drawLine(Palette.Text2.copy(alpha = 0.4f), Offset(plot.left, c.y), Offset(plot.right, c.y), strokeWidth = 1.dp.toPx())
                drawCircle(Palette.Surface, 7.dp.toPx(), c)
                drawCircle(color, 5.dp.toPx(), c)
            }
        }
    }
}

/** Live-announced line showing the selected chart value, or a hint when nothing is selected. */
@Composable
fun ChartReadout(title: String?, detail: String?, hint: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface2)
            .padding(start = 12.dp, end = if (title != null) 0.dp else 12.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers), color = Palette.Text2)
            } else {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        if (title != null) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = Palette.Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>, onClick: ((String) -> Unit)? = null, clickableUntil: Int = items.size) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEachIndexed { i, (name, color) ->
            val action = onClick?.takeIf { i < clickableUntil }
            val clickable = action != null
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (action != null) Modifier.clickable(onClickLabel = "Filter every view to $name") { action(name) } else Modifier)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(name, style = MaterialTheme.typography.labelMedium, color = if (clickable) Palette.Text else Palette.Text2)
            }
        }
    }
}

/** Card with a chart/table toggle so every plotted number is also reachable as text. */
@Composable
fun ChartCard(
    title: String,
    subtitle: String?,
    caption: String? = null,
    controls: (@Composable () -> Unit)? = null,
    table: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showTable by rememberSaveable(title) { mutableStateOf(false) }
    Panel {
        PanelHeader(
            title,
            subtitle,
            trailing = if (table == null) null else {
                { TableToggle(showTable, title) { showTable = !showTable } }
            },
            controls = controls,
        )
        if (showTable && table != null) table() else content()
        if (!caption.isNullOrBlank()) Caption(caption)
    }
}

@Composable
private fun TableToggle(showing: Boolean, title: String, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.heightIn(min = 44.dp).semantics {
            contentDescription = if (showing) "Show $title as a chart" else "Show $title as a table"
        },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (showing) Palette.AccentLine else Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (showing) Palette.AccentWash else Palette.Surface2,
            contentColor = if (showing) Palette.Accent else Palette.Text2,
        ),
    ) {
        Icon(if (showing) Icons.Rounded.Insights else Icons.Rounded.TableRows, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (showing) "Chart" else "Table", style = MaterialTheme.typography.labelLarge)
    }
}

data class TableCol(val label: String, val width: Dp, val numeric: Boolean = false)

/**
 * Horizontally scrollable table, paginated so long series never compose thousands of rows at once.
 * Each row is announced as one item with its column names.
 */
@Composable
fun DataTable(
    columns: List<TableCol>,
    rowCount: Int,
    cell: (row: Int, col: Int) -> String,
    pageSize: Int = 25,
    stateKey: String = "",
    rowNote: ((Int) -> String?)? = null,
    onRowClick: ((Int) -> Unit)? = null,
) {
    var page by rememberSaveable(stateKey, rowCount) { mutableIntStateOf(0) }
    val pages = max(1, ceil(rowCount / pageSize.toDouble()).toInt())
    val current = page.coerceIn(0, pages - 1)
    val from = current * pageSize
    val to = min(rowCount, from + pageSize)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rowCount == 0) {
            Caption("No rows in the current filters.")
            return@Column
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
                .horizontalScroll(rememberScrollState()),
        ) {
            Column {
                Row(Modifier.background(Palette.Surface2).padding(vertical = 8.dp).clearAndSetSemantics {}) {
                    columns.forEach { c ->
                        Text(
                            c.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Muted,
                            textAlign = if (c.numeric) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.width(c.width).padding(horizontal = 8.dp),
                        )
                    }
                }
                for (r in from until to) {
                    if (r > from) HorizontalDivider(color = Palette.Line, thickness = 1.dp)
                    val note = rowNote?.invoke(r)
                    val spoken = columns.indices.joinToString(", ") { c -> "${columns[c].label}: ${cell(r, c)}" } + (note?.let { ", $it" } ?: "")
                    Row(Modifier.then(if (onRowClick == null) Modifier else Modifier.clickable { onRowClick(r) }).padding(vertical = 9.dp).clearAndSetSemantics {
                        contentDescription = spoken
                        if (onRowClick != null) onClick("Inspect track") { onRowClick(r); true }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        columns.forEachIndexed { c, col ->
                            Row(Modifier.width(col.width).padding(horizontal = 8.dp), horizontalArrangement = if (col.numeric) Arrangement.End else Arrangement.Start) {
                                Text(
                                    cell(r, c),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                    color = if (c == 0) Palette.Text else Palette.Text2,
                                    textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                                )
                                if (c == 0 && note != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Tag(note)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Rows ${Fmt.int(from + 1)} to ${Fmt.int(to)} of ${Fmt.int(rowCount)}",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Pager(current, pages, onPage = { page = it }, label = "Table page")
    }
}

/** "Less to more" key for heatmaps, with optional value labels under each step. */
@Composable
fun HeatLegend(lowLabel: String, highLabel: String, stepLabels: List<String> = emptyList()) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Color scale from $lowLabel to $highLabel" +
                stepLabels.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = ", steps ").orEmpty()
        },
    ) {
        Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(end = 6.dp))
        Palette.Heat.forEachIndexed { i, c ->
            Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(15.dp).clip(RoundedCornerShape(3.dp)).background(c))
                val label = stepLabels.getOrNull(i).orEmpty()
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true).padding(top = 3.dp),
                    )
                }
            }
        }
        Text(highLabel, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Shared scroll container used by wide calendar grids. */
@Composable
fun HorizontalScrollBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { content() }
}
