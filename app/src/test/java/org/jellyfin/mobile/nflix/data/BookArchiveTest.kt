package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookArchiveTest : FunSpec({
    fun archive(root: File, entries: Map<String, String>): File = File(root, "book.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
    fun epub(href: String = "chapter.xhtml") = linkedMapOf(
        "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
        "OPS/book.opf" to """<p:package xmlns:p="http://www.idpf.org/2007/opf"><p:manifest><p:item id="chapter" href="$href"/></p:manifest><p:spine><p:itemref idref="chapter"/></p:spine></p:package>""",
        "OPS/chapter.xhtml" to "<html><body>Chapter</body></html>",
    )
    suspend fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("book-archive-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    test("cache reached through a symlink still opens EPUB chapters") { inDirectory { root ->
        // Android exposes the cache as /data/user/0/... which links to /data/data/...
        val real = File(root, "real").apply { mkdirs() }
        val alias = Files.createSymbolicLink(File(root, "alias").toPath(), real.toPath()).toFile()
        val book = BookArchive.parse(archive(root, epub()), "book", alias) as BookFile.Epub
        book.chapters.single().readText() shouldBe "<html><body>Chapter</body></html>"
    } }
    test("chapter hrefs climbing out of the OPF folder stay inside the book") { inDirectory { root ->
        val entries = epub("../Text/one.xhtml").apply {
            remove("OPS/chapter.xhtml")
            put("Text/one.xhtml", "<html/>")
        }
        (BookArchive.parse(archive(root, entries), "book", root) as BookFile.Epub).chapters.single().name shouldBe "one.xhtml"
    } }
    test("namespaced EPUB spine resolves chapters") { inDirectory { root ->
        val book = BookArchive.parse(archive(root, epub()), "book", root) as BookFile.Epub
        book.chapters.single().name shouldBe "chapter.xhtml"
    } }
    test("encoded paths and fragments resolve without treating plus as space") { inDirectory { root ->
        val entries = epub("a%2Bb.xhtml#page").apply {
            remove("OPS/chapter.xhtml")
            put("OPS/a+b.xhtml", "<html/>")
        }
        (BookArchive.parse(archive(root, entries), "book", root) as BookFile.Epub).chapters.single().name shouldBe "a+b.xhtml"
    } }
    test("ZIP traversal and sibling-prefix traversal fail") { inDirectory { root ->
        listOf("../outside.txt", "../book-extract-evil/outside.txt").forEach { path ->
            shouldThrow<IOException> { BookArchive.parse(archive(root, mapOf(path to "bad")), "book", root) }
        }
        File(root, "books/outside.txt").exists() shouldBe false
    } }
    test("EPUB metadata cannot escape extraction root") { inDirectory { root ->
        shouldThrow<IOException> { BookArchive.parse(archive(root, epub("../../outside.xhtml")), "book", root) }
    } }
    test("missing EPUB chapter fails explicitly") { inDirectory { root ->
        shouldThrow<IOException> { BookArchive.parse(archive(root, epub("missing.xhtml")), "book", root) }
    } }
    test("incomplete extraction is replaced on retry") { inDirectory { root ->
        File(root, "books/book-x").mkdirs()
        File(root, "books/book-x/stale.txt").writeText("partial")
        val book = BookArchive.parse(archive(root, epub()), "book", root) as BookFile.Epub
        book.chapters.size shouldBe 1
        File(book.root, "stale.txt").exists() shouldBe false
    } }
    test("comic pages use numeric order") { inDirectory { root ->
        val file = archive(root, mapOf("10.jpg" to "10", "2.jpg" to "2", "1.jpg" to "1"))
        (BookArchive.parse(file, "book", root) as BookFile.Comic).pages.map { it.name } shouldBe listOf("1.jpg", "2.jpg", "10.jpg")
    } }
    test("XML external entities are rejected") { inDirectory { root ->
        val entries = epub().apply {
            put("META-INF/container.xml", """<!DOCTYPE container [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><container>&xxe;</container>""")
        }
        shouldThrow<Exception> { BookArchive.parse(archive(root, entries), "book", root) }
    } }
    test("book identifier cannot escape cache") { inDirectory { root ->
        shouldThrow<IllegalArgumentException> { BookArchive.parse(archive(root, epub()), "../escape", root) }
    } }
})
