package org.jellyfin.mobile.nflix.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Local reading positions, so "Continue reading" works offline and instantly. */
private object ReadingProgress {
    private fun prefs(context: Context) = context.getSharedPreferences("nflix_reading", Context.MODE_PRIVATE)
    fun position(context: Context, id: String) = prefs(context).getInt("pos-$id", 0)
    fun fraction(context: Context, id: String) = prefs(context).getFloat("frac-$id", 0f)
    fun save(context: Context, id: String, position: Int, fraction: Float) = prefs(context).edit {
        putInt("pos-$id", position)
        putFloat("frac-$id", fraction)
        putLong("at-$id", System.currentTimeMillis())
        val recent = (prefs(context).getString("recent", "") ?: "").split(',').filter { it.isNotBlank() && it != id }
        putString("recent", (listOf(id) + recent).take(20).joinToString(","))
    }
    fun recent(context: Context) = (prefs(context).getString("recent", "") ?: "").split(',').filter { it.isNotBlank() }
}

@Composable
fun BooksScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val views = rememberLoad("views") { deps.jellyfin.views(it) }.value.orEmpty()
    val books = views.firstOrNull { it.collectionType == "books" }
    val recentIds = remember { ReadingProgress.recent(context) }
    val reading = rememberLoad("reading", recentIds) {
        if (recentIds.isEmpty()) emptyList() else deps.jellyfin.items(ids = recentIds, limit = recentIds.size, extra = mapOf("sortBy" to null)).items
            .sortedBy { recentIds.indexOf(it.id) }
    }.value.orEmpty()
    val suggestions = rememberLoad("horus-books") { runCatching { deps.horus.trending(HorusType.Book).results.take(15) }.getOrDefault(emptyList()) }.value.orEmpty()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Livres")
        if (books == null) {
            if (views.isNotEmpty()) EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Pas de bibliothèque de livres", null)
            return@Column
        }
        BrowseGrid(
            viewId = books.id, types = "Book,AudioBook", key = "books-${books.id}",
            header = {
                if (reading.isNotEmpty()) {
                    item(key = "reading", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Continuer la lecture", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(reading.distinctBy { it.id }, key = { "r-${it.id}" }) { book -> ReadingCard(book) }
                            }
                        }
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item(key = "horus-books", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                                Text("À demander sur Horus", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { navigator.selectTab(Route.Horus) }) { Icon(Icons.Rounded.TravelExplore, "Horus", tint = Nflix.Muted) }
                            }
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(suggestions.distinctBy { it.id }, key = { "s-${it.id}" }) { media -> HorusCard(media, { navigator.open(Route.HorusDetail(media.type, media.id)) }, width = 110.dp) }
                            }
                        }
                    }
                }
                item(key = "all-title", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Bibliothèque", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                }
            },
            onItemClick = { item, origin -> navigator.open(Route.Item(item.id, origin)) },
        )
    }
}

