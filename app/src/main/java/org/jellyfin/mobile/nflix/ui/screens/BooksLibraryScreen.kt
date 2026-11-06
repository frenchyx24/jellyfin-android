package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.nflix.data.Audiobook
import org.jellyfin.mobile.nflix.data.BookLibrary
import org.jellyfin.mobile.nflix.data.Ebook
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.data.Saga
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SectionHeader
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.formatTicks
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

/** Whole "Livres" library, organised once and shared by the shelf, saga and audiobook screens. */
internal data class BookShelves(
    val ebooks: List<Ebook>,
    val audiobooks: List<Audiobook>,
    val sagas: List<Saga>,
    val authors: List<Saga>,
)

private object BookShelvesCache {
    @Volatile var value: BookShelves? = null
    @Volatile var at = 0L
}

internal suspend fun loadBookShelves(deps: AppDeps, force: Boolean = false): BookShelves {
    BookShelvesCache.value?.takeIf { !force && System.currentTimeMillis() - BookShelvesCache.at < 60_000 }?.let { return it }
    val view = deps.jellyfin.views(force).firstOrNull { it.collectionType == "books" } ?: return BookShelves(emptyList(), emptyList(), emptyList(), emptyList())
    val fields = mapOf("fields" to "PrimaryImageAspectRatio,Overview,ProductionYear,ParentId,Path,ChildCount")
    val shelves = coroutineScope {
        val books = async { deps.jellyfin.items(parentId = view.id, includeTypes = "Book", sort = LibrarySort.Name, limit = 2000, extra = fields, force = force).items }
        val chapters = async { deps.jellyfin.items(parentId = view.id, includeTypes = "AudioBook", sort = LibrarySort.Name, limit = 10_000, extra = fields, force = force).items }
        val folders = async { deps.jellyfin.items(parentId = view.id, includeTypes = "Folder", sort = LibrarySort.Name, limit = 2000, extra = fields, force = force).items }
        val ebooks = BookLibrary.ebooks(books.await()) { BookLibrary.authorFromPath(it.path) }
        val audiobooks = BookLibrary.audiobooks(chapters.await(), folders.await().associateBy { it.id })
        val (sagas, authors) = BookLibrary.sagas(ebooks, audiobooks)
        BookShelves(ebooks, audiobooks, sagas, authors)
    }
    BookShelvesCache.value = shelves
    BookShelvesCache.at = System.currentTimeMillis()
    return shelves
}

