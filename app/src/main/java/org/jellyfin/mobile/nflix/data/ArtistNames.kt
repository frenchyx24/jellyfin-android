package org.jellyfin.mobile.nflix.data

import java.text.Normalizer
import java.util.Locale

/**
 * Artist credit helpers, mirror of the server's: "Daft Punk, Pharrell Williams" and
 * "Daft Punk feat. X" belong to "Daft Punk"; case, accents and full-width forms
 * ("Tyler， The Creator") are ignored when comparing.
 */
object ArtistNames {
    private val featuring = Regex("""\s+(feat\.?|ft\.?|featuring|with|x|avec)\s+""", RegexOption.IGNORE_CASE)
    private val marks = Regex("\\p{Mn}+")
    private val commaNames = listOf(
        "tyler, the creator", "earth, wind & fire", "crosby, stills, nash & young", "crosby, stills & nash",
        "emerson, lake & palmer", "peter, paul and mary", "blood, sweat & tears", "now, now", "black country, new road",
    )

    fun primary(credit: String?): String {
        var value = credit.orEmpty().trim().replace('，', ',').replace(" - Topic", "").trim()
        if (value.isEmpty()) return ""
        val lower = value.lowercase(Locale.ROOT)
        commaNames.firstOrNull { lower.startsWith(it) }?.let { return value.substring(0, it.length).trim() }
        featuring.find(value)?.takeIf { it.range.first > 0 }?.let { value = value.substring(0, it.range.first) }
        for (separator in listOf(", ", " / ", "; ", " · ")) {
            val i = value.indexOf(separator)
            if (i > 0) value = value.substring(0, i)
        }
        return value.trim()
    }

    fun key(name: String?): String =
        Normalizer.normalize(Normalizer.normalize(name.orEmpty(), Normalizer.Form.NFKC), Normalizer.Form.NFD)
            .replace(marks, "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /**
     * Collapses duplicate artists: same name up to case/accents/width, and multi-artist credits
     * folded into their main artist. [name] reads the display name; returns groups, best first.
     */
    fun <T> group(items: List<T>, name: (T) -> String, preferred: (T) -> Boolean): List<Pair<String, List<T>>> {
        val byKey = LinkedHashMap<String, MutableList<T>>()
        for (item in items) {
            val main = primary(name(item))
            val k = key(main).ifEmpty { key(name(item)) }
            byKey.getOrPut(k) { mutableListOf() }.add(item)
        }
        return byKey.values.map { members ->
            val sorted = members.sortedWith(
                compareByDescending<T> { key(name(it)) == key(primary(name(it))) }
                    .thenByDescending { preferred(it) }
                    .thenBy { name(it).length },
            )
            primary(name(sorted.first())).ifBlank { name(sorted.first()) } to sorted
        }
    }
}
