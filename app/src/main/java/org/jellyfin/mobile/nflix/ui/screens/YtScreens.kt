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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.YtHit
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.YtDownloadAllButton
import org.jellyfin.mobile.nflix.ui.YtDownloadButton
import org.jellyfin.mobile.nflix.ui.YtDownloads

/** Shared list of YT Music tracks: tap plays the list from that index, long press opens the track menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YtTrackList(hits: List<YtHit>, onPlay: (List<org.jellyfin.mobile.nflix.data.YtTrack>, Int) -> Unit) {
    val deps = LocalDeps.current
    val trackHits = remember(hits) { hits.filter { it.kind == "track" && it.videoId.isNotBlank() } }
    val tracks = remember(trackHits) { trackHits.map { it.toTrack() } }
    var menu by remember { mutableStateOf<YtHit?>(null) }
    val videoIds = remember(trackHits) { trackHits.map { it.videoId }.distinct() }
    // One call: the rows already in the library show their check mark without a per-row lookup.
    LaunchedEffect(videoIds) {
        runCatching { deps.yt.downloaded(videoIds) }.onSuccess { YtDownloads.markDownloaded(it) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ContentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tracks.size > 1) {
                item(key = "download-all") {
                    YtDownloadAllButton(tracks.map { it.videoId to it.title }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            items(trackHits.size, key = { "$it-${trackHits[it].videoId}" }) { index ->
                val hit = trackHits[index]
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onPlay(tracks, index) },
                            onLongClick = { menu = hit },
                        ).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Nflix.Muted,
                        modifier = Modifier.width(22.dp),
                    )
                    NImage(
                        hit.thumbnail,
                        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                        placeholderIcon = Icons.Rounded.MusicNote,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(hit.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            hit.subtitle.ifBlank { "Musique" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Nflix.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    YtDownloadButton(hit.videoId, title = hit.title)
                }
            }
        }
        YtTrackMenu(menu) { menu = null }
    }
}

private fun YtHit.toTrack() = org.jellyfin.mobile.nflix.data.YtTrack(
    videoId = videoId,
    title = title,
    artist = subtitle,
    duration = 0,
    thumbnail = thumbnail.orEmpty(),
)

/** Online music artist page: top tracks for the artist name. */
@Composable
fun YtArtistScreen(name: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var result by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.YtSearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        runCatching { deps.yt.artist(name, 30) }
            .onSuccess { result = it }
            .onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        if (result != null) {
            val hero = result!!.results.firstOrNull()?.thumbnail?.replace("hqdefault", "maxresdefault")
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                NImage(hero, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, placeholderIcon = Icons.Rounded.Person)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night))))
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Artiste", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Box(Modifier.align(Alignment.TopStart).padding(4.dp)) { BackButton() }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton()
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Artiste", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
        }
        when {
            error != null -> EmptyState(Icons.Rounded.Person, "Artiste indisponible", error)
            result == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Nflix.Red)
                }
            }
            else -> {
                val albums = result!!.albums
                if (albums.isNotEmpty()) {
                    Text("Albums", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        albums.take(8).forEach { album ->
                            Row(
                                Modifier.clip(CircleShape).background(Nflix.Surface).clickable { navigator.open(Route.YtAlbum(album.browseId, album.title)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Album, null, tint = Nflix.Muted, modifier = Modifier.size(16.dp))
                                Text(album.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                YtTrackList(result!!.results) { tracks, index -> deps.music.playYt(tracks, index) }
            }
        }
    }
}

/** YouTube Music album page: tracks of a MPREb browse id. */
@Composable
fun YtAlbumScreen(browseId: String, title: String) {
    val deps = LocalDeps.current
    var hits by remember { mutableStateOf<List<YtHit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(browseId) {
        runCatching { deps.yt.album(browseId, 100) }
            .onSuccess { hits = it }
            .onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton()
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Album", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            }
        }
        when {
            error != null -> EmptyState(Icons.Rounded.Album, "Album indisponible", error)
            hits == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Nflix.Red)
            }
            else -> YtTrackList(hits!!) { tracks, index -> deps.music.playYt(tracks, index) }
        }
    }
}

/** Personalized feed ("Pour toi" tab): sections built server-side from play history + online recents. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YtFeedScreen() {
    val deps = LocalDeps.current
    var sections by remember { mutableStateOf<List<org.jellyfin.mobile.nflix.data.YtMusicRepository.YtFeedSection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<YtHit?>(null) }
    LaunchedEffect(Unit) {
        runCatching { deps.yt.feed() }
            .onSuccess { sections = it }
            .onFailure { error = it.message }
    }
    val withTracks = remember(sections) {
        sections?.map { section ->
            section to section.tracks.filter { it.kind == "track" }.map {
                org.jellyfin.mobile.nflix.data.YtTrack(videoId = it.videoId, title = it.title, artist = it.subtitle, thumbnail = it.thumbnail.orEmpty())
            }
        }.orEmpty()
    }
    val feedIds = remember(sections) {
        sections.orEmpty().flatMap { section -> section.tracks.map { it.videoId } }.filter { it.isNotBlank() }
    }
    LaunchedEffect(feedIds) {
        runCatching { deps.yt.downloaded(feedIds) }.onSuccess { YtDownloads.markDownloaded(it) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = ContentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                error != null -> item { EmptyState(Icons.Rounded.MusicNote, "Fil indisponible", error) }
                sections == null -> items(3) { Skeleton(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(190.dp)) }
                sections!!.isEmpty() -> item {
                    Text(
                        "Écoute quelques morceaux pour que le fil se remplisse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Nflix.Muted,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    withTracks.forEachIndexed { sectionIndex, (section, tracks) ->
                        item(key = "t$sectionIndex") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
                                if (section.subtitle.isNotBlank()) Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        item(key = "r$sectionIndex") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(tracks.size) { index ->
                                    val track = tracks[index]
                                    Column(
                                        Modifier.width(150.dp).combinedClickable(
                                            onClick = { deps.music.playYt(tracks, index) },
                                            onLongClick = {
                                                menu = YtHit(
                                                    kind = "track",
                                                    videoId = track.videoId,
                                                    title = track.title,
                                                    subtitle = track.artist,
                                                    thumbnail = track.thumbnail.takeIf { it.isNotBlank() },
                                                )
                                            },
                                        ),
                                    ) {
                                        Box(Modifier.size(150.dp)) {
                                            NImage(
                                                track.thumbnail.takeIf { it.isNotBlank() },
                                                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                                placeholderIcon = Icons.Rounded.MusicNote,
                                            )
                                            YtDownloadButton(
                                                track.videoId,
                                                title = track.title,
                                                onImage = true,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                            )
                                        }
                                        Spacer(Modifier.size(6.dp))
                                        Text(track.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        YtTrackMenu(menu) { menu = null }
    }
}

/**
 * Long-press actions for one online track. The album row only appears once the server resolved the
 * album browse id: a visible row that does nothing is worse than no row at all.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun YtTrackMenu(hit: YtHit?, onDismiss: () -> Unit) {
    if (hit == null || hit.kind != "track") return
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var busy by remember(hit.videoId) { mutableStateOf(false) }
    var info by remember(hit.videoId) { mutableStateOf<org.jellyfin.mobile.nflix.data.YtItemInfo?>(null) }
    LaunchedEffect(hit.videoId) {
        info = runCatching { deps.yt.info(hit.videoId) }.getOrNull()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Nflix.SurfaceHigh,
        contentColor = Nflix.Text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            YtMenuRow(Icons.Rounded.Person, "Voir l'artiste", busy) {
                val artist = info?.artist?.takeIf { it.isNotBlank() } ?: hit.subtitle
                onDismiss()
                if (artist.isNotBlank()) navigator.open(Route.YtArtist(artist))
            }
            val albumBrowseId = info?.albumBrowseId.orEmpty()
            if (albumBrowseId.isNotBlank()) {
                YtMenuRow(Icons.Rounded.Album, "Voir l'album", busy) {
                    onDismiss()
                    navigator.open(Route.YtAlbum(albumBrowseId, info?.album?.ifBlank { hit.title } ?: hit.title))
                }
            }
            YtMenuRow(Icons.Rounded.Download, "Télécharger dans Nflix", busy) {
                scope.launch {
                    busy = true
                    YtDownloads.download(deps, hit.videoId, hit.title)
                    busy = false
                    onDismiss()
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun YtMenuRow(icon: ImageVector, label: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy, onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = Nflix.Red, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (busy) Nflix.Muted else Nflix.Text)
    }
}
