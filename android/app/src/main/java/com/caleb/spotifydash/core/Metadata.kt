package com.caleb.spotifydash.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.io.Writer
import java.time.Instant

val FEATURE_KEYS = listOf("energy", "valence", "danceability", "acousticness", "instrumentalness", "speechiness", "liveness", "tempo", "loudness")
data class AudioFeatures(
    val energy: Double? = null, val valence: Double? = null, val danceability: Double? = null,
    val acousticness: Double? = null, val instrumentalness: Double? = null, val speechiness: Double? = null,
    val liveness: Double? = null, val tempo: Double? = null, val loudness: Double? = null,
) {
    operator fun get(key: String): Double? = when (key) {
        "energy" -> energy; "valence" -> valence; "danceability" -> danceability; "acousticness" -> acousticness
        "instrumentalness" -> instrumentalness; "speechiness" -> speechiness; "liveness" -> liveness; "tempo" -> tempo; "loudness" -> loudness
        else -> null
    }
    companion object { fun from(values: List<Double?>) = AudioFeatures(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]) }
}
data class TrackMetadata(
    val uri: String, val status: String, val fetchedAt: String, val providerId: String,
    val title: String, val artists: List<String>, val durationMs: Long?, val isrc: String,
    val features: AudioFeatures, val key: Int?, val mode: Int?,
) { val paired: Boolean get() = status == "found" && features.energy != null && features.valence != null }
data class Metadata(
    val format: String = "spotify-dash-metadata", val version: Int = 1, val provider: String = "reccobeats",
    val modelVersion: String? = null, val generatedAt: String = Instant.now().toString(), val tracks: List<TrackMetadata> = emptyList(),
)
fun moodKey(e: HistoryEvent, demo: Boolean): String = if (demo) "demo:" + compactJson(listOf(e.artist, e.title, e.album)) else e.uri.ifEmpty { "unmatched:" + compactJson(listOf(e.kind, e.artist, e.title, e.album)) }
private val metadataGson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
private fun compactJson(value: Any) = metadataGson.toJson(value)
internal val trackUri = Regex("spotify:track:[A-Za-z0-9]{22}")

