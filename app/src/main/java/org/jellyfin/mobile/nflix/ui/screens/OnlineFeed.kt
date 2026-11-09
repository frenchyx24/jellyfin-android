package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.data.OnlineFeedSection
import org.jellyfin.mobile.nflix.data.OnlineMix
import org.jellyfin.mobile.nflix.data.OnlineTrack
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.SectionHeader
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

private val feedMixColors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFF4511E), Color(0xFF3949AB))

/** Library states of every track of the feed, then live updates while some are being added. */
@Composable
internal fun TrackFeedStates(sections: List<OnlineFeedSection>?) {
    val deps = LocalDeps.current
    val tracks = remember(sections) {
        sections.orEmpty().flatMap { s -> s.tracks + s.mixes.flatMap { it.tracks } }.distinctBy { it.id }
    }
    LaunchedEffect(tracks) {
        if (tracks.isEmpty()) return@LaunchedEffect
        OnlineLibrary.seed(tracks)
        OnlineLibrary.refresh(deps, tracks.map { it.id })
    }
}

/**
 * Online suggestion rows as LazyColumn items, shared by "Tout" and "Pour toi": tap plays, long
 * press opens the track sheet, the corner button adds the track to the library.
 */
internal fun LazyListScope.onlineFeedSections(
    sections: List<OnlineFeedSection>,
    deps: AppDeps,
    keyPrefix: String,
    onMenu: (OnlineTrack) -> Unit,
) {
    sections.forEach { section ->
        when (section.type) {
            "mixes" -> if (section.mixes.isNotEmpty()) item(key = "$keyPrefix-${section.key}") {
                Column {
                    SectionHeader(section.title)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.mixes.size, key = { "mix-${section.mixes[it].id}-$it" }) { index ->
                            OnlineMixCard(section.mixes[index], index) { deps.playOnline(section.mixes[index].tracks.withLibrary(), 0) }
                        }
                    }
                }
            }
            "albums" -> if (section.albums.isNotEmpty()) item(key = "$keyPrefix-${section.key}") {
                Column {
                    SectionHeader(section.title)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.albums, key = { "al-${it.id}" }) { OnlineAlbumCard(it, width = 150.dp) }
                    }
                }
            }
            "artists" -> if (section.artists.isNotEmpty()) item(key = "$keyPrefix-${section.key}") {
                Column {
                    SectionHeader(section.title)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.artists, key = { "ar-${it.id}" }) { OnlineArtistBubble(it, 104.dp) }
                    }
                }
            }
            else -> if (section.tracks.isNotEmpty()) item(key = "$keyPrefix-${section.key}") {
                Column {
                    SectionHeader(section.title)
                    if (section.subtitle.isNotBlank()) {
                        Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.tracks.size, key = { "tr-${section.tracks[it].id}-$it" }) { index ->
                            val track = section.tracks[index]
                            OnlineTrackCard(track, onClick = { deps.playOnline(section.tracks.withLibrary(), index) }, onLongClick = { onMenu(track) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnlineTrackCard(track: OnlineTrack, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.size(150.dp)) {
            NImage(track.cover.ifBlank { null }, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), description = track.title, placeholderIcon = Icons.Rounded.MusicNote)
            OnlineAddButton(track, Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)))
        }
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlineMixCard(mix: OnlineMix, index: Int, onPlay: () -> Unit) {
    val color = feedMixColors[index % feedMixColors.size]
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onPlay)) {
        Box(Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(color)) {
            NImage(mix.cover.ifBlank { null }, Modifier.fillMaxSize(), placeholderIcon = Icons.Rounded.Person, background = color)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.9f)), startY = 120f)))
            Box(Modifier.fillMaxWidth().height(5.dp).background(color))
            Text(
                mix.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 48.dp, bottom = 10.dp),
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Lancer le mix", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(mix.subtitle, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "Pour toi" tab: every online suggestion row. */
@Composable
fun OnlineForYouScreen() {
    val deps = LocalDeps.current
    val load = rememberLoad("music-online-feed") { force -> deps.online.feed(refresh = force) }
    val sections = load.value
    var menu by remember { mutableStateOf<OnlineTrack?>(null) }
    TrackFeedStates(sections)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            when {
                sections == null && load is Load.Failed -> item(key = "error") { EmptyState(Icons.Rounded.MusicNote, "Suggestions indisponibles", "Réessayez dans un instant.") }
                sections == null -> {
                    item(key = "sk-title") { Skeleton(Modifier.padding(16.dp).width(160.dp).height(22.dp)) }
                    items(3, key = { "sk$it" }) { SkeletonRow(4, 150.dp, 1f) }
                }
                sections.isEmpty() -> item(key = "empty") { EmptyState(Icons.Rounded.MusicNote, "Rien pour l'instant", "Écoutez quelques titres pour remplir vos suggestions.") }
                else -> onlineFeedSections(sections, deps, "fy") { menu = it }
            }
        }
        OnlineTrackMenu(menu) { menu = null }
    }
}
