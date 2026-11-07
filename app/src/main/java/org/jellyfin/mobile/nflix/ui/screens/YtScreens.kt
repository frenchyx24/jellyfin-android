package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.data.YtHit
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route

/** Shared list of YT Music tracks; tap plays the whole list from the tapped index. */
@Composable
private fun YtTrackList(hits: List<YtHit>, onPlay: (List<org.jellyfin.mobile.nflix.data.YtTrack>, Int) -> Unit) {
    val deps = LocalDeps.current
    val tracks = remember(hits) { hits.filter { it.kind == "track" }.map { it.toTrack() } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ContentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(hits.size) { index ->
            val hit = hits[index]
            if (hit.kind != "track") return@items
            Row(
                Modifier.fillMaxWidth().clickable {
                    onPlay(tracks, tracks.indexOfFirst { it.videoId == hit.videoId }.coerceAtLeast(0))
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NImage(
                    hit.thumbnail,
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                    placeholderIcon = Icons.Rounded.MusicNote,
                )
                Column(Modifier.weight(1f)) {
                    Text(hit.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        hit.subtitle.ifBlank { "YouTube Music" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Nflix.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun YtHit.toTrack() = org.jellyfin.mobile.nflix.data.YtTrack(
    videoId = videoId,
    title = title,
    artist = subtitle,
    duration = 0,
    thumbnail = thumbnail.orEmpty(),
)

/** YouTube Music artist page: top tracks for the artist name. */
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
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton()
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Artiste", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
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
