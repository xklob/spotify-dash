package com.caleb.spotifydash

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caleb.spotifydash.core.Analytics
import com.caleb.spotifydash.core.Csv
import com.caleb.spotifydash.core.Dataset
import com.caleb.spotifydash.core.DatasetMeta
import com.caleb.spotifydash.core.Filters
import com.caleb.spotifydash.core.HistoryPage
import com.caleb.spotifydash.core.ImportBuilder
import com.caleb.spotifydash.core.QueryResult
import com.caleb.spotifydash.core.validateFilters
import com.caleb.spotifydash.data.DatasetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class DashboardState(
    val meta: DatasetMeta? = null, val filters: Filters = Filters(), val result: QueryResult? = null,
    val history: HistoryPage = HistoryPage(), val historyOrder: String = "newest",
    val busy: Boolean = false, val busyText: String = "", val error: String? = null, val message: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DatasetStore(application)
    private val resolver = application.contentResolver
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val mutable = MutableStateFlow(DashboardState(busy = true, busyText = "Opening your history…"))
    val state: StateFlow<DashboardState> = mutable.asStateFlow()
    private var engine: Analytics? = null
    private var displayedFilters = Filters()
    private var queryJob: Job? = null
    private var operationJob: Job? = null

    init {
        operationJob = viewModelScope.launch {
            try {
                val loaded = withContext(worker) {
                    val data = store.load(); val filters = store.loadFilters()
                    val analytics = data?.let(::Analytics)
                    Loaded(analytics, filters, analytics?.query(filters), analytics?.history(filters) ?: HistoryPage())
                }
                engine = loaded.analytics
                displayedFilters = loaded.filters
                mutable.value = DashboardState(loaded.analytics?.dataset?.meta, loaded.filters, loaded.result, loaded.history)
            } catch (e: Exception) { fail(e) }
        }
    }

    private data class Loaded(val analytics: Analytics?, val filters: Filters, val result: QueryResult?, val history: HistoryPage)
    private fun fail(e: Exception) {
        if (e is CancellationException) throw e
        mutable.update { it.copy(busy = false, busyText = "", error = e.message?.take(500) ?: "The operation could not be completed. Your previous history is unchanged.") }
    }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }

    private fun operate(label: String, action: suspend () -> Unit) {
        if (operationJob?.isActive == true) return
        queryJob?.cancel()
        operationJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, busyText = label, error = null, message = null) }
            try { action() } catch (e: Exception) { fail(e) }
        }
    }

    fun setFilters(filters: Filters) {
        if (operationJob?.isActive == true) return
        try { validateFilters(filters) } catch (e: IllegalArgumentException) { mutable.update { it.copy(error = e.message) }; return }
        val analytics = engine ?: return
        queryJob?.cancel()
        mutable.update { it.copy(filters = filters, busy = true, busyText = "Updating the view…", error = null) }
        queryJob = viewModelScope.launch {
            try {
                val order = mutable.value.historyOrder
                val result = withContext(worker) {
                    val query = analytics.query(filters)
                    coroutineContext.ensureActive()
                    val history = analytics.history(filters, order = order)
                    store.saveFilters(filters)
                    query to history
                }
                displayedFilters = filters
                mutable.update { it.copy(result = result.first, history = result.second, busy = false, busyText = "") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(filters = displayedFilters) }
                fail(e)
            }
        }
    }

    fun loadHistory(page: Int = 0, order: String = "newest") {
        if (mutable.value.busy) return
        val analytics = engine ?: return
        val filters = mutable.value.filters
        queryJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, busyText = "Opening playback records…") }
            try {
                val history = withContext(worker) { analytics.history(filters, page, order = order) }
                mutable.update { it.copy(history = history, historyOrder = order, busy = false, busyText = "") }
            } catch (e: Exception) { fail(e) }
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        operate("Reading your Spotify files…") { import(uris) }
    }
    fun importFolder(uri: Uri) {
        operate("Finding history files…") {
            val uris = withContext(worker) {
                val root = requireNotNull(DocumentFile.fromTreeUri(getApplication(), uri)) { "This folder could not be opened." }
                val result = mutableListOf<Uri>()
                fun visit(folder: DocumentFile, depth: Int) {
                    require(depth <= 12) { "Choose the Spotify export folder directly." }
                    folder.listFiles().forEach { file ->
                        if (file.isDirectory) visit(file, depth + 1)
                        else if (file.name?.endsWith(".json", true) == true) result.add(file.uri)
                    }
                }
                visit(root, 0)
                require(result.isNotEmpty()) { "No JSON files were found. Extract Spotify's ZIP first, then choose its Extended Streaming History folder." }
                result
            }
            import(uris)
        }
    }
    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifEmpty { "history.json" }

    private suspend fun import(uris: List<Uri>) {
        val old = engine?.dataset?.takeUnless { it.meta.source == "demo" }
        val filters = if (engine?.dataset?.meta?.source == "demo") Filters(timezone = mutable.value.filters.timezone) else mutable.value.filters
        val order = mutable.value.historyOrder
        val loaded = withContext(worker) {
            val builder = ImportBuilder(old)
            for ((index, uri) in uris.withIndex()) {
                coroutineContext.ensureActive()
                val name = displayName(uri)
                mutable.update { it.copy(busyText = "Reading file ${index + 1} of ${uris.size}: $name") }
                val taskContext = coroutineContext
                requireNotNull(resolver.openInputStream(uri)) { "$name could not be opened." }.bufferedReader().use { input ->
                    builder.add(input, name) { taskContext.ensureActive() }
                }
            }
            val data = builder.finish()
            mutable.update { it.copy(busyText = "Building your listening views…") }
            val analytics = Analytics(data)
            val result = analytics.query(filters)
            val history = analytics.history(filters, order = order)
            store.save(data); store.saveFilters(filters)
            Loaded(analytics, filters, result, history)
        }
        engine = loaded.analytics
        displayedFilters = loaded.filters
        mutable.update { it.copy(meta = loaded.analytics!!.dataset.meta, filters = loaded.filters, result = loaded.result, history = loaded.history,
            busy = false, busyText = "", message = "History imported. ${loaded.analytics.dataset.events.size} events are ready to explore.") }
    }

    fun loadDemo() {
        operate("Opening the fictional demo…") {
            val filters = Filters(timezone = mutable.value.filters.timezone)
            val loaded = withContext(worker) {
                val data = store.demo(); val analytics = Analytics(data)
                val result = analytics.query(filters); val history = analytics.history(filters)
                store.save(data); store.saveFilters(filters)
                Loaded(analytics, filters, result, history)
            }
            engine = loaded.analytics
            displayedFilters = filters
            mutable.value = DashboardState(loaded.analytics!!.dataset.meta, filters, loaded.result, loaded.history, message = "Fictional demo loaded. Import your files whenever you're ready.")
        }
    }

    fun clearData() {
        operate("Clearing saved history…") {
            withContext(worker) { store.clear() }
            engine = null
            displayedFilters = Filters()
            mutable.value = DashboardState(message = "Saved history cleared. Your original export files were not changed.")
        }
    }

    fun exportCsv(uri: Uri) {
        if (mutable.value.busy) return
        val analytics = engine ?: return
        val filters = mutable.value.filters
        operate("Exporting all matching records…") {
            val count = withContext(worker) {
                val rows = analytics.all(filters)
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "The export destination could not be opened." }.bufferedWriter().use { Csv.write(rows, filters.timezone, it) }
                rows.size
            }
            mutable.update { it.copy(busy = false, busyText = "", message = "$count matching records exported to CSV.") }
        }
    }
}
