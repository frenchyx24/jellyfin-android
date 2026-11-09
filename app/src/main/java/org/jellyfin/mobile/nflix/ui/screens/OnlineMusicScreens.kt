package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.mobile.nflix.data.NflixHttpException
import org.jellyfin.mobile.nflix.data.OnlineAlbum
import org.jellyfin.mobile.nflix.data.OnlineArtist
import org.jellyfin.mobile.nflix.data.OnlineTrack
import org.jellyfin.mobile.nflix.data.VideoCatalog
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.ItemMenu
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SectionHeader
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

/**
 * Library state of online tracks, shared by every screen: queued/running/done, with the Jellyfin
 * item once the scan picked the file up. Downloads run on the server; the app only polls.
 */
object OnlineLibrary {
    private val states = mutableStateMapOf<String, String>()
    private val items = mutableStateMapOf<String, String>()
    var canDownload by mutableStateOf(false)
        private set

    fun state(id: String): String = states[id] ?: "idle"
    fun itemId(id: String): String? = items[id]

    fun seed(tracks: List<OnlineTrack>) {
        tracks.forEach { t ->
            if (states[t.id] == null || t.state == "done") states[t.id] = t.state
            t.itemId?.let { items[t.id] = it }
        }
    }

    /** Fresh states for [ids]; keeps polling while some of them are still in the queue. */
    suspend fun refresh(deps: AppDeps, ids: List<String>) {
        var rounds = 0
        while (rounds++ < 240) {
            val (fresh, can) = runCatching { deps.online.states(ids) }.getOrNull() ?: return
            canDownload = can
            fresh.forEach { (id, s) ->
                val before = states[id]
                states[id] = s.state
                s.itemId?.let { items[id] = it }
                if (before == "running" && s.state == "failed") ItemMenu.message = s.message.ifBlank { "Ajout impossible" }
            }
            if (fresh.values.none { it.state == "queued" || it.state == "running" || (it.state == "done" && it.itemId == null) }) return
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return
            delay(if (fresh.values.any { it.state == "running" }) 3_000 else 6_000)
        }
    }

    /** Queues tracks (or a whole album) and follows them until they are in the library. */
    suspend fun add(deps: AppDeps, tracks: List<OnlineTrack>, albumId: String? = null) {
        val pending = tracks.filter { state(it.id) == "idle" || state(it.id) == "failed" }
        if (pending.isEmpty() && albumId == null) {
            ItemMenu.message = "Déjà dans la bibliothèque"
            return
        }
        pending.forEach { states[it.id] = "queued" }
        val queued = runCatching { deps.online.download(if (albumId != null) emptyList() else pending.map { it.id }, albumId) }
        queued.onFailure { error ->
            pending.forEach { states[it.id] = "idle" }
            ItemMenu.message = when ((error as? NflixHttpException)?.code) {
                403 -> "Ajout à la bibliothèque réservé aux administrateurs."
                412 -> "Aucun dossier musique configuré sur le serveur."
                else -> "Ajout impossible, réessayez."
            }
            return
        }
        val count = queued.getOrDefault(0)
        ItemMenu.message = when {
            count == 0 -> "Déjà dans la bibliothèque"
            count == 1 -> "Ajout à la bibliothèque en cours…"
            else -> "$count titres en cours d'ajout à la bibliothèque…"
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { refresh(deps, tracks.map { it.id }) }
    }
}

private fun formatDuration(seconds: Int): String = if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)

/** Tracks with their library item when they already are on Nflix (plays the local file). */
internal fun List<OnlineTrack>.withLibrary(): List<OnlineTrack> = map { t -> OnlineLibrary.itemId(t.id)?.let { t.copy(itemId = it) } ?: t }

@Composable
internal fun OnlineAddButton(track: OnlineTrack, modifier: Modifier = Modifier) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val state = OnlineLibrary.state(track.id)
    Box(
        modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = state == "idle" || state == "failed") { scope.launch { OnlineLibrary.add(deps, listOf(track)) } },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            "queued" -> Icon(Icons.Rounded.Schedule, "En attente", tint = Nflix.Muted, modifier = Modifier.size(20.dp))
            "running" -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Nflix.Red)
            "done" -> Icon(Icons.Rounded.CheckCircle, "Dans la bibliothèque", tint = Nflix.Success, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Rounded.LibraryAdd, "Ajouter à la bibliothèque", tint = Nflix.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnlineTrackRow(track: OnlineTrack, number: Int?, showCover: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, status: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showCover) {
            NImage(track.cover.ifBlank { null }, Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)), placeholderIcon = Icons.Rounded.MusicNote)
        } else if (number != null) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted, modifier = Modifier.width(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(track.artist, if (showCover) track.album else "").filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(track.duration), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        if (status == null) OnlineAddButton(track)
        else Text(status, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
    }
}

