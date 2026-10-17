package com.caleb.spotifydash.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opens a web page or Spotify link outside the app; the app itself has no network access. */
val LocalOpenUrl = staticCompositionLocalOf<(String) -> Unit> { {} }

fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can open $url", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "This link could not be opened.", Toast.LENGTH_LONG).show()
    }
}

/** Rounded graphite card used for every section. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = Palette.Surface,
    border: Color = Palette.Line,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = color,
        contentColor = Palette.Text,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    controls: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.semantics { heading() })
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            trailing?.invoke()
        }
        if (controls != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
        }
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Palette.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp), color = color, modifier = modifier)
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = modifier)
}

/** Pill segmented control; each option is a radio button for accessibility services. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .semantics { contentDescription = label }
            .selectableGroup(),
    ) {
        options.forEach { (value, text) ->
            val on = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Palette.Surface3 else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Muted, maxLines = 1)
            }
        }
    }
}

/** A selectable chip in the filter sheet. Selection is announced as a radio-like state. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.AccentWash else Palette.Surface2)
            .border(1.dp, if (selected) Palette.AccentLine else Palette.Line, RoundedCornerShape(10.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.AccentStrong else Palette.Text2)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, detail: String? = null, hero: Boolean = false) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(if (hero) Palette.AccentWash else Palette.Surface)
            .border(1.dp, if (hero) Palette.AccentLine else Palette.Line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text2)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = if (hero) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularNumbers),
                color = if (hero) Palette.Accent else Palette.Text,
            )
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(unit, style = MaterialTheme.typography.titleMedium, color = Palette.Text2, modifier = Modifier.padding(bottom = if (hero) 6.dp else 3.dp))
            }
        }
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

data class FactItem(val label: String, val value: String, val detail: String? = null)

/**
 * Grid that drops to fewer columns when a cell would be narrower than [minCell] at the current
 * text size, so large accessibility fonts get one roomy column instead of cramped pairs.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    maxColumns: Int,
    minCell: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    equalHeight: Boolean = true,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = (maxWidth / (minCell * scale)).toInt().coerceIn(1, maxColumns.coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(if (equalHeight) Modifier.height(IntrinsicSize.Min) else Modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item -> cell(item, Modifier.weight(1f).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Label, value, detail facts in a responsive grid of up to [columns] columns. */
@Composable
fun FactGrid(facts: List<FactItem>, columns: Int, modifier: Modifier = Modifier) {
    AdaptiveGrid(facts, maxColumns = columns, minCell = 136.dp, modifier = modifier, spacing = 14.dp, equalHeight = false) { fact, m -> Fact(fact, m) }
}

@Composable
fun Fact(fact: FactItem, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(fact.label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(
            fact.value,
            style = (if (fact.value.length > 14) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontFeatureSettings = TabularNumbers),
            color = Palette.Text,
        )
        if (!fact.detail.isNullOrBlank()) Text(fact.detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

/** Proportional bar. Zero gets no fill at all; tiny positive values stay visible as a sliver. */
@Composable
fun ValueBar(fraction: Double, modifier: Modifier = Modifier, color: Color = Palette.Accent, height: Dp = 4.dp, track: Color = Palette.Surface3) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (fraction > 0 && fraction.isFinite()) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.006, 1.0).toFloat()).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

enum class Tone { Info, Error, Success, Demo }

@Composable
fun Notice(
    tone: Tone,
    title: String?,
    body: String?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val (wash, line, accent, icon) = when (tone) {
        Tone.Error -> Quad(Palette.DangerWash, Palette.Danger.copy(alpha = 0.4f), Palette.DangerText, Icons.Rounded.ErrorOutline)
        Tone.Success -> Quad(Palette.AccentWash, Palette.AccentLine, Palette.Accent, Icons.Rounded.CheckCircle)
        Tone.Demo -> Quad(Palette.VioletWash, Palette.Violet.copy(alpha = 0.45f), Palette.VioletText, Icons.Rounded.Science)
        Tone.Info -> Quad(Palette.Surface2, Palette.Line2, Palette.Text2, Icons.Rounded.Info)
    }
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(wash)
            .border(1.dp, line, shape)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss != null) 4.dp else 14.dp)
            .semantics { liveRegion = if (tone == Tone.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
            if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss message", tint = Palette.Muted) }
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: ImageVector)

@Composable
fun Tag(text: String, color: Color = Palette.Text2, background: Color = Palette.Surface3, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
fun KindBadge(kind: String, modifier: Modifier = Modifier) {
    val (fg, bg) = when (kind) {
        "music" -> Palette.Accent to Palette.AccentWash
        "podcast" -> Palette.VioletText to Palette.VioletWash
        "audiobook" -> Palette.BlueText to Palette.Blue.copy(alpha = 0.14f)
        else -> Palette.Text2 to Palette.Surface3
    }
    Tag(Fmt.kind(kind), color = fg, background = bg, modifier = modifier)
}

/** Spotify catalog link. Renders nothing when the record has no usable URI. */
@Composable
fun SpotifyLinkButton(uri: String, label: String) {
    val url = Fmt.spotifyUrl(uri) ?: return
    val open = LocalOpenUrl.current
    IconButton(onClick = { open(url) }) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open $label in Spotify", tint = Palette.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ExternalLink(text: String, url: String, modifier: Modifier = Modifier) {
    val open = LocalOpenUrl.current
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Open in browser") { open(url) }
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
    }
}

/** Ranked row with a proportional bar; the numeric value is always printed beside it. */
@Composable
fun RankRow(
    rank: Int,
    primary: String,
    secondary: String?,
    fraction: Double,
    value: String,
    detail: String?,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    uri: String? = null,
    barColor: Color = Palette.Accent,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            Fmt.int(rank),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
            color = Palette.Faint,
            modifier = Modifier.widthIn(min = 22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(primary, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!secondary.isNullOrBlank()) {
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ValueBar(fraction, color = barColor, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 76.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers), color = Palette.Text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers), color = Palette.Muted)
        }
        if (uri != null) SpotifyLinkButton(uri, primary)
    }
}

