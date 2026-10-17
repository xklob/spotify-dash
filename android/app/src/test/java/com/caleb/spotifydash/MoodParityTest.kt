package com.caleb.spotifydash

import com.caleb.spotifydash.core.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class MoodParityTest {
    private val gson = GsonBuilder().serializeNulls().create()
    private fun resource(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)).bufferedReader().use { it.readText() }
    private fun compare(expected: JsonElement, actual: JsonElement?, path: String) {
        assertNotNull(path, actual); actual!!
        when {
            expected.isJsonNull -> assertTrue(path, actual.isJsonNull)
            expected.isJsonObject -> expected.asJsonObject.entrySet().forEach { (k, v) -> compare(v, actual.asJsonObject.get(k), "$path.$k") }
            expected.isJsonArray -> { assertEquals("$path.size", expected.asJsonArray.size(), actual.asJsonArray.size()); expected.asJsonArray.forEachIndexed { i, v -> compare(v, actual.asJsonArray[i], "$path[$i]") } }
            expected.asJsonPrimitive.isNumber -> assertEquals(path, expected.asDouble, actual.asDouble, maxOf(1e-8, kotlin.math.abs(expected.asDouble) * 1e-10))
            else -> assertEquals(path, expected, actual)
        }
    }
    @Test fun allMoodChartsMatchWebWithMissingScoresWeightsSmoothingAndDst() {
        val data = ImportBuilder().apply { add(StringReader(resource("mood-history.json")), "mood-history.json") }.finish()
        val metadata = MetadataCodec.read(StringReader(resource("mood-metadata.json"))); val engine = Analytics(data)
        JsonParser.parseString(resource("mood-expected.json")).asJsonArray.forEach { raw ->
            val s = raw.asJsonObject
            compare(s.get("expected"), gson.toJsonTree(engine.mood(gson.fromJson(s.get("filters"), Filters::class.java), metadata, gson.fromJson(s.get("options"), MoodOptions::class.java))), s.get("name").asString)
        }
    }
    @Test fun portableMetadataRoundTripsAndRejectsDuplicatesInvalidScoresAndCrossProviderMerge() {
        val json = resource("mood-metadata.json"); val m = MetadataCodec.read(StringReader(json))
        val output = StringWriter().also { MetadataCodec.write(m, it) }.toString()
        assertEquals(m, MetadataCodec.read(StringReader(output)))
        assertThrows(Exception::class.java) { MetadataCodec.read(StringReader(json.replace("\"energy\": 0", "\"energy\": 2"))) }
        val duplicate = JsonParser.parseString(json).asJsonObject; duplicate.getAsJsonArray("tracks").add(duplicate.getAsJsonArray("tracks")[0])
        assertThrows(Exception::class.java) { MetadataCodec.read(StringReader(duplicate.toString())) }
        assertThrows(Exception::class.java) { MetadataCodec.merge(m, m.copy(provider = "demo")) }
        val old = m.copy(tracks = m.tracks.map { it.copy(fetchedAt = "2020-01-01T00:00:00Z") })
        assertEquals(m.tracks, MetadataCodec.merge(m, old).tracks)
    }
}
