package com.caleb.spotifydash.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caleb.spotifydash.DashboardState
import com.caleb.spotifydash.DashboardViewModel
import com.caleb.spotifydash.core.Filters
import kotlinx.coroutines.delay

private val JsonTypes = arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")

@Composable
fun DashboardApp(model: DashboardViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openUrl = remember(context) { { url: String -> openExternal(context, url) } }
    SpotifyDashTheme {
        CompositionLocalProvider(LocalOpenUrl provides openUrl, LocalContentColor provides Palette.Text) {
            DashboardShell(state, model)
        }
    }
}

private enum class NavMode { Bottom, Rail, Side }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardShell(state: DashboardState, model: DashboardViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Overview) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val meta = state.meta
    val result = state.result

    // Typing updates a local draft; the query runs once typing pauses.
    var searchDraft by rememberSaveable { mutableStateOf(state.filters.search) }
    var pushedSearch by rememberSaveable { mutableStateOf(state.filters.search) }
    val latestFilters by rememberUpdatedState(state.filters)
    LaunchedEffect(state.filters.search) {
        if (state.filters.search != pushedSearch) {
            searchDraft = state.filters.search
            pushedSearch = state.filters.search
        }
    }
    LaunchedEffect(searchDraft) {
        if (searchDraft == latestFilters.search) return@LaunchedEffect
        delay(350)
        pushedSearch = searchDraft
        model.setFilters(latestFilters.copy(search = searchDraft))
    }
    val applyFilters: (Filters) -> Unit = { f ->
        // Keep a search the user is still typing unless this change sets the search itself.
        val merged = if (f.search == latestFilters.search) f.copy(search = searchDraft) else f
        searchDraft = merged.search
        pushedSearch = merged.search
        model.setFilters(merged)
    }
    val resetFilters: () -> Unit = {
        searchDraft = ""
        pushedSearch = ""
        model.setFilters(Filters(timezone = latestFilters.timezone))
    }

    // A new dataset (first import, demo, or real files replacing the demo) starts at the overview.
    var lastSource by rememberSaveable { mutableStateOf(meta?.source) }
    LaunchedEffect(meta?.source) {
        if (meta?.source != lastSource) {
            if (meta != null) destination = Destination.Overview
            lastSource = meta?.source
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
        model.dismissMessage()
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.importFiles(uris)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.importFolder(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.exportCsv(uri)
    }
    val metadataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importMetadata(uri) }
    val metadataExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) model.exportMetadata(uri) }
    LaunchedEffect(destination, state.busy, state.mood) {
        if (destination == Destination.Mood && !state.busy && state.mood == null && state.error == null) model.requestMood()
    }

    val actions = DashActions(
        setFilters = applyFilters,
        filterArtist = { artist -> applyFilters(latestFilters.copy(artist = artist)) },
        filterTitle = { title, artist -> applyFilters(latestFilters.copy(artist = artist, search = title)) },
        navigate = { destination = it },
        resetFilters = resetFilters,
        pickFiles = { filesLauncher.launch(JsonTypes) },
        pickFolder = { folderLauncher.launch(null) },
        requestDemo = { if (meta?.source == "import") confirm = "demo" else model.loadDemo() },
        requestClear = { confirm = "clear" },
        exportCsv = {
            val scope = result?.scope
            exportLauncher.launch("spotify-dash-history_${scope?.start?.ifEmpty { null } ?: "start"}_to_${scope?.end?.ifEmpty { null } ?: "end"}.csv")
        },
        loadHistory = { page, order -> model.loadHistory(page, order) },
        pickMetadata = { metadataLauncher.launch(JsonTypes) },
        exportMetadata = { metadataExport.launch("spotify-dash-metadata.json") },
        moodOptions = model::requestMood,
    )

    BackHandler(enabled = meta != null && destination != Destination.Overview) { destination = Destination.Overview }

    // Brief query refreshes only show the thin bar; longer work also shows the status pill.
    var showTask by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) {
        if (state.busy) { delay(700); showTask = true } else showTask = false
    }

    val bounds = remember(meta, state.filters.timezone) { DataBounds.of(meta, state.filters.timezone) }
    val years = remember(result?.options) { result?.options?.years.orEmpty().sortedDescending() }
    val activeCount = activeChips(state.filters, bounds, years).size

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val navMode = when {
            maxWidth >= 1000.dp -> NavMode.Side
            maxWidth >= 600.dp -> NavMode.Rail
            else -> NavMode.Bottom
        }
        val imeVisible = WindowInsets.isImeVisible
        val shortScreen = maxHeight < 500.dp
        Row(Modifier.fillMaxSize()) {
            if (meta != null && navMode == NavMode.Side) {
                SideNav(destination, { destination = it }, state)
            } else if (meta != null && navMode == NavMode.Rail) {
                RailNav(destination) { destination = it }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (meta != null) {
                    TopBar(
                        destination = destination,
                        state = state,
                        showWordmark = navMode != NavMode.Side,
                        compact = shortScreen,
                        activeFilters = activeCount,
                        onFilters = { sheetOpen = true },
                    )
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxSize(), color = Palette.Accent, trackColor = Palette.Surface2)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    val contentWidth = maxWidth
                    val gutter: Dp = when {
                        contentWidth > 1140.dp -> (contentWidth - 1080.dp) / 2
                        contentWidth >= 600.dp -> 24.dp
                        else -> 16.dp
                    }
                    val wide = contentWidth >= 840.dp
                    Column(Modifier.fillMaxSize()) {
                        state.error?.let { error ->
                            Notice(
                                Tone.Error,
                                "Something went wrong",
                                error,
                                modifier = Modifier.padding(start = gutter, end = gutter, top = 12.dp),
                                onDismiss = model::dismissError,
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            holder.SaveableStateProvider(if (meta == null) "welcome" else destination.name) {
                                MainContent(
                                    state = state,
                                    destination = destination,
                                    actions = actions,
                                    gutter = gutter,
                                    wide = wide,
                                    bottomInset = navMode != NavMode.Bottom || meta == null,
                                    searchDraft = searchDraft,
                                    onSearchDraft = { searchDraft = it },
                                    bounds = bounds,
                                    onOpenSheet = { sheetOpen = true },
                                )
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTask && state.busy && state.busyText.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    ) {
                        TaskPill(state.busyText)
                    }
                    SnackbarHost(
                        snackbar,
                        modifier = Modifier.align(Alignment.BottomCenter).then(if (navMode != NavMode.Bottom || meta == null) Modifier.navigationBarsPadding() else Modifier),
                    ) { data ->
                        Snackbar(data, containerColor = Palette.Surface3, contentColor = Palette.Text, actionColor = Palette.Accent, dismissActionContentColor = Palette.Text2)
                    }
                }
                if (meta != null && navMode == NavMode.Bottom && !imeVisible) {
                    BottomNav(destination) { destination = it }
                }
            }
        }
    }

    if (sheetOpen && meta != null) {
        FilterSheet(
            filters = state.filters.copy(search = searchDraft),
            options = result?.options,
            bounds = bounds,
            onFilters = applyFilters,
            onReset = resetFilters,
            onDismiss = { sheetOpen = false },
        )
    }

    when (confirm) {
        "clear" -> ConfirmDialog(
            title = "Delete the saved history?",
            body = "This removes the listening history saved in this app on this device. Your original Spotify files are not touched, and you can import them again at any time.",
            confirmLabel = "Delete saved history",
            danger = true,
            onConfirm = { confirm = null; model.clearData() },
            onDismiss = { confirm = null },
        )
        "demo" -> ConfirmDialog(
            title = "Replace your history with the demo?",
            body = "The fictional demo replaces the imported history saved in this app. To return to your own data afterwards, import your JSON files again.",
            confirmLabel = "Load fictional demo",
            danger = false,
            onConfirm = { confirm = null; model.loadDemo() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun MainContent(
    state: DashboardState,
    destination: Destination,
    actions: DashActions,
    gutter: Dp,
    wide: Boolean,
    bottomInset: Boolean,
    searchDraft: String,
    onSearchDraft: (String) -> Unit,
    bounds: DataBounds,
    onOpenSheet: () -> Unit,
) {
    val meta = state.meta
    val result = state.result
    val extraBottom = if (bottomInset) Modifier.navigationBarsPadding() else Modifier
    when {
        meta == null && state.busy -> CenteredStatus(state.busyText.ifBlank { "Opening your history…" }, extraBottom)
        meta == null -> WelcomeScreen(actions, state.busy, gutter, extraBottom)
        destination == Destination.Data -> DataScreen(state, actions, gutter, wide, extraBottom)
        result == null -> Column(
            extraBottom.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.busy) {
                LoadingPanel(
                    "Analyzing ${Fmt.int(meta.events)} events on this device",
                    "The first view of a large archive takes a few seconds while every timestamp is placed in ${Fmt.zoneLabel(state.filters.timezone)}.",
                )
            } else {
                Panel {
                    Text("This view could not be calculated", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text("Try again, or reset the filters to start from the full history.", style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Try again", Icons.Rounded.RestartAlt, onClick = { actions.setFilters(state.filters) })
                        SecondaryButton("Reset filters", null, onClick = actions.resetFilters)
                    }
                }
            }
        }
        else -> {
            val header: androidx.compose.foundation.lazy.LazyListScope.(Dp) -> Unit = { gap ->
                if (meta.source == "demo") {
                    item(key = "demo-note") {
                        DemoBanner(Modifier.padding(bottom = gap)) { actions.navigate(Destination.Data) }
                    }
                }
                item(key = "filters") {
                    Box(Modifier.padding(bottom = gap)) {
                        FilterHeader(
                            filters = state.filters,
                            searchDraft = searchDraft,
                            onSearchDraft = onSearchDraft,
                            bounds = bounds,
                            options = result.options,
                            onFilters = actions.setFilters,
                            onOpenSheet = onOpenSheet,
                            onReset = actions.resetFilters,
                        )
                    }
                }
            }
            val env = ScreenEnv(state, result, state.filters, wide, gutter, actions, header)
            Box(extraBottom.fillMaxSize()) {
                if (result.summary.events == 0) {
                    ScreenList(androidx.compose.foundation.lazy.rememberLazyListState(), gutter, header) {
                        item(key = "empty") { EmptyFilterResult(actions.resetFilters) }
                    }
                } else when (destination) {
                    Destination.Overview -> OverviewScreen(env)
                    Destination.Favorites -> FavoritesScreen(env)
                    Destination.Habits -> HabitsScreen(env)
                    Destination.Discoveries -> DiscoveriesScreen(env)
                    Destination.Mood -> MoodScreen(env)
                    Destination.History -> HistoryScreen(env)
                    Destination.Data -> Unit
                }
            }
        }
    }
}

/** One-line reminder that the loaded records are fictional, with a shortcut to import real files. */
@Composable
private fun DemoBanner(modifier: Modifier, onImport: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.VioletWash)
            .border(1.dp, Palette.Violet.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Science, contentDescription = null, tint = Palette.VioletText, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Fictional demo data", style = MaterialTheme.typography.labelLarge, color = Palette.VioletText)
            Text("Generated records, not anyone's real listening", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
        }
        QuietButton("Import", onClick = onImport)
    }
}

@Composable
private fun CenteredStatus(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            WaveGlyph(size = 40.dp)
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
        }
    }
}

@Composable
private fun TaskPill(text: String) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface3)
            .border(1.dp, Palette.Line2, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = Palette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopBar(destination: Destination, state: DashboardState, showWordmark: Boolean, compact: Boolean, activeFilters: Int, onFilters: () -> Unit) {
    val meta = state.meta ?: return
    val result = state.result
    val tz = Fmt.zoneLabel(state.filters.timezone)
    val subtitle = if (destination != Destination.Data && result != null) {
        "${Fmt.date(result.scope.start)} to ${Fmt.date(result.scope.end)} · $tz"
    } else {
        val b = DataBounds.of(meta, state.filters.timezone)
        "${b.first?.let { Fmt.date(it) } ?: "n/a"} to ${b.last?.let { Fmt.date(it) } ?: "n/a"} · ${Fmt.plural(meta.events, "event")}"
    }
    Column(Modifier.fillMaxWidth().background(Palette.Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 52.dp else 64.dp).padding(start = 16.dp, end = 6.dp, top = if (compact) 2.dp else 6.dp, bottom = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showWordmark) {
                WaveGlyph(size = 24.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        destination.title,
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    )
                    if (meta.source == "demo") Tag("Demo", color = Palette.VioletText, background = Palette.VioletWash)
                    else if (destination != Destination.Data && activeFilters > 0) Tag("Filtered", color = Palette.Accent, background = Palette.AccentWash)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (destination != Destination.Data && result != null) {
                IconButton(onClick = onFilters, modifier = Modifier.size(52.dp)) {
                    BadgedBox(badge = { if (activeFilters > 0) Badge(containerColor = Palette.Accent, contentColor = Palette.AccentInk) { Text("$activeFilters") } }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = if (activeFilters > 0) "Filters, $activeFilters active" else "Filters",
                            tint = if (activeFilters > 0) Palette.Accent else Palette.Text2,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Palette.Line, thickness = 1.dp)
    }
}

@Composable
private fun BottomNav(current: Destination, onSelect: (Destination) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    var moreOpen by remember { mutableStateOf(false) }
    val main = listOf(Destination.Overview, Destination.Favorites, Destination.Mood, Destination.History)
    if (moreOpen) AlertDialog(onDismissRequest = { moreOpen = false }, title = { Text("More sections") }, text = {
        Column { Destination.entries.filter { it !in main }.forEach { d ->
            TextButton(onClick = { moreOpen = false; onSelect(d) }, modifier = Modifier.fillMaxWidth()) { Icon(d.icon, null); Spacer(Modifier.width(10.dp)); Text(d.title) }
        } }
    }, confirmButton = { TextButton(onClick = { moreOpen = false }) { Text("Close") } })
    NavigationBar(containerColor = Palette.BgSide, tonalElevation = 0.dp) {
        main.forEach { d ->
            NavigationBarItem(
                selected = d == current,
                onClick = { onSelect(d) },
                icon = { Icon(d.icon, contentDescription = null) },
                // With large text, labels would truncate, so the bar shows icons and the
                // top bar title names the current section; every item keeps its spoken name.
                label = if (fontScale < 1.3f) {
                    {
                        Text(
                            d.short,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.semantics { contentDescription = d.title },
                        )
                    }
                } else null,
                modifier = if (fontScale < 1.3f) Modifier else Modifier.semantics { contentDescription = d.title },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.Accent,
                    selectedTextColor = Palette.Text,
                    indicatorColor = Palette.AccentWash,
                    unselectedIconColor = Palette.Muted,
                    unselectedTextColor = Palette.Muted,
                ),
            )
        }
        NavigationBarItem(selected = current !in main, onClick = { moreOpen = true }, icon = { Icon(Icons.Rounded.MoreHoriz, null) }, modifier = Modifier.semantics { contentDescription = "More sections" }, label = if (fontScale < 1.3f) { { Text("More") } } else null,
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Palette.Accent, selectedTextColor = Palette.Text, indicatorColor = Palette.AccentWash, unselectedIconColor = Palette.Muted, unselectedTextColor = Palette.Muted))
    }
}

@Composable
private fun RailNav(current: Destination, onSelect: (Destination) -> Unit) {
    NavigationRail(
        containerColor = Palette.BgSide,
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding(),
    ) {
        Destination.entries.forEach { d ->
            NavigationRailItem(
                selected = d == current,
                onClick = { onSelect(d) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = {
                    Text(d.short, maxLines = 1, style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { contentDescription = d.title })
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Palette.Accent,
                    selectedTextColor = Palette.Text,
                    indicatorColor = Palette.AccentWash,
                    unselectedIconColor = Palette.Muted,
                    unselectedTextColor = Palette.Muted,
                ),
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SideNav(current: Destination, onSelect: (Destination) -> Unit, state: DashboardState) {
    val meta = state.meta ?: return
    Column(
        Modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(Palette.BgSide)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Wordmark(Modifier.padding(start = 8.dp))
        Text("Extended history explorer", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(start = 38.dp, bottom = 22.dp))
        Destination.entries.forEach { d ->
            val on = d == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Palette.Surface2 else Palette.BgSide)
                    .clickable(role = Role.Tab, onClick = { onSelect(d) })
                    .semantics { selected = on }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Palette.Accent else Palette.BgSide))
                Icon(d.icon, contentDescription = null, tint = if (on) Palette.Accent else Palette.Muted, modifier = Modifier.size(20.dp))
                Text(d.title, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Text2)
            }
        }
        Spacer(Modifier.height(24.dp))
        val b = DataBounds.of(meta, state.filters.timezone)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (meta.source == "demo") Palette.VioletWash else Palette.Surface)
                .border(1.dp, if (meta.source == "demo") Palette.Violet.copy(alpha = 0.4f) else Palette.Line, RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "Open Data and methods") { onSelect(Destination.Data) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Eyebrow(if (meta.source == "demo") "Fictional demo" else "Imported history", color = if (meta.source == "demo") Palette.VioletText else Palette.Muted)
            Text(
                "${b.first?.let { Fmt.month(it.toString()) } ?: "n/a"} to ${b.last?.let { Fmt.month(it.toString()) } ?: "n/a"}",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Text,
            )
            Text("${Fmt.plural(meta.events, "event")} · ${Fmt.plural(meta.files.size, "file")}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Row(Modifier.padding(top = 12.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(14.dp).padding(top = 2.dp))
            Text("Analyzed on this device. The app has no internet access.", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Text2,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (danger) Palette.Danger else Palette.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.Text2) } },
    )
}
