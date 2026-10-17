package com.caleb.spotifydash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.core.Ranking

private val Views = listOf("artists" to "Artists & shows", "tracks" to "Titles", "albums" to "Albums")
private val Nouns = mapOf("artists" to ("artist or show" to "artists and shows"), "tracks" to ("title" to "titles"), "albums" to ("album" to "albums"))

/** Full rankings of artists and shows, titles, and albums, lazily listed. */
@Composable
fun FavoritesScreen(env: ScreenEnv) {
    val r = env.result
    val listState = rememberLazyListState()
    var view by rememberSaveable { mutableStateOf("artists") }
    var sort by rememberSaveable { mutableStateOf("time") }
    var query by rememberSaveable { mutableStateOf("") }
    val source = when (view) {
        "tracks" -> r.tracks
        "albums" -> r.albums
        else -> r.artists
    }
    val ranked = remember(Ref(source), sort) {
        if (sort == "time") source
        else source.sortedWith(compareByDescending<Ranking> { it.plays }.thenByDescending { it.ms }.thenBy { it.name })
    }
    val matches: List<Int>? = remember(Ref(ranked), query, view) {
        val q = query.trim()
        if (q.isEmpty()) null else ranked.indices.filter { i ->
            val rk = ranked[i]
            rk.name.contains(q, true) || (view != "artists" && rk.artist.contains(q, true)) || (view == "tracks" && rk.album.contains(q, true))
        }
    }
    val count = matches?.size ?: ranked.size
    val metric: (Ranking) -> Double = { if (sort == "time") it.ms.toDouble() else it.plays.toDouble() }
    val top = ranked.firstOrNull()?.let(metric) ?: 0.0
    val (one, many) = Nouns.getValue(view)

    val open: (Ranking) -> Unit = { rk ->
        when (view) {
            "artists" -> { env.actions.filterArtist(rk.name); view = "tracks"; query = "" }
            "albums" -> { env.actions.filterTitle(rk.name, rk.artist); view = "tracks"; query = "" }
            else -> env.actions.filterTitle(rk.name, rk.artist)
        }
    }
    val openLabel: (Ranking) -> String = { rk ->
        when (view) {
            "artists" -> "Filter every view to ${rk.name} and list their titles"
            "albums" -> "Filter every view to ${rk.artist} and search their records for ${rk.name}"
            else -> "Filter every view to ${rk.name} by ${rk.artist}"
        }
    }

    ScreenList(listState, env.gutter, env.header, spacing = 0.dp) {
        if (env.filters.artist.isNotEmpty()) {
            item(key = "drill") {
                Column {
                    Panel {
                        Eyebrow("Filtered to")
                        Text(env.filters.artist, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                        Text(
                            "${Fmt.msHours(r.summary.ms)} · ${Fmt.plural(r.summary.events, "event")} · ${Fmt.plural(r.summary.tracks, "title")}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularNumbers),
                            color = Palette.Text2,
                        )
                        SecondaryButton("All artists", Icons.AutoMirrored.Rounded.ArrowBack, onClick = {
                            env.actions.filterArtist("")
                            view = "artists"
                            query = ""
                        })
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        item(key = "controls") {
            Column {
                Panel {
                    PanelHeader(
                        "Rankings",
                        "${Fmt.int(ranked.size)} ${if (ranked.size == 1) one else many} in the current filters, ranked by ${if (sort == "time") "listening time" else "event count"}",
                    )
                    Segmented(Views, view, { view = it; query = "" }, label = "Ranking type")
                    Segmented(listOf("time" to "By time", "events" to "By events"), sort, { sort = it }, label = "Rank by")
                    SearchField(query, { query = it }, placeholder = "Find in $many", description = "Find in these rankings by name")
                    Text(
                        when {
                            count == 0 -> "No names match"
                            matches == null -> "Showing all ${Fmt.int(count)}"
                            else -> "${Fmt.int(count)} of ${Fmt.int(ranked.size)} match"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        items(count, key = { i -> "$view-$sort-${matches?.get(i) ?: i}" }) { i ->
            val index = matches?.get(i) ?: i
            val rk = ranked[index]
            FavoriteRow(
                rank = index + 1,
                item = rk,
                view = view,
                sort = sort,
                fraction = if (top > 0) metric(rk) / top else 0.0,
                first = i == 0,
                last = i == count - 1,
                onOpen = { open(rk) },
                openLabel = openLabel(rk),
                onArtist = { env.actions.filterArtist(rk.artist) },
            )
        }
        item(key = "caption") {
            Column {
                Spacer(Modifier.height(14.dp))
                Caption(
                    buildString {
                        append("Events count every recorded playback of any length in the current filters, so they are not the 30 second play measure. ")
                        append("Hours sum recorded playback time. Skip rate divides recorded skips by events with a known skip flag (n). ")
                        append(
                            when (view) {
                                "artists" -> "Selecting a name filters every view to that artist and lists their titles."
                                "albums" -> "Selecting an album filters every view to its artist and searches titles and albums for its name, so similarly named titles can also match."
                                else -> "Selecting a title filters every view to it with a search and an artist filter."
                            },
                        )
                        if (view != "tracks") append(" Spotify links appear on titles only, because the export has no artist or album identifiers.")
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    rank: Int,
    item: Ranking,
    view: String,
    sort: String,
    fraction: Double,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    openLabel: String,
    onArtist: () -> Unit,
) {
    val radius = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
    )
    Column(Modifier.clip(shape).background(Palette.Surface)) {
        if (!first) HorizontalDivider(color = Palette.Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                Fmt.int(rank),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                color = Palette.Faint,
                modifier = Modifier.widthIn(min = 28.dp).padding(top = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                when (view) {
                    "tracks" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClickLabel = "Filter every view to ${item.artist}", role = Role.Button, onClick = onArtist)
                                .padding(vertical = 8.dp),
                        )
                        if (item.album.isNotBlank()) {
                            Text(" · ${item.album}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                    "albums" -> if (item.artist.isNotBlank()) Text(item.artist, style = MaterialTheme.typography.bodySmall, color = Palette.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ValueBar(fraction, modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    "${if (sort == "time") Fmt.plural(item.plays, "event") else Fmt.msHours(item.ms)} · skip ${if (item.skipKnown > 0) "${Fmt.pct(item.skipRate)} (n ${Fmt.int(item.skipKnown)})" else "n/a, no flags"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                    color = Palette.Muted,
                )
                Text("${Fmt.date(item.first)} to ${Fmt.date(item.last)}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (sort == "time") Fmt.msHours(item.ms) else Fmt.int(item.plays),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
                    color = Palette.Text,
                    modifier = Modifier.padding(end = 10.dp),
                )
                if (sort != "time") Text("events", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(end = 10.dp))
                if (view == "tracks") SpotifyLinkButton(item.uri, item.name)
            }
        }
    }
}