@Composable
internal fun OnlineAlbumCard(album: OnlineAlbum, width: androidx.compose.ui.unit.Dp = 140.dp, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Column(modifier.width(width).clip(RoundedCornerShape(8.dp)).clickable { navigator.open(Route.OnlineAlbum(album.id)) }) {
        NImage(album.cover.ifBlank { null }, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.Rounded.Album)
        Text(album.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(listOf(album.typeLabel, album.year).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
    }
}

@Composable
internal fun OnlineArtistBubble(artist: OnlineArtist, size: androidx.compose.ui.unit.Dp = 96.dp) {
    val navigator = LocalNavigator.current
    Column(
        Modifier.width(size + 8.dp).clip(RoundedCornerShape(12.dp)).clickable { navigator.open(Route.OnlineArtist(artist.id, artist.name)) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NImage(artist.picture.ifBlank { null }, Modifier.size(size).clip(CircleShape), placeholderIcon = Icons.Rounded.Person)
        Text(artist.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Long-press sheet of an online track: artist, album, library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineTrackMenu(track: OnlineTrack?, onDismiss: () -> Unit) {
    if (track == null) return
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Nflix.SurfaceHigh, contentColor = Nflix.Text) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NImage(track.cover.ifBlank { null }, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), placeholderIcon = Icons.Rounded.MusicNote)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (track.artistId.isNotBlank()) MenuRow(Icons.Rounded.Person, "Voir l'artiste") { onDismiss(); navigator.open(Route.OnlineArtist(track.artistId, track.artist)) }
            if (track.albumId.isNotBlank()) MenuRow(Icons.Rounded.Album, "Voir l'album") { onDismiss(); navigator.open(Route.OnlineAlbum(track.albumId)) }
            val state = OnlineLibrary.state(track.id)
            if (state == "idle" || state == "failed") {
                MenuRow(Icons.Rounded.LibraryAdd, "Ajouter à la bibliothèque") { onDismiss(); scope.launch { OnlineLibrary.add(deps, listOf(track)) } }
            }
            if (track.albumId.isNotBlank()) {
                MenuRow(Icons.Rounded.LibraryMusic, "Ajouter tout l'album") { onDismiss(); scope.launch { OnlineLibrary.add(deps, listOf(track), albumId = track.albumId) } }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = Nflix.Red, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (primary) Nflix.Red else Nflix.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else Nflix.Muted, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Nflix.Muted, fontWeight = FontWeight.SemiBold)
    }
}

/** Library artist and actor sharing this name (one call each, exact name match only). */
internal data class NameMatches(val artistId: String?, val personId: String?)

internal suspend fun findNameMatches(deps: AppDeps, name: String): NameMatches {
    val wanted = VideoCatalog.normalize(name)
    fun pick(root: Any?): String? = ((root as? JsonObject)?.get("Items") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .firstOrNull { VideoCatalog.normalize((it["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty()) == wanted }
        ?.let { (it["Id"] as? JsonPrimitive)?.contentOrNull }
    val artist = runCatching { pick(deps.api.getJson("Artists", mapOf("searchTerm" to name, "limit" to 10, "userId" to deps.api.userId))) }.getOrNull()
    val person = runCatching { pick(deps.api.getJson("Persons", mapOf("searchTerm" to name, "limit" to 10, "userId" to deps.api.userId))) }.getOrNull()
    // A person only counts when Nflix has films or series with them.
    val personWithTitles = person?.takeIf {
        runCatching { deps.jellyfin.items(includeTypes = "Movie,Series", limit = 1, personId = it).total > 0 }.getOrDefault(false)
    }
    return NameMatches(artist, personWithTitles)
}

/** "Aussi acteur" / "Aussi sur Nflix" links between the music artist and the person pages. */
@Composable
internal fun CrossLinks(name: String, showArtist: Boolean, showPerson: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val matches = rememberLoad("name-matches", name) { findNameMatches(deps, name) }.value ?: return
    val artistId = matches.artistId.takeIf { showArtist }
    val personId = matches.personId.takeIf { showPerson }
    if (artistId == null && personId == null) return
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (artistId != null) PillButton(Icons.Rounded.LibraryMusic, "Ses titres sur Nflix") { navigator.open(Route.Artist(artistId)) }
        if (personId != null) PillButton(Icons.Rounded.Movie, "Aussi acteur · films et séries") { navigator.open(Route.Person(personId, name)) }
    }
}

/** Requests the artist's whole discography through Horus. */
@Composable
internal fun DiscographyRequestButton(artistName: String) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var busy by remember(artistName) { mutableStateOf(false) }
    PillButton(Icons.Rounded.CloudDownload, if (busy) "Demande en cours…" else "Demander la discographie", enabled = !busy) {
        busy = true
        scope.launch {
            runCatching { deps.horus.requestDiscography(artistName) }
                .onSuccess { (requested, handled, total) ->
                    ItemMenu.message = when {
                        total == 0 -> "Aucun album trouvé pour cet artiste"
                        requested == 0 -> "Toute la discographie est déjà demandée ou disponible"
                        else -> "$requested album(s) demandé(s)" + if (handled > 0) " · $handled déjà suivis" else ""
                    }
                }
                .onFailure { ItemMenu.message = it.message ?: "Demande impossible" }
            busy = false
        }
    }
}

/** Online artist page: picture, Nflix links, top tracks, full discography, similar artists. */
@Composable
fun OnlineArtistScreen(id: String?, name: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad("online-artist", id, name) { if (!id.isNullOrBlank()) deps.online.artist(id) else deps.online.artistByName(name) }
    val page = load.value
    var menu by remember { mutableStateOf<OnlineTrack?>(null) }
    LaunchedEffect(page) {
        page ?: return@LaunchedEffect
        OnlineLibrary.seed(page.top)
        OnlineLibrary.refresh(deps, page.top.map { it.id })
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                val artistName = page?.artist?.name ?: name
                CollectionHeader(
                    title = artistName,
                    subtitle = page?.let { p -> listOfNotNull(p.albums.size.takeIf { it > 0 }?.let { "$it sorties" }, p.artist.fans.takeIf { it > 0 }?.let { "%,d fans".format(it).replace(',', ' ') }).joinToString(" · ") },
                    image = page?.artist?.picture?.ifBlank { null },
                    origin = "online-artist/${id ?: name}",
                    round = true,
                    kind = "Artiste",
                    onPlay = { page?.top?.takeIf { it.isNotEmpty() }?.let { deps.playOnline(it.withLibrary(), 0) } },
                    onShuffle = { page?.top?.takeIf { it.isNotEmpty() }?.let { deps.playOnline(it.withLibrary().shuffled(), 0) } },
                )
            }
            item(key = "links") {
                val artistName = page?.artist?.name ?: name
                CrossLinks(artistName, showArtist = true, showPerson = true)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiscographyRequestButton(artistName)
                }
            }
            when {
                page == null && load is org.jellyfin.mobile.nflix.ui.Load.Failed -> item(key = "error") {
                    EmptyState(Icons.Rounded.MusicNote, "Artiste indisponible", "Le catalogue en ligne ne répond pas.")
                }
                page == null -> items(6, key = { "sk$it" }) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(56.dp)) }
                else -> {
                    if (page.top.isNotEmpty()) {
                        item(key = "top-title") { SectionHeader("Titres populaires") }
                        items(page.top.size, key = { "top-${page.top[it].id}" }) { index ->
                            val track = page.top[index]
                            OnlineTrackRow(track, index + 1, showCover = true, onClick = { deps.playOnline(page.top.withLibrary(), index) }, onLongClick = { menu = track })
                        }
                        if (page.top.size > 1) item(key = "add-top") {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                PillButton(Icons.Rounded.LibraryAdd, "Ajouter ces titres à la bibliothèque") { scope.launch { OnlineLibrary.add(deps, page.top) } }
                            }
                        }
                    }
                    val groups = page.albums.groupBy { if (it.type == "album") "Albums" else if (it.type == "single") "Singles" else "EP et compilations" }
                    listOf("Albums", "EP et compilations", "Singles").forEach { label ->
                        val list = groups[label].orEmpty()
                        if (list.isEmpty()) return@forEach
                        item(key = "albums-$label") {
                            Column {
                                SectionHeader(label)
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(list, key = { it.id }) { album -> OnlineAlbumCard(album) }
                                }
                            }
                        }
                    }
                    if (page.related.isNotEmpty()) item(key = "related") {
                        Column {
                            SectionHeader("Artistes similaires")
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(page.related, key = { it.id }) { OnlineArtistBubble(it) }
                            }
                        }
                    }
                }
            }
        }
        BackButton()
        OnlineTrackMenu(menu) { menu = null }
    }
}

