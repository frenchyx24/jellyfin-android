package org.jellyfin.mobile.nflix.data

/**
 * Relevance of a title for a query, 0 when unrelated. Jellyfin's search is loose (it matches any word,
 * episode titles, people…); results are re-ranked and filtered here.
 */
object SearchRanking {
    const val THRESHOLD = 300

    private val articles = setOf("le", "la", "les", "l", "un", "une", "des", "the", "a", "an", "de", "du", "d")

    fun tokens(value: String): List<String> =
        VideoCatalog.normalize(value).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun compact(value: String) = tokens(value).joinToString(" ")

    /** Levenshtein distance, stopped early above [limit]. */
    fun distance(a: String, b: String, limit: Int = 3): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var best = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                best = minOf(best, current[j])
            }
            if (best > limit) return limit + 1
            previous = current
        }
        return previous[b.length]
    }

    private fun tolerance(token: String) = when {
        token.length >= 8 -> 2
        token.length >= 4 -> 1
        else -> 0
    }

    /** How well one query token matches a title token: 3 exact, 2 prefix, 1 typo, 0 none. */
    private fun tokenMatch(query: String, title: String): Int = when {
        title == query -> 3
        title.startsWith(query) && query.length >= 2 -> 2
        query.length >= 4 && distance(query, title, tolerance(query)) <= tolerance(query) -> 1
        query.length >= 5 && title.length > query.length && distance(query, title.take(query.length), 1) <= 1 -> 1
        else -> 0
    }

    fun score(query: String, title: String?): Int {
        if (title.isNullOrBlank()) return 0
        val q = compact(query)
        val t = compact(title)
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return 1000
        if (t.startsWith(q)) return 850 - (t.length - q.length).coerceAtMost(100)
        val queryTokens = tokens(query).filterNot { it in articles && tokens(query).size > 1 }
        val titleTokens = tokens(title)
        if (queryTokens.isEmpty()) return 0
        val matches = queryTokens.map { qt -> titleTokens.maxOfOrNull { tokenMatch(qt, it) } ?: 0 }
        if (matches.all { it > 0 }) {
            val quality = matches.sum().toFloat() / (matches.size * 3)
            // Shorter titles that cover the query win over long ones that merely contain it.
            val coverage = queryTokens.size.toFloat() / titleTokens.size.coerceAtLeast(1)
            return (450 + 250 * quality + 100 * coverage.coerceAtMost(1f)).toInt()
        }
        if (t.contains(q) && q.length >= 3) return 400
        // Most of a long query matched: still worth showing, low in the list.
        val matched = matches.count { it > 0 }
        return if (queryTokens.size >= 3 && matched >= queryTokens.size - 1) 320 else 0
    }

    /** Best score of an item over its names (original title, series, artist…). */
    fun score(query: String, item: Item): Int = when (item.type) {
        // An episode only belongs in results when its series or its own title really matches.
        "Episode" -> maxOf(score(query, item.seriesName).takeIf { it >= 600 }?.minus(150) ?: 0, score(query, item.name).takeIf { it >= 850 } ?: 0)
        "Person", "MusicArtist" -> score(query, item.name).takeIf { it >= 450 } ?: 0
        "Audio" -> maxOf(score(query, item.name), (score(query, item.albumArtist) - 200).coerceAtLeast(0))
        "MusicAlbum" -> maxOf(score(query, item.name), (score(query, item.albumArtist) - 150).coerceAtLeast(0))
        else -> maxOf(score(query, item.name), score(query, item.originalTitle) - 20)
    }

    fun rank(query: String, items: List<Item>): List<Item> =
        items.map { it to score(query, it) }
            .filter { it.second >= THRESHOLD }
            .sortedWith(compareByDescending<Pair<Item, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0.0 })
            .map { it.first }

    /** Shorter search term used when the full query finds nothing (typos): the longest word's start. */
    fun fallbackTerm(query: String): String? =
        tokens(query).filter { it !in articles }.maxByOrNull { it.length }?.takeIf { it.length >= 4 }?.take(4)
}
