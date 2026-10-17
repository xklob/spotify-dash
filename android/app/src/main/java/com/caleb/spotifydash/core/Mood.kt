package com.caleb.spotifydash.core

import java.time.LocalDate
import java.util.BitSet
import kotlin.math.ln

data class MoodOptions(val weight: String = "time", val period: String = "month")
internal interface MoodInput { val e: HistoryEvent; val date: String; val day: Int; val hour: Int; val item: String }
data class MoodBucket(
    val label: String, val start: String = "", val end: String = "", val plays: Int, val ms: Long, val tracks: Int,
    val coveredPlays: Int, val coveredMs: Long, val coveredTracks: Int, val coverage: Double?, val values: AudioFeatures,
    val featureCoverage: AudioFeatures, val skipRate: Double?, val skipKnown: Int,
)
data class MoodTrack(val uri: String, val title: String, val artist: String, val plays: Int, val ms: Long, val skipRate: Double?, val skipKnown: Int, val metadata: TrackMetadata)
data class MoodCell(val x: Int, val y: Int, var weight: Double = 0.0, var tracks: Int = 0, var plays: Int = 0, var ms: Long = 0)
data class MoodCorrelations(val energySkip: Double?, val skipTracks: Int, val energyRepeats: Double?, val repeatTracks: Int)
data class MoodResult(
    val provider: String, val modelVersion: String?, val retrievedAt: String, val cached: Int, val pending: Int,
    val summary: MoodBucket, val periods: List<MoodBucket>, val rolling: List<MoodBucket>, val years: List<MoodBucket>,
    val heatmap: List<MoodBucket>, val distribution: List<MoodCell>, val energyBands: List<MoodBucket>,
    val familiarity: List<MoodBucket>, val comparison: List<MoodBucket>, val tracks: List<MoodTrack>, val trackCount: Int, val correlations: MoodCorrelations,
)
private fun moodRatio(a: Number, b: Number): Double? = if (b.toDouble() > 0) a.toDouble() / b.toDouble() else null
fun periodBounds(date: String, period: String): Pair<String, String> {
    val d = LocalDate.parse(date)
    if (period == "week") { val start = d.minusDays(d.dayOfWeek.value - 1L); return start.toString() to start.plusDays(6).toString() }
    return d.withDayOfMonth(1).toString() to d.withDayOfMonth(d.lengthOfMonth()).toString()
}
private class MoodAccumulator(val weight: String) {
    var plays = 0; var ms = 0L; var coveredPlays = 0; var coveredMs = 0L; var skipKnown = 0; var skipped = 0
    // Dense per-query ordinals avoid retaining hundreds of large HashSets for a full archive.
    val all = BitSet(); val covered = BitSet()
    val sums = DoubleArray(9); val weights = DoubleArray(9); val knownMs = DoubleArray(9)
    fun add(p: MoodInput, t: TrackMetadata?, key: Int) {
        val e = p.e; val fresh = !all.get(key); all.set(key); plays++; ms += e.ms
        if (t?.paired == true) { coveredPlays++; coveredMs += e.ms; covered.set(key) }
        if (e.skipped != null) { skipKnown++; if (e.skipped) skipped++ }
        if (t == null || t.status != "found") return
        val w = when (weight) { "time" -> e.ms.toDouble(); "plays" -> 1.0; else -> if (fresh) 1.0 else 0.0 }
        FEATURE_KEYS.forEachIndexed { i, k ->
            val v = t.features[k]
            if (v != null && (i > 1 || t.paired)) { sums[i] += v * w; weights[i] += w; knownMs[i] += e.ms }
        }
    }
    fun result(label: String = "", start: String = "", end: String = "") = MoodBucket(label, start, end, plays, ms, all.cardinality(), coveredPlays, coveredMs, covered.cardinality(),
        moodRatio(coveredMs, ms), AudioFeatures.from(sums.indices.map { moodRatio(sums[it], weights[it]) }),
        AudioFeatures.from(knownMs.map { moodRatio(it, ms) }), moodRatio(skipped, skipKnown), skipKnown)
}
private class MoodRank(val p: MoodInput, val metadata: TrackMetadata) {
    var plays = 0; var ms = 0L; var skipped = 0; var known = 0
    fun result(key: String) = MoodTrack(key, p.e.title, p.e.artist, plays, ms, moodRatio(skipped, known), known, metadata)
}
internal fun analyzeMood(rows: List<MoodInput>, firstItem: Map<String, String>, metadata: Metadata, options: MoodOptions, start: String, end: String, pending: Int): MoodResult {
    require(options.weight in listOf("time", "plays", "tracks") && options.period in listOf("month", "week")) { "Invalid mood chart options." }
    val music = rows.filter { it.e.kind == "music" }; val byUri = metadata.tracks.associateBy { it.uri }
    val ordinals = HashMap<String, Int>()
    fun make() = MoodAccumulator(options.weight)
    val total = make(); val periods = sortedMapOf<String, MoodAccumulator>(); val years = sortedMapOf<String, MoodAccumulator>()
    val heat = List(168) { make() }; val bands = List(5) { make() }; val familiarity = List(2) { make() }; val comparison = List(2) { make() }
    val ranks = LinkedHashMap<String, MoodRank>(); val distribution = List(100) { MoodCell(it % 10, it / 10) }
    val midpoint = LocalDate.ofEpochDay((LocalDate.parse(start).toEpochDay() + LocalDate.parse(end).toEpochDay()) / 2).toString()
    music.forEach { p ->
        val e = p.e; val key = moodKey(e, metadata.provider == "demo"); val t = byUri[key]; val ordinal = ordinals.getOrPut(key) { ordinals.size }
        val month = periodBounds(p.date, options.period).first
        listOf(total, periods.getOrPut(month, ::make), years.getOrPut(p.date.take(4), ::make), heat[p.day * 24 + p.hour],
            familiarity[if ((firstItem[p.item] ?: p.date) < p.date) 1 else 0], comparison[if (p.date <= midpoint) 0 else 1]).forEach { it.add(p, t, ordinal) }
        if (t?.paired != true) return@forEach
        val energy = t.features.energy!!; val valence = t.features.valence!!
        bands[(energy * 5).toInt().coerceAtMost(4)].add(p, t, ordinal)
        val cell = distribution[(energy * 10).toInt().coerceAtMost(9) * 10 + (valence * 10).toInt().coerceAtMost(9)]
        val fresh = key !in ranks; cell.plays++; cell.ms += e.ms; if (fresh) cell.tracks++
        cell.weight += when (options.weight) { "time" -> e.ms / 3600000.0; "plays" -> 1.0; else -> if (fresh) 1.0 else 0.0 }
        val r = ranks.getOrPut(key) { MoodRank(p, t) }; r.plays++; r.ms += e.ms
        if (e.skipped != null) { r.known++; if (e.skipped) r.skipped++ }
    }
    var cursor = start
    while (cursor <= end) {
        val (s, e) = periodBounds(cursor, options.period); periods.getOrPut(s, ::make); years.getOrPut(cursor.take(4), ::make)
        cursor = LocalDate.parse(e).plusDays(1).toString()
    }
    val sortedPeriods = periods.keys.toList(); val rolling = sortedPeriods.map { make() }; val indices = sortedPeriods.withIndex().associate { it.value to it.index }
    music.forEach { p ->
        val i = indices[periodBounds(p.date, options.period).first]!!; val key = moodKey(p.e, metadata.provider == "demo")
        for (j in i until minOf(i + 3, rolling.size)) rolling[j].add(p, byUri[key], ordinals[key]!!)
    }
    val tracks = ranks.map { (key, r) -> r.result(key) }.sortedWith(compareByDescending<MoodTrack> { it.ms }.thenByDescending { it.plays }.thenBy { it.uri })
    val skips = tracks.filter { it.skipKnown >= 5 }.map { it.metadata.features.energy!! to it.skipRate!! }
    val repeats = tracks.map { it.metadata.features.energy!! to ln(1.0 + it.plays) }
    return MoodResult(metadata.provider, metadata.modelVersion, metadata.tracks.maxOfOrNull { it.fetchedAt } ?: "", metadata.tracks.size, pending,
        total.result("Selected music", start, end), periods.map { (s, a) -> a.result(s, s, periodBounds(s, options.period).second) },
        rolling.mapIndexed { i, a -> a.result(sortedPeriods[i], sortedPeriods[maxOf(0, i - 2)], periodBounds(sortedPeriods[i], options.period).second) },
        years.map { (y, a) -> a.result(y, "$y-01-01", "$y-12-31") },
        heat.mapIndexed { i, a -> a.result("${listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[i / 24]} ${(i % 24).toString().padStart(2, '0')}:00") }, distribution,
        bands.mapIndexed { i, a -> a.result(String.format(java.util.Locale.ROOT, "%.1f–%.1f", i / 5.0, (i + 1) / 5.0)) },
        familiarity.mapIndexed { i, a -> a.result(if (i == 1) "Returning tracks" else "First recorded day") },
        listOf(comparison[0].result("Earlier half", start, midpoint), comparison[1].result("Later half", LocalDate.parse(midpoint).plusDays(1).toString(), end)),
        tracks.take(200), tracks.size, MoodCorrelations(pearson(skips), skips.size, pearson(repeats), repeats.size))
}