/** Online album page: cover, play, add the whole album, track list. */
@Composable
fun OnlineAlbumScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad("online-album", id) { deps.online.album(id) }
    val page = load.value
    var menu by remember { mutableStateOf<OnlineTrack?>(null) }
    LaunchedEffect(page) {
        page ?: return@LaunchedEffect
        OnlineLibrary.seed(page.tracks)
        OnlineLibrary.refresh(deps, page.tracks.map { it.id })
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                CollectionHeader(
                    title = page?.album?.title.orEmpty(),
                    subtitle = page?.album?.let { a -> listOf(a.artist, a.year, "${page.tracks.size} titres").filter { it.isNotBlank() }.joinToString(" · ") },
                    image = page?.album?.cover?.ifBlank { null },
                    origin = "online-album/$id",
                    kind = page?.album?.typeLabel ?: "Album",
                    onPlay = { page?.tracks?.takeIf { it.isNotEmpty() }?.let { deps.playOnline(it.withLibrary(), 0) } },
                    onShuffle = { page?.tracks?.takeIf { it.isNotEmpty() }?.let { deps.playOnline(it.withLibrary().shuffled(), 0) } },
                    extra = {
                        page?.album?.artistId?.takeIf { it.isNotBlank() }?.let { artistId ->
                            Text(
                                "Voir l'artiste", style = MaterialTheme.typography.labelLarge, color = Nflix.Red,
                                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp))
                                    .clickable { navigator.open(Route.OnlineArtist(artistId, page.album.artist)) }.padding(vertical = 4.dp),
                            )
                        }
                    },
                )
            }
            if (page != null && page.tracks.isNotEmpty()) item(key = "add") {
                val missing = page.tracks.count { OnlineLibrary.state(it.id) != "done" }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (missing == 0) PillButton(Icons.Rounded.CheckCircle, "Album dans la bibliothèque", enabled = false) {}
                    else PillButton(Icons.Rounded.Download, "Ajouter l'album à la bibliothèque", primary = true) {
                        scope.launch { OnlineLibrary.add(deps, page.tracks, albumId = page.album.id) }
                    }
                }
            }
            when {
                page == null && load is org.jellyfin.mobile.nflix.ui.Load.Failed -> item(key = "error") {
                    EmptyState(Icons.Rounded.Album, "Album indisponible", "Le catalogue en ligne ne répond pas.")
                }
                page == null -> items(8, key = { "sk$it" }) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(48.dp)) }
                else -> items(page.tracks.size, key = { "t-${page.tracks[it].id}" }) { index ->
                    val track = page.tracks[index]
                    OnlineTrackRow(track, track.trackNumber.takeIf { it > 0 } ?: (index + 1), showCover = false, onClick = { deps.playOnline(page.tracks.withLibrary(), index) }, onLongClick = { menu = track })
                }
            }
        }
        BackButton()
        OnlineTrackMenu(menu) { menu = null }
    }
}

