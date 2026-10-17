package com.caleb.spotifydash.core

import java.io.Writer
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

object Csv {
    private val timestamp = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
    private fun cell(value: Any?): String {
        var s = value?.toString().orEmpty()
        if (Regex("^\\s*[=+@-]").containsMatchIn(s) || s.startsWith('\t') || s.startsWith('\r') || s.startsWith('\n')) s = "'" + s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
    fun write(rows: List<HistoryRow>, timezone: String, output: Writer) {
        output.write("\uFEFFended_utc,local_date,local_time,timezone,title,artist_or_show,album,kind,media,milliseconds_played,platform,country,skipped,shuffle,offline,end_reason,spotify_uri")
        for (row in rows) {
            val e = row.event
            output.write("\r\n")
            output.write(listOf(timestamp.format(Instant.ofEpochMilli(e.ts)), row.date, row.time, timezone, e.title, e.artist, e.album,
                e.kind, e.media, e.ms, e.platform, e.country, e.skipped, e.shuffle, e.offline, e.reasonEnd, e.uri).joinToString(",") { cell(it) })
        }
    }
}
