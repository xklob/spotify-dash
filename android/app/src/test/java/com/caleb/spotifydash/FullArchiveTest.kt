package com.caleb.spotifydash

import com.caleb.spotifydash.core.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in local verification. Exports and its report are never checked into Git. */
class FullArchiveTest {
    @Test fun reconcilePrivateArchiveWhenExplicitlyProvided() {
        val source = System.getenv("SPOTIFY_DASH_VALIDATE_SOURCE")
        val expectedPath = System.getenv("SPOTIFY_DASH_VALIDATE_EXPECTED")
        assumeTrue("Set source and expected paths to validate a local export.", source != null && expectedPath != null)
        val started = System.currentTimeMillis()
        val builder = ImportBuilder()
        val files = File(source!!).listFiles()!!.filter { it.name.startsWith("Streaming_History") && it.extension == "json" }.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        files.forEach { file -> file.bufferedReader().use { builder.add(it,file.name) } }
        val data = builder.finish()
        val importedMs = System.currentTimeMillis() - started
        val expected = JsonParser.parseReader(File(expectedPath!!).reader()).asJsonObject
        val meta = expected.getAsJsonObject("meta")
        assertEquals(meta.get("events").asInt,data.events.size)
        assertEquals(meta.get("rawRows").asInt,data.meta.rawRows)
        assertEquals(meta.get("duplicates").asInt,data.meta.duplicates)
        assertEquals(meta.get("invalidRows").asInt,data.meta.invalidRows)
        assertEquals(meta.get("totalMs").asLong,data.meta.totalMs)
        val engine = Analytics(data)
        val result = engine.query(Filters())
        val summary = expected.getAsJsonObject("summary")
        assertEquals(summary.get("events").asInt,result.summary.events)
        assertEquals(summary.get("qualifiedPlays").asInt,result.summary.qualifiedPlays)
        assertEquals(summary.get("ms").asLong,result.summary.ms)
        assertEquals(summary.get("tracks").asInt,result.summary.tracks)
        assertEquals(summary.get("artists").asInt,result.summary.artists)
        assertEquals(summary.get("activeDays").asInt,result.summary.activeDays)
        assertEquals(summary.get("longestStreak").asInt,result.summary.longestStreak)
        assertEquals(expected.getAsJsonObject("correlation").get("r").asDouble,result.correlation.r!!,1e-10)
        assertEquals(expected.getAsJsonObject("correlation").get("n").asInt,result.correlation.n)
        for (row in expected.getAsJsonArray("yearly")) {
            val e = row.asJsonObject
            val actual = result.yearly.single { it.period == e.get("period").asString }
            assertEquals(e.get("hours").asDouble,actual.hours,1e-8)
            assertEquals(e.get("plays").asInt,actual.plays)
            assertEquals(e.get("artists").asInt,actual.artists)
            assertEquals(e.get("newArtists").asInt,actual.newArtists)
            assertEquals(e.get("topArtist").asString,actual.topArtist)
            assertEquals(e.get("partial").asBoolean,actual.partial)
            if (!e.get("overlap").isJsonNull) assertEquals(e.get("overlap").asDouble,actual.overlap!!,1e-10)
            if (!e.get("familiarShare").isJsonNull) assertEquals(e.get("familiarShare").asDouble,actual.familiarShare!!,1e-10)
        }
        assertEquals(data.meta.totalMs / 3600000.0,result.monthly.sumOf { it.hours },1e-7)
        assertEquals(data.meta.totalMs / 3600000.0,result.daily.sumOf { it.hours },1e-7)
        assertEquals(data.meta.totalMs / 3600000.0,result.hours.sumOf { it.hours },1e-7)
        assertEquals(data.meta.totalMs,result.artists.sumOf { it.ms })
        assertEquals(data.meta.totalMs,result.tracks.sumOf { it.ms })
        assertEquals(data.meta.totalMs / 3600000.0,result.composition.sumOf { it.values.sum() },1e-7)
        val filtered = engine.query(Filters(start="2024-01-01",end="2024-12-31",kind="music",minMs=30000))
        val expectedFiltered = expected.getAsJsonObject("filtered2024")
        assertEquals(expectedFiltered.get("events").asInt,filtered.summary.events)
        assertEquals(expectedFiltered.get("ms").asLong,filtered.summary.ms)
        System.getenv("SPOTIFY_DASH_VALIDATE_REPORT")?.let { path ->
            File(path).writeText(GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
                "status" to "passed", "importMs" to importedMs, "totalMs" to System.currentTimeMillis()-started,
                "meta" to data.meta, "summary" to result.summary, "filtered2024" to filtered.summary, "correlation" to result.correlation)))
        }
    }
}
