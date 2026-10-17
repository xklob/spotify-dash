package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caleb.spotifydash.core.DatasetMeta
import com.caleb.spotifydash.core.Filters
import com.caleb.spotifydash.core.Options
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** First and last calendar dates of the imported history in the selected timezone. */
@Immutable
data class DataBounds(val first: LocalDate?, val last: LocalDate?) {
    companion object {
        fun of(meta: DatasetMeta?, tz: String) = DataBounds(Fmt.isoToLocalDate(meta?.first, tz), Fmt.isoToLocalDate(meta?.last, tz))
    }
}

val KindOptions = listOf("all" to "All types", "music" to "Music", "podcast" to "Podcasts", "audiobook" to "Audiobooks", "unknown" to "Unidentified")
val MinOptions = listOf(0L to "Any length", 10_000L to "10 s or more", 30_000L to "30 s or more", 60_000L to "1 min or more", 120_000L to "2 min or more", 300_000L to "5 min or more")
val ShuffleOptions = listOf("all" to "On or off", "yes" to "Shuffle on", "no" to "Shuffle off")

private fun clamp(start: LocalDate, end: LocalDate, b: DataBounds): Pair<String, String> {
    val s = if (b.first != null && start.isBefore(b.first)) b.first else start
    val e = if (b.last != null && end.isAfter(b.last)) b.last else end
    return s.toString() to e.toString()
}

/** Date range for a preset. Recent ranges are anchored at the last imported event, not today. */
fun presetRange(preset: String, b: DataBounds): Pair<String, String>? = when {
    preset == "all" -> "" to ""
    b.last == null -> null
    preset == "last30" -> clamp(b.last.minusDays(29), b.last, b)
    preset == "last90" -> clamp(b.last.minusDays(89), b.last, b)
    preset == "last365" -> clamp(b.last.minusDays(364), b.last, b)
    preset.startsWith("year:") -> preset.removePrefix("year:").toIntOrNull()?.let { y -> clamp(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), b) }
    else -> null
}

fun detectPreset(f: Filters, b: DataBounds, years: List<String>): String {
    if (f.start.isEmpty() && f.end.isEmpty()) return "all"
    val candidates = listOf("last30", "last90", "last365") + years.map { "year:$it" }
    return candidates.firstOrNull { presetRange(it, b) == (f.start to f.end) } ?: "custom"
}

fun presetLabel(preset: String): String = when {
    preset == "all" -> "All time"
    preset == "last30" -> "Last 30 days"
    preset == "last90" -> "Last 90 days"
    preset == "last365" -> "Last 365 days"
    preset.startsWith("year:") -> preset.removePrefix("year:")
    else -> "Custom dates"
}

data class FilterChipModel(val key: String, val label: String, val clear: (Filters) -> Filters)

fun activeChips(f: Filters, b: DataBounds, years: List<String>): List<FilterChipModel> {
    val chips = ArrayList<FilterChipModel>()
    if (f.start.isNotEmpty() || f.end.isNotEmpty()) {
        val preset = detectPreset(f, b, years)
        val label = if (preset == "custom") "${if (f.start.isNotEmpty()) Fmt.date(f.start) else "Start"} to ${if (f.end.isNotEmpty()) Fmt.date(f.end) else "end"}" else presetLabel(preset)
        chips += FilterChipModel("range", label) { it.copy(start = "", end = "") }
    }
    if (f.search.isNotBlank()) chips += FilterChipModel("search", "Search: “${f.search.trim()}”") { it.copy(search = "") }
    if (f.artist.isNotEmpty()) chips += FilterChipModel("artist", "Artist: ${f.artist}") { it.copy(artist = "") }
    if (f.kind != "all") chips += FilterChipModel("kind", KindOptions.firstOrNull { it.first == f.kind }?.second ?: f.kind) { it.copy(kind = "all") }
    if (f.minMs > 0) chips += FilterChipModel("min", MinOptions.firstOrNull { it.first == f.minMs }?.second ?: "${f.minMs / 1000} s or more") { it.copy(minMs = 0) }
    if (f.platform.isNotEmpty()) chips += FilterChipModel("platform", "Device: ${f.platform}") { it.copy(platform = "") }
    if (f.country.isNotEmpty()) chips += FilterChipModel("country", "Country: ${Fmt.country(f.country)}") { it.copy(country = "") }
    if (f.shuffle != "all") chips += FilterChipModel("shuffle", if (f.shuffle == "yes") "Shuffle on" else "Shuffle off") { it.copy(shuffle = "all") }
    return chips
}

