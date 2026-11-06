package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.Badge
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.ProgressBar
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.bottomScrim
import org.jellyfin.mobile.nflix.ui.formatRuntime
import org.jellyfin.mobile.nflix.ui.formatTicks
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.rowSection
import org.jellyfin.mobile.nflix.ui.sharedPoster
import org.jellyfin.mobile.nflix.ui.value

@Composable
fun DetailScreen(id: String, origin: String?) {
    val deps = LocalDeps.current
    val reloader = rememberReloader()
    val load = rememberLoad(id, reloader = reloader, initial = deps.jellyfin.cachedItem(id)) { force -> deps.jellyfin.item(id, force) }
    val item = load.value
    val listState = rememberLazyListState()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val merged = item?.let { base -> userData[base.id]?.let { base.copy(userData = it) } ?: base }

    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        if (merged == null) {
            Column {
                Skeleton(Modifier.fillMaxWidth().height(430.dp), RoundedCornerShape(0.dp))
                Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(52.dp))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                item(key = "header") { DetailHeader(merged, origin, listState.firstVisibleItemScrollOffset.toFloat()) }
                item(key = "actions") { Actions(merged) { reloader.reload() } }
                item(key = "overview") { Overview(merged) }
                when (merged.type) {
                    "Series" -> item(key = "seasons") { Seasons(merged) }
                    "Season" -> item(key = "episodes") { EpisodeList(merged.seriesId ?: merged.id, merged.id) }
                    "BoxSet" -> item(key = "children") { CollectionChildren(merged) }
                }
                if (merged.people.isNotEmpty()) {
                    rowSection("cast", "Distribution", merged.people.take(30), { it.id + (it.role ?: "") }) { person -> CastCard(person) }
                }
                item(key = "similar") { SimilarRow(merged) }
            }
        }
        BackButton()
    }
}

@Composable
fun BackButton() {
    val navigator = LocalNavigator.current
    IconButton(
        onClick = { navigator.back() },
        modifier = Modifier.statusBarsPadding().padding(8.dp).size(42.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
}

@Composable
private fun DetailHeader(item: Item, origin: String?, scroll: Float) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val portraitType = item.isBook || item.isAudioBook
    if (portraitType) {
        BookHeader(item, origin, scroll)
        return
    }
    // Full-bleed artwork, title centred over a deep fade: the layout of a streaming title page.
    Box(Modifier.fillMaxWidth().height(540.dp)) {
        NImage(
            deps.jellyfin.backdrop(item) ?: deps.jellyfin.primary(item, 900),
            Modifier.fillMaxSize().sharedPoster(origin ?: "detail/${item.id}")
                .graphicsLayer { translationY = scroll * 0.4f; alpha = (1f - scroll / 1400f).coerceIn(0.3f, 1f) },
            contentScale = ContentScale.Crop,
            placeholderIcon = null,
        )
        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.65f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night.copy(alpha = 0.75f), Nflix.Night))),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item.seriesName?.let { series ->
                Text(
                    series.uppercase(), style = MaterialTheme.typography.labelLarge, color = Nflix.Red, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { item.seriesId?.let { navigator.open(Route.Item(it)) } }.padding(4.dp),
                )
            }
            val logo = deps.jellyfin.logo(item)
            if (logo != null && item.type != "Episode") {
                NImage(logo, Modifier.fillMaxWidth(0.75f).height(96.dp), contentScale = ContentScale.Fit, placeholderIcon = null, background = Color.Transparent)
            } else {
                Text(
                    if (item.type == "Episode") listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.name,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            MetaLine(item, centered = true)
        }
    }
}

