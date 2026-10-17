package com.caleb.spotifydash.data

import android.content.Context
import android.util.AtomicFile
import com.caleb.spotifydash.core.Dataset
import com.caleb.spotifydash.core.DatasetCodec
import com.caleb.spotifydash.core.Filters
import com.caleb.spotifydash.core.validateFilters
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class DatasetStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "history.json.gz"))
    private val preferences = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
    fun load(): Dataset? {
        if (!file.baseFile.exists()) return null
        return file.openRead().use { raw -> GZIPInputStream(raw).bufferedReader().use(DatasetCodec::read) }
    }
    fun save(data: Dataset) {
        val stream = file.startWrite()
        try {
            val gzip = GZIPOutputStream(stream)
            val writer = gzip.bufferedWriter()
            DatasetCodec.write(data, writer)
            writer.flush(); gzip.finish(); gzip.flush()
            file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun clear() { file.delete(); preferences.edit().clear().commit() }
    // aapt transparently expands assets ending in .gz and drops that suffix.
    // Keep an explicit .gzip suffix so the stored bytes have a stable format.
    fun demo(): Dataset = context.assets.open("demo.json.gzip").use { GZIPInputStream(it).bufferedReader().use(DatasetCodec::read) }
    fun loadFilters(): Filters = runCatching {
        val j = JSONObject(preferences.getString("filters", "{}") ?: "{}")
        Filters(j.optString("start"), j.optString("end"), j.optString("kind", "all"), j.optLong("minMs"),
            j.optString("search"), j.optString("artist"), j.optString("platform"), j.optString("country"),
            j.optString("timezone", "America/New_York"), j.optString("shuffle", "all")).also(::validateFilters)
    }.getOrDefault(Filters())
    fun saveFilters(f: Filters) {
        val j = JSONObject().put("start", f.start).put("end", f.end).put("kind", f.kind).put("minMs", f.minMs)
            .put("search", f.search).put("artist", f.artist).put("platform", f.platform).put("country", f.country)
            .put("timezone", f.timezone).put("shuffle", f.shuffle)
        check(preferences.edit().putString("filters", j.toString()).commit()) { "Could not save filter settings." }
    }
}
