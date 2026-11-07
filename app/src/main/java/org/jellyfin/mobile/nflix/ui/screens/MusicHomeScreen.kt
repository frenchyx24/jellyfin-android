package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.LibraryFilter
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SectionHeader
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value
import java.time.LocalTime

private enum class MusicTab(val label: String) { Home("Tout"), PourToi("Pour toi"), Albums("Albums"), Artists("Artistes"), Playlists("Playlists") }

private data class MusicHomeData(
    val quick: List<Item>,
    val recentAlbums: List<Item>,
    val latest: List<Item>,
    val mixArtists: List<Item>,
    val artists: List<Item>,
    val discover: List<Item>,
    val genres: List<String>,
    val likedCount: Int,
)

/** Mix tile colours, cycled: each mix gets its own identity like a streaming service's daily mixes. */
private val mixColors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFF4511E), Color(0xFF3949AB))
private val genreColors = listOf(
    Color(0xFFDC148C), Color(0xFF006450), Color(0xFF8400E7), Color(0xFF1E3264), Color(0xFFE8115B), Color(0xFF477D95),
    Color(0xFFBA5D07), Color(0xFF148A08), Color(0xFF503750), Color(0xFFE13300), Color(0xFF27856A), Color(0xFF7358FF),
)

private suspend fun loadMusicHome(deps: AppDeps, viewId: String, playlistsId: String?, force: Boolean): MusicHomeData = coroutineScope {
    val jellyfin = deps.jellyfin
    val recentTracks = async {
        runCatching { jellyfin.items(parentId = viewId, includeTypes = "Audio", sort = LibrarySort.Played, limit = 60, extra = mapOf("isPlayed" to true), force = force).items }.getOrDefault(emptyList())
    }
    val latest = async { runCatching { jellyfin.latest(viewId, 20, force = force) }.getOrDefault(emptyList()) }
    val playlists = async {
        runCatching { if (playlistsId == null) emptyList() else jellyfin.items(parentId = playlistsId, includeTypes = "Playlist", limit = 12, force = force).items }.getOrDefault(emptyList())
    }
    val liked = async { runCatching { jellyfin.items(parentId = viewId, includeTypes = "Audio", filter = LibraryFilter.Favorites, limit = 1, force = force).total }.getOrDefault(0) }
    val artists = async { runCatching { jellyfin.artists(viewId, 0, 40).items }.getOrDefault(emptyList()) }
    val discover = async { runCatching { jellyfin.items(parentId = viewId, includeTypes = "MusicAlbum", sort = LibrarySort.Random, limit = 16, force = force).items }.getOrDefault(emptyList()) }
    val genres = async { runCatching { jellyfin.genres(viewId) }.getOrDefault(emptyList()) }

    val tracks = recentTracks.await()
    // Albums heard recently, in listening order.
    val albumIds = tracks.mapNotNull { it.albumId }.distinct().take(16)
    val recentAlbums = if (albumIds.isEmpty()) emptyList() else runCatching {
        jellyfin.items(ids = albumIds, limit = albumIds.size, extra = mapOf("sortBy" to null), force = force).items.sortedBy { albumIds.indexOf(it.id) }
    }.getOrDefault(emptyList())
    val allArtists = artists.await()
    // Mix seeds: the artists the user actually listens to, else the library's artists.
    val heardArtistIds = tracks.flatMap { t -> t.albumArtists.map { it.id } + t.artistItems.map { it.id } }.distinct()
    val heardArtists = heardArtistIds.mapNotNull { id -> allArtists.firstOrNull { it.id == id } }
    val mixArtists = (heardArtists + allArtists).distinctBy { it.id }.take(6)
    val playlistItems = playlists.await()
    MusicHomeData(
        quick = (recentAlbums.take(4) + playlistItems.take(3)).distinctBy { it.id }.take(5),
        recentAlbums = recentAlbums,
        latest = latest.await(),
        mixArtists = mixArtists,
        artists = (heardArtists + allArtists).distinctBy { it.id }.take(20),
        discover = discover.await(),
        genres = genres.await().take(12),
        likedCount = liked.await(),
    )
}

