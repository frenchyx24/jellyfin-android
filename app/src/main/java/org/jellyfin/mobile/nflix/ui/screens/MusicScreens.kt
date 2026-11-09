package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.formatTicks
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rowSection
import org.jellyfin.mobile.nflix.ui.sharedPoster
import org.jellyfin.mobile.nflix.ui.value

/** One artist of the grid once duplicates are folded: the item to open and the name to show. */
@androidx.compose.runtime.Immutable
private data class ArtistTile(val item: Item, val name: String, val image: String?)

private enum class ArtistSort(val label: String) { Name("A-Z"), NameDesc("Z-A"), Added("Ajoutés récemment"), Played("Les plus écoutés") }

@Composable
internal fun ArtistsGrid(viewId: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var sort by rememberSaveable { mutableStateOf(ArtistSort.Name) }
    val artists = rememberLoad("artists", viewId, sort) {
        when (sort) {
            ArtistSort.Added -> deps.jellyfin.artists(viewId, 0, 500, sortBy = "DateCreated,SortName", descending = true).items
            ArtistSort.Played -> deps.jellyfin.artists(viewId, 0, 500, sortBy = "PlayCount,SortName", descending = true).items
            else -> deps.jellyfin.artists(viewId, 0, 500).items
        }
    }.value
    // Same artist under several spellings or credits ("Daft Punk, Pharrell Williams") shows once.
    val tiles = remember(artists, sort) {
        artists?.let { list ->
            val groups = org.jellyfin.mobile.nflix.data.ArtistNames.group(list.distinctBy { it.id }, { it.name }) { deps.jellyfin.primary(it, 300) != null }
            val mapped = groups.map { (name, members) ->
                val best = members.first()
                ArtistTile(best, name, members.firstNotNullOfOrNull { deps.jellyfin.primary(it, 300) })
            }
            when (sort) {
                ArtistSort.Name -> mapped.sortedBy { org.jellyfin.mobile.nflix.data.ArtistNames.key(it.name) }
                ArtistSort.NameDesc -> mapped.sortedByDescending { org.jellyfin.mobile.nflix.data.ArtistNames.key(it.name) }
                else -> mapped
            }
        }
    }
    // Real pictures for artists the library has none for.
    val missing = remember(tiles) { tiles.orEmpty().filter { it.image == null }.map { it.name } }
    val pictures = rememberLoad("artist-pictures", missing) {
        if (missing.isEmpty()) emptyMap() else runCatching { deps.online.pictures(missing) }.getOrDefault(emptyMap())
    }.value.orEmpty()
    val similar = rememberLoad("music-online-feed") { force -> runCatching { deps.online.feed(refresh = force) }.getOrDefault(emptyList()) }
        .value.orEmpty().firstOrNull { it.key == "similar" }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ArtistSort.entries.size, key = { ArtistSort.entries[it].name }) { index ->
                    val option = ArtistSort.entries[index]
                    val selected = option == sort
                    Box(
                        Modifier.height(30.dp).clip(CircleShape).background(if (selected) Nflix.Text else Nflix.SurfaceHigh).clickable { sort = option }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Nflix.Night else Nflix.Text) }
                }
            }
        }
        val list = tiles
        if (list == null) items(12, key = { "sk$it" }) { Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(100.dp)) }
        else items(list, key = { it.item.id }) { tile ->
            Column(Modifier.pressable({ navigator.open(Route.Artist(tile.item.id)) }, { org.jellyfin.mobile.nflix.ui.ItemMenu.show(tile.item) }), horizontalAlignment = Alignment.CenterHorizontally) {
                NImage(tile.image ?: pictures[tile.name], Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).sharedPoster("artist/${tile.item.id}"), description = tile.name, placeholderIcon = Icons.Rounded.Person)
                Spacer(Modifier.height(6.dp))
                Text(tile.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (similar != null && similar.artists.isNotEmpty()) {
            item(key = "online-title", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("À découvrir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(similar.title, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
            items(similar.artists, key = { "online-${it.id}" }) { OnlineArtistBubble(it, 96.dp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(track: Item, number: Int, showArt: Boolean = false, playlistId: String? = null, onClick: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val state by deps.music.state.collectAsState()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val favorite = (userData[track.id] ?: track.userData)?.favorite == true
    val playing = state.current?.id == track.id
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { org.jellyfin.mobile.nflix.ui.ItemMenu.show(track, playlistId) })
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            NImage(deps.jellyfin.primary(track, 120), Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.Rounded.MusicNote)
        } else {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = Nflix.Red, modifier = Modifier.size(18.dp))
                else Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.name, style = MaterialTheme.typography.titleSmall, color = if (playing) Nflix.Red else Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.artistItems.joinToString { it.name }.ifBlank { track.albumArtist }, track.runTimeTicks?.let { formatTicks(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { scope.launch { deps.jellyfin.setFavorite(track, !favorite) } }) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (favorite) Nflix.Red else Nflix.Muted, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { org.jellyfin.mobile.nflix.ui.ItemMenu.show(track, playlistId) }) {
            Icon(Icons.Rounded.MoreVert, "Plus d'options", tint = Nflix.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun CollectionHeader(title: String, subtitle: String?, image: String?, origin: String, round: Boolean = false, kind: String? = null, onMore: (() -> Unit)? = null, onPlay: () -> Unit, onShuffle: () -> Unit, extra: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth()) {
        // Colour wash from the artwork itself, fading into the page.
        NImage(image, Modifier.fillMaxWidth().height(420.dp).blur(60.dp).graphicsLayer { alpha = 0.7f }, placeholderIcon = null)
        Box(Modifier.fillMaxWidth().height(420.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Nflix.Night))))
        Column(Modifier.statusBarsPadding().padding(top = 56.dp).fillMaxWidth()) {
            NImage(
                image,
                Modifier.align(Alignment.CenterHorizontally).size(230.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).sharedPoster(origin),
                placeholderIcon = if (round) Icons.Rounded.Person else Icons.Rounded.Album,
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                kind?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, fontWeight = FontWeight.Bold) }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.75f)) }
                extra()
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "Plus d'options", tint = Nflix.Text) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShuffle, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Shuffle, "Lecture aléatoire", Modifier.size(28.dp), tint = Nflix.Text) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Nflix.Red).pressable(onPlay),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, "Lecture", Modifier.size(36.dp), tint = Color.White) }
            }
        }
    }
}