/** Search, quick range, filter sheet button, and removable chips. Sits at the top of every analysis screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterHeader(
    filters: Filters,
    searchDraft: String,
    onSearchDraft: (String) -> Unit,
    bounds: DataBounds,
    options: Options?,
    onFilters: (Filters) -> Unit,
    onOpenSheet: () -> Unit,
    onReset: () -> Unit,
) {
    val years = remember(options) { options?.years.orEmpty().sortedDescending() }
    val chips = activeChips(filters, bounds, years)
    val preset = detectPreset(filters, bounds, years)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(
            value = searchDraft,
            onValueChange = onSearchDraft,
            placeholder = "Search titles, artists, albums",
            description = "Search every view by title, artist, or album",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RangeMenu(preset, years, bounds, onPick = { p ->
                if (p == "custom") onOpenSheet() else presetRange(p, bounds)?.let { (s, e) -> onFilters(filters.copy(start = s, end = e)) }
            })
            HeaderButton(
                text = if (chips.isEmpty()) "Filters" else "Filters · ${chips.size}",
                icon = Icons.Rounded.Tune,
                highlighted = chips.isNotEmpty(),
                onClick = onOpenSheet,
                description = if (chips.isEmpty()) "More filters" else "More filters, ${chips.size} active",
            )
            Spacer(Modifier.weight(1f))
            if (chips.isNotEmpty()) QuietButton("Reset", onClick = onReset, icon = Icons.Rounded.RestartAlt)
        }
        if (chips.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { chip -> RemovableChip(chip.label) { onFilters(chip.clear(filters)) } }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, description: String, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { contentDescription = description },
        singleLine = true,
        placeholder = { Text(placeholder, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Palette.Muted) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Palette.Muted) } }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Palette.Surface,
            unfocusedContainerColor = Palette.Surface,
            focusedBorderColor = Palette.AccentLine,
            unfocusedBorderColor = Palette.Line,
            cursorColor = Palette.Accent,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
        ),
    )
}

@Composable
private fun HeaderButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, highlighted: Boolean, onClick: () -> Unit, description: String, trailing: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) Palette.AccentWash else Palette.Surface)
            .border(1.dp, if (highlighted) Palette.AccentLine else Palette.Line, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = 46.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!trailing) Icon(icon, contentDescription = null, tint = if (highlighted) Palette.Accent else Palette.Text2, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (highlighted) Palette.AccentStrong else Palette.Text, maxLines = 1)
        if (trailing) Icon(icon, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RangeMenu(preset: String, years: List<String>, bounds: DataBounds, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderButton(
            text = presetLabel(preset),
            icon = Icons.Rounded.ArrowDropDown,
            highlighted = preset != "all",
            onClick = { open = true },
            description = "Date range: ${presetLabel(preset)}. Change range",
            trailing = true,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Surface2) {
            val item: @Composable (String, String) -> Unit = { key, label ->
                DropdownMenuItem(
                    text = { Text(label, color = if (key == preset) Palette.Accent else Palette.Text) },
                    onClick = { open = false; onPick(key) },
                    trailingIcon = if (key == preset) { { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Palette.Accent) } } else null,
                )
            }
            item("all", "All time")
            MenuNote(bounds.last?.let { "Ending ${Fmt.date(it)}, the last imported day" } ?: "Ending at the last imported day")
            item("last30", "Last 30 days")
            item("last90", "Last 90 days")
            item("last365", "Last 365 days")
            if (years.isNotEmpty()) {
                MenuNote("Calendar year")
                years.forEach { y -> item("year:$y", y) }
            }
            HorizontalDivider(color = Palette.Line)
            item("custom", "Custom dates…")
        }
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Line2, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Remove this filter", role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = "Filter: $label" }
            .heightIn(min = 40.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        Icon(Icons.Rounded.Close, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
    }
}

/** Every filter in one sheet. Changes apply immediately; the sheet stays open for further tweaks. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: Filters,
    options: Options?,
    bounds: DataBounds,
    onFilters: (Filters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var datePick by rememberSaveable { mutableStateOf<String?>(null) }
    val years = remember(options) { options?.years.orEmpty().sortedDescending() }
    val preset = detectPreset(filters, bounds, years)
    val chips = activeChips(filters, bounds, years)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.Surface, contentColor = Palette.Text) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        if (chips.isEmpty()) "Applied to every view" else "${Fmt.plural(chips.size, "filter")} active across every view",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                if (chips.isNotEmpty()) QuietButton("Reset", onClick = onReset, icon = Icons.Rounded.RestartAlt)
            }

            SheetSection("Date range", "Recent ranges end at the last imported day${bounds.last?.let { ", ${Fmt.date(it)}" } ?: ""}. Dates follow the selected timezone.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all", "last30", "last90", "last365").forEach { key ->
                        ChoiceChip(presetLabel(key), selected = preset == key, onClick = {
                            presetRange(key, bounds)?.let { (s, e) -> onFilters(filters.copy(start = s, end = e)) }
                        })
                    }
                }
                if (years.isNotEmpty()) {
                    Text("Calendar year", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        years.forEach { y ->
                            ChoiceChip(y, selected = preset == "year:$y", onClick = {
                                presetRange("year:$y", bounds)?.let { (s, e) -> onFilters(filters.copy(start = s, end = e)) }
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateButton("From", filters.start.ifEmpty { null }, bounds.first, Modifier.weight(1f)) { datePick = "start" }
                    DateButton("To", filters.end.ifEmpty { null }, bounds.last, Modifier.weight(1f)) { datePick = "end" }
                }
            }

            SheetSection("Content type", null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindOptions.forEach { (key, label) -> ChoiceChip(label, filters.kind == key, onClick = { onFilters(filters.copy(kind = key)) }) }
                }
            }

            SheetSection("Minimum played", "Events shorter than this are left out everywhere. Plays of 30 s or more is this app's convention for a counted play.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinOptions.forEach { (ms, label) -> ChoiceChip(label, filters.minMs == ms, onClick = { onFilters(filters.copy(minMs = ms)) }) }
                    if (MinOptions.none { it.first == filters.minMs }) ChoiceChip("${filters.minMs / 1000} s or more", true, onClick = {})
                }
            }

            SheetSection("Artist or show", "Exact name match. ${options?.artists?.size?.let { Fmt.plural(it, "name") } ?: "Names"} to choose from.") {
                PickerField(
                    label = "Artist or show",
                    value = filters.artist.ifEmpty { "Any artist or show" },
                    active = filters.artist.isNotEmpty(),
                    onClick = { picker = "artist" },
                    onClear = if (filters.artist.isNotEmpty()) ({ onFilters(filters.copy(artist = "")) }) else null,
                )
            }

            SheetSection("Device", "Playback platforms grouped into broad device families.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("All devices", filters.platform.isEmpty(), onClick = { onFilters(filters.copy(platform = "")) })
                    options?.platforms.orEmpty().forEach { p -> ChoiceChip(p, filters.platform == p, onClick = { onFilters(filters.copy(platform = p)) }) }
                }
            }

            SheetSection("Country", "The connection country Spotify recorded, not a home address.") {
                PickerField(
                    label = "Country",
                    value = if (filters.country.isEmpty()) "All countries" else Fmt.country(filters.country),
                    active = filters.country.isNotEmpty(),
                    onClick = { picker = "country" },
                    onClear = if (filters.country.isNotEmpty()) ({ onFilters(filters.copy(country = "")) }) else null,
                )
            }

            SheetSection("Shuffle", null) {
                Segmented(ShuffleOptions, filters.shuffle, { onFilters(filters.copy(shuffle = it)) }, label = "Shuffle state")
            }

            SheetSection("Timezone", "Every timestamp is placed in this zone, for all years. Travel timezones cannot be recovered from the export.") {
                PickerField(
                    label = "Timezone",
                    value = "${Fmt.zoneLabel(filters.timezone)} · ${Fmt.zoneOffset(filters.timezone)}",
                    active = false,
                    onClick = { picker = "zone" },
                    onClear = null,
                )
            }

            PrimaryButton("Done", Icons.Rounded.Check, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }

    when (picker) {
        "artist" -> {
            val items = remember(options) { options?.artists.orEmpty().map { PickerOption(it, it) } }
            SearchablePicker(
                title = "Artist or show",
                searchHint = "Find among ${Fmt.int(items.size)} names",
                items = items,
                selected = filters.artist,
                anyOption = PickerOption("", "Any artist or show"),
                onPick = { onFilters(filters.copy(artist = it)); picker = null },
                onDismiss = { picker = null },
            )
        }
        "country" -> {
            val items = remember(options) { options?.countries.orEmpty().map { PickerOption(it, Fmt.country(it), it.takeIf { c -> c.length == 2 }) }.sortedBy { it.label } }
            SearchablePicker(
                title = "Country",
                searchHint = "Find a country",
                items = items,
                selected = filters.country,
                anyOption = PickerOption("", "All countries"),
                onPick = { onFilters(filters.copy(country = it)); picker = null },
                onDismiss = { picker = null },
            )
        }
        "zone" -> {
            val items = remember(filters.timezone) { zoneOptions(filters.timezone) }
            SearchablePicker(
                title = "Timezone",
                searchHint = "Find a city or region",
                items = items,
                selected = filters.timezone,
                anyOption = null,
                onPick = { onFilters(filters.copy(timezone = it)); picker = null },
                onDismiss = { picker = null },
            )
        }
    }

    datePick?.let { which ->
        val current = Fmt.ymd(if (which == "start") filters.start else filters.end)
        DatePickDialog(
            title = if (which == "start") "Start date" else "End date",
            initial = current ?: (if (which == "start") bounds.first else bounds.last),
            min = bounds.first,
            max = bounds.last,
            onPick = { d ->
                val v = d.toString()
                onFilters(
                    if (which == "start") {
                        if (filters.end.isNotEmpty() && v > filters.end) filters.copy(start = v, end = v) else filters.copy(start = v)
                    } else {
                        if (filters.start.isNotEmpty() && v < filters.start) filters.copy(start = v, end = v) else filters.copy(end = v)
                    },
                )
            },
            onClear = if ((if (which == "start") filters.start else filters.end).isNotEmpty()) {
                { onFilters(if (which == "start") filters.copy(start = "") else filters.copy(end = "")) }
            } else null,
            onDismiss = { datePick = null },
        )
    }
}

@Composable
private fun SheetSection(title: String, note: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.semantics { heading() })
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        content()
    }
}

@Composable
private fun DateButton(label: String, value: String?, fallback: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface2)
            .border(1.dp, if (value != null) Palette.AccentLine else Palette.Line, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Choose ${label.lowercase()} date", onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(
                value?.let { Fmt.date(it) } ?: fallback?.let { Fmt.date(it) } ?: "Any",
                style = MaterialTheme.typography.labelLarge,
                color = if (value != null) Palette.Text else Palette.Text2,
            )
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, active: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface2)
            .border(1.dp, if (active) Palette.AccentLine else Palette.Line, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Choose ${label.lowercase()}", onClick = onClick)
            .semantics { stateDescription = value }
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (active) Palette.Text else Palette.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear ${label.lowercase()}", tint = Palette.Muted) }
        } else {
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Palette.Muted, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

data class PickerOption(val value: String, val label: String, val detail: String? = null)

private val SuggestedZones = listOf(
    "America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles", "America/Anchorage",
    "Pacific/Honolulu", "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Kolkata", "Asia/Tokyo", "Australia/Sydney", "UTC",
)

/** Every IANA zone the device knows, with this device's zone and common zones first. */
fun zoneOptions(current: String): List<PickerOption> {
    val now = Instant.now()
    val device = runCatching { ZoneId.systemDefault().id }.getOrNull()
    val all = ZoneId.getAvailableZoneIds().filter { it.contains('/') || it == "UTC" }.sorted()
    val suggested = listOfNotNull(device, current).plus(SuggestedZones).distinct().filter { z -> runCatching { ZoneId.of(z) }.isSuccess }
    fun opt(z: String, note: String? = null) = PickerOption(z, Fmt.zoneLabel(z), listOfNotNull(Fmt.zoneOffset(z, now), note).joinToString(" · "))
    return suggested.map { opt(it, if (it == device) "this device" else null) } + all.filter { it !in suggested }.map { opt(it) }
}

