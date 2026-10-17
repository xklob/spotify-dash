package com.caleb.spotifydash.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Display formatting shared by every screen. Labels are English, so numbers use US separators too. */
object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val intFmt = DecimalFormat("#,##0", symbols)
    private val oneFmt = DecimalFormat("#,##0.0", symbols)
    private val compactOne = DecimalFormat("0.#", symbols)
    const val HOUR_MS = 3_600_000.0

    val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val MONTHS_LONG = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun int(n: Number?): String {
        val d = n?.toDouble() ?: return "n/a"
        return if (d.isFinite()) intFmt.format(d.roundToLong()) else "n/a"
    }

    fun plural(n: Int, one: String, many: String = "${one}s") = "${int(n)} ${if (n == 1) one else many}"

    /** 1.2K, 34M, 240: compact axis numbers. */
    fun compact(v: Double): String {
        if (!v.isFinite()) return "n/a"
        val a = abs(v)
        return when {
            a >= 1e9 -> compactOne.format(v / 1e9) + "B"
            a >= 1e6 -> compactOne.format(v / 1e6) + "M"
            a >= 1e3 -> compactOne.format(v / 1e3) + "K"
            a >= 10 || v == floor(v) -> int(v)
            else -> compactOne.format(v)
        }
    }

    /** Hours without a unit: 12,345 / 345 / 34.5 / 3.4 / <0.1 */
    fun hoursValue(h: Double): String = when {
        !h.isFinite() -> "n/a"
        h == 0.0 -> "0"
        h < 0.1 -> "<0.1"
        h >= 100 -> intFmt.format(h.roundToLong())
        else -> oneFmt.format(h)
    }

    fun hours(h: Double) = "${hoursValue(h)} h"
    fun msHours(ms: Long) = hours(ms / HOUR_MS)
    fun msHours(ms: Double) = hours(ms / HOUR_MS)

    fun minutes(min: Double): String {
        if (!min.isFinite()) return "n/a"
        if (min == 0.0) return "0 min"
        if (min < 1) return "<1 min"
        if (min < 60) return "${min.roundToInt()} min"
        var h = floor(min / 60).toInt()
        var m = (min - h * 60).roundToInt()
        if (m == 60) { h += 1; m = 0 }
        return if (m > 0) "$h h $m min" else "$h h"
    }

    /** Clock-style playback length: 0:42, 3:05, 1:02:09 */
    fun clock(ms: Double?): String {
        if (ms == null || !ms.isFinite()) return "n/a"
        val total = (ms / 1000).roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    fun pct(frac: Double?, digits: Int = 1): String {
        if (frac == null || !frac.isFinite()) return "n/a"
        return String.format(Locale.US, "%.${digits}f%%", frac * 100)
    }

    fun ymd(s: String?): LocalDate? = s?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    fun date(s: String?): String {
        if (s.isNullOrEmpty()) return "n/a"
        val d = ymd(s) ?: return s
        return "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}, ${d.year}"
    }

    fun date(d: LocalDate): String = "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}, ${d.year}"

    fun dateLong(s: String): String {
        val d = ymd(s) ?: return s
        return "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${MONTHS_LONG[d.monthValue - 1]} ${d.dayOfMonth}, ${d.year}"
    }

    fun dayMonth(s: String): String {
        val d = ymd(s) ?: return s
        return "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    private val periodRegex = Regex("""^(\d{4})-(\d{2})""")

    fun month(period: String, long: Boolean = false): String {
        val m = periodRegex.find(period) ?: return period
        val idx = m.groupValues[2].toInt() - 1
        if (idx !in 0..11) return period
        return "${(if (long) MONTHS_LONG else MONTHS)[idx]} ${m.groupValues[1]}"
    }

    /** Axis tick for a YYYY-MM period: the year for January, otherwise Apr '12. */
    fun monthTick(period: String): String {
        val m = periodRegex.find(period) ?: return period
        val idx = m.groupValues[2].toInt() - 1
        if (idx !in 0..11) return period
        return if (idx == 0) m.groupValues[1] else "${MONTHS[idx]} '${m.groupValues[1].takeLast(2)}"
    }

    fun parseInstant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(iso).toInstant() }.getOrNull()
            ?: iso.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
    }

    fun zone(tz: String): ZoneId = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.of("UTC"))

    /** Calendar date of an ISO timestamp in the selected timezone. */
    fun isoToLocalDate(iso: String?, tz: String): LocalDate? {
        val instant = parseInstant(iso) ?: return ymd(iso)
        return instant.atZone(zone(tz)).toLocalDate()
    }

    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)
    private val timeZoneShort = DateTimeFormatter.ofPattern("zzz", Locale.US)

    fun dateTime(iso: String?, tz: String): String {
        val instant = parseInstant(iso) ?: return if (iso.isNullOrBlank()) "n/a" else iso
        val zoned = instant.atZone(zone(tz))
        return "${dateTimeFormat.format(zoned)} ${timeZoneShort.format(zoned)}"
    }

    /** Short zone name in effect at an instant, such as EDT or GMT+2. */
    fun zoneAbbrev(epochMs: Long, tz: String): String =
        runCatching { timeZoneShort.format(Instant.ofEpochMilli(epochMs).atZone(zone(tz))) }.getOrDefault(tz)

    fun zoneLabel(tz: String) = tz.replace('_', ' ')

    fun zoneOffset(tz: String, at: Instant = Instant.now()): String {
        val offset = runCatching { ZoneId.of(tz).rules.getOffset(at) }.getOrNull() ?: return ""
        val id = offset.id
        return if (id == "Z") "UTC" else "UTC$id"
    }

    fun span(days: Int): String {
        if (days >= 365) {
            val y = days / 365.25
            return if (y >= 10) "${y.roundToInt()} years" else String.format(Locale.US, "%.1f years", y)
        }
        return "${(days / 30.44).roundToInt()} months"
    }

    private val spotifyUri = Regex("""^spotify:(track|episode|show|album|artist|playlist|audiobook|chapter):([A-Za-z0-9]+)$""")

    fun spotifyUrl(uri: String?): String? {
        val m = spotifyUri.find(uri ?: return null) ?: return null
        return "https://open.spotify.com/${m.groupValues[1]}/${m.groupValues[2]}"
    }

    private val reasons = mapOf(
        "trackdone" to "Played to the end",
        "fwdbtn" to "Forward button",
        "backbtn" to "Back button",
        "endplay" to "Playback ended",
        "clickrow" to "Another item chosen",
        "playbtn" to "Play button",
        "remote" to "Remote device",
        "logout" to "Logged out",
        "trackerror" to "Playback error",
        "unexpected-exit" to "App exited unexpectedly",
        "unexpected-exit-while-paused" to "App exited while paused",
        "appload" to "App loaded",
        "popup" to "Popup",
        "uriopen" to "Link opened",
        "unknown" to "Not recorded",
        "" to "Not recorded",
    )

    fun reason(code: String): String = reasons[code] ?: code.replace(Regex("[-_]+"), " ")

    fun country(code: String): String {
        if (code.isBlank() || code == "Unknown") return "Not recorded"
        if (!Regex("^[A-Z]{2}$").matches(code)) return code
        val name = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.US) }.getOrNull()
        return if (name.isNullOrBlank() || name == code) code else name
    }

    private val kinds = mapOf("music" to "Music", "podcast" to "Podcast", "audiobook" to "Audiobook", "unknown" to "Unidentified")
    fun kind(kind: String): String = kinds[kind] ?: kind.replaceFirstChar { it.uppercase() }

    fun hourName(h: Int): String = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    fun hourTick(h: Int): String = "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "a" else "p"}"
    fun hourSpan(h: Int): String = "${hourName(h)} to ${hourName((h + 1) % 24)}"

    fun flag(v: Boolean?, yes: String = "Yes", no: String = "No"): String = when (v) {
        null -> "Not recorded"
        true -> yes
        false -> no
    }

    fun weekdayName(dayOfWeek: java.time.DayOfWeek): String = dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.US)
}
