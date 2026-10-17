package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.HistoryRow
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Every filtered playback record, a page at a time, with a CSV export of the whole population. */
@Composable
fun HistoryScreen(env: ScreenEnv) {
    val page = env.state.history
    val order = env.state.historyOrder
    val busy = env.state.busy
    val tz = env.filters.timezone
    val listState = rememberLazyListState()
    val rows = page.rows
    // Pages are full except possibly the last, so the range is exact on every page.
    val firstIndex = if (rows.isEmpty()) 0 else if (page.page >= page.pages - 1) page.total - rows.size + 1 else page.page * rows.size + 1
    val lastIndex = if (rows.isEmpty()) 0 else firstIndex + rows.size - 1
    var shownPage by rememberSaveable { mutableIntStateOf(page.page) }
    LaunchedEffect(page.page, order) {
        if (page.page != shownPage) {
            shownPage = page.page
            listState.scrollToItem(0)
        }
    }
    val goTo: (Int) -> Unit = { p -> env.actions.loadHistory(p, order) }

    ScreenList(listState, env.gutter, env.header, spacing = 0.dp) {
        item(key = "records-head") {
            Column {
                Panel {
                    PanelHeader(
                        "Playback records",
                        "${Fmt.plural(page.total, "event")} match the filters. Times show when playback ended, ${Fmt.zoneLabel(tz)}.",
                    )
                    Segmented(
                        listOf("newest" to "Newest first", "oldest" to "Oldest first"),
                        order,
                        { if (it != order) env.actions.loadHistory(0, it) },
                        label = "Record order",
                    )
                    PrimaryButton(
                        "Export all ${Fmt.int(page.total)} as CSV",
                        Icons.Rounded.Download,
                        onClick = env.actions.exportCsv,
                        enabled = !busy && page.total > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Caption("The CSV holds every matching event, not just this page, with UTC and local timestamps. You choose where it is saved.")
                }
                Spacer(Modifier.height(10.dp))
                if (rows.isNotEmpty()) {
                    Text(
                        "Showing ${Fmt.int(firstIndex)} to ${Fmt.int(lastIndex)} of ${Fmt.int(page.total)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                        color = Palette.Muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Pager(page.page, page.pages, goTo, enabled = !busy)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "no-rows") {
                Panel { Text("No playback records match the current filters.", color = Palette.Text2) }
            }
        }
        itemsIndexed(rows, key = { i, row -> "${page.page}-$i-${row.event.id}" }) { i, row ->
            RecordRow(row, tz, first = i == 0, last = i == rows.lastIndex, onArtist = { env.actions.filterArtist(row.event.artist) })
        }
        if (rows.isNotEmpty()) {
            item(key = "pager-bottom") {
                Column(Modifier.padding(top = 10.dp)) {
                    Pager(page.page, page.pages, goTo, enabled = !busy)
                    Caption(
                        "Search, date range, type, minimum played, artist, device, country, and shuffle filters all apply here. " +
                            "Tap a record for every recorded field.",
                    )
                }
            }
        }
    }
}

private val utcFormat = DateTimeFormatter.ISO_INSTANT

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRow(row: HistoryRow, tz: String, first: Boolean, last: Boolean, onArtist: () -> Unit) {
    val e = row.event
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    val radius = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
    )
    val zoneShort = Fmt.zoneAbbrev(e.ts, tz)
    Column(Modifier.clip(shape).background(Palette.Surface)) {
        if (!first) HorizontalDivider(color = Palette.Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Hide details" else "Show details", role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Details shown" else "Details hidden" }
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClickLabel = "Filter every view to ${e.artist}", role = Role.Button, onClick = onArtist)
                                .padding(vertical = 8.dp),
                        )
                        if (e.album.isNotBlank()) {
                            Text(" · ${e.album}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                }
                SpotifyLinkButton(e.uri, e.title)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 10.dp)) {
                Text(
                    "${Fmt.date(row.date)} · ${row.time} $zoneShort",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                    color = Palette.Text2,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Text(
                    "${Fmt.clock(e.ms.toDouble())} played",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                    color = Palette.Text2,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                KindBadge(e.kind, Modifier.align(Alignment.CenterVertically))
                if (e.media == "video") Tag("Video", modifier = Modifier.align(Alignment.CenterVertically))
                if (e.skipped == true) Tag("Skipped", color = Palette.DangerText, background = Palette.DangerWash, modifier = Modifier.align(Alignment.CenterVertically))
                if (e.shuffle == true) Tag("Shuffle", modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (open) {
                Column(Modifier.padding(top = 6.dp, end = 10.dp)) {
                    FactGrid(
                        listOf(
                            FactItem("Ended (local)", "${Fmt.date(row.date)} ${row.time}", "${Fmt.zoneLabel(tz)} ($zoneShort)"),
                            FactItem("Ended (UTC)", utcFormat.format(Instant.ofEpochMilli(e.ts)).replace("T", " ").removeSuffix("Z") + " UTC"),
                            FactItem("Played", Fmt.clock(e.ms.toDouble()), "${Fmt.int(e.ms)} ms"),
                            FactItem("Content", "${Fmt.kind(e.kind)} · ${if (e.media == "video") "video" else "audio"}"),
                            FactItem("Device", e.platform),
                            FactItem("Country", Fmt.country(e.country), e.country.takeIf { it.length == 2 }),
                            FactItem("Shuffle", Fmt.flag(e.shuffle, "On", "Off")),
                            FactItem("Skipped", Fmt.flag(e.skipped)),
                            FactItem("Offline", Fmt.flag(e.offline)),
                            FactItem("Private session", Fmt.flag(e.incognito)),
                            FactItem("How it started", Fmt.reason(e.reasonStart), e.reasonStart.ifBlank { null }),
                            FactItem("How it ended", Fmt.reason(e.reasonEnd), e.reasonEnd.ifBlank { null }),
                        ),
                        columns = 2,
                    )
                    if (e.uri.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Spotify URI", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Text(e.uri, style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
                Text(if (open) "Less" else "Details", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}
