package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

/** An e-book (EPUB, PDF, CBZ…) with its place in a saga when known. */
@Immutable
data class Ebook(val item: Item, val author: String?, val saga: String?, val volume: Int?)

/**
 * An audiobook: Jellyfin exposes one AudioBook item per chapter file, grouped here by their folder
 * ("Title - Narrators/Title (01).mp3").
 */
@Immutable
data class Audiobook(
    val id: String,
    val title: String,
    val narrators: String?,
    val author: String?,
    val cover: Item,
    val chapters: List<Item>,
    val saga: String? = null,
    val volume: Int? = null,
) {
    val durationTicks: Long get() = chapters.sumOf { it.runTimeTicks ?: 0L }

    /** Chapter to resume: the first one started but not finished, else the first unplayed one. */
    val resumeIndex: Int
        get() = chapters.indexOfFirst { (it.userData?.positionTicks ?: 0) > 0 && it.userData?.played != true }
            .takeIf { it >= 0 } ?: chapters.indexOfFirst { it.userData?.played != true }.coerceAtLeast(0)

    /** Listened share of the whole book, weighted by chapter length. */
    val progress: Float
        get() {
            val total = durationTicks
            if (total <= 0) return 0f
            val heard = chapters.sumOf { chapter ->
                val length = chapter.runTimeTicks ?: 0L
                when {
                    chapter.userData?.played == true -> length
                    else -> (chapter.userData?.positionTicks ?: 0L).coerceAtMost(length)
                }
            }
            return (heard.toDouble() / total).toFloat().coerceIn(0f, 1f)
        }

    val started: Boolean get() = chapters.any { it.userData?.played == true || (it.userData?.positionTicks ?: 0) > 0 }
    val finished: Boolean get() = chapters.isNotEmpty() && chapters.all { it.userData?.played == true }
}

/** A saga shelf: e-books and audiobooks of the same series, in reading order. */
@Immutable
data class Saga(val name: String, val author: String?, val ebooks: List<Ebook>, val audiobooks: List<Audiobook>, val authorShelf: Boolean = false) {
    val size: Int get() = ebooks.size + audiobooks.size
}

object BookLibrary {
    private val hashVolume = Regex("""\(([^()]+?),?\s*#\s*(\d+)\)""")
    private val tomeVolume = Regex("""(?i)\b(?:t\.?|tome)\s*(\d{1,3})\b""")
    private val narratorSplit = Regex("""\s+-\s+""")

    /** Groups chapter files by folder; [folders] maps folder id → folder item (for its name and cover). */
    fun audiobooks(chapters: List<Item>, folders: Map<String, Item>): List<Audiobook> =
        chapters.groupBy { it.parentId ?: it.id }.map { (folderId, files) ->
            val sorted = files.sortedWith(compareBy<Item>({ it.parentIndexNumber ?: 0 }, { it.indexNumber ?: Int.MAX_VALUE }, { naturalKey(it.path ?: it.name) }))
            val folder = folders[folderId]
            // A single file sitting directly in an author folder is its own book.
            val standalone = folder == null || files.size == 1 && folder.name == sorted.first().albumArtist
            val rawTitle = if (standalone) sorted.first().album?.takeIf { it.isNotBlank() } ?: sorted.first().name else folder!!.name
            val parts = rawTitle.split(narratorSplit, limit = 2)
            val title = parts[0].trim()
            Audiobook(
                id = if (standalone) sorted.first().id else folderId,
                title = title,
                narrators = parts.getOrNull(1)?.replace(" + ", ", ")?.takeIf { it.isNotBlank() && !it.equals("Full Cast", true) }
                    ?: parts.getOrNull(1)?.takeIf { it.equals("Full Cast", true) }?.let { "Distribution complète" },
                author = sorted.firstNotNullOfOrNull { it.albumArtist?.takeIf(String::isNotBlank) },
                cover = folder?.takeIf { it.imageTags.containsKey("Primary") } ?: sorted.first(),
                chapters = sorted,
                volume = volumeOf(rawTitle),
            )
        }

    fun ebooks(items: List<Item>, authorOf: (Item) -> String?): List<Ebook> = items.map { book ->
        val folderName = book.path?.substringBeforeLast('/')?.substringAfterLast('/')
        val explicit = hashVolume.find(folderName.orEmpty()) ?: hashVolume.find(book.name)
        Ebook(
            item = book,
            author = authorOf(book),
            saga = book.seriesName?.takeIf { it.isNotBlank() } ?: explicit?.groupValues?.get(1)?.trim(),
            volume = book.indexNumber ?: explicit?.groupValues?.get(2)?.toIntOrNull() ?: volumeOf(folderName.orEmpty()) ?: volumeOf(book.name),
        )
    }

