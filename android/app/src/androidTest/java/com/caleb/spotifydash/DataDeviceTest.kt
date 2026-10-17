package com.caleb.spotifydash

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caleb.spotifydash.core.*
import com.caleb.spotifydash.data.DatasetStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.StringReader

@RunWith(AndroidJUnit4::class)
class DataDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fictionalAssetAndNativeStorageRoundTrip() {
        // Use a disposable cache file, preserving any imported app data.
        val demo = DatasetStore(context).demo()
        assertEquals("demo",demo.meta.source)
        assertEquals(11583,demo.events.size)
        val file = File(context.cacheDir,"test-roundtrip.json")
        try {
            file.bufferedWriter().use { DatasetCodec.write(demo,it) }
            val restored = file.bufferedReader().use(DatasetCodec::read)
            assertEquals(demo.meta,restored.meta)
            assertEquals(demo.events,restored.events)
            val result = Analytics(restored).query(Filters())
            assertEquals(demo.meta.totalMs,result.summary.ms)
            assertTrue(result.monthly.size >= 36)
        } finally { file.delete() }
    }

    @Test fun importedArchiveCacheAndFullCsvWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        val expectedEvents = args.getString("expectedEvents")?.toIntOrNull()
        assumeTrue("Opt-in check after importing an archive through Android's file picker.", expectedEvents != null)
        val data = requireNotNull(DatasetStore(context).load())
        assertEquals(expectedEvents,data.events.size)
        val engine = Analytics(data)
        val result = engine.query(Filters())
        assertEquals(args.getString("expectedMs")!!.toLong(),result.summary.ms)
        assertEquals(args.getString("expectedTracks")!!.toInt(),result.summary.tracks)
        assertEquals(args.getString("expectedArtists")!!.toInt(),result.summary.artists)
        val filters = Filters(start="2024-01-01",end="2024-12-31",kind="music",minMs=30000)
        val all = engine.all(filters)
        assertEquals(args.getString("expectedFiltered")!!.toInt(),all.size)
        val target = File(context.getExternalFilesDir(null),"qa-filtered-export.csv")
        target.bufferedWriter().use { Csv.write(all,filters.timezone,it) }
        assertTrue(target.length() > 0)
        val before = data.events.size
        assertThrows(Exception::class.java) { ImportBuilder(data).add(StringReader("[{bad]"),"bad.json") }
        assertEquals(before,DatasetStore(context).load()!!.events.size)
    }

    @Test fun nativeFullArchiveImportWhenExplicitlyStaged() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in emulator import benchmark.",args.getString("importStaged") == "true")
        val directory = File(context.filesDir,"qa-source")
        val sources = requireNotNull(directory.listFiles()) { "Stage the opt-in source files in the debug app's private qa-source directory." }
            .filter { it.extension == "json" }.sortedBy { it.name }
        assertTrue(sources.isNotEmpty())
        val started = System.currentTimeMillis()
        val data = ImportBuilder().apply {
            sources.forEach { file -> file.bufferedReader().use { add(it,file.name) } }
        }.finish()
        assertEquals(args.getString("expectedEvents")!!.toInt(),data.events.size)
        assertEquals(args.getString("expectedMs")!!.toLong(),data.meta.totalMs)
        val analytics = Analytics(data)
        val result = analytics.query(Filters())
        assertEquals(args.getString("expectedTracks")!!.toInt(),result.summary.tracks)
        assertEquals(args.getString("expectedArtists")!!.toInt(),result.summary.artists)
        DatasetStore(context).save(data)
        val report = File(context.getExternalFilesDir(null),"qa-import-report.txt")
        report.writeText("events=${data.events.size}\nms=${data.meta.totalMs}\nelapsedMs=${System.currentTimeMillis()-started}\nmaxHeapBytes=${Runtime.getRuntime().maxMemory()}\nusedHeapBytes=${Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()}\n")
    }
}