@Composable
fun BooksScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var audio by rememberSaveable { mutableStateOf(false) }
    val load = rememberLoad("book-shelves") { force -> loadBookShelves(deps, force) }
    val shelves = load.value
    val suggestions = rememberLoad("horus-books") { runCatching { deps.horus.trending(HorusType.Book).results.take(15) }.getOrDefault(emptyList()) }.value.orEmpty()

    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item(key = "title") {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Livres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher") }
            }
        }
        item(key = "switch") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(CircleShape).background(Nflix.Surface).padding(4.dp)) {
                ModeTab("Livres", Icons.AutoMirrored.Rounded.MenuBook, shelves?.ebooks?.size, !audio, Modifier.weight(1f)) { audio = false }
                ModeTab("Livres audio", Icons.Rounded.Headphones, shelves?.audiobooks?.size, audio, Modifier.weight(1f)) { audio = true }
            }
        }
        when {
            load is Load.Failed -> item(key = "error") { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Bibliothèque indisponible", load.message) }
            shelves == null -> items(3, key = { "sk$it" }) { SkeletonRow(4, 110.dp) }
            else -> {
                val sagas = shelves.sagas.filter { if (audio) it.audiobooks.isNotEmpty() else it.ebooks.isNotEmpty() }
                val authors = shelves.authors.filter { if (audio) it.audiobooks.isNotEmpty() else it.ebooks.isNotEmpty() }
                if (sagas.isEmpty() && authors.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            if (audio) Icons.Rounded.Headphones else Icons.AutoMirrored.Rounded.MenuBook,
                            if (audio) "Aucun livre audio" else "Aucun livre",
                            "Demandez-en depuis l'onglet Horus.",
                        )
                    }
                }
                // Continue: started audiobooks, or e-books opened recently in the reader.
                val continueAudio = shelves.audiobooks.filter { it.started && !it.finished }.sortedByDescending { it.progress }
                val recent = ReadingProgress.recent(context)
                val continueBooks = recent.mapNotNull { id -> shelves.ebooks.firstOrNull { it.item.id == id } }
                if (audio && continueAudio.isNotEmpty()) item(key = "continue-audio") {
                    SectionHeader("Reprendre l'écoute")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueAudio, key = { "ca-${it.id}" }) { book -> ContinueAudioCard(book) }
                    }
                }
                if (!audio && continueBooks.isNotEmpty()) item(key = "continue-books") {
                    SectionHeader("Continuer la lecture")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueBooks, key = { "cb-${it.item.id}" }) { book -> ContinueBookCard(book) }
                    }
                }
                if (sagas.isNotEmpty()) item(key = "sagas-title") {
                    Text(
                        "Sagas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 20.dp, top = 26.dp),
                    )
                }
                sagas.forEach { saga -> item(key = "saga-${saga.name}-$audio") { Shelf(saga, audio) } }
                if (authors.isNotEmpty()) item(key = "authors-title") {
                    Text(
                        "Par auteur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 20.dp, top = 26.dp),
                    )
                }
                authors.forEach { shelf -> item(key = "author-${shelf.name}-$audio") { Shelf(shelf, audio) } }
                if (!audio && suggestions.isNotEmpty()) item(key = "horus") {
                    SectionHeader("À demander sur Horus", action = "Horus", onAction = { navigator.selectTab(Route.Horus) })
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(suggestions.distinctBy { it.id }, key = { "s-${it.id}" }) { media ->
                            HorusCard(media, { navigator.open(Route.HorusDetail(media.type, media.id)) }, width = 110.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(CircleShape).background(if (selected) Nflix.Red else Color.Transparent).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Color.White else Nflix.Muted)
        Spacer(Modifier.width(8.dp))
        Text(
            label + (count?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else Nflix.Muted,
        )
    }
}

@Composable
private fun Shelf(saga: Saga, audio: Boolean) {
    val navigator = LocalNavigator.current
    val count = if (audio) saga.audiobooks.size else saga.ebooks.size
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { navigator.open(Route.Saga(saga.name, audio)) }.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(saga.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (saga.authorShelf) null else saga.author,
                        when {
                            saga.authorShelf -> "$count titre(s) hors saga"
                            audio -> "$count livre(s) audio"
                            else -> "$count tome(s)"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Nflix.Muted,
                )
            }
            Text("Tout voir", style = MaterialTheme.typography.labelMedium, color = Nflix.Red)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (audio) items(saga.audiobooks, key = { it.id }) { AudiobookCover(it, showVolume = !saga.authorShelf) }
            else items(saga.ebooks, key = { it.item.id }) { EbookCover(it, showVolume = !saga.authorShelf) }
        }
    }
}

@Composable
private fun EbookCover(book: Ebook, showVolume: Boolean, width: androidx.compose.ui.unit.Dp = 112.dp) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val fraction = remember(book.item.id) { ReadingProgress.fraction(context, book.item.id) }
    Column(Modifier.width(width).pressable({ navigator.open(Route.Item(book.item.id, "book/${book.item.id}")) }, { org.jellyfin.mobile.nflix.ui.ItemMenu.show(book.item) })) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(6.dp))) {
            NImage(deps.jellyfin.primary(book.item, 300), Modifier.fillMaxSize(), description = book.item.name, placeholderIcon = Icons.AutoMirrored.Rounded.MenuBook)
            if (showVolume && book.volume != null) VolumeBadge("T${book.volume}", Modifier.align(Alignment.TopStart))
            if (fraction > 0f) ThinProgress(fraction, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(book.item.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudiobookCover(book: Audiobook, showVolume: Boolean, width: androidx.compose.ui.unit.Dp = 132.dp) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Column(Modifier.width(width).pressable({ navigator.open(Route.Audiobook(book.id)) })) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(10.dp))) {
            NImage(deps.jellyfin.primary(book.cover, 400), Modifier.fillMaxSize(), description = book.title, placeholderIcon = Icons.Rounded.Headphones)
            if (showVolume && book.volume != null) VolumeBadge("T${book.volume}", Modifier.align(Alignment.TopStart))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) { Icon(if (book.finished) Icons.Rounded.CheckCircle else Icons.Rounded.Headphones, null, Modifier.size(16.dp), tint = Color.White) }
            if (book.progress > 0f && !book.finished) ThinProgress(book.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(book.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(hours(book.durationTicks), "${book.chapters.size} ch.").joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1,
        )
    }
}

@Composable
private fun VolumeBadge(text: String, modifier: Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White,
        modifier = modifier.padding(6.dp).clip(RoundedCornerShape(5.dp)).background(Nflix.Red).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ThinProgress(progress: Float, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Nflix.Red))
    }
}