@Composable
fun AlbumScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val album = rememberLoad("album", id, initial = deps.jellyfin.cachedItem(id)) { deps.jellyfin.item(id, it) }.value
    val tracks = rememberLoad("tracks", id) { deps.jellyfin.albumTracks(id) }.value
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                CollectionHeader(
                    title = album?.name ?: "",
                    subtitle = listOfNotNull(album?.albumArtist, album?.year?.toString()).joinToString(" · "),
                    image = album?.let { deps.jellyfin.primary(it, 600) },
                    origin = "grid-albums/$id",
                    kind = "Album",
                    onMore = album?.let { a -> { org.jellyfin.mobile.nflix.ui.ItemMenu.show(a) } },
                    onPlay = { tracks?.let { deps.music.play(it) } },
                    onShuffle = { tracks?.let { deps.music.play(it, shuffle = true) } },
                    extra = {
                        album?.albumArtists?.firstOrNull()?.let { artist ->
                            Text("Voir l'artiste", color = Nflix.Red, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { navigator.open(Route.Artist(artist.id)) }.padding(vertical = 4.dp))
                        }
                        album?.let { a ->
                            Text("Signaler un problème", color = Nflix.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp).clickable { org.jellyfin.mobile.nflix.ui.ItemMenu.report(a) })
                        }
                    },
                )
            }
            if (tracks == null) item { Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(200.dp)) }
            else itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                Box(Modifier.appear(index * 25)) { TrackRow(track, track.indexNumber ?: (index + 1)) { deps.music.play(tracks, index) } }
            }
        }
        BackButton()
    }
}