    /**
     * Builds saga shelves. Explicit series metadata wins; otherwise titles by the same author that share a
     * leading phrase of at least two words ("Harry Potter …", "La femme de ménage …") form a saga.
     * Sub-series such as "Harry Potter - Nouvelles" join their parent saga.
     */
    /** Returns saga shelves first, then one shelf per author for titles outside any saga. */
    fun sagas(ebooks: List<Ebook>, audiobooks: List<Audiobook>): Pair<List<Saga>, List<Saga>> {
        data class Entry(val title: String, val author: String?, val saga: String?, val ebook: Ebook?, val audio: Audiobook?)
        // "McFadden Freida", " Freida McFadden" and "Freida McFadden" are one author: compare word sets, show the usual spelling.
        val rawAuthors = ebooks.mapNotNull { it.author?.trim() } + audiobooks.mapNotNull { it.author?.trim() }
        val authorNames = rawAuthors.filter { it.isNotBlank() }.groupBy(::authorKey).mapValues { (_, names) -> names.groupingBy { it }.eachCount().maxBy { it.value }.key }
        fun author(name: String?) = name?.trim()?.takeIf { it.isNotBlank() }?.let { authorNames[authorKey(it)] ?: it }
        val entries = ebooks.map { Entry(it.item.name, author(it.author), it.saga, it.copy(author = author(it.author)), null) } +
            audiobooks.map { Entry(it.title, author(it.author), it.saga, null, it.copy(author = author(it.author))) }

        val explicit = entries.mapNotNull { it.saga }.distinctBy(::key)
        // Collapse "Harry Potter - Nouvelles" into "Harry Potter" when the parent exists.
        fun canonical(name: String): String = explicit.filter { parent -> key(name) != key(parent) && key(name).startsWith(key(parent) + " ") }
            .minByOrNull { it.length } ?: name

        val inferred = mutableMapOf<String, String>() // title key → saga name
        entries.filter { it.saga == null }.groupBy { it.author?.let(::key) }.forEach { (_, group) ->
            val keys = group.map { key(it.title) }.distinct()
            keys.forEach { title ->
                // Shortest other title that this title starts with, or contains as a phrase.
                val base = keys.filter { other -> other != title && other.split(' ').size >= 2 && (title.startsWith("$other ") || title.contains(" $other")) }
                    .minByOrNull { it.length }
                if (base != null) {
                    val display = group.first { key(it.title) == base }.title
                    inferred[title] = display
                    inferred[base] = display
                }
            }
            // Common two-word prefix shared by several titles ("Harry Potter à…", "Harry Potter et…").
            keys.groupBy { it.split(' ').take(2).joinToString(" ") }.filter { (prefix, titles) -> titles.size >= 2 && prefix.split(' ').size == 2 && prefix.split(' ').none { it.length <= 2 } }
                .forEach { (prefix, titles) ->
                    val display = group.first { key(it.title).startsWith(prefix) }.title.split(' ').take(2).joinToString(" ")
                    titles.forEach { inferred.putIfAbsent(it, display) }
                }
        }

        fun sagaOf(entry: Entry): String? = (entry.saga ?: inferred[key(entry.title)])?.let(::canonical)
            ?: explicit.firstOrNull { key(entry.title).startsWith(key(it) + " ") }

        val grouped = entries.groupBy { sagaOf(it)?.let(::key) }
        val sagas = grouped.filterKeys { it != null }
            // One title in two formats (e-book + audiobook) is not a saga.
            .filterValues { members -> members.map { key(it.title) }.distinct().size >= 2 }
            .map { (_, members) ->
                val name = members.firstNotNullOf { sagaOf(it) }.let { found -> explicit.firstOrNull { key(it) == key(found) } ?: found }
                // Spin-offs ("Harry Potter - Nouvelles") go after the main volumes.
                fun spinOff(own: String?) = own != null && key(own) != key(name)
                Saga(
                    name = name,
                    author = members.firstNotNullOfOrNull { it.author },
                    ebooks = members.mapNotNull { it.ebook }
                        .sortedWith(compareBy({ spinOff(it.saga) }, { it.volume ?: Int.MAX_VALUE }, { it.item.year ?: Int.MAX_VALUE }, { it.item.name }))
                        .map { it.copy(saga = name) },
                    audiobooks = members.mapNotNull { it.audio }
                        .sortedWith(compareBy({ spinOff(it.saga) }, { it.volume ?: Int.MAX_VALUE }, { it.title }))
                        .map { it.copy(saga = name) },
                )
            }.sortedBy { key(it.name) }
        val inSaga = sagas.flatMap { s -> s.ebooks.map { it.item.id } + s.audiobooks.map { it.id } }.toSet()
        val loose = entries.filter { (it.ebook?.item?.id ?: it.audio?.id) !in inSaga }
        val byAuthor = loose.groupBy { it.author ?: "" }.toSortedMap().map { (author, members) ->
            Saga(
                name = author.ifBlank { "Autres titres" },
                author = author.ifBlank { null },
                ebooks = members.mapNotNull { it.ebook }.sortedBy { it.item.name },
                audiobooks = members.mapNotNull { it.audio }.sortedBy { it.title },
                authorShelf = true,
            )
        }
        return sagas to byAuthor
    }

    /** Author folder of a file laid out as ".../Author/Title/file.ext". */
    fun authorFromPath(path: String?): String? = path?.split('/')?.takeIf { it.size >= 3 }?.let { it[it.size - 3] }?.takeIf { it.isNotBlank() }

    fun volumeOf(text: String): Int? = hashVolume.find(text)?.groupValues?.get(2)?.toIntOrNull() ?: tomeVolume.find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun authorKey(name: String) = key(name).split(' ').filter { it.isNotBlank() }.sorted().joinToString(" ")

    /** Accent-, case- and punctuation-insensitive comparison key; edition notes in brackets are ignored. */
    fun key(text: String): String = Normalizer.normalize(text.replace(Regex("""\s*[(\[][^()\[\]]*[)\]]"""), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[’'`]"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun naturalKey(text: String): String = Regex("\\d+").replace(text.lowercase(Locale.ROOT)) { it.value.padStart(10, '0') }
}
