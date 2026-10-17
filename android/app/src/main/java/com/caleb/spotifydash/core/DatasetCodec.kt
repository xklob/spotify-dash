package com.caleb.spotifydash.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.io.Writer

/** Explicit, versioned local format. No source IPs, usernames, or user agents. */
object DatasetCodec {
    fun write(data: Dataset, output: Writer) {
        val w = JsonWriter(output)
        w.beginObject().name("version").value(1)
        val m = data.meta
        w.name("meta").beginObject().name("source").value(m.source).name("importedAt").value(m.importedAt)
            .name("rawRows").value(m.rawRows).name("duplicates").value(m.duplicates).name("invalidRows").value(m.invalidRows)
            .name("events").value(m.events).name("first").value(m.first).name("last").value(m.last).name("totalMs").value(m.totalMs)
        w.name("files").beginArray(); m.files.forEach { w.value(it) }; w.endArray()
        w.name("warnings").beginArray(); m.warnings.forEach { w.value(it) }; w.endArray().endObject()
        w.name("events").beginArray()
        data.events.forEach { e ->
            w.beginObject().name("id").value(e.id).name("ts").value(e.ts).name("ms").value(e.ms).name("kind").value(e.kind)
                .name("media").value(e.media).name("title").value(e.title).name("artist").value(e.artist).name("album").value(e.album)
                .name("uri").value(e.uri).name("platform").value(e.platform).name("country").value(e.country)
                .name("shuffle").value(e.shuffle).name("skipped").value(e.skipped).name("offline").value(e.offline)
                .name("incognito").value(e.incognito).name("reasonStart").value(e.reasonStart).name("reasonEnd").value(e.reasonEnd).endObject()
        }
        w.endArray().endObject().flush()
    }

    fun read(input: Reader): Dataset {
        val r = JsonReader(input).apply { strictness = Strictness.STRICT }
        var version = 0; var meta: DatasetMeta? = null; val events = mutableListOf<HistoryEvent>()
        val strings = HashMap<String, String>()
        fun text(j: JsonObject, name: String) = j.text(name).let { strings.getOrPut(it) { it } }
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "version" -> version = r.nextInt()
            "meta" -> {
                val m = JsonParser.parseReader(r).asJsonObject
                meta = DatasetMeta(m.text("source"), m.getAsJsonArray("files").map { it.asString }, m.text("importedAt"),
                    m.get("rawRows").asInt, m.get("duplicates").asInt, m.get("invalidRows").asInt, m.get("events").asInt,
                    m.text("first"), m.text("last"), m.get("totalMs").asLong, m.getAsJsonArray("warnings").map { it.asString })
            }
            "events" -> {
                r.beginArray()
                while (r.hasNext()) {
                    val e = JsonParser.parseReader(r).asJsonObject
                    events.add(HistoryEvent(e.text("id"), e.get("ts").asLong, e.get("ms").asLong, text(e, "kind"), text(e, "media"),
                        text(e, "title"), text(e, "artist"), text(e, "album"), text(e, "uri"), text(e, "platform"), text(e, "country"),
                        e.flag("shuffle"), e.flag("skipped"), e.flag("offline"), e.flag("incognito"), text(e, "reasonStart"), text(e, "reasonEnd")))
                }
                r.endArray()
            }
            else -> r.skipValue()
        }
        r.endObject()
        require(r.peek() == JsonToken.END_DOCUMENT && version == 1 && meta != null && events.isNotEmpty()) { "The saved history is invalid. Clear it and import the original JSON files again." }
        require(events.size == meta.events && events.all { it.ms >= 0 && it.id.isNotEmpty() }) { "The saved history is incomplete. Import the original JSON files again." }
        events.sortWith(compareBy<HistoryEvent> { it.ts }.thenBy { it.id })
        return Dataset(meta, events)
    }
}