@Composable
fun MusicScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val views = rememberLoad("views") { deps.jellyfin.views(it) }.value.orEmpty()
    val music = views.firstOrNull { it.collectionType == "music" }
    val playlists = views.firstOrNull { it.collectionType == "playlists" }
    var tab by rememberSaveable { mutableStateOf(MusicTab.Home) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(greeting(deps), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MusicTab.entries, key = { it.name }) { t ->
                val selected = t == tab
                Box(
                    Modifier.height(34.dp).clip(CircleShape).background(if (selected) Nflix.Red else Nflix.SurfaceHigh).clickable { tab = t }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Nflix.Text) }
            }
        }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "music-tab") { current ->
            when {
                music == null && views.isNotEmpty() -> EmptyState(Icons.Rounded.MusicNote, "Pas de bibliothèque musicale", null)
                music == null -> Box(Modifier.fillMaxSize())
                current == MusicTab.Home -> MusicHome(music.id, playlists?.id)
                current == MusicTab.PourToi -> YtFeedScreen()
                current == MusicTab.Albums -> BrowseGrid(viewId = music.id, types = "MusicAlbum", key = "albums-${music.id}", square = true)
                current == MusicTab.Artists -> ArtistsGrid(music.id)
                else -> BrowseGrid(viewId = playlists?.id, types = "Playlist", key = "playlists", square = true)
            }
        }
    }
}

private fun greeting(deps: AppDeps): String {
    val hour = LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    val name = (deps.session.miraName ?: deps.session.userName)?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (name != null) "$hello, $name" else hello
}

@Composable
private fun MusicHome(viewId: String, playlistsId: String?) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad("music-home", viewId) { force -> loadMusicHome(deps, viewId, playlistsId, force) }
    val data = load.value
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        when {
            load is Load.Failed -> item(key = "error") { EmptyState(Icons.Rounded.MusicNote, "Musique indisponible", load.message) }
            data == null -> {
                item(key = "sk-quick") { Skeleton(Modifier.padding(16.dp).fillMaxWidth().height(180.dp)) }
                items(3, key = { "sk$it" }) { SkeletonRow(4, 140.dp, 1f) }
            }
            else -> {
                item(key = "quick") {
                    // Two-column quick access, liked songs first.
                    val tiles = listOf<Item?>(null) + data.quick
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item -> QuickTile(item, data.likedCount, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                item(key = "shuffle") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Brush.horizontalGradient(listOf(Nflix.RedDeep, Nflix.Red)))
                            .clickable {
                                scope.launch { deps.music.play(deps.jellyfin.items(parentId = viewId, includeTypes = "Audio", sort = LibrarySort.Random, limit = 100).items) }
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lecture aléatoire", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("100 titres pris au hasard dans votre bibliothèque", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                    }
                }
                if (data.mixArtists.isNotEmpty()) item(key = "mixes") {
                    SectionHeader("Vos mix")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(data.mixArtists, key = { _, a -> "mix-${a.id}" }) { index, artist -> MixCard(artist, index) }
                    }
                }
                if (data.recentAlbums.isNotEmpty()) item(key = "recent") {
                    SectionHeader("Écoutés récemment")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.recentAlbums, key = { "r-${it.id}" }) { AlbumTile(it, "music-recent") }
                    }
                }
                if (data.latest.isNotEmpty()) item(key = "latest") {
                    SectionHeader("Nouveautés", action = "Tout voir", onAction = { navigator.open(Route.Browse(viewId, "Albums", "MusicAlbum")) })
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.latest, key = { "l-${it.id}" }) { AlbumTile(it, "music-latest") }
                    }
                }
                if (data.artists.isNotEmpty()) item(key = "artists") {
                    SectionHeader("Vos artistes")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(data.artists, key = { "a-${it.id}" }) { artist ->
                            Column(Modifier.width(112.dp).pressable({ navigator.open(Route.Artist(artist.id)) }, { org.jellyfin.mobile.nflix.ui.ItemMenu.show(artist) }), horizontalAlignment = Alignment.CenterHorizontally) {
                                NImage(deps.jellyfin.primary(artist, 300), Modifier.size(112.dp).clip(CircleShape), placeholderIcon = Icons.Rounded.Person)
                                Spacer(Modifier.height(8.dp))
                                Text(artist.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                Text("Artiste", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                            }
                        }
                    }
                }
                if (data.genres.isNotEmpty()) item(key = "genres") {
                    SectionHeader("Parcourir par genre")
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.genres.chunked(2).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { i, genre -> GenreTile(genre, genreColors[(rowIndex * 2 + i) % genreColors.size], viewId, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (data.discover.isNotEmpty()) item(key = "discover") {
                    SectionHeader("À découvrir")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.discover, key = { "d-${it.id}" }) { AlbumTile(it, "music-discover") }
                    }
                }
            }
        }
    }
}

