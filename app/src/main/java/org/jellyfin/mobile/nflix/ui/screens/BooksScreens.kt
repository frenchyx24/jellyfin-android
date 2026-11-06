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
import androidx.compose.material.icons.rounded.TextFields
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.BookArchive
import org.jellyfin.mobile.nflix.data.BookFile
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.koin.compose.koinInject
import kotlin.coroutines.coroutineContext

/** Local reading positions, so "Continue reading" works offline and instantly. */
internal object ReadingProgress {
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

private val bookMutex = Mutex()

private suspend fun downloadBook(context: Context, api: NflixApi, id: String, force: Boolean, onProgress: (Float) -> Unit): File = bookMutex.withLock {
    withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "Identifiant de livre invalide." }
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val target = File(dir, "$id.bin")
        if (!force && target.exists() && target.length() > 0) return@withContext target
        if (force) target.delete()
        val temp = File(dir, "$id.part")
        temp.delete()
        val request = Request.Builder().url(api.url("Items/$id/Download")).header("Authorization", api.authorization()).build()
        val client = api.client.newBuilder().callTimeout(5, TimeUnit.MINUTES).build()
        val call = client.newCall(request)
        kotlinx.coroutines.coroutineScope {
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Téléchargement impossible (${response.code}).")
                    val body = response.body ?: throw IOException("Fichier vide.")
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                read += count
                                if (total > 0) onProgress(read.toFloat() / total)
                            }
                        }
                    }
                    if (total >= 0 && read != total) throw IOException("Téléchargement incomplet.")
                }
            } catch (error: Exception) {
                temp.delete()
                throw error
            } finally {
                watcher.cancel()
            }
        }
        if (temp.length() == 0L) throw IOException("Fichier vide.")
        if (!temp.renameTo(target)) throw IOException("Impossible de conserver le livre téléchargé.")
        target
    }
}

private fun parseBook(file: File, id: String, cacheDir: File): BookFile = BookArchive.parse(file, id, cacheDir)

@Composable
fun ReaderScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val downloadDao: DownloadDao = koinInject()
    val storageManager: StorageManager = koinInject()
    val item = rememberLoad("reader-item", id, initial = deps.jellyfin.cachedItem(id)) { deps.jellyfin.item(id) }.value
    var progress by remember(id) { mutableFloatStateOf(0f) }
    var retry by remember(id) { mutableIntStateOf(0) }
    val book by produceState<Result<BookFile>?>(null, id, retry) {
        value = null
        progress = 0f
        value = try {
            val offlineFile = withContext(Dispatchers.IO) {
                runCatching {
                    val download = downloadDao.getDownloadByItemId(UUID.fromString(id)) ?: return@runCatching null
                    if (download.status != DownloadStatus.DOWNLOADED) return@runCatching null
                    val files = downloadDao.getFiles(download.id)
                    if (!storageManager.verify(DownloadFiles(download, files))) return@runCatching null
                    val uri = files.firstOrNull { it.type == DownloadFileType.ITEM }?.uri ?: return@runCatching null
                    storageManager.documentFor(uri)?.uri?.path?.let(::File)?.takeIf { it.isFile }
                }.getOrNull()
            }
            val file = offlineFile ?: downloadBook(context, deps.api, id, force = retry > 0) { progress = it }
            Result.success(withContext(Dispatchers.IO) { parseBook(file, id, context.cacheDir) })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            context.cacheDir.resolve("books/$id.bin").delete()
            Result.failure(error)
        }
    }
    var chrome by remember { mutableStateOf(true) }
    var prefs by remember { mutableStateOf(ReaderPrefs.load(context)) }
    var settings by remember { mutableStateOf(false) }
    val ink = if (prefs.theme.dark) Color.White else Color(0xFF1A1A1A)
    Box(Modifier.fillMaxSize().background(prefs.theme.background)) {
        when (val result = book) {
            null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress > 0f) CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Nflix.Red)
                else CircularProgressIndicator(color = Nflix.Red)
                Spacer(Modifier.height(12.dp))
                Text("Préparation du livre… ${(progress * 100).toInt()} %", color = ink.copy(alpha = 0.7f))
            }
            else -> result.fold(
                onSuccess = { file ->
                    when (file) {
                        is BookFile.Pdf -> PdfReader(id, file.file, night = prefs.theme.dark, onTap = { chrome = !chrome }, onFailure = { retry++ })
                        is BookFile.Epub -> EpubReader(id, file, prefs, { prefs = it }, chrome) { chrome = !chrome }
                        is BookFile.Comic -> ComicReader(id, file.pages) { chrome = !chrome }
                        is BookFile.Unsupported -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Format ${file.format} non pris en charge", "Ouvrez-le avec une autre application.")
                            Button(onClick = { item?.let { deps.openUrl(deps.jellyfin.downloadUrl(it)) } }, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red)) {
                                Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir ailleurs")
                            }
                        }
                    }
                },
                onFailure = { error ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Lecture impossible", error.message)
                        Button(onClick = { retry++ }) { Text("Réessayer") }
                    }
                },
            )
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(prefs.theme.background, prefs.theme.background.copy(alpha = 0f))))
                    .statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = ink) }
                Text(item?.name ?: "", style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // PDF and comics: the theme sets the page surround and the PDF night mode.
                if ((book?.getOrNull()) !is BookFile.Epub) IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.TextFields, "Affichage", tint = ink) }
            }
        }
        if (settings) ReaderSettingsSheet(prefs, { prefs = it }) { settings = false }
    }
}

@Composable
private fun PdfReader(id: String, file: File, night: Boolean, onTap: () -> Unit, onFailure: () -> Unit) {
    val context = LocalContext.current
    val opened = remember(file) {
        runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try { PdfRenderer(descriptor) } catch (error: Exception) { descriptor.close(); throw error }
        }
    }
    val renderer = opened.getOrNull()
    if (renderer == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "PDF illisible ou protégé", opened.exceptionOrNull()?.message)
            Button(onClick = onFailure) { Text("Télécharger à nouveau") }
        }
        return
    }
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
            val rendered by produceState<Result<Bitmap>?>(null, renderer, index) {
                value = try {
                    Result.success(withContext(Dispatchers.IO) {
                        lock.withLock {
                            renderer.openPage(index).use { page ->
                                val width = 1200
                                val height = (width.toFloat() / page.width * page.height).toInt().coerceIn(1, 6000)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    })
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            val image = rendered?.getOrNull()
            when {
                rendered == null -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(Nflix.Surface))
                image != null -> Image(
                    image.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                    // Night themes invert the page (white paper becomes dark, black ink becomes light).
                    colorFilter = if (night) InvertFilter else null,
                )
                else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Page ${index + 1} illisible", rendered?.exceptionOrNull()?.message)
                    Button(onClick = onFailure) { Text("Télécharger à nouveau") }
                }
            }
        }
    }
}

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

/** Night themes invert PDF pages: white paper turns dark grey, black ink turns light grey. */
private val InvertFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            -0.85f, 0f, 0f, 0f, 235f,
            0f, -0.85f, 0f, 0f, 235f,
            0f, 0f, -0.85f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