@Composable
fun ArtistScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val artist = rememberLoad("artist", id, initial = deps.jellyfin.cachedItem(id)) { deps.jellyfin.item(id, it) }.value
    val albums = rememberLoad("artist-albums", id) { deps.jellyfin.artistAlbums(id) }.value.orEmpty()
    val top = rememberLoad("artist-top", id) { deps.jellyfin.artistTopTracks(id) }.value.orEmpty()
    val scope = rememberCoroutineScope()
    val ownImage = artist?.let { deps.jellyfin.primary(it, 600) }
    val onlinePicture = rememberLoad("artist-picture", artist?.name, ownImage == null) {
        val name = artist?.name
        if (name == null || ownImage != null) null else runCatching { deps.online.pictures(listOf(name))[name] }.getOrNull()
    }.value
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = ContentBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                CollectionHeader(
                    title = artist?.name ?: "", subtitle = "${albums.size} albums",
                    image = ownImage ?: onlinePicture, origin = "artist/$id", round = true, kind = "Artiste",
                    onMore = artist?.let { a -> { org.jellyfin.mobile.nflix.ui.ItemMenu.show(a) } },
                    onPlay = { if (top.isNotEmpty()) deps.music.play(top) else artist?.let { a -> deps.play(scope, a) } },
                    onShuffle = { artist?.let { a -> deps.play(scope, a) } },
                )
            }
            if (top.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "top-title") { org.jellyfin.mobile.nflix.ui.SectionHeader("Titres populaires") }
                top.distinctBy { it.id }.forEachIndexed { index, track ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "top-${track.id}") { TrackRow(track, index + 1, showArt = true) { deps.music.play(top, index) } }
                }
            }
            artist?.name?.let { name ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "cross-links") { CrossLinks(name, showArtist = false, showPerson = true) }
            }
            artist?.overview?.takeIf { it.isNotBlank() }?.let { bio ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "bio") {
                    var expanded by remember { mutableStateOf(false) }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(Nflix.SurfaceHigh).clickable { expanded = !expanded }.padding(14.dp)) {
                        Text("À propos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.8f), maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "albums-title") { org.jellyfin.mobile.nflix.ui.SectionHeader("Sur Nflix") }
            }
            items(albums.distinctBy { it.id }, key = { it.id }) { album ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    PosterCard(album, deps.jellyfin.primary(album, 300), "artist-$id/${album.id}", { navigator.open(Route.Album(album.id)) },
                        width = androidx.compose.ui.unit.Dp.Unspecified, aspect = 1f, modifier = Modifier.fillMaxWidth())
                }
            }
            artist?.name?.let { name ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "online-section") {
                    val localTitles = rememberLoad("artist-all-titles", id) {
                        deps.jellyfin.items(includeTypes = "Audio", limit = 1000, extra = mapOf("artistIds" to id)).items
                            .map { org.jellyfin.mobile.nflix.data.VideoCatalog.normalize(it.name) }.toSet()
                    }.value
                    if (localTitles != null) OnlineArtistSection(name, localTitles)
                }
            }
        }
        BackButton()
    }
}

@Composable
fun PlaylistScreen(id: String) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val playlist = rememberLoad("playlist", id, initial = deps.jellyfin.cachedItem(id)) { deps.jellyfin.item(id, it) }.value
    var pendingRevision by remember { mutableStateOf(0) }
    val pending = rememberLoad("playlist-pending", id, org.jellyfin.mobile.nflix.ui.ItemMenu.revision, pendingRevision) { runCatching { deps.sync.pending(id) }.getOrDefault(emptyList()) }.value.orEmpty()
    // Reload the library part only when a pending track has moved into it.
    val items = rememberLoad("playlist-items", id, org.jellyfin.mobile.nflix.ui.ItemMenu.revision, pending.size) { deps.jellyfin.playlistItems(id) }.value
    // Pending tracks move into the playlist once the server has them: re-check now and then.
    LaunchedEffect(id, pending.isNotEmpty()) {
        while (pending.isNotEmpty()) { kotlinx.coroutines.delay(if (pending.any { it.progress in 0..99 }) 8_000 else 30_000); pendingRevision++ }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                CollectionHeader(
                    title = playlist?.name ?: "", subtitle = items?.let { "${it.size + pending.size} éléments" },
                    image = playlist?.let { deps.jellyfin.primary(it, 600) }, origin = "grid-playlists/$id", kind = "Playlist",
                    onMore = playlist?.let { p -> { org.jellyfin.mobile.nflix.ui.ItemMenu.show(p) } },
                    onPlay = { playlist?.let { deps.play(scope, it) } },
                    onShuffle = { items?.let { list -> if (list.any { it.type == "Audio" }) deps.music.play(list, shuffle = true) else playlist?.let { deps.play(scope, it) } } },
                )
            }
            items?.let { list ->
                itemsIndexed(list, key = { i, t -> "$i-${t.id}" }) { index, entry ->
                    if (entry.type == "Audio") TrackRow(entry, index + 1, showArt = true, playlistId = id) { deps.music.play(list, index) }
                    else TrackRow(entry, index + 1, showArt = true, playlistId = id) { deps.play(scope, entry) }
                }
            }
            if (pending.isNotEmpty()) {
                item(key = "pending-title") {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                        Text("En ligne", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Nflix.Text)
                        Text("Lus en streaming, ajoutés automatiquement au serveur dès qu'ils sont disponibles", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                }
                itemsIndexed(pending, key = { _, t -> "p-${t.id}" }) { index, track ->
                    OnlineTrackRow(
                        track.toOnline(), null, showCover = true,
                        onClick = {
                            val playable = pending.filter { it.videoId.isNotBlank() }
                            val start = playable.indexOfFirst { it.id == track.id }
                            if (start >= 0) deps.music.playOnline(playable.map { it.toOnline() }, start) { deps.sync.pendingStreamUrl(it) }
                            else org.jellyfin.mobile.nflix.ui.ItemMenu.message = "Titre indisponible en ligne"
                        },
                        onLongClick = {
                            scope.launch {
                                val ok = runCatching { deps.sync.removePending(id, track.id) }.isSuccess
                                org.jellyfin.mobile.nflix.ui.ItemMenu.message = if (ok) "« ${track.title} » retiré de la playlist" else "Suppression impossible"
                                pendingRevision++
                            }
                        },
                        status = track.status.ifBlank { "En préparation" },
                        progress = track.progress,
                    )
                }
            }
        }
        BackButton()
    }
}