@Composable
private fun BookHeader(item: Item, origin: String?, scroll: Float) {
    val deps = LocalDeps.current
    Box(Modifier.fillMaxWidth().height(470.dp)) {
        NImage(
            deps.jellyfin.primary(item, 900),
            Modifier.fillMaxSize().graphicsLayer { translationY = scroll * 0.45f; alpha = (1f - scroll / 1400f).coerceIn(0.3f, 1f) }.bottomScrim(0.2f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Nflix.Night.copy(alpha = 0.6f)))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NImage(
                deps.jellyfin.primary(item, 400),
                Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(10.dp)).sharedPoster(origin ?: "detail/${item.id}"),
            )
            Spacer(Modifier.height(14.dp))
            Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            MetaLine(item, centered = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(item: Item, centered: Boolean = false) {
    val deps = LocalDeps.current
    FlowRow(
        horizontalArrangement = if (centered) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
    ) {
        if (item.genres.isNotEmpty() && centered) {
            Text(item.genres.take(3).joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = Nflix.Text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        item.communityRating?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = Nflix.Warning, modifier = Modifier.size(14.dp))
                Text(" %.1f".format(it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        item.year?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        item.episodeLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        formatRuntime(item.runtimeMinutes)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        item.officialRating?.let { Badge(it, Color.White.copy(alpha = 0.16f)) }
        item.maxHeight?.let { height ->
            Badge(when { height >= 2000 -> "4K"; height >= 1000 -> "HD"; else -> "SD" }, Color.White.copy(alpha = 0.16f))
        }
        if (deps.plugins.enhanced.showAudioLanguages && item.audioLanguages.isNotEmpty()) {
            Badge(item.audioLanguages.take(4).joinToString(" · "), Color.White.copy(alpha = 0.16f))
        }
        if (item.type == "Book") item.container?.let { Badge(it.uppercase(), Color.White.copy(alpha = 0.16f)) }
    }
}

@Composable
private fun Actions(item: Item, onChanged: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val data = item.userData
    val resumable = (data?.positionTicks ?: 0) > 0 && item.isPlayableVideo
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Button(
            onClick = { if (item.isBook) navigator.open(Route.Reader(item.id)) else deps.play(scope, item, navigator = navigator) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(if (item.isBook) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    item.isBook -> "Lire"
                    resumable -> "Reprendre"
                    else -> "Lecture"
                },
                fontWeight = FontWeight.Bold, fontSize = 17.sp,
            )
        }
        if (item.isPlayableVideo || item.type == "Season") {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { deps.download(item) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (item.type == "Season") "Télécharger la saison" else "Télécharger", fontWeight = FontWeight.SemiBold)
            }
        }
        if (resumable && deps.plugins.enhanced.showWatchProgress) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress).background(Nflix.Red))
                }
                Spacer(Modifier.width(10.dp))
                val left = item.runtimeMinutes?.let { (it * (1f - item.progress)).toInt() }?.takeIf { it > 0 }
                Text(
                    left?.let { "Reste ${formatRuntime(it)}" } ?: formatTicks(data!!.positionTicks),
                    style = MaterialTheme.typography.labelMedium, color = Nflix.Muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            val favorite = data?.favorite == true
            ActionIcon(if (favorite) Icons.Rounded.Check else Icons.Rounded.Add, "Ma liste", favorite) { scope.launch { deps.jellyfin.setFavorite(item, !favorite) } }
            if (item.type == "Series") FollowBell(item.id, item.name)
            if (!item.isBook) {
                val played = data?.played == true
                ActionIcon(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.Visibility, if (played) "Vu" else "Marquer vu", played) {
                    scope.launch { deps.jellyfin.setPlayed(item, !played); onChanged() }
                }
            }
            if (resumable) ActionIcon(Icons.Rounded.Replay, "Du début") { deps.play(scope, item, fromStart = true) }
            val remoteTrailer = item.trailers.firstNotNullOfOrNull { org.jellyfin.mobile.nflix.ui.TrailerSource.youtubeId(it.url) }
            if ((item.localTrailerCount ?: 0) > 0 || remoteTrailer != null) {
                ActionIcon(Icons.Rounded.Movie, "Bande-annonce") {
                    scope.launch {
                        // Server copy first: full quality, native player, no third-party ads.
                        val local = if ((item.localTrailerCount ?: 0) > 0) runCatching { deps.jellyfin.localTrailers(item.id) }.getOrDefault(emptyList()) else emptyList()
                        when {
                            local.isNotEmpty() -> deps.playVideos(scope, local.map { it.id }, 0, null)
                            remoteTrailer != null -> org.jellyfin.mobile.nflix.ui.TrailerSource.show(remoteTrailer, item.name)
                        }
                    }
                }
            }
            if (item.isBook || item.isPlayableVideo || item.type in setOf("Series", "Season", "MusicAlbum", "MusicArtist", "Playlist")) {
                ActionIcon(Icons.Rounded.Download, "Télécharger") { scope.launch { deps.downloadComplete(item) } }
            }
            if (resumable && deps.plugins.enhanced.removeContinueWatching) {
                ActionIcon(Icons.Rounded.RemoveCircleOutline, "Retirer") { scope.launch { deps.jellyfin.removeFromResume(item); onChanged() } }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(74.dp).pressable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(icon, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "action-icon") { current ->
            Icon(current, label, tint = if (active) Nflix.Red else Nflix.Text, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(item: Item) {
    val navigator = LocalNavigator.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        item.taglines.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Nflix.Text.copy(alpha = 0.9f)); Spacer(Modifier.height(6.dp)) }
        item.overview?.let { text ->
            Text(
                text, style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
            )
        }
        if (item.genres.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.genres.forEach { genre ->
                    Surface(color = Nflix.SurfaceHigh, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable {
                        navigator.open(Route.Browse("all", genre, typesForItem(item), genre))
                    }) { Text(genre, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                }
            }
        }
        val directors = item.people.filter { it.type == "Director" }.joinToString { it.name }
        if (directors.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("Réalisation : $directors", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
        }
        val studio = item.studios.firstOrNull()?.name
        if (studio != null) Text("Studio : $studio", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
    }
}

private fun typesForItem(item: Item) = when (item.type) {
    "Series", "Season", "Episode" -> "Series"
    "Book", "AudioBook" -> "Book,AudioBook"
    "MusicAlbum", "Audio" -> "MusicAlbum"
    else -> "Movie"
}

@Composable
private fun Seasons(series: Item) {
    val deps = LocalDeps.current
    val seasons = rememberLoad("seasons", series.id) { deps.jellyfin.seasons(series.id) }.value.orEmpty()
    if (seasons.isEmpty()) return
    var selected by rememberSaveable(series.id) { mutableStateOf(-1) }
    val index = if (selected >= 0) selected.coerceIn(0, seasons.lastIndex) else seasons.indexOfFirst { (it.userData?.unplayed ?: 0) > 0 }.coerceAtLeast(0)
    Column(Modifier.padding(top = 18.dp)) {
        ScrollableTabRow(
            selectedTabIndex = index,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = Nflix.Text,
            divider = {},
            indicator = { positions ->
                if (index < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[index]), color = Nflix.Red)
            },
        ) {
            seasons.forEachIndexed { i, season ->
                Tab(selected = i == index, onClick = { selected = i }, text = { Text(season.name, maxLines = 1) })
            }
        }
        AnimatedContent(seasons[index].id, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "season") { seasonId ->
            EpisodeList(series.id, seasonId)
        }
    }
}

@Composable
private fun EpisodeList(seriesId: String, seasonId: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val episodes = rememberLoad("episodes", seasonId) { deps.jellyfin.episodes(seriesId, seasonId) }
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val list = episodes.value
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (list == null) repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
        list.orEmpty().forEachIndexed { i, base ->
            val episode = userData[base.id]?.let { base.copy(userData = it) } ?: base
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
                    .pressable({ navigator.open(Route.Item(episode.id, "episode/${episode.id}")) }, { org.jellyfin.mobile.nflix.ui.ItemMenu.show(episode) }).padding(8.dp).appear(i * 40),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).pressable({
                    deps.playVideos(scope, list.orEmpty().map { it.id }, i, episode.userData?.positionTicks)
                })) {
                    NImage(deps.jellyfin.thumb(episode, 400), Modifier.fillMaxSize().sharedPoster("episode/${episode.id}"))
                    Box(Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, "Lecture", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    ProgressBar(episode.progress, Modifier.align(Alignment.BottomCenter))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("${episode.indexNumber ?: ""}. ${episode.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(formatRuntime(episode.runtimeMinutes), episode.premiereDate?.take(10)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    episode.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                if (episode.userData?.played == true) {
                    IconButton(onClick = { scope.launch { deps.jellyfin.setPlayed(episode, false) } }) { Icon(Icons.Rounded.CheckCircle, "Vu", tint = Nflix.Red) }
                }
            }
        }
    }
}

@Composable
private fun CollectionChildren(collection: Item) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val children = rememberLoad("children", collection.id) { deps.jellyfin.children(collection.id, "ProductionYear,SortName") }.value.orEmpty()
    if (children.isEmpty()) return
    Column {
        org.jellyfin.mobile.nflix.ui.SectionHeader("Dans cette collection (${children.size})")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(children.distinctBy { it.id }, key = { it.id }) { child ->
                val origin = "boxset/${child.id}"
                PosterCard(child, deps.jellyfin.primary(child, 300), origin, { navigator.open(Route.Item(child.id, origin)) })
            }
        }
    }
}

@Composable
private fun SimilarRow(item: Item) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val similar = rememberLoad("similar", item.id) { deps.jellyfin.similar(item.id) }.value.orEmpty()
    if (similar.isEmpty()) return
    Column {
        org.jellyfin.mobile.nflix.ui.SectionHeader("Vous aimerez aussi")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(similar.distinctBy { it.id }, key = { it.id }) { other ->
                val origin = "similar-${item.id}/${other.id}"
                PosterCard(other, deps.jellyfin.primary(other, 300), origin, { navigator.open(Route.Item(other.id, origin)) })
            }
        }
    }
}

@Composable
private fun CastCard(person: org.jellyfin.mobile.nflix.data.Person) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Column(Modifier.width(92.dp).pressable({ navigator.open(Route.Person(person.id, person.name)) }), horizontalAlignment = Alignment.CenterHorizontally) {
        NImage(deps.jellyfin.personImage(person), Modifier.size(84.dp).clip(CircleShape), placeholderIcon = Icons.Rounded.Person)
        Spacer(Modifier.height(6.dp))
        Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        person.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun PersonScreen(id: String, name: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Box(Modifier.fillMaxSize()) {
        BrowseGrid(
            viewId = null, types = "Movie,Series", key = "person-$id",
            header = {
                item(key = "person-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.statusBarsPadding().padding(top = 48.dp, bottom = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        NImage(deps.api.imageUrl(id, "Primary", null, 400), Modifier.size(140.dp).clip(CircleShape), placeholderIcon = Icons.Rounded.Person)
                        Spacer(Modifier.height(12.dp))
                        Text(name, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            },
            onItemClick = { item, origin -> navigator.open(Route.Item(item.id, origin)) },
            personId = id,
        )
        BackButton()
    }
}

@Suppress("unused")
private val heroGradient = Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night))