@Composable
private fun ContinueAudioCard(book: Audiobook) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Row(
        Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).pressable({ navigator.open(Route.Audiobook(book.id)) }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NImage(deps.jellyfin.primary(book.cover, 200), Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.Rounded.Headphones)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Chapitre ${book.resumeIndex + 1} sur ${book.chapters.size}", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(book.progress).background(Nflix.Red))
            }
            Text("${(book.progress * 100).toInt()} % écouté", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
        PlayResumeButton(book, 40.dp)
    }
}

@Composable
private fun ContinueBookCard(book: Ebook) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val fraction = remember(book.item.id) { ReadingProgress.fraction(context, book.item.id) }
    Row(
        Modifier.width(280.dp).clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).pressable({ navigator.open(Route.Reader(book.item.id)) }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NImage(deps.jellyfin.primary(book.item, 200), Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            book.saga?.let { Text(listOfNotNull(it, book.volume?.let { v -> "tome $v" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Red, maxLines = 1) }
            Text(book.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Nflix.Red))
            }
            Text("${(fraction * 100).toInt()} % lu", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

/** Plays an audiobook from its resume point, or toggles it when it is the one playing. */
@Composable
private fun PlayResumeButton(book: Audiobook, size: androidx.compose.ui.unit.Dp) {
    val deps = LocalDeps.current
    val state by deps.music.state.collectAsState()
    val playingThis = state.current?.id in book.chapters.map { it.id }
    Box(
        Modifier.size(size).clip(CircleShape).background(Nflix.Red).clickable {
            if (playingThis) deps.music.toggle() else startAudiobook(deps, book, book.resumeIndex)
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playingThis && state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playingThis && state.isPlaying) "Pause" else "Écouter", tint = Color.White)
    }
}

private fun startAudiobook(deps: AppDeps, book: Audiobook, index: Int) {
    val chapter = book.chapters.getOrNull(index)
    val resumeMs = chapter?.userData?.takeIf { it.played != true }?.positionTicks?.div(10_000) ?: 0L
    deps.music.play(book.chapters, startIndex = index, startPositionMs = resumeMs)
}

private fun hours(ticks: Long): String? {
    if (ticks <= 0) return null
    val minutes = ticks / 600_000_000L
    return if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}" else "$minutes min"
}

@Composable
fun SagaScreen(name: String, audio: Boolean) {
    val deps = LocalDeps.current
    val load = rememberLoad("book-shelves") { force -> loadBookShelves(deps, force) }
    val shelves = load.value
    val saga = shelves?.let { s -> (s.sagas + s.authors).firstOrNull { it.name == name } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 12.dp)) {
                    Text(if (saga?.authorShelf == true) "AUTEUR" else "SAGA", style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Bold)
                    Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    saga?.let {
                        Text(
                            listOfNotNull(it.author.takeIf { _ -> !it.authorShelf }, "${it.ebooks.size} livre(s)", "${it.audiobooks.size} livre(s) audio").joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted,
                        )
                        val allIds = (it.ebooks.map { book -> book.item.id } + it.audiobooks.flatMap { book -> book.chapters.map { chapter -> chapter.id } }).distinct()
                        if (allIds.isNotEmpty()) {
                            Button(
                                onClick = { deps.downloadItems(allIds) },
                                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            ) {
                                Icon(Icons.Rounded.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Télécharger toute la collection · ${allIds.size}")
                            }
                        }
                    }
                }
            }
            when {
                shelves == null -> items(4, key = { "sk$it" }) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(120.dp)) }
                saga == null -> item(key = "missing") { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Saga introuvable", null) }
                else -> {
                    // Requested format first, then the other one, so both editions stay one tap away.
                    val ebooks = @Composable { saga.ebooks.forEach { VolumeRowEbook(it, showVolume = !saga.authorShelf) } }
                    val audios = @Composable { saga.audiobooks.forEach { VolumeRowAudio(it, showVolume = !saga.authorShelf) } }
                    val first = if (audio) "Livres audio" to audios else "Livres" to ebooks
                    val second = if (audio) "Livres" to ebooks else "Livres audio" to audios
                    val firstCount = if (audio) saga.audiobooks.size else saga.ebooks.size
                    val secondCount = if (audio) saga.ebooks.size else saga.audiobooks.size
                    if (firstCount > 0) item(key = "first") { Column { SectionHeader(first.first); first.second() } }
                    if (secondCount > 0) item(key = "second") { Column { SectionHeader(second.first); second.second() } }
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun VolumeRowEbook(book: Ebook, showVolume: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val fraction = remember(book.item.id) { ReadingProgress.fraction(context, book.item.id) }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
            .clickable { navigator.open(Route.Item(book.item.id, "book/${book.item.id}")) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NImage(deps.jellyfin.primary(book.item, 240), Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (showVolume && book.volume != null) Text("Tome ${book.volume}", style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Bold)
            Text(book.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.item.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted) }
            if (fraction > 0f) Text("${(fraction * 100).toInt()} % lu", style = MaterialTheme.typography.labelSmall, color = Nflix.Success)
        }
        IconButton(onClick = { navigator.open(Route.Reader(book.item.id)) }) { Icon(Icons.AutoMirrored.Rounded.MenuBook, "Lire", tint = Nflix.Text) }
    }
}

@Composable
private fun VolumeRowAudio(book: Audiobook, showVolume: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
            .clickable { navigator.open(Route.Audiobook(book.id)) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NImage(deps.jellyfin.primary(book.cover, 240), Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.Rounded.Headphones)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (showVolume && book.volume != null) Text("Tome ${book.volume}", style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Bold)
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(book.narrators, hours(book.durationTicks)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.started) Text(if (book.finished) "Terminé" else "${(book.progress * 100).toInt()} % écouté", style = MaterialTheme.typography.labelSmall, color = Nflix.Success)
        }
        PlayResumeButton(book, 40.dp)
    }
}

