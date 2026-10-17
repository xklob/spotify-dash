package com.caleb.spotifydash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caleb.spotifydash.DashboardState
import com.caleb.spotifydash.core.Filters
import com.caleb.spotifydash.core.QueryResult

enum class Destination(val title: String, val short: String, val icon: ImageVector) {
    Overview("Overview", "Overview", Icons.Rounded.Dashboard),
    Favorites("Favorites", "Favorites", Icons.Rounded.FavoriteBorder),
    Habits("Habits", "Habits", Icons.Rounded.Schedule),
    Discoveries("Discoveries", "Discover", Icons.Rounded.AutoAwesome),
    History("History", "History", Icons.Rounded.History),
    Data("Data & methods", "Data", Icons.Rounded.Storage),
}

/** Everything a screen can ask the app to do. Screens never talk to the view model directly. */
class DashActions(
    val setFilters: (Filters) -> Unit,
    val filterArtist: (String) -> Unit,
    val filterTitle: (title: String, artist: String) -> Unit,
    val navigate: (Destination) -> Unit,
    val resetFilters: () -> Unit,
    val pickFiles: () -> Unit,
    val pickFolder: () -> Unit,
    val requestDemo: () -> Unit,
    val requestClear: () -> Unit,
    val exportCsv: () -> Unit,
    val loadHistory: (page: Int, order: String) -> Unit,
)

/** Shared inputs for the analysis screens. */
class ScreenEnv(
    val state: DashboardState,
    val result: QueryResult,
    val filters: Filters,
    val wide: Boolean,
    val gutter: Dp,
    val actions: DashActions,
    /** Adds the demo note and filter header; the argument is extra space below each header item. */
    val header: LazyListScope.(gap: Dp) -> Unit,
)

/** Scrolling column for a destination: the filter header first, then the screen's cards. */
@Composable
fun ScreenList(
    listState: LazyListState,
    gutter: Dp,
    header: (LazyListScope.(gap: Dp) -> Unit)?,
    spacing: Dp = 14.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        header?.invoke(this, if (spacing < 14.dp) 14.dp - spacing else 0.dp)
        content()
    }
}

/** Two cards side by side on wide layouts, stacked on phones. */
fun LazyListScope.pair(wide: Boolean, key: String, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (wide) {
        item(key = key) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) { first() }
                Column(Modifier.weight(1f)) { second() }
            }
        }
    } else {
        item(key = "$key-a") { first() }
        item(key = "$key-b") { second() }
    }
}
