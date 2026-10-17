package com.caleb.spotifydash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.caleb.spotifydash.data.DatasetStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a disposable emulator. This flow replaces the debug app's local dataset with fiction. */
@RunWith(AndroidJUnit4::class)
class DashboardUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitForText(text: String) {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SemanticsNodeInteraction.activate() {
        // Exercise the same accessibility action without racing scroll/IME screen coordinates.
        performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }
    private fun navigate(destination: String) {
        if (destination in listOf("Habits", "Discoveries", "Data & methods")) {
            compose.onNodeWithContentDescription("More sections").activate()
            compose.onNodeWithText(destination).activate()
        } else compose.onNodeWithContentDescription(destination).activate()
    }

    @Test fun demoNavigationGlobalFiltersPaginationAndConfirmedDeletionSurviveRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DatasetStore(context)
        store.clear()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Import your files")
            scrollTo("Explore the fictional demo")
            compose.onNodeWithText("Explore the fictional demo").activate()
            waitForText("Fictional demo data")

            for (destination in listOf("Favorites", "Habits", "Discoveries", "Mood & energy", "History", "Data & methods", "Overview")) {
                navigate(destination)
                waitForText(destination)
            }

            compose.onNodeWithContentDescription("Search every view by title, artist, or album")
                .performTextInput("no-such-title-ui-verification")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("History").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("History").activate()
            compose.waitUntil(30_000) { store.loadFilters().search == "no-such-title-ui-verification" }
            scrollTo("No listening matches these filters")
            compose.onNodeWithText("No listening matches these filters").assertIsDisplayed()
            assertEquals("no-such-title-ui-verification", store.loadFilters().search)
            scrollTo("Reset filters")
            compose.onNodeWithText("Reset filters").activate()
            compose.waitUntil(30_000) { store.loadFilters().search.isEmpty() }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("No listening matches these filters").fetchSemanticsNodes().isEmpty() }
            scrollTo("Export all 11,583 as CSV")
            compose.onNodeWithText("Export all 11,583 as CSV").assertIsEnabled()
            scrollTo("Page 1 of 232")
            compose.onNodeWithContentDescription("Last page").activate()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Page 232 of 232").fetchSemanticsNodes().isNotEmpty()
            }
            scrollTo("Showing 11,551 to 11,583 of 11,583")
            compose.onNodeWithText("Showing 11,551 to 11,583 of 11,583").assertIsDisplayed()
            assertEquals("demo", store.load()!!.meta.source)

            navigate("Data & methods")
            scrollTo("Delete saved history")
            compose.onNodeWithText("Delete saved history").activate()
            waitForText("Delete the saved history?")
            compose.onNodeWithText("Cancel").activate()
            assertNotNull(store.load())
            compose.onNodeWithText("Delete saved history").activate()
            waitForText("Delete the saved history?")
            compose.onAllNodesWithText("Delete saved history").onLast().activate()
            waitForText("Import your files")
            assertNull(store.load())
            scenario.recreate()
            waitForText("Import your files")
            assertNull(store.load())
        }
    }
}