/** Full screen player with synced lyrics (LrcLib plugin through the Jellyfin lyrics API) and queue. */
@Composable
fun NowPlayingScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val state by deps.music.state.collectAsState()
    val track = state.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var panel by rememberSaveable { mutableStateOf(0) } // 0 artwork, 1 lyrics, 2 queue
    LaunchedEffect(track?.id) {
        while (true) {
            position = deps.music.position()
            duration = deps.music.duration()
            delay(250)
        }
    }
    if (track == null) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }
    // Library lyrics first (file or Jellyfin provider), then the server lookup by artist + title,
    // which also covers online tracks. Synced lines keep their timestamps (ticks).
    val lyrics = rememberLoad("lyrics", track.id) {
        val local = if (!track.id.contains(':')) deps.jellyfin.lyrics(track.id) else null
        if (local != null && local.lines.isNotEmpty()) local
        else runCatching {
            val found = deps.online.lyrics(track.artist, track.title, track.album, ((if (track.durationMs > 0) track.durationMs else deps.music.duration()) / 1000).toInt())
            org.jellyfin.mobile.nflix.data.Lyrics(found.lines.map { org.jellyfin.mobile.nflix.data.LyricLine(it.text, if (found.synced && it.startMs >= 0) it.startMs * 10_000 else null) })
        }.getOrNull() ?: local ?: org.jellyfin.mobile.nflix.data.Lyrics()
    }.value
    val context = androidx.compose.ui.platform.LocalContext.current
    val visualPrefs = remember { context.getSharedPreferences("nflix_player", android.content.Context.MODE_PRIVATE) }
    var vinyl by remember { mutableStateOf(visualPrefs.getBoolean("vinyl", true)) }
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        AmbientBackground(track.artwork, state.isPlaying)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navigator.back() }) { Icon(Icons.Rounded.ExpandMore, "Réduire", modifier = Modifier.size(30.dp)) }
                Text(track.album ?: "En lecture", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                IconButton(onClick = { org.jellyfin.mobile.nflix.ui.ItemMenu.showPlayer() }) { Icon(Icons.Rounded.MoreVert, "Plus d'options") }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(panel, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "np-panel") { p ->
                    when (p) {
                        1 -> LyricsPanel(lyrics, position) { deps.music.seekTo(it) }
                        2 -> QueuePanel()
                        else -> AnimatedContent(
                            track.id,
                            transitionSpec = {
                                (androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 } + androidx.compose.animation.scaleIn(tween(350), initialScale = 0.85f) + fadeIn(tween(300))) togetherWith
                                    (androidx.compose.animation.slideOutHorizontally(tween(250)) { -it / 3 } + androidx.compose.animation.scaleOut(tween(250), targetScale = 0.85f) + fadeOut(tween(200)))
                            },
                            label = "np-art",
                        ) { _ ->
                            // Breathes slightly larger while playing, settles back on pause.
                            val artScale by animateFloatAsState(if (state.isPlaying) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "np-art-scale")
                            AnimatedArtwork(track.artwork, state.isPlaying, vinyl, Modifier.sharedPoster("now-playing-art").graphicsLayer { scaleX = artScale; scaleY = artScale })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(track.id, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "np-title") { _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                        Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = Nflix.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    EqualizerBars(state.isPlaying)
                }
            }
            Spacer(Modifier.height(8.dp))
            val fraction = if (duration > 0) position.toFloat() / duration else 0f
            AnimatedSeekBar(fraction, state.isPlaying, onSeek = { f -> position = (f * duration).toLong(); deps.music.seekTo((f * duration).toLong()) })
            Row {
                Text(formatTicks(position * 10_000), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                Spacer(Modifier.weight(1f))
                Text(formatTicks(duration * 10_000), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BouncyIconButton(Icons.Rounded.Shuffle, "Aléatoire", { deps.music.toggleShuffle() }, tint = if (state.shuffle) Nflix.Red else Nflix.Text, spinOnChange = state.shuffle)
                BouncyIconButton(Icons.Rounded.SkipPrevious, "Précédent", { deps.music.previous() }, size = 56.dp, iconSize = 36.dp, nudge = -18f)
                AnimatedPlayButton(state.isPlaying) { deps.music.toggle() }
                BouncyIconButton(Icons.Rounded.SkipNext, "Suivant", { deps.music.next() }, size = 56.dp, iconSize = 36.dp, nudge = 18f)
                BouncyIconButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Répéter", { deps.music.cycleRepeat() },
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Nflix.Red else Nflix.Text, spinOnChange = state.repeatMode,
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { vinyl = !vinyl; visualPrefs.edit().putBoolean("vinyl", vinyl).apply(); panel = 0 }) {
                    Icon(if (vinyl) Icons.Rounded.Album else Icons.Rounded.Image, if (vinyl) "Afficher la pochette" else "Afficher le vinyle", tint = if (panel == 0) Nflix.Red else Nflix.Muted)
                }
                IconButton(onClick = { panel = if (panel == 1) 0 else 1 }) { Icon(Icons.Rounded.Lyrics, "Paroles", tint = if (panel == 1) Nflix.Red else Nflix.Muted) }
                IconButton(onClick = { panel = if (panel == 2) 0 else 2 }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente", tint = if (panel == 2) Nflix.Red else Nflix.Muted) }
                IconButton(onClick = { deps.music.stop() }) { Icon(Icons.Rounded.Stop, "Arrêter la musique", tint = Nflix.Muted) }
            }
        }
    }
}

@Composable
private fun LyricsPanel(lyrics: org.jellyfin.mobile.nflix.data.Lyrics?, positionMs: Long, onSeek: (Long) -> Unit) {
    if (lyrics == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Nflix.Red, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }
    val lines = lyrics.lines
    if (lines.isEmpty()) {
        EmptyState(Icons.Rounded.Lyrics, "Pas de paroles", "Aucune parole n'est disponible pour ce titre.")
        return
    }
    val synced = lines.any { it.start != null }
    val ticks = positionMs * 10_000
    val current = if (synced) lines.indexOfLast { (it.start ?: Long.MAX_VALUE) <= ticks } else -1
    val list = rememberLazyListState()
    // Keep the current line in the upper third, like a karaoke screen.
    LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            val active = index == current
            val alpha by animateFloatAsState(if (!synced || active) 1f else if (index < current) 0.35f else 0.55f, tween(300), label = "lyric")
            val scale by animateFloatAsState(if (active) 1.04f else 1f, spring(dampingRatio = 0.7f), label = "lyric-scale")
            val start = line.start
            Text(
                line.text.ifBlank { "♪" },
                fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .then(if (start != null) Modifier.clickable { onSeek(start / 10_000) } else Modifier)
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) },
            )
        }
    }
}

@Composable
private fun QueuePanel() {
    val deps = LocalDeps.current
    val state by deps.music.state.collectAsState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.queue, key = { i, t -> "$i-${t.id}" }) { index, track ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (index == state.index) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { deps.music.skipTo(index) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NImage(track.artwork, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.Rounded.MusicNote)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = if (index == state.index) Nflix.Red else Nflix.Text)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1)
                }
                if (index == state.index) Icon(Icons.Rounded.GraphicEq, null, tint = Nflix.Red)
            }
        }
    }
}