object MetadataCodec {
    // Explicit field names survive release shrinking/obfuscation and keep the portable schema stable.
    fun write(metadata: Metadata, output: Writer) {
        val w = JsonWriter(output).apply { serializeNulls = true }
        w.beginObject().name("format").value(metadata.format).name("version").value(metadata.version)
            .name("provider").value(metadata.provider).name("modelVersion").value(metadata.modelVersion).name("generatedAt").value(metadata.generatedAt)
        w.name("tracks").beginArray()
        metadata.tracks.forEach { t ->
            w.beginObject().name("uri").value(t.uri).name("status").value(t.status).name("fetchedAt").value(t.fetchedAt)
                .name("providerId").value(t.providerId).name("title").value(t.title).name("durationMs").value(t.durationMs).name("isrc").value(t.isrc)
                .name("key").value(t.key).name("mode").value(t.mode).name("artists").beginArray()
            t.artists.forEach { w.value(it) }; w.endArray().name("features").beginObject()
            FEATURE_KEYS.forEach { k -> w.name(k).value(t.features[k]) }; w.endObject().endObject()
        }
        w.endArray().endObject().flush()
    }
    fun read(input: Reader): Metadata {
        val r = JsonReader(input).apply { strictness = Strictness.STRICT }
        var format = ""; var version = 0; var provider = ""; var model: String? = null; var generated = ""
        val tracks = ArrayList<TrackMetadata>(); val seen = HashSet<String>()
        fun date(value: String): String { require(runCatching { Instant.parse(value) }.isSuccess) { "Metadata has an invalid retrieval date." }; return value }
        fun optionalInteger(j: JsonObject, key: String, min: Long, max: Long): Long? {
            val value = j.get(key)?.takeUnless { it.isJsonNull } ?: return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Metadata has an invalid numeric field." }
            val d = value.asDouble
            require(d.isFinite() && d >= min && d <= max && d == d.toLong().toDouble()) { "Metadata has an invalid numeric field." }
            return d.toLong()
        }
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "format" -> format = r.nextString(); "version" -> version = r.nextInt(); "provider" -> provider = r.nextString()
            "modelVersion" -> if (r.peek() == JsonToken.NULL) r.nextNull() else model = r.nextString().take(500)
            "generatedAt" -> generated = date(r.nextString())
            "tracks" -> {
                r.beginArray()
                while (r.hasNext()) {
                    require(tracks.size < 100000) { "Metadata files support at most 100,000 tracks." }
                    val t = JsonParser.parseReader(r).asJsonObject
                    val uri = t.text("uri").take(1600); val status = t.text("status")
                    require(seen.add(uri) && status in listOf("found", "missing")) { "Metadata has invalid or duplicate track identifiers." }
                    val f = t.getAsJsonObject("features") ?: JsonObject()
                    val values = FEATURE_KEYS.map { k ->
                        val value = f.get(k)?.takeUnless { it.isJsonNull }
                        if (value == null) null else {
                            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Metadata has an invalid $k score." }
                            val v = value.asDouble; val min = if (k == "loudness") -100.0 else 0.0
                            val max = when (k) { "tempo" -> 400.0; "loudness" -> 10.0; else -> 1.0 }
                            require(v.isFinite() && v in min..max) { "Metadata has an invalid $k score." }; v
                        }
                    }
                    require(status != "missing" || values.all { it == null }) { "Missing tracks cannot contain scores." }
                    tracks.add(TrackMetadata(uri, status, date(t.text("fetchedAt")), t.text("providerId").take(500), t.text("title").take(500),
                        t.getAsJsonArray("artists")?.take(100)?.map { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString.take(500) else "" } ?: emptyList(),
                        optionalInteger(t, "durationMs", 0, 86400000), t.text("isrc").take(32), AudioFeatures.from(values),
                        optionalInteger(t, "key", -1, 11)?.toInt(), optionalInteger(t, "mode", 0, 1)?.toInt()))
                }
                r.endArray()
            }
            else -> r.skipValue()
        }
        r.endObject()
        require(r.peek() == JsonToken.END_DOCUMENT && format == "spotify-dash-metadata" && version == 1 && provider in listOf("reccobeats", "demo") && generated.isNotEmpty()) { "Choose a Spotify Dash metadata JSON file (format version 1)." }
        require(tracks.all { if (provider == "demo") it.uri.startsWith("demo:") else trackUri.matches(it.uri) }) { "Metadata has an invalid track identifier." }
        return Metadata(provider = provider, modelVersion = model, generatedAt = generated, tracks = tracks)
    }
    fun merge(previous: Metadata, next: Metadata): Metadata {
        require(previous.provider == next.provider && previous.modelVersion == next.modelVersion) { "Metadata from different providers or model versions cannot be combined." }
        val records = previous.tracks.associateByTo(LinkedHashMap()) { it.uri }
        next.tracks.forEach { t -> if (records[t.uri]?.let { Instant.parse(t.fetchedAt) >= Instant.parse(it.fetchedAt) } != false) records[t.uri] = t }
        return next.copy(tracks = records.values.toList())
    }
    fun demo(data: Dataset): Metadata {
        val seen = HashSet<String>(); val tracks = ArrayList<TrackMetadata>(); val date = "2026-01-01T00:00:00.000Z"
        data.events.forEach { e ->
            val uri = moodKey(e, true)
            if (e.kind != "music" || !seen.add(uri)) return@forEach
            var seed = 7; uri.forEach { seed = seed * 31 + it.code }
            fun random(): Double { seed = seed * 1664525 + 1013904223; return seed.toUInt().toLong() / 4294967296.0 }
            val values = FEATURE_KEYS.map { when (it) { "tempo" -> 70 + random() * 100; "loudness" -> -15 + random() * 10; else -> random() } }
            if (seen.size % 7 != 0) tracks.add(TrackMetadata(uri, "found", date, "", e.title, listOf(e.artist), 240000, "", AudioFeatures.from(values), null, null))
        }
        return Metadata(provider = "demo", modelVersion = "fictional-v1", generatedAt = date, tracks = tracks)
    }
}