/** Compact tile of the quick-access grid; null is the "Titres likés" tile. */
@Composable
private fun QuickTile(item: Item?, likedCount: Int, modifier: Modifier) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(Nflix.SurfaceHigh).pressable({
            when {
                item == null -> navigator.open(Route.LikedSongs)
                item.type == "Playlist" -> navigator.open(Route.Playlist(item.id))
                else -> navigator.open(Route.Album(item.id))
            }
        }, item?.let { { org.jellyfin.mobile.nflix.ui.ItemMenu.show(it) } }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item == null) {
            Box(
                Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
        } else {
            NImage(deps.jellyfin.primary(item, 160), Modifier.size(56.dp), placeholderIcon = if (item.type == "Playlist") Icons.Rounded.QueueMusic else Icons.Rounded.Album)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(item?.name ?: "Titres likés", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item == null) Text("$likedCount titre(s)", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

@Composable
private fun AlbumTile(album: Item, origin: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Column(Modifier.width(150.dp).pressable({ navigator.open(Route.Album(album.id)) }, { org.jellyfin.mobile.nflix.ui.ItemMenu.show(album) })) {
        NImage(deps.jellyfin.primary(album, 300), Modifier.size(150.dp).clip(RoundedCornerShape(6.dp)), description = album.name, placeholderIcon = Icons.Rounded.Album)
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(album.albumArtist, album.year?.toString()).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Instant mix seeded by an artist (Jellyfin InstantMix): plays similar tracks from the library. */
@Composable
private fun MixCard(artist: Item, index: Int) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val color = mixColors[index % mixColors.size]
    Column(Modifier.width(150.dp).pressable({ scope.launch { deps.music.play(deps.jellyfin.instantMix(artist.id)) } })) {
        Box(Modifier.size(150.dp).clip(RoundedCornerShape(6.dp)).background(color)) {
            NImage(deps.jellyfin.primary(artist, 300), Modifier.fillMaxSize(), placeholderIcon = Icons.Rounded.Person, background = color)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.9f)), startY = 120f)))
            Box(Modifier.fillMaxWidth().height(5.dp).background(color))
            Text(
                "Mix ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Lancer le mix", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text("${artist.name} et plus", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GenreTile(genre: String, color: Color, viewId: String, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(8.dp)).background(color).pressable({ navigator.open(Route.Browse(viewId, genre, "MusicAlbum", genre)) }),
    ) {
        Text(genre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 8.dp).size(64.dp).rotate(25f),
        )
    }
}

@Composable
fun LikedSongsScreen() {
    val deps = LocalDeps.current
    val views = rememberLoad("views") { deps.jellyfin.views(it) }.value.orEmpty()
    val music = views.firstOrNull { it.collectionType == "music" }
    val tracks = rememberLoad("liked-songs", music?.id) {
        if (music == null) emptyList() else deps.jellyfin.items(parentId = music.id, includeTypes = "Audio", filter = LibraryFilter.Favorites, sort = LibrarySort.Added, limit = 500, force = it).items
    }.value
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF450AF5), Nflix.Night)))) {
                    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 12.dp)) {
                        Box(
                            Modifier.size(150.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
                        Spacer(Modifier.height(16.dp))
                        Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, fontWeight = FontWeight.Bold)
                        Text("Titres likés", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("${tracks?.size ?: 0} titre(s)", style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.75f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { tracks?.takeIf { it.isNotEmpty() }?.let { deps.music.play(it, shuffle = true) } }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Rounded.Shuffle, "Lecture aléatoire", Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(60.dp).clip(CircleShape).background(Nflix.Red).pressable({ tracks?.takeIf { it.isNotEmpty() }?.let { deps.music.play(it) } }),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.PlayArrow, "Lecture", Modifier.size(36.dp), tint = Color.White) }
                        }
                    }
                }
            }
            when {
                tracks == null -> items(6, key = { "sk$it" }) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().height(52.dp)) }
                tracks.isEmpty() -> item(key = "empty") { EmptyState(Icons.Rounded.Favorite, "Aucun titre liké", "Touchez le cœur d'un titre pour le retrouver ici.") }
                else -> itemsIndexed(tracks, key = { _, t -> t.id }) { index, track -> TrackRow(track, index + 1, showArt = true) { deps.music.play(tracks, index) } }
            }
        }
        BackButton()
    }
}
