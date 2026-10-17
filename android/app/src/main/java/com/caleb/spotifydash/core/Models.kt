package com.caleb.spotifydash.core

data class HistoryEvent(
    val id: String, val ts: Long, val ms: Long, val kind: String = "unknown", val media: String = "audio",
    val title: String = "Unknown title", val artist: String = "Unknown artist", val album: String = "", val uri: String = "",
    val platform: String = "Unknown", val country: String = "Unknown", val shuffle: Boolean? = null,
    val skipped: Boolean? = null, val offline: Boolean? = null, val incognito: Boolean? = null,
    val reasonStart: String = "unknown", val reasonEnd: String = "unknown",
)
data class DatasetMeta(
    val source: String = "import", val files: List<String> = emptyList(), val importedAt: String = "",
    val rawRows: Int = 0, val duplicates: Int = 0, val invalidRows: Int = 0, val events: Int = 0,
    val first: String = "", val last: String = "", val totalMs: Long = 0, val warnings: List<String> = emptyList(),
)
data class Dataset(val meta: DatasetMeta, val events: List<HistoryEvent>)
data class Filters(
    val start: String = "", val end: String = "", val kind: String = "all", val minMs: Long = 0,
    val search: String = "", val artist: String = "", val platform: String = "", val country: String = "",
    val timezone: String = "America/New_York", val shuffle: String = "all",
)
data class Ranking(
    val key: String, val name: String, val artist: String, val album: String, val uri: String,
    val plays: Int, val ms: Long, val skipRate: Double?, val skipKnown: Int, val first: String, val last: String,
)
data class PeriodPoint(
    val period: String, val hours: Double, val plays: Int, val artists: Int, val newArtists: Int,
    val skipRate: Double?, val familiarShare: Double?, val topArtist: String = "", val partial: Boolean = false,
    val overlap: Double? = null,
)
data class DailyPoint(val date: String, val hours: Double, val minutes: Double, val plays: Int, val tracks: Int, val artists: Int, val uniqueShare: Double?)
data class GroupPoint(val name: String, val hours: Double, val plays: Int, val skipRate: Double?, val skipKnown: Int, val averageMinutes: Double = 0.0, val calendarDays: Int = 0)
data class HeatPoint(val day: Int, val hour: Int, val hours: Double, val plays: Int)
data class CompositionPoint(val period: String, val values: List<Double>)
data class Rediscovery(val name: String, val artist: String, val uri: String, val gapDays: Int, val before: String, val after: String)
data class Obsession(val name: String, val artist: String, val month: String, val plays: Int, val hours: Double, val uri: String)
data class TopDay(val date: String, val hours: Double)
data class Summary(
    val events: Int, val qualifiedPlays: Int, val ms: Long, val tracks: Int, val artists: Int,
    val activeDays: Int, val calendarDays: Int, val skipRate: Double?, val skipKnown: Int,
    val shuffleRate: Double?, val shuffleKnown: Int, val averageMs: Double?, val newArtists: Int,
    val longestStreak: Int, val topDay: TopDay?, val first: String, val last: String, val topArtistShare: Double?,
)
data class Correlation(val r: Double?, val n: Int,
    val label: String = "Daily listening time vs. distinct titles",
    val explanation: String = "Pearson correlation across days with recorded listening. More listening creates more opportunities to hear distinct titles; this association is descriptive, not causal.")
data class Options(val years: List<String>, val platforms: List<String>, val countries: List<String>, val artists: List<String>)
data class Scope(val start: String, val end: String, val timezone: String)
data class QueryResult(
    val summary: Summary, val monthly: List<PeriodPoint>, val yearly: List<PeriodPoint>, val daily: List<DailyPoint>,
    val heatmap: List<HeatPoint>, val hours: List<GroupPoint>, val weekdays: List<GroupPoint>,
    val artists: List<Ranking>, val tracks: List<Ranking>, val albums: List<Ranking>,
    val platforms: List<GroupPoint>, val countries: List<GroupPoint>, val kinds: List<GroupPoint>,
    val reasons: List<GroupPoint>, val shuffleComparison: List<GroupPoint>,
    val composition: List<CompositionPoint>, val compositionKeys: List<String>,
    val rediscoveries: List<Rediscovery>, val obsessions: List<Obsession>, val correlation: Correlation,
    val options: Options, val scope: Scope,
)
data class HistoryRow(val event: HistoryEvent, val date: String, val time: String)
data class HistoryPage(val rows: List<HistoryRow> = emptyList(), val total: Int = 0, val page: Int = 0, val pages: Int = 0)