/** Online part of a library artist page: popular tracks missing from Nflix and the discography. */
@Composable
internal fun OnlineArtistSection(name: String, localTitles: Set<String>) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val page = rememberLoad("online-artist-section", name) { runCatching { deps.online.artistByName(name) }.getOrNull() }.value ?: return
    if (VideoCatalog.normalize(page.artist.name) != VideoCatalog.normalize(name)) return
    var menu by remember { mutableStateOf<OnlineTrack?>(null) }
    val missing = remember(page, localTitles) { page.top.filter { VideoCatalog.normalize(it.title) !in localTitles }.take(10) }
    LaunchedEffect(page) {
        OnlineLibrary.seed(page.top)
        OnlineLibrary.refresh(deps, missing.map { it.id })
    }
    Column {
        if (missing.isNotEmpty()) {
            SectionHeader("En ligne · populaires", action = "Tout voir", onAction = { navigator.open(Route.OnlineArtist(page.artist.id, page.artist.name)) })
            missing.forEachIndexed { index, track ->
                OnlineTrackRow(track, index + 1, showCover = true, onClick = { deps.playOnline(missing.withLibrary(), index) }, onLongClick = { menu = track })
            }
        }
        if (page.albums.isNotEmpty()) {
            SectionHeader("Discographie complète", action = "Tout voir", onAction = { navigator.open(Route.OnlineArtist(page.artist.id, page.artist.name)) })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(page.albums.take(20), key = { it.id }) { OnlineAlbumCard(it, width = 130.dp) }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { DiscographyRequestButton(page.artist.name) }
    }
    OnlineTrackMenu(menu) { menu = null }
}

/** On a person page: link to their music when the actor is also a recording artist. */
@Composable
internal fun PersonMusicLink(name: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val local = rememberLoad("person-artist", name) { findNameMatches(deps, name).artistId }.value
    val online = rememberLoad("person-online-artist", name) {
        runCatching { deps.online.artistByName(name) }.getOrNull()?.artist
            ?.takeIf { VideoCatalog.normalize(it.name) == VideoCatalog.normalize(name) && it.fans >= 2_000 }
    }.value
    if (local == null && online == null) return
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (local != null) PillButton(Icons.Rounded.LibraryMusic, "Aussi artiste · ses titres sur Nflix", primary = true) { navigator.open(Route.Artist(local)) }
        else if (online != null) PillButton(Icons.Rounded.MusicNote, "Aussi artiste · écouter", primary = true) { navigator.open(Route.OnlineArtist(online.id, online.name)) }
    }
}
