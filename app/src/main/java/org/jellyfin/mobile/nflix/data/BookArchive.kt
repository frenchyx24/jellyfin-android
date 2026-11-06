package org.jellyfin.mobile.nflix.data

import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import java.util.concurrent.ConcurrentHashMap

internal sealed interface BookFile {
    data class Pdf(val file: File) : BookFile
    data class Epub(val root: File, val chapters: List<File>) : BookFile
    data class Comic(val pages: List<File>) : BookFile
    data class Unsupported(val format: String) : BookFile
}

internal object BookArchive {
    private val locks = ConcurrentHashMap<String, Any>()
    private const val MAX_ENTRIES = 20_000
    private const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024

    fun parse(file: File, id: String, cacheDir: File): BookFile = locks.computeIfAbsent("${cacheDir.absolutePath}:$id") { Any() }.let { lock ->
        synchronized(lock) { parseLocked(file, id, cacheDir) }
    }

    private fun parseLocked(file: File, id: String, cacheDir: File): BookFile {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "Identifiant de livre invalide." }
        val header = ByteArray(5)
        val headerSize = file.inputStream().use { input ->
            var count = 0
            while (count < header.size) {
                val read = input.read(header, count, header.size - count)
                if (read < 0) break
                count += read
            }
            count
        }
        if (headerSize == 5 && header.contentEquals("%PDF-".toByteArray())) return BookFile.Pdf(file)
        if (headerSize < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
            return BookFile.Unsupported(if (header.firstOrNull() == 'R'.code.toByte()) "CBR" else "inconnu")
        }
        // Android's cache dir is reached through a symlink (/data/user/0 → /data/data); every containment check
        // compares canonical paths, so start from the canonical directory.
        val cache = cacheDir.canonicalFile
        val root = File(cache, "books/$id-x")
        val staging = File(cache, "books/$id-extract")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Impossible de préparer le livre.")
        try {
            ZipFile(file).use { zip ->
                var count = 0
                var expanded = 0L
                zip.entries().asSequence().forEach { entry ->
                    if (++count > MAX_ENTRIES) throw IOException("Archive trop volumineuse.")
                    val destination = inside(staging, entry.name)
                    if (!entry.isDirectory) {
                        destination.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val bytes = input.read(buffer)
                                    if (bytes < 0) break
                                    expanded += bytes
                                    if (expanded > MAX_EXPANDED_BYTES) throw IOException("Archive trop volumineuse.")
                                    output.write(buffer, 0, bytes)
                                }
                            }
                        }
                    }
                }
            }
            root.deleteRecursively()
            if (!staging.renameTo(root)) throw IOException("Impossible de préparer le livre.")
            val container = File(root, "META-INF/container.xml")
            if (container.isFile) {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
                    isExpandEntityReferences = false
                }
                val builder = factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
                }
                val containerDoc = try {
                    builder.parse(container)
                } catch (error: Exception) {
                    if (error.cause is javax.xml.parsers.ParserConfigurationException) throw error
                    throw IOException("Conteneur EPUB invalide.", error)
                }
                val rootfile = containerDoc.getElementsByTagNameNS("*", "rootfile").item(0) as? Element
                    ?: throw IOException("Métadonnées EPUB absentes.")
                val opfPath = rootfile.getAttribute("full-path")
                val opf = inside(root, opfPath)
                val opfDir = opfPath.substringBeforeLast('/', "")
                val document = builder.parse(opf)
                val manifest = mutableMapOf<String, String>()
                val items = document.getElementsByTagNameNS("*", "item")
                for (i in 0 until items.length) {
                    val element = items.item(i) as Element
                    manifest[element.getAttribute("id")] = element.getAttribute("href")
                }
                val spine = document.getElementsByTagNameNS("*", "itemref")
                val chapters = (0 until spine.length).map { i ->
                    val reference = (spine.item(i) as Element).getAttribute("idref")
                    val href = manifest[reference] ?: throw IOException("Chapitre EPUB absent : $reference.")
                    val path = URLDecoder.decode(href.substringBefore('#').substringBefore('?').replace("+", "%2B"), "UTF-8")
                    inside(root, if (opfDir.isEmpty()) path else "$opfDir/$path").also {
                        if (!it.isFile) throw IOException("Fichier de chapitre EPUB absent.")
                    }
                }
                if (chapters.isEmpty()) throw IOException("Ce livre ne contient aucun chapitre.")
                return BookFile.Epub(root, chapters)
            }
            val pages = root.walkTopDown().filter {
                it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
            }.sortedWith(compareBy<File> { naturalKey(it.relativeTo(root).path) }).toList()
            return if (pages.isEmpty()) BookFile.Unsupported("ZIP") else BookFile.Comic(pages)
        } catch (error: Exception) {
            staging.deleteRecursively()
            root.deleteRecursively()
            throw error
        }
    }

    /**
     * Resolves an archive-relative [path] under [root] lexically. Extraction only writes regular files, so no
     * symlink can redirect a path; comparing strings avoids depending on how Android canonicalizes its cache dir.
     */
    internal fun inside(root: File, path: String): File {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains('\u0000')) throw IOException("Chemin de livre non autorisé.")
        val parts = ArrayDeque<String>()
        normalized.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) throw IOException("Chemin de livre non autorisé.") else parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        if (parts.isEmpty()) throw IOException("Chemin de livre non autorisé.")
        return File(root, parts.joinToString(File.separator))
    }

    private fun naturalKey(path: String): String = Regex("\\d+").replace(path.lowercase()) {
        it.value.trimStart('0').ifEmpty { "0" }.let { digits -> "${digits.length.toString().padStart(10, '0')}:$digits" }
    }
}
