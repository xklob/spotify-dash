package com.caleb.spotifydash

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caleb.spotifydash.core.*
import com.caleb.spotifydash.data.DatasetStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MoodUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun click(node: SemanticsNodeInteraction) { node.performSemanticsAction(SemanticsActions.OnClick) { it() }; compose.waitForIdle() }
    private fun waitText(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun scroll(text: String) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)) }
    @Test fun metadataImportPersistenceWeightingChartsAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        val store = DatasetStore(context); store.clear()
        val data = ImportBuilder().apply { instrumentation.context.assets.open("mood-history.json").bufferedReader().use { add(it, "mood-history.json") } }.finish()
        store.save(data); store.saveFilters(Filters(start = "2024-01-01", end = "2024-01-31", timezone = "UTC"))
        val input = File(context.cacheDir, "mood-test.json"); val output = File(context.cacheDir, "mood-export.json")
        instrumentation.context.assets.open("mood-metadata.json").use { source -> input.outputStream().use(source::copyTo) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitText("Overview"); click(compose.onNodeWithContentDescription("Mood & energy")); waitText("Catalog metadata")
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Calculating mood and energy…").fetchSemanticsNodes().isEmpty() }
                scenario.onActivity { ViewModelProvider(it)[DashboardViewModel::class.java].importMetadata(Uri.fromFile(input)) }
                compose.waitUntil(30_000) { store.loadMetadata()?.tracks?.size == 5 }
                scroll("Musical energy"); waitText("0.20")
                scenario.onActivity { ViewModelProvider(it)[DashboardViewModel::class.java].requestMood(MoodOptions(weight = "tracks")) }
                waitText("0.40")
                for (title in listOf("Mood over time", "Mood distribution", "Earlier and later", "Mood by weekday and hour", "Sound and texture", "Energy and playback behavior", "New and familiar tracks", "Catalog coverage by year", "Tracks behind the scores")) {
                    scroll(title); compose.onNodeWithText(title).assertIsDisplayed()
                }
                scenario.onActivity { ViewModelProvider(it)[DashboardViewModel::class.java].exportMetadata(Uri.fromFile(output)) }
                compose.waitUntil(30_000) { output.exists() && output.length() > 0 }
                assertEquals(store.loadMetadata(), output.bufferedReader().use(MetadataCodec::read))
                scenario.recreate(); waitText("Mood & energy"); scroll("Musical energy"); waitText("0.40")
                assertEquals(5, store.loadMetadata()!!.tracks.size)
                input.writeText("{\"format\":\"bad\"}")
                scenario.onActivity { ViewModelProvider(it)[DashboardViewModel::class.java].importMetadata(Uri.fromFile(input)) }
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Operation could not be completed").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Choose a Spotify Dash metadata JSON file (format version 1).").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(5, store.loadMetadata()!!.tracks.size)
            }
        } finally { input.delete(); output.delete() }
    }
}