@Composable
fun AudiobookScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val load = rememberLoad("book-shelves", id) { force -> loadBookShelves(deps, force) }
    val book = load.value?.audiobooks?.firstOrNull { it.id == id }
    val state by deps.music.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when {
            load is Load.Failed -> EmptyState(Icons.Rounded.Headphones, "Livre audio indisponible", load.message, Modifier.align(Alignment.Center))
            load.value == null -> Skeleton(Modifier.fillMaxWidth().height(420.dp), RoundedCornerShape(0.dp))
            book == null -> EmptyState(Icons.Rounded.Headphones, "Livre audio introuvable", null, Modifier.align(Alignment.Center))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        NImage(deps.jellyfin.primary(book.cover, 600), Modifier.fillMaxSize().blur(40.dp), placeholderIcon = null)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Nflix.Night))))
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            NImage(
                                deps.jellyfin.primary(book.cover, 600),
                                Modifier.size(210.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(14.dp)),
                                placeholderIcon = Icons.Rounded.Headphones,
                            )
                            Spacer(Modifier.height(16.dp))
                            book.saga?.let { Text(listOfNotNull(it, book.volume?.let { v -> "tome $v" }).joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Bold) }
                            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            book.author?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Nflix.Text.copy(alpha = 0.9f)) }
                            book.narrators?.let { Text("Lu par $it", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, textAlign = TextAlign.Center) }
                        }
                    }
                }
                item(key = "actions") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            listOfNotNull(hours(book.durationTicks), "${book.chapters.size} chapitres", if (book.started) "${(book.progress * 100).toInt()} % écouté" else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = Nflix.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(book.progress).background(Nflix.Red))
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { startAudiobook(deps, book, if (book.finished) 0 else book.resumeIndex) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    book.finished -> "Réécouter"
                                    book.started -> "Reprendre · chapitre ${book.resumeIndex + 1}"
                                    else -> "Écouter"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                item(key = "chapters-title") { SectionHeader("Chapitres") }
                itemsIndexed(book.chapters, key = { _, c -> c.id }) { index, chapter ->
                    val current = state.current?.id == chapter.id
                    val played = chapter.userData?.played == true
                    val partial = (chapter.userData?.positionTicks ?: 0) > 0 && !played
                    Row(
                        Modifier.fillMaxWidth().clickable { startAudiobook(deps, book, index) }
                            .background(if (current) Nflix.Red.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}", style = MaterialTheme.typography.labelLarge, color = if (current) Nflix.Red else Nflix.Muted,
                            modifier = Modifier.width(34.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(chapter.name, style = MaterialTheme.typography.bodyMedium, color = if (current) Nflix.Red else if (played) Nflix.Muted else Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (partial) {
                                val length = chapter.runTimeTicks ?: 0L
                                val fraction = if (length > 0) (chapter.userData!!.positionTicks.toFloat() / length).coerceIn(0f, 1f) else 0f
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Nflix.Red))
                                }
                            }
                        }
                        if (played) Icon(Icons.Rounded.CheckCircle, "Écouté", Modifier.size(18.dp), tint = Nflix.Success)
                        else chapter.runTimeTicks?.let { Text(formatTicks(it), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted) }
                    }
                }
                item(key = "now") {
                    if (state.current != null) {
                        Text(
                            "Lecture dans le lecteur audio de Nflix, même écran éteint.", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted,
                            modifier = Modifier.fillMaxWidth().padding(20.dp).clickable { navigator.open(Route.NowPlaying) }, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        BackButton()
    }
}
