package com.caleb.spotifydash

import com.caleb.spotifydash.core.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class AnalyticsParityTest {
    private val gson = GsonBuilder().serializeNulls().create()
    private fun resource(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)).bufferedReader().use { it.readText() }
    private fun fixture() = ImportBuilder().apply { add(StringReader(resource("parity-history.json")), "parity-history.json") }.finish()
    private fun compare(expected: JsonElement, actual: JsonElement?, path: String) {
        assertNotNull(path, actual)
        actual!!
        when {
            expected.isJsonNull -> assertTrue(path, actual.isJsonNull)
            expected.isJsonObject -> expected.asJsonObject.entrySet().forEach { (key, value) -> compare(value, actual.asJsonObject.get(key), "$path.$key") }
            expected.isJsonArray -> {
                var a = actual.asJsonArray.toList(); var e = expected.asJsonArray.toList()
                if (path.substringAfterLast('.') in listOf("artists", "tracks", "albums") && e.firstOrNull()?.isJsonObject == true) {
                    fun key(x: JsonElement) = listOf("name", "artist", "album", "uri").joinToString("|") { x.asJsonObject.get(it).asString }
                    a = a.sortedBy(::key); e = e.sortedBy(::key)
                }
                assertEquals("$path.size", e.size, a.size)
                e.indices.forEach { compare(e[it], a[it], "$path[$it]") }
            }
            expected.asJsonPrimitive.isNumber -> assertEquals(path, expected.asDouble, actual.asDouble, maxOf(1e-8, kotlin.math.abs(expected.asDouble) * 1e-10))
            else -> assertEquals(path, expected, actual)
        }
    }
    @Test fun everyNativeMetricMatchesTheWebOnSharedFictionalRecords() {
        val expected = JsonParser.parseString(resource("parity-expected.json")).asJsonObject
        val data = fixture()
        compare(expected.get("meta"), gson.toJsonTree(data.meta), "meta")
        val engine = Analytics(data)
        for (scenario in expected.getAsJsonArray("scenarios")) {
            val s = scenario.asJsonObject
            val filters = gson.fromJson(s.get("filters"), Filters::class.java)
            compare(s.get("expected"), gson.toJsonTree(engine.query(filters)), s.get("name").asString)
        }
    }
    @Test fun timestampBucketingRespectsDstAndSelectedTimezone() {
        val engine = Analytics(fixture())
        val ny = engine.history(Filters(start="2024-03-10",end="2024-03-10"), order="oldest")
        assertEquals(listOf("01:59:00", "03:01:00"), ny.rows.map { it.time })
        val utc = engine.history(Filters(start="2024-03-10",end="2024-03-10",timezone="UTC"), order="oldest")
        assertEquals(listOf("06:59:00", "07:01:00"), utc.rows.map { it.time })
        val fall = engine.history(Filters(start="2024-11-03",end="2024-11-03"), order="oldest")
        assertEquals(listOf("01:30:00", "01:30:00"), fall.rows.map { it.time })
    }
    @Test fun deduplicationSurvivesPersistenceAndKeepsDistinctSourceDetails() {
        val record = """{"ts":"2024-01-01T12:00:00Z","ms_played":60000,"master_metadata_track_name":"Example","ip_addr":"192.0.2.1","username":"fictional-only"}"""
        val first = ImportBuilder().apply { add(StringReader("[$record,$record]"), "audio.json") }.finish()
        assertEquals(1,first.events.size); assertEquals(1,first.meta.duplicates)
        val output = StringWriter().also { DatasetCodec.write(first,it) }.toString()
        assertFalse(output.contains("192.0.2.1")); assertFalse(output.contains("fictional-only")); assertFalse(output.contains("ip_addr"))
        val restored = DatasetCodec.read(StringReader(output))
        val second = ImportBuilder(restored).apply { add(StringReader("[$record,${record.replace("192.0.2.1","192.0.2.2")}]"), "audio2.json") }.finish()
        assertEquals(2,second.events.size); assertEquals(2,second.meta.duplicates)
        val video = ImportBuilder(second).apply { add(StringReader("[$record]"),"video.json") }.finish()
        assertEquals(3,video.events.size)
    }
    @Test fun malformedAndWrongPackageImportsFailWithoutMutatingExistingData() {
        val original = fixture()
        listOf("[{bad]", "[{\"endTime\":\"2024-01-01\",\"msPlayed\":30000}]", "{}").forEach { json ->
            assertThrows(Exception::class.java) { ImportBuilder(original).add(StringReader(json),"bad.json") }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            ImportBuilder(original).add(StringReader("[{bad]"), "broken-history.json")
        }
        assertTrue(error.message!!.contains("broken-history.json is not valid JSON"))
        assertTrue(error.message!!.contains("saved history has not changed"))
        assertFalse(error.message!!.contains("LENIENT"))
        assertEquals(19, original.events.size)
    }
    @Test fun paginationAndCsvExportUseTheWholeFilteredPopulation() {
        val engine = Analytics(fixture()); val f = Filters(kind="music",minMs=30000)
        val all = engine.all(f); val first = engine.history(f,0,3,"oldest"); val last = engine.history(f,999,3,"oldest")
        assertEquals(all.size,first.total); assertEquals(3,first.rows.size); assertEquals(first.pages-1,last.page)
        assertEquals(all.last(),last.rows.last())
        val csv = StringWriter().also { Csv.write(all,f.timezone,it) }.toString()
        assertEquals(all.size,csv.split("\r\n").size-1)
        val hostile = all.first().copy(event=all.first().event.copy(title=" =SUM(1,2)", artist="quoted\"name", skipped=null))
        val safe = StringWriter().also { Csv.write(listOf(hostile),"UTC",it) }.toString()
        assertTrue(safe.contains("\"' =SUM(1,2)\"")); assertTrue(safe.contains("quoted\"\"name"))
    }
    @Test fun filterValidationAndCorrelationHandleInsufficientEvidence() {
        assertThrows(IllegalArgumentException::class.java) { validateFilters(Filters(start="2024-02-30")) }
        assertThrows(IllegalArgumentException::class.java) { validateFilters(Filters(start="2024-03-01",end="2024-01-01")) }
        assertThrows(IllegalArgumentException::class.java) { validateFilters(Filters(timezone="Never/Exists")) }
        assertNull(pearson(listOf(1.0 to 2.0,2.0 to 4.0)))
        assertNull(pearson(listOf(1.0 to 2.0,1.0 to 3.0,1.0 to 4.0)))
        assertEquals(1.0,pearson(listOf(1.0 to 2.0,2.0 to 4.0,3.0 to 6.0))!!,1e-12)
    }
}
