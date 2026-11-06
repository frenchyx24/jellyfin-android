package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Layout mirrors the real "Livres" library: Author/Title/file.epub and Author/"Title - Narrators"/Title (NN).mp3. */
class BookLibraryTest : FunSpec({
    fun book(id: String, name: String, folder: String, author: String, series: String? = null, index: Int? = null) =
        Item(id = id, name = name, type = "Book", path = "/media/Livres/$author/$folder/$folder.epub", seriesName = series, indexNumber = index)

    fun chapter(id: String, folderId: String, n: Int, author: String, album: String, ticks: Long = 600_000_000L, data: UserData? = null) =
        Item(id = id, name = "Chapitre $n", type = "AudioBook", parentId = folderId, indexNumber = n, albumArtist = author, album = album, runTimeTicks = ticks, userData = data)

    val rowling = "J.K. Rowling"
    val freida = "Freida McFadden"
    val ebooks = listOf(
        book("hp1", "Harry Potter à l'École des Sorciers", "Harry Potter à l'École des Sorciers", rowling, series = ""),
        book("hp3", "Harry Potter et le Prisonnier d'Azkaban", "Harry Potter et le prisonnier d'Azkaban (Harry Potter, #3)", rowling, "Harry Potter", 3),
        book("hp6", "Harry Potter et le Prince de Sang-Mêlé", "Harry Potter et le Prince de Sang-Melé (La série de livres Harry Potter t. 6) (French Edition)", rowling),
        book("beedle", "Les contes de Beedle Le barde", "Les Contes de Beedle le Barde", rowling, "Harry Potter", 1),
        book("poudlard", "Poudlard : Le Guide pas complet", "Poudlard Le Guide", rowling, "Harry Potter - Nouvelles", 1),
        book("place", "Une place à prendre", "Une place à prendre", rowling, ""),
        book("fdm3", "La femme de ménage T3 : La femme de ménage voit tout", "La femme de ménage", freida),
        book("secrets", "Les secrets de la femme de ménage", "Les Secrets de la femme de ménage", freida),
        book("psy", "La psy", "La psy", freida),
        book("prof", "La prof (French Edition)", "La prof", freida),
    ).let { items -> BookLibrary.ebooks(items) { BookLibrary.authorFromPath(it.path) } }

    val folders = mapOf(
        "f-fdm" to Item(id = "f-fdm", name = "La femme de ménage - Audrey Sourdive + Laure Filiu", type = "Folder", imageTags = mapOf("Primary" to "x")),
        "f-psy" to Item(id = "f-psy", name = "La psy - Alice de Ferran + Léovanie Raud", type = "Folder"),
        "f-archives" to Item(id = "f-archives", name = "Archives du Monde des Sorciers - Full Cast", type = "Folder"),
    )
    val audio = BookLibrary.audiobooks(
        listOf(
            chapter("a2", "f-fdm", 2, freida, "La femme de ménage", data = UserData(positionTicks = 300_000_000L)),
            chapter("a1", "f-fdm", 1, freida, "La femme de ménage", data = UserData(played = true)),
            chapter("a3", "f-fdm", 3, freida, "La femme de ménage"),
            chapter("p1", "f-psy", 1, "McFadden Freida ", "La psy"),
            chapter("w1", "f-archives", 1, rowling, "Archives du Monde des Sorciers"),
        ),
        folders,
    )

    test("chapter files become one audiobook per folder with narrators and resume point") {
        val fdm = audio.single { it.id == "f-fdm" }
        fdm.title shouldBe "La femme de ménage"
        fdm.narrators shouldBe "Audrey Sourdive, Laure Filiu"
        fdm.chapters.map { it.id } shouldBe listOf("a1", "a2", "a3")
        fdm.resumeIndex shouldBe 1
        fdm.progress.toDouble() shouldBe 0.5
        fdm.cover.id shouldBe "f-fdm"
        audio.single { it.id == "f-archives" }.narrators shouldBe "Distribution complète"
    }

    test("sagas come from metadata, folder hints and shared titles, in volume order") {
        val (sagas, others) = BookLibrary.sagas(ebooks, audio)
        val potter = sagas.single { it.name == "Harry Potter" }
        potter.ebooks.map { it.item.id } shouldBe listOf("beedle", "hp3", "hp6", "hp1", "poudlard")
        potter.ebooks.single { it.item.id == "hp6" }.volume shouldBe 6
        val menage = sagas.single { BookLibrary.key(it.name) == "la femme de menage" }
        menage.ebooks.map { it.item.id }.toSet() shouldBe setOf("fdm3", "secrets")
        menage.audiobooks.map { it.id } shouldBe listOf("f-fdm")
        // Titles without a saga stay on their author's shelf.
        others.single { it.name == freida }.ebooks.map { it.item.id }.toSet() shouldBe setOf("psy", "prof")
        // Same title as e-book and audiobook is not a saga, and author spellings are merged.
        others.single { it.name == freida }.audiobooks.map { it.id } shouldBe listOf("f-psy")
        others.count { it.name.contains("McFadden") } shouldBe 1
        others.single { it.name == rowling }.let { shelf ->
            shelf.ebooks.map { it.item.id } shouldBe listOf("place")
            shelf.audiobooks.map { it.id } shouldBe listOf("f-archives")
        }
    }
})