/** First, previous, next, last controls with an announced page status. */
@Composable
fun Pager(page: Int, pages: Int, onPage: (Int) -> Unit, enabled: Boolean = true, label: String = "Page") {
    if (pages <= 1) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = { onPage(0) }, enabled = enabled && page > 0) { Icon(Icons.Rounded.FirstPage, contentDescription = "First page") }
        IconButton(onClick = { onPage(page - 1) }, enabled = enabled && page > 0) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page") }
        Text(
            "$label ${Fmt.int(page + 1)} of ${Fmt.int(pages)}",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNumbers),
            color = Palette.Text2,
            modifier = Modifier.padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = { onPage(page + 1) }, enabled = enabled && page < pages - 1) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page") }
        IconButton(onClick = { onPage(pages - 1) }, enabled = enabled && page < pages - 1) { Icon(Icons.AutoMirrored.Rounded.LastPage, contentDescription = "Last page") }
    }
}

@Composable
fun EmptyFilterResult(onReset: () -> Unit) {
    Panel(padding = PaddingValues(22.dp)) {
        Text("No listening matches these filters", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.semantics { heading() })
        Text(
            "No recorded events fall inside the current date range and filter combination. Remove a filter above, or start over.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Text2,
        )
        PrimaryButton("Reset filters", Icons.Rounded.RestartAlt, onClick = onReset)
    }
}

@Composable
fun LoadingPanel(title: String, detail: String?) {
    Panel(padding = PaddingValues(22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Accent,
            contentColor = Palette.AccentInk,
            disabledContainerColor = Palette.Surface3,
            disabledContentColor = Palette.Faint,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (danger) Palette.Danger.copy(alpha = 0.5f) else Palette.Line2),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (danger) Palette.DangerWash else Palette.Surface2,
            contentColor = if (danger) Palette.DangerText else Palette.Text,
            disabledContentColor = Palette.Faint,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(10.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = Palette.Accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Palette.Accent else Palette.Faint)
    }
}

@Composable
fun TonalButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Palette.Surface3, contentColor = Palette.Text),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The equalizer mark from the web favicon, drawn natively. */
@Composable
fun WaveGlyph(size: Dp = 22.dp, color: Color = Palette.Accent, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val unit = this.size.minDimension / 24f
        val bars = listOf(7f, 13f, 20f, 11f, 16f)
        bars.forEachIndexed { i, h ->
            drawRoundRect(
                color = if (i == bars.lastIndex) color.copy(alpha = 0.5f) else color,
                topLeft = Offset((1.6f + i * 4.4f) * unit, (24f - h) / 2f * unit),
                size = Size(2.8f * unit, h * unit),
                cornerRadius = CornerRadius(1.4f * unit),
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WaveGlyph()
        Row {
            Text("Spotify ", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text("Dash", style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
        }
    }
}

/** Bulleted paragraph list used for privacy notes and instructions. */
@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { text ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 9.dp).size(5.dp).clip(CircleShape).background(Palette.Accent))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun NumberedList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Palette.AccentWash).border(1.dp, Palette.AccentLine, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Palette.Accent) }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, modifier = Modifier.weight(1f))
            }
        }
    }
}
