package com.caleb.spotifydash.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.MalformedJsonException
import java.io.Reader
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

private val json = GsonBuilder().disableHtmlEscaping().create()
private val timestampPattern = Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?(?:Z|[+-]\\d{2}:\\d{2})?$")
private val uriPattern = Regex("^spotify:(track|episode|show|audiobook|chapter):[a-zA-Z0-9]+$")
internal fun JsonObject.text(name: String): String = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim().orEmpty()
internal fun JsonObject.flag(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

fun platformFamily(value: String): String {
    val s = value.trim().lowercase(Locale.ROOT)
    return when {
        "android" in s -> "Android"
        "windows" in s -> "Windows"
        Regex("ios|iphone|ipad").containsMatchIn(s) -> "iOS"
        Regex("osx|macintosh|macos|mac os").containsMatchIn(s) -> "macOS"
        "cast" in s -> "Chromecast"
        "sonos" in s -> "Sonos"
        Regex("web_player|web player|webplayer|browser").containsMatchIn(s) -> "Web player"
        "linux" in s -> "Linux"
        "playstation" in s -> "PlayStation"
        "xbox" in s -> "Xbox"
        Regex("amazon|alexa|echo").containsMatchIn(s) -> "Alexa"
        s.isEmpty() -> "Unknown"
        else -> "Other"
    }
}

// Canonical source values are transient. Only their digest survives in storage.
private fun canonical(value: JsonElement): String = when {
    value.isJsonNull -> "null"
    value.isJsonArray -> value.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
    value.isJsonObject -> value.asJsonObject.entrySet().joinToString(",", "{", "}") { json.toJson(it.key) + ":" + canonical(it.value) }
    value.asJsonPrimitive.isNumber -> value.asBigDecimal.stripTrailingZeros().toPlainString()
    else -> json.toJson(value)
}

class ImportBuilder(existing: Dataset? = null) {
    private val events = existing?.events?.toMutableList() ?: mutableListOf()
    private val ids = events.mapTo(HashSet()) { it.id }
    private val files = existing?.meta?.files?.toMutableSet() ?: linkedSetOf()
    private val strings = HashMap<String, String>()
    private var rawRows = existing?.meta?.rawRows ?: 0
    private var duplicates = existing?.meta?.duplicates ?: 0
    private var invalidRows = existing?.meta?.invalidRows ?: 0
    private val digest = MessageDigest.getInstance("SHA-256")
    private fun pooled(s: String) = strings.getOrPut(s) { s }

    fun add(input: Reader, filename: String, onProgress: (Int) -> Unit = {}) {
        val basename = filename.split('/', '\\').last().ifBlank { "history.json" }
        try {
            read(input, basename, onProgress)
        } catch (e: JsonParseException) {
            throw invalidJson(basename, e)
        } catch (e: MalformedJsonException) {
            throw invalidJson(basename, e)
        }
    }

    private fun invalidJson(filename: String, cause: Exception) = IllegalArgumentException(
        "$filename is not valid JSON. Extract the Spotify download again and choose its Extended Streaming History JSON files. Your saved history has not changed.", cause,
    )

    private fun read(input: Reader, basename: String, onProgress: (Int) -> Unit) {
        val reader = JsonReader(input).apply { strictness = Strictness.STRICT }
        require(reader.peek() == JsonToken.BEGIN_ARRAY) { "$basename: expected an array of Extended Streaming History records." }
        reader.beginArray()
        var count = 0
        var hasSchema = false
        while (reader.hasNext()) {
            val raw = JsonParser.parseReader(reader)
            if (raw.isJsonObject && raw.asJsonObject.has("ts") && raw.asJsonObject.has("ms_played")) hasSchema = true
            rawRows++
            count++
            val event = normalize(raw, basename)
            if (event == null) invalidRows++
            else if (!ids.add(event.id)) duplicates++
            else events.add(event)
            if (count % 4096 == 0) onProgress(count)
        }
        reader.endArray()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "$basename: unexpected content after the JSON array." }
        require(count == 0 || hasSchema) { "$basename: choose the Extended Streaming History JSON files, not the basic Account data package." }
        files.add(basename)
        onProgress(count)
    }

    private fun normalize(raw: JsonElement, filename: String): HistoryEvent? {
        if (!raw.isJsonObject) return null
        val r = raw.asJsonObject
        val stamp = r.text("ts")
        if (!timestampPattern.matches(stamp)) return null
        val iso = stamp.replace(' ', 'T').let { if (it.endsWith('Z') || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(it)) it else it + "Z" }
        val ts = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull() ?: return null
        if (ts < 1136073600000L || ts > System.currentTimeMillis() + 86400000L) return null
        val played = r.get("ms_played")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: return null
        val ms = runCatching { played.asBigDecimal.longValueExact() }.getOrNull() ?: return null
        if (ms < 0 || ms > 9007199254740991L) return null
        var kind = "unknown"
        var title = ""
        var artist = ""
        var album = ""
        var uri = ""
        when {
            r.text("master_metadata_track_name").isNotEmpty() || r.text("spotify_track_uri").isNotEmpty() -> {
                kind = "music"; title = r.text("master_metadata_track_name"); artist = r.text("master_metadata_album_artist_name")
                album = r.text("master_metadata_album_album_name"); uri = r.text("spotify_track_uri")
            }
            r.text("episode_name").isNotEmpty() || r.text("spotify_episode_uri").isNotEmpty() -> {
                kind = "podcast"; title = r.text("episode_name"); artist = r.text("episode_show_name"); uri = r.text("spotify_episode_uri")
            }
            r.text("audiobook_title").isNotEmpty() || r.text("audiobook_uri").isNotEmpty() -> {
                kind = "audiobook"; title = r.text("audiobook_chapter_title").ifEmpty { r.text("audiobook_title") }
                artist = r.text("audiobook_title"); album = artist
                uri = r.text("audiobook_chapter_uri").ifEmpty { r.text("audiobook_uri") }
            }
        }
        val media = if (filename.contains("video", true)) "video" else "audio"
        val ordered = JsonArray().apply {
            add(media)
            add(JsonArray().apply { r.keySet().sorted().forEach { key -> add(JsonArray().apply { add(key); add(r.get(key)) }) } })
        }
        val hex = "0123456789abcdef"
        val id = buildString(64) {
            for (byte in digest.digest(canonical(ordered).toByteArray(Charsets.UTF_8))) {
                val value = byte.toInt() and 255
                append(hex[value ushr 4]); append(hex[value and 15])
            }
        }
        val country = r.text("conn_country").takeIf { it.matches(Regex("[A-Za-z]{2}")) }?.uppercase(Locale.ROOT) ?: "Unknown"
        return HistoryEvent(id, ts, ms, kind, media, pooled(title.ifEmpty { "Unknown title" }), pooled(artist.ifEmpty { "Unknown artist" }),
            pooled(album), pooled(uri.takeIf(uriPattern::matches).orEmpty()), pooled(platformFamily(r.text("platform"))), pooled(country),
            r.flag("shuffle"), r.flag("skipped"), r.flag("offline"), r.flag("incognito_mode"),
            pooled(r.text("reason_start").ifEmpty { "unknown" }), pooled(r.text("reason_end").ifEmpty { "unknown" }))
    }

    fun finish(source: String = "import"): Dataset {
        require(events.isNotEmpty()) { "No valid listening records were found. Select your Extended Streaming History JSON files." }
        events.sortWith(compareBy<HistoryEvent> { it.ts }.thenBy { it.id })
        return Dataset(DatasetMeta(source, files.toList(), Instant.now().toString(), rawRows, duplicates, invalidRows, events.size,
            Instant.ofEpochMilli(events.first().ts).toString(), Instant.ofEpochMilli(events.last().ts).toString(),
            events.sumOf { it.ms }, buildList {
                if (invalidRows > 0) add("$invalidRows invalid records were excluded.")
                if (duplicates > 0) add("$duplicates duplicate source records were removed.")
            }), events.toList())
    }
}