@Composable
private fun ReadingCard(book: Item) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val fraction = remember(book.id) { ReadingProgress.fraction(context, book.id) }
    Row(
        Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).pressable({ navigator.open(Route.Reader(book.id)) }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NImage(deps.jellyfin.primary(book, 200), Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(book.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction }, color = Nflix.Red, trackColor = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

private sealed interface BookFile {
    data class Pdf(val file: File) : BookFile
    data class Epub(val root: File, val chapters: List<File>) : BookFile
    data class Comic(val pages: List<File>) : BookFile
    data class Unsupported(val format: String) : BookFile
}

private val bookMutex = Mutex()

private suspend fun downloadBook(context: Context, api: NflixApi, id: String, onProgress: (Float) -> Unit): File = bookMutex.withLock {
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val target = File(dir, "$id.bin")
        if (target.exists() && target.length() > 0) return@withContext target
        val temp = File(dir, "$id.part")
        val request = Request.Builder().url(api.url("Items/$id/Download")).header("Authorization", api.authorization()).build()
        api.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Téléchargement impossible (${response.code}).")
            val body = response.body ?: throw java.io.IOException("Fichier vide.")
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        temp.renameTo(target)
        target
    }
}

private fun parseBook(file: File, id: String, cacheDir: File): BookFile {
    val header = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
    if (header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte()) return BookFile.Pdf(file)
    if (header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) return BookFile.Unsupported(if (header[0] == 'R'.code.toByte()) "CBR" else "inconnu")
    val out = File(cacheDir, "books/$id-x")
    ZipFile(file).use { zip ->
        if (!out.exists()) {
            out.mkdirs()
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val dest = File(out, entry.name).canonicalFile
                if (!dest.path.startsWith(out.canonicalPath)) return@forEach // zip slip guard
                dest.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }
    val container = File(out, "META-INF/container.xml")
    if (container.exists()) {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val opfPath = (factory.newDocumentBuilder().parse(container).getElementsByTagName("rootfile").item(0) as Element).getAttribute("full-path")
        val opf = File(out, opfPath)
        val doc = factory.newDocumentBuilder().parse(opf)
        val manifest = mutableMapOf<String, String>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            manifest[el.getAttribute("id")] = el.getAttribute("href")
        }
        val spine = doc.getElementsByTagName("itemref")
        val chapters = (0 until spine.length).mapNotNull { i ->
            manifest[(spine.item(i) as Element).getAttribute("idref")]?.let { href -> File(opf.parentFile, java.net.URLDecoder.decode(href, "UTF-8")) }
        }.filter { it.exists() }
        return BookFile.Epub(out, chapters)
    }
    val pages = out.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
        .sortedBy { it.path.lowercase() }.toList()
    return if (pages.isEmpty()) BookFile.Unsupported("ZIP") else BookFile.Comic(pages)
}

@Composable
fun ReaderScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val item = rememberLoad("reader-item", id, initial = deps.jellyfin.cachedItem(id)) { deps.jellyfin.item(id) }.value
    var progress by remember { mutableFloatStateOf(0f) }
    val book by produceState<Result<BookFile>?>(null, id) {
        value = runCatching {
            val file = downloadBook(context, deps.api, id) { progress = it }
            withContext(Dispatchers.IO) { parseBook(file, id, context.cacheDir) }
        }
    }
    var chrome by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        when (val result = book) {
            null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = Nflix.Red)
                Spacer(Modifier.height(12.dp))
                Text("Préparation du livre… ${(progress * 100).toInt()} %", color = Nflix.Muted)
            }
            else -> result.fold(
                onSuccess = { file ->
                    when (file) {
                        is BookFile.Pdf -> PdfReader(id, file.file) { chrome = !chrome }
                        is BookFile.Epub -> EpubReader(id, file) { chrome = !chrome }
                        is BookFile.Comic -> ComicReader(id, file.pages) { chrome = !chrome }
                        is BookFile.Unsupported -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Format ${file.format} non pris en charge", "Ouvrez-le avec une autre application.")
                            Button(onClick = { item?.let { deps.openUrl(deps.jellyfin.downloadUrl(it)) } }, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red)) {
                                Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir ailleurs")
                            }
                        }
                    }
                },
                onFailure = { error -> EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Lecture impossible", error.message, Modifier.align(Alignment.Center)) },
            )
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Text(item?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PdfReader(id: String, file: File, onTap: () -> Unit) {
    val context = LocalContext.current
    val renderer = remember(file) { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
    val lock = remember { Mutex() }
    DisposableEffect(renderer) { onDispose { runCatching { renderer.close() } } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = ReadingProgress.position(context, id).coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0)))
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect { page ->
            ReadingProgress.save(context, id, page, (page + 1f) / renderer.pageCount.coerceAtLeast(1))
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 60.dp),
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { scale = if (scale > 1f) 1f else 2f }) }
            .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) } }
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        items(renderer.pageCount, key = { it }) { index ->
            val bitmap by produceState<Bitmap?>(null, index) {
                value = withContext(Dispatchers.IO) {
                    lock.withLock {
                        renderer.openPage(index).use { page ->
                            val width = 1200
                            val height = (width.toFloat() / page.width * page.height).toInt()
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
            val image = bitmap
            if (image == null) Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(Nflix.Surface))
            else Image(image.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubReader(id: String, book: BookFile.Epub, onTap: () -> Unit) {
    val context = LocalContext.current
    var chapter by remember { mutableIntStateOf(ReadingProgress.position(context, id).coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))) }
    var fontSize by remember { mutableIntStateOf(context.getSharedPreferences("nflix_reading", Context.MODE_PRIVATE).getInt("font", 110)) }
    LaunchedEffect(chapter) { ReadingProgress.save(context, id, chapter, (chapter + 1f) / book.chapters.size.coerceAtLeast(1)) }
    if (book.chapters.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Livre vide", null)
        return
    }
    val css = "html,body{background:#0B0B0F!important;color:#E8E8EE!important;font-family:serif;line-height:1.65;font-size:$fontSize%;}" +
        "body{padding:64px 22px 110px 22px!important;margin:0!important;max-width:760px;margin:auto!important}" +
        "img,svg{max-width:100%!important;height:auto!important}a{color:#ff5a66}*{background-color:transparent!important;color:inherit}"
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#0B0B0F"))
                    settings.allowFileAccess = true
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(
                                "(function(){var s=document.getElementById('nflix-css')||document.createElement('style');s.id='nflix-css';s.innerHTML=${org.json.JSONObject.quote(css)};document.head.appendChild(s);})();",
                                null,
                            )
                        }
                    }
                    setOnClickListener { onTap() }
                }
            },
            update = { view ->
                val url = "file://" + book.chapters[chapter].absolutePath
                if (view.url != url) view.loadUrl(url) else view.evaluateJavascript(
                    "(function(){var s=document.getElementById('nflix-css');if(s){s.innerHTML=${org.json.JSONObject.quote(css)};}})();", null,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(color = Nflix.SurfaceHigh.copy(alpha = 0.96f), shape = RoundedCornerShape(24.dp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp)) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (chapter > 0) chapter-- }, enabled = chapter > 0) { Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "Chapitre précédent") }
                Text("${chapter + 1} / ${book.chapters.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { if (chapter < book.chapters.lastIndex) chapter++ }, enabled = chapter < book.chapters.lastIndex) { Icon(Icons.AutoMirrored.Rounded.NavigateNext, "Chapitre suivant") }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { fontSize = (fontSize - 10).coerceAtLeast(70); saveFont(context, fontSize) }) { Icon(Icons.Rounded.Remove, "Plus petit") }
                IconButton(onClick = { fontSize = (fontSize + 10).coerceAtMost(200); saveFont(context, fontSize) }) { Icon(Icons.Rounded.Add, "Plus grand") }
            }
        }
    }
}

private fun saveFont(context: Context, size: Int) = context.getSharedPreferences("nflix_reading", Context.MODE_PRIVATE).edit { putInt("font", size) }

@Composable
private fun ComicReader(id: String, pages: List<File>, onTap: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = ReadingProgress.position(context, id).coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { pages.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { ReadingProgress.save(context, id, it, (it + 1f) / pages.size.coerceAtLeast(1)) }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, beyondViewportPageCount = 1) { page ->
            coil3.compose.AsyncImage(model = pages[page], contentDescription = "Page ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(
            "${pager.currentPage + 1} / ${pages.size}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Suppress("unused")
private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.clickable(onClick = onClick)
@Suppress("unused")
private val sizeHelper = Modifier.size(1.dp)