/**
 * Full-screen searchable list. Only visible rows are composed, so thousands of names stay fast.
 */
@Composable
fun SearchablePicker(
    title: String,
    searchHint: String,
    items: List<PickerOption>,
    selected: String,
    anyOption: PickerOption?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = remember(query, items) {
        val q = query.trim()
        if (q.isEmpty()) items else {
            val prefix = items.filter { it.label.startsWith(q, ignoreCase = true) }
            val rest = items.filter { !it.label.startsWith(q, ignoreCase = true) && (it.label.contains(q, ignoreCase = true) || it.value.contains(q, ignoreCase = true)) }
            prefix + rest
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Palette.Bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close $title picker") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                }
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = searchHint,
                    description = searchHint,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    focusRequester = focus,
                )
                Text(
                    if (query.isBlank()) Fmt.plural(items.size, "option") else "${Fmt.int(shown.size)} of ${Fmt.int(items.size)} match",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (anyOption != null && query.isBlank()) {
                        item(key = "\u0000any") { PickerRow(anyOption, selected == anyOption.value) { onPick(anyOption.value) } }
                    }
                    items(shown, key = { it.value }) { opt -> PickerRow(opt, opt.value == selected) { onPick(opt.value) } }
                    if (shown.isEmpty()) {
                        item(key = "\u0000none") {
                            Text("Nothing matches “${query.trim()}”.", color = Palette.Muted, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun PickerRow(opt: PickerOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(opt.label, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.Accent else Palette.Text)
            if (!opt.detail.isNullOrBlank()) Text(opt.detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.Accent)
    }
}

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(
    title: String,
    initial: LocalDate?,
    min: LocalDate?,
    max: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val lowYear = min?.year ?: 2000
    val highYear = max?.year ?: LocalDate.now().year
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(DAY_MS),
        initialDisplayedMonthMillis = initial?.withDayOfMonth(1)?.toEpochDay()?.times(DAY_MS),
        yearRange = lowYear..highYear,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, DAY_MS))
                return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
            }
            override fun isSelectableYear(year: Int): Boolean = year in lowYear..highYear
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = Palette.Surface)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                onDismiss()
            }, enabled = state.selectedDateMillis != null) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = { onClear(); onDismiss() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(
            state = state,
            colors = colors,
            title = { Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}
