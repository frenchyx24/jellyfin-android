package org.jellyfin.mobile.nflix.data

/**
 * What the user typed, split into a title and the hints around it:
 * "supracell s1e3", "one piece saison 2", "dune 2021", "naruto ep 12".
 */
data class SearchQuery(val text: String, val season: Int? = null, val episode: Int? = null, val year: Int? = null) {
    val wantsEpisode: Boolean get() = season != null || episode != null

    companion object {
        private val seasonEpisode = Regex("""\bs\s?(\d{1,2})\s?(?:e|ep|x)\s?(\d{1,4})\b""")
        private val crossed = Regex("""\b(\d{1,2})x(\d{1,4})\b""")
        private val seasonWords = Regex("""\b(?:saison|season)\s*(\d{1,2})(?:\s*(?:episode|ep|e)\s*(\d{1,4}))?\b""")
        private val episodeWords = Regex("""\b(?:episode|ep)\s*(\d{1,4})\b""")
        private val seasonShort = Regex("""\bs(\d{1,2})\b""")
        private val yearPattern = Regex("""\b(?:19|20)\d{2}\b""")

        fun parse(raw: String): SearchQuery {
            var text = VideoCatalog.normalize(raw).replace(Regex("[\\s]+"), " ")
            var season: Int? = null
            var episode: Int? = null
            fun take(regex: Regex, apply: (MatchResult) -> Unit): Boolean {
                val match = regex.find(text) ?: return false
                apply(match)
                text = text.removeRange(match.range)
                return true
            }
            take(seasonEpisode) { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt() } ||
                take(crossed) { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt() } ||
                take(seasonWords) { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toIntOrNull() }
            if (episode == null) take(episodeWords) { episode = it.groupValues[1].toInt() }
            if (season == null && episode == null) {
                // "s2" alone only once something else names the show.
                seasonShort.find(text)?.takeIf { text.removeRange(it.range).isNotBlank() }?.let { match ->
                    season = match.groupValues[1].toInt()
                    text = text.removeRange(match.range)
                }
            }
            var year: Int? = null
            yearPattern.find(text)?.takeIf { text.removeRange(it.range).isNotBlank() }?.let { match ->
                year = match.value.toInt()
                text = text.removeRange(match.range)
            }
            return SearchQuery(text.replace(Regex("\\s+"), " ").trim(), season, episode, year)
        }
    }
}

/** The catalog with every name prepared once; built off the main thread when the catalog arrives. */
class SearchIndex(val items: List<Item>) {
    internal class Entry(val item: Item, val name: SearchRanking.Prepared, val original: SearchRanking.Prepared?)

    internal val entries: List<Entry> = items.map { item ->
        Entry(item, SearchRanking.Prepared(item.name), item.originalTitle?.takeIf { it.isNotBlank() && it != item.name }?.let(SearchRanking::Prepared))
    }

    internal fun score(query: SearchRanking.Prepared, entry: Entry): Int =
        maxOf(SearchRanking.score(query, entry.name), entry.original?.let { SearchRanking.score(query, it) - 20 } ?: 0)

    companion object {
        val Empty = SearchIndex(emptyList())
    }
}

/** Instant, offline matching over the video catalog, before (and next to) the server's search. */
object SearchEngine {
    private val yearOnly = Regex("""(19|20)\d{2}""")

    private fun yearFits(item: Item, year: Int): Boolean = item.year == null || kotlin.math.abs(item.year - year) <= 1

    /**
     * Titles matching the query, best first. A year narrows to that year, unless the number belongs to
     * the title itself ("blade runner 2049", "2001 l'odyssee de l'espace").
     */
    suspend fun local(query: SearchQuery, index: SearchIndex, limit: Int = 60): List<Item> {
        if (query.text.isBlank()) return emptyList()
        if (yearOnly.matches(query.text)) {
            val year = query.text.toInt()
            return index.items.filter { it.year == year }.sortedByDescending { it.communityRating ?: 0.0 }.take(limit)
        }
        val text = SearchRanking.Prepared(query.text)
        val withYear = query.year?.let { SearchRanking.Prepared("${query.text} $it") }
        val scored = ArrayList<Pair<Item, Int>>()
        index.entries.forEachIndexed { i, entry ->
            if (i % 400 == 0) kotlinx.coroutines.yield() // a new keystroke cancels stale work
            var score = index.score(text, entry)
            if (query.year != null && !yearFits(entry.item, query.year)) score = 0
            if (withYear != null) score = maxOf(score, index.score(withYear, entry))
            if (score >= SearchRanking.THRESHOLD) scored += entry.item to score
        }
        return scored.sortedWith(compareByDescending<Pair<Item, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0.0 })
            .take(limit).map { it.first }
    }

    /** Genre named by the query ("horreur", "comedie", "science"), from the catalog's own genres. */
    fun genre(query: SearchQuery, catalog: List<Item>): String? {
        val q = VideoCatalog.normalize(query.text)
        if (q.length < 3) return null
        val genres = catalog.asSequence().flatMap { it.genres.asSequence() }.distinct().toList()
        return genres.firstOrNull { VideoCatalog.normalize(it) == q }
            ?: genres.filter { VideoCatalog.normalize(it).startsWith(q) && q.length >= 4 }.minByOrNull { it.length }
    }

    fun byGenre(genre: String, catalog: List<Item>, limit: Int = 40): List<Item> =
        catalog.filter { item -> item.genres.any { it.equals(genre, ignoreCase = true) } }
            .sortedByDescending { it.communityRating ?: 0.0 }.take(limit)

    /** Most common genres, for the browse chips. */
    fun topGenres(catalog: List<Item>, count: Int = 18): List<String> =
        catalog.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(count).map { it.key }

    /**
     * Local and server hits in one list, best first, one entry per title. Everything is scored against
     * the current query (local hits may come from the previous keystroke) and filtered the same way.
     */
    fun merge(query: SearchQuery, local: List<Item>, server: List<Item>): List<Item> {
        val seen = HashSet<String>()
        val all = (local + server).filter { seen.add(it.id) }
        if (yearOnly.matches(query.text)) return local + server.filter { it.id !in local.mapTo(HashSet()) { l -> l.id } && it.year == query.text.toInt() }
        val text = SearchRanking.Prepared(query.text)
        val withYear = query.year?.let { SearchRanking.Prepared("${query.text} $it") }
        return all.map { item ->
            var score = SearchRanking.score(text, item)
            if (query.year != null && !yearFits(item, query.year)) score = 0
            if (withYear != null) score = maxOf(score, SearchRanking.score(withYear, item))
            item to score
        }
            .filter { it.second >= SearchRanking.THRESHOLD }
            .sortedWith(compareByDescending<Pair<Item, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0.0 })
            .map { it.first }
    }
}
