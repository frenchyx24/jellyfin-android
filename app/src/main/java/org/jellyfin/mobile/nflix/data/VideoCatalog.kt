package org.jellyfin.mobile.nflix.data

import java.text.Normalizer
import java.util.Locale

/**
 * Video classification shared by the Vidéos tab and the search.
 *
 * "Animé" means Japanese animation only. Western animation (Pixar, DreamWorks, Disney…) stays in
 * Films or Séries under the "Animation" genre.
 */
object VideoCatalog {
    /** Studios whose animated work is Japanese anime. Lower-case, accent-free. */
    private val japaneseStudios = setOf(
        "toei animation", "mappa", "madhouse", "sunrise", "bones", "kyoto animation", "ufotable", "production i.g",
        "wit studio", "a-1 pictures", "studio ghibli", "tms entertainment", "pierrot", "studio pierrot", "cloverworks",
        "shaft", "trigger", "studio trigger", "david production", "lerche", "j.c.staff", "studio deen", "white fox",
        "p.a. works", "kinema citrus", "silver link", "doga kobo", "science saru", "studio bind",
        "polygon pictures", "olm", "xebec", "gainax", "khara", "tatsunoko production", "brain's base",
        "8bit", "feel.", "studio 4°c", "studio 4c", "comix wave films", "shin-ei animation", "nippon animation",
        "telecom animation film", "satelight", "tezuka productions", "liden films", "lay-duce", "passione",
        "studio chizu", "geno studio", "bandai namco pictures", "studio voln", "artland", "diomedea", "zexcs",
        "hal film maker", "manglobe", "arms", "studio hibari", "project no.9", "c2c", "tnk", "seven arcs",
        "aniplex", "studio colorido", "yokohama animation laboratory", "cloud hearts", "studio kai",
    )

    private val animeGenres = setOf("anime", "animes", "japanimation", "manga")
    private val japanWords = setOf("japan", "japon", "jp")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    /** "Animé", "Anime", "Japanimation"… but never "Animation". */
    fun isAnimeLabel(value: String): Boolean = normalize(value) in animeGenres

    fun isAnimation(value: String): Boolean = normalize(value).let { it == "animation" || it == "dessin anime" || it == "dessins animes" }

    fun isAnime(item: Item): Boolean {
        if (item.genres.any(::isAnimeLabel) || item.tags.any(::isAnimeLabel)) return true
        if (item.genres.none(::isAnimation)) return false
        // Animated: Japanese production or a Japanese studio makes it an anime.
        if (item.productionLocations.any { normalize(it) in japanWords }) return true
        return item.studios.any { normalize(it.name) in japaneseStudios }
    }

    /** A library named like an anime library ("Animés", "Anime", "Manga"…), not "Animation". */
    fun isAnimeLibrary(view: Item): Boolean {
        val words = normalize(view.name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return words.any { it in animeGenres }
    }

    /** Preferred order for genre shelves; labels match French and English metadata. */
    private val preferredGenres = listOf(
        "action", "comedie|comedy", "aventure|adventure", "science-fiction|science fiction|sci-fi & fantasy", "thriller",
        "drame|drama", "horreur|horror", "fantastique|fantasy", "animation", "familial|family", "crime", "romance",
        "mystere|mystery", "guerre|war|war & politics", "histoire|history", "documentaire|documentary", "western", "musique|music",
    ).map { it.split('|').toSet() }

    /** Genres sorted for display: popular first, the rest alphabetically. Anime labels never appear. */
    fun sortGenres(genres: Collection<String>): List<String> {
        val unique = genres.filter { it.isNotBlank() && !isAnimeLabel(it) }.distinctBy(::normalize)
        fun rank(genre: String) = preferredGenres.indexOfFirst { normalize(genre) in it }.let { if (it < 0) Int.MAX_VALUE else it }
        return unique.sortedWith(compareBy<String>(::rank).thenBy(::normalize))
    }

    fun hasGenre(item: Item, genre: String): Boolean = item.genres.any { normalize(it) == normalize(genre) }
}
