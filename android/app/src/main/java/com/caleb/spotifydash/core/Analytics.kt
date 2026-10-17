package com.caleb.spotifydash.core

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

private const val HOUR = 3_600_000.0
private fun ratio(a: Number, b: Number): Double? = if (b.toDouble() == 0.0) null else a.toDouble() / b.toDouble()
fun searchText(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
private fun itemKey(e: HistoryEvent): String = e.uri.ifEmpty { listOf(e.kind, e.artist, e.title, e.album).joinToString("") { "${it.length}:$it" } }

fun pearson(points: List<Pair<Double, Double>>): Double? {
    if (points.size < 3) return null
    val mx = points.sumOf { it.first } / points.size
    val my = points.sumOf { it.second } / points.size
    var xx = 0.0; var yy = 0.0; var xy = 0.0
    points.forEach { (x, y) -> xx += (x - mx) * (x - mx); yy += (y - my) * (y - my); xy += (x - mx) * (y - my) }
    return if (xx == 0.0 || yy == 0.0) null else (xy / sqrt(xx * yy)).coerceIn(-1.0, 1.0)
}

private data class Prepared(val e: HistoryEvent, val date: String, val time: String, val month: String, val year: String,
    val hour: Int, val day: Int, val item: String, val search: String)
private class Bucket(private val detailed: Boolean = false, private val mix: Boolean = false) {
    var ms = 0L; var plays = 0; var skipped = 0; var known = 0; var familiarMs = 0L
    val artists = HashSet<String>(); val items = HashSet<String>(); val newArtists = HashSet<String>()
    val artistMs = LinkedHashMap<String, Long>()
    fun add(p: Prepared, isNew: Boolean, familiar: Boolean) {
        val e = p.e
        ms += e.ms; plays++
        if (detailed) { artists.add(e.artist); items.add(p.item); if (isNew) newArtists.add(e.artist) }
        if (e.skipped != null) { known++; if (e.skipped) skipped++ }
        if (familiar) familiarMs += e.ms
        if (mix) artistMs[e.artist] = (artistMs[e.artist] ?: 0) + e.ms
    }
    fun group(name: String) = GroupPoint(name, ms / HOUR, plays, ratio(skipped, known), known)
    fun period(name: String) = PeriodPoint(name, ms / HOUR, plays, artists.size, newArtists.size, ratio(skipped, known), ratio(familiarMs, ms))
}
private class RankAccumulator(val key: String, val name: String, val first: Prepared) {
    var plays = 0; var ms = 0L; var skipped = 0; var known = 0; var last = first.date
    fun add(p: Prepared) { plays++; ms += p.e.ms; last = p.date; if (p.e.skipped != null) { known++; if (p.e.skipped) skipped++ } }
    fun result() = Ranking(key, name, first.e.artist, first.e.album, first.e.uri, plays, ms, ratio(skipped, known), known, first.date, last)
}
private class ObsessionAccumulator(val first: Prepared) { var plays = 0; var ms = 0L }

class Analytics(val dataset: Dataset) {
    private var zone = ""
    private var prepared = emptyList<Prepared>()
    private var firstArtist = HashMap<String, String>()
    private var firstItem = HashMap<String, String>()
    private fun prepare(timezone: String) {
        if (zone == timezone) return
        val zoneId = ZoneId.of(timezone)
        val dates = HashMap<LocalDate, String>(); val times = HashMap<Int, String>()
        val months = HashMap<String, String>(); val years = HashMap<String, String>()
        val searches = HashMap<String, String>(); val itemKeys = HashMap<String, String>()
        val newFirstArtist = HashMap<String, String>(); val newFirstItem = HashMap<String, String>()
        prepared = dataset.events.map { e ->
            val local = Instant.ofEpochMilli(e.ts).atZone(zoneId)
            val date = dates.getOrPut(local.toLocalDate()) { local.toLocalDate().toString() }
            val time = times.getOrPut(local.toLocalTime().toSecondOfDay()) { local.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)) }
            val key = itemKey(e); val item = itemKeys.getOrPut(key) { key }
            val searchKey = listOf(e.title, e.artist, e.album).joinToString("\n")
            val search = searches.getOrPut(searchKey) { searchText(searchKey) }
            newFirstArtist.putIfAbsent(e.artist, date); newFirstItem.putIfAbsent(item, date)
            val month = date.take(7); val year = date.take(4)
            Prepared(e, date, time, months.getOrPut(month) { month }, years.getOrPut(year) { year }, local.hour,
                local.dayOfWeek.value - 1, item, search)
        }
        firstArtist = newFirstArtist; firstItem = newFirstItem; zone = timezone
    }

    private fun select(f: Filters): List<Prepared> {
        validateFilters(f)
        prepare(f.timezone)
        val query = searchText(f.search.trim())
        return prepared.filter { p ->
            val e = p.e
            (f.start.isEmpty() || p.date >= f.start) && (f.end.isEmpty() || p.date <= f.end) &&
                (f.kind == "all" || e.kind == f.kind) && e.ms >= f.minMs &&
                (query.isEmpty() || p.search.contains(query)) && (f.artist.isEmpty() || e.artist == f.artist) &&
                (f.platform.isEmpty() || e.platform == f.platform) && (f.country.isEmpty() || e.country == f.country) &&
                (f.shuffle == "all" || e.shuffle == (f.shuffle == "yes"))
        }
    }

    @Synchronized fun history(f: Filters, page: Int = 0, pageSize: Int = 50, order: String = "newest"): HistoryPage {
        val rows = select(f).let { if (order == "newest") it.asReversed() else it }
        val size = pageSize.coerceIn(1, 500)
        val pages = (rows.size + size - 1) / size
        val current = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
        return HistoryPage(rows.drop(current * size).take(size).map { HistoryRow(it.e, it.date, it.time) }, rows.size, current, pages)
    }

    @Synchronized fun all(f: Filters): List<HistoryRow> = select(f).map { HistoryRow(it.e, it.date, it.time) }

    @Synchronized fun query(f: Filters): QueryResult {
        val selected = select(f)
        val dataStart = prepared.firstOrNull()?.date.orEmpty(); val dataEnd = prepared.lastOrNull()?.date.orEmpty()
        val start = f.start.ifEmpty { dataStart }; val end = f.end.ifEmpty { dataEnd }
        require(start.isNotEmpty() && end.isNotEmpty()) { "Import some listening history first." }
        val from = LocalDate.parse(start); val to = LocalDate.parse(end)
        require(to.toEpochDay() - from.toEpochDay() <= 36500) { "Choose a date range shorter than 100 years." }
        val months = sortedMapOf<String, Bucket>(); val years = sortedMapOf<String, Bucket>(); val days = HashMap<String, Bucket>()
        val platforms = LinkedHashMap<String, Bucket>(); val countries = LinkedHashMap<String, Bucket>(); val kinds = LinkedHashMap<String, Bucket>()
        val reasons = LinkedHashMap<String, Bucket>(); val shuffle = LinkedHashMap<String, Bucket>()
        val hourBuckets = List(24) { Bucket() }; val weekdayBuckets = List(7) { Bucket() }
        val heatMs = LongArray(168); val heatPlays = IntArray(168)
        val artists = LinkedHashMap<String, RankAccumulator>(); val tracks = LinkedHashMap<String, RankAccumulator>(); val albums = LinkedHashMap<String, RankAccumulator>()
        val total = Bucket(detailed = true); var qualified = 0; var shuffleYes = 0; var shuffleKnown = 0
        val obsessions = LinkedHashMap<Pair<String, String>, ObsessionAccumulator>()
        val previous = HashMap<String, Prepared>(); val gaps = mutableListOf<Rediscovery>()
        fun rank(map: MutableMap<String, RankAccumulator>, key: String, name: String, p: Prepared) {
            map.getOrPut(key) { RankAccumulator(key, name, p) }.add(p)
        }
        for (p in selected) {
            val e = p.e; val first = firstArtist.getValue(e.artist); val familiar = firstItem.getValue(p.item) < p.date
            val discovered = first >= start && first <= end
            total.add(p, discovered, familiar)
            months.getOrPut(p.month) { Bucket(true, true) }.add(p, discovered && first.take(7) == p.month, familiar)
            years.getOrPut(p.year) { Bucket(true, true) }.add(p, discovered && first.take(4) == p.year, familiar)
            days.getOrPut(p.date) { Bucket(true) }.add(p, first == p.date, familiar)
            hourBuckets[p.hour].add(p, false, familiar); weekdayBuckets[p.day].add(p, false, familiar)
            platforms.getOrPut(e.platform) { Bucket() }.add(p, false, familiar)
            countries.getOrPut(e.country) { Bucket() }.add(p, false, familiar)
            kinds.getOrPut(e.kind) { Bucket() }.add(p, false, familiar)
            reasons.getOrPut(e.reasonEnd) { Bucket() }.add(p, false, familiar)
            val shuffleLabel = when (e.shuffle) { true -> "Shuffle on"; false -> "Shuffle off"; null -> "Unknown" }
            shuffle.getOrPut(shuffleLabel) { Bucket() }.add(p, false, familiar)
            heatMs[p.day * 24 + p.hour] += e.ms; heatPlays[p.day * 24 + p.hour]++
            if (e.ms >= 30000) qualified++
            if (e.shuffle != null) { shuffleKnown++; if (e.shuffle) shuffleYes++ }
            rank(artists, e.artist, e.artist, p); rank(tracks, p.item, e.title, p)
            if (e.album.isNotEmpty()) rank(albums, "${e.artist.length}:${e.artist}${e.album}", e.album, p)
            obsessions.getOrPut(p.item to p.month) { ObsessionAccumulator(p) }.apply { plays++; ms += e.ms }
            val prev = previous[p.item]
            if (prev != null && e.ms > 0) {
                val gapDays = (LocalDate.parse(p.date).toEpochDay() - LocalDate.parse(prev.date).toEpochDay()).toInt()
                if (gapDays >= 180) gaps.add(Rediscovery(e.title, e.artist, e.uri, gapDays, prev.date, p.date))
            }
            if (e.ms > 0) previous[p.item] = p
        }
        fun ranks(map: Map<String, RankAccumulator>) = map.values.map { it.result() }
            .sortedWith(compareByDescending<Ranking> { it.ms }.thenByDescending { it.plays }.thenBy { it.name })
        val artistRanks = ranks(artists); val trackRanks = ranks(tracks); val albumRanks = ranks(albums)
        val daily = mutableListOf<DailyPoint>(); val calendarCounts = IntArray(7)
        var longestStreak = 0; var streak = 0; var activeDays = 0; var topDay: TopDay? = null
        var date = from
        while (date <= to) {
            val name = date.toString(); val b = days[name]; val ms = b?.ms ?: 0
            calendarCounts[date.dayOfWeek.value - 1]++
            months.getOrPut(name.take(7)) { Bucket(true, true) }; years.getOrPut(name.take(4)) { Bucket(true, true) }
            daily.add(DailyPoint(name, ms / HOUR, ms / 60000.0, b?.plays ?: 0, b?.items?.size ?: 0, b?.artists?.size ?: 0, b?.let { ratio(it.items.size, it.plays) }))
            if (ms > 0) { activeDays++; streak++; longestStreak = maxOf(longestStreak, streak) } else streak = 0
            if (ms > 0 && (topDay == null || ms / HOUR > topDay.hours)) topDay = TopDay(name, ms / HOUR)
            date = date.plusDays(1)
        }
        val monthly = months.map { (m, b) -> b.period(m) }
        var prior: Set<String>? = null
        val yearly = years.map { (year, b) ->
            val top = b.artistMs.entries.sortedByDescending { it.value }.take(20)
            val current = top.mapTo(linkedSetOf()) { it.key }; val previousYear = prior
            val overlap = if (previousYear.isNullOrEmpty() || current.isEmpty()) null else ratio(current.intersect(previousYear).size, (current + previousYear).size)
            prior = current
            b.period(year).copy(topArtist = top.firstOrNull()?.key.orEmpty(), overlap = overlap,
                partial = "$year-01-01" < start || "$year-12-31" > end || "$year-01-01" < dataStart || "$year-12-31" > dataEnd)
        }
        val compositionKeys = artistRanks.take(5).map { it.name }.toMutableList()
        var other = "Other"; while (other in compositionKeys) other += " artists"
        compositionKeys.add(other)
        val composition = monthly.map { m ->
            val b = months.getValue(m.period)
            val values = compositionKeys.dropLast(1).map { (b.artistMs[it] ?: 0) / HOUR }
            CompositionPoint(m.period, values + (b.ms / HOUR - values.sum()).coerceAtLeast(0.0))
        }
        fun groups(map: Map<String, Bucket>) = map.map { (name, b) -> b.group(name) }.sortedByDescending { it.hours }
        val active = daily.filter { it.minutes > 0 }
        return QueryResult(
            summary = Summary(total.plays, qualified, total.ms, total.items.size, total.artists.size, activeDays, daily.size,
                ratio(total.skipped, total.known), total.known, ratio(shuffleYes, shuffleKnown), shuffleKnown, ratio(total.ms, total.plays),
                total.newArtists.size, longestStreak, topDay, selected.firstOrNull()?.date.orEmpty(), selected.lastOrNull()?.date.orEmpty(), ratio(artistRanks.take(10).sumOf { it.ms }, total.ms)),
            monthly = monthly, yearly = yearly, daily = daily,
            heatmap = List(168) { HeatPoint(it / 24, it % 24, heatMs[it] / HOUR, heatPlays[it]) },
            hours = hourBuckets.mapIndexed { i, b -> b.group(i.toString().padStart(2, '0') + ":00") },
            weekdays = weekdayBuckets.mapIndexed { i, b -> b.group(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i])
                .copy(averageMinutes = if (calendarCounts[i] == 0) 0.0 else b.ms / 60000.0 / calendarCounts[i], calendarDays = calendarCounts[i]) },
            artists = artistRanks, tracks = trackRanks, albums = albumRanks, platforms = groups(platforms), countries = groups(countries),
            kinds = groups(kinds), reasons = groups(reasons), shuffleComparison = groups(shuffle), composition = composition, compositionKeys = compositionKeys,
            rediscoveries = gaps.sortedByDescending { it.gapDays }.take(30),
            obsessions = obsessions.values.filter { it.plays > 1 }.sortedWith(compareByDescending<ObsessionAccumulator> { it.plays }.thenByDescending { it.ms }).take(30)
                .map { Obsession(it.first.e.title, it.first.e.artist, it.first.month, it.plays, it.ms / HOUR, it.first.e.uri) },
            correlation = Correlation(pearson(active.map { it.minutes to it.tracks.toDouble() }), active.size),
            options = Options(prepared.mapTo(sortedSetOf()) { it.year }.toList(), prepared.mapTo(sortedSetOf()) { it.e.platform }.toList(),
                prepared.mapTo(sortedSetOf()) { it.e.country }.toList(), prepared.mapTo(sortedSetOf()) { it.e.artist }.toList()),
            scope = Scope(start, end, f.timezone),
        )
    }
}

fun validateFilters(f: Filters) {
    require(f.start.isEmpty() || runCatching { LocalDate.parse(f.start).toString() == f.start }.getOrDefault(false)) { "Enter the start date as YYYY-MM-DD." }
    require(f.end.isEmpty() || runCatching { LocalDate.parse(f.end).toString() == f.end }.getOrDefault(false)) { "Enter the end date as YYYY-MM-DD." }
    require(f.start.isEmpty() || f.end.isEmpty() || f.start <= f.end) { "The start date must be on or before the end date." }
    require(runCatching { ZoneId.of(f.timezone) }.isSuccess) { "Choose a valid timezone." }
    require(f.minMs >= 0) { "Minimum playback duration cannot be negative." }
}
