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
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import org.jellyfin.mobile.nflix.ui.CastButton
import org.jellyfin.mobile.nflix.ui.CastRemoteSheet
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

    val accent = rememberAccent(item?.let { deps.jellyfin.primary(it, 120) })
    val trailerUrl = rememberLoad("hero-trailer", id) {
        if ((item?.localTrailerCount ?: 0) > 0) deps.jellyfin.localTrailers(id).firstOrNull()?.let(deps.jellyfin::trailerStreamUrl) else null
    }.value
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 900 } }
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        if (merged == null) {
            Column {
                Skeleton(Modifier.fillMaxWidth().height(430.dp), RoundedCornerShape(0.dp))
                Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(52.dp))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                item(key = "header") {
                    val scroll = remember(listState) {
                        derivedStateOf {
                            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
                        }
                    }.value
                    if (merged.isBook || merged.isAudioBook) DetailHeader(merged, origin, scroll)
                    else CinematicHeader(merged, origin, scroll, accent, trailerUrl)
                }
                item(key = "actions") { Actions(merged, accent) { reloader.reload() } }
                if (merged.type == "Series") item(key = "next-up") { SeriesProgress(merged, accent, Modifier.appear(80)) }
                item(key = "overview") { Overview(merged, Modifier.appear(120)) }
                when (merged.type) {
                    "Series" -> item(key = "seasons") { Seasons(merged, accent) }
                    "Season" -> item(key = "episodes") { EpisodeList(merged.seriesId ?: merged.id, merged.id, accent) }
                    "BoxSet" -> item(key = "children") { CollectionChildren(merged) }
                }
                if (merged.people.isNotEmpty()) {
                    rowSection("cast", "Distribution", merged.people.take(30), { it.id + (it.role ?: "") }) { person -> CastCard(person) }
                }
                item(key = "similar") { SimilarRow(merged) }
            }
        }
        val m = merged
        if (m != null && m.type in setOf("Episode", "Season") && m.seriesId != null) {
            // Episodes and seasons: a breadcrumb back up to the show, always on screen.
            TitleBreadcrumb(m, accent, solid = collapsed)
        } else {
            if (!collapsed) BackButton()
            m?.let { CollapsedTitleBar(collapsed, it.name, accent) { if (it.type == "Series") FollowBell(it.id, it.name) } }
        }
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
private fun Actions(item: Item, accent: Color, onChanged: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val data = item.userData
    val resumable = (data?.positionTicks ?: 0) > 0 && item.isPlayableVideo
    val pulse = rememberArrivalPulse(item.id)
    var castRemote by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Button(
            onClick = { if (item.isBook) navigator.open(Route.Reader(item.id)) else deps.play(scope, item, navigator = navigator) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
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
            val left = item.runtimeMinutes?.let { (it * (1f - item.progress)).toInt() }?.takeIf { it > 0 }
            val at = formatTicks(data!!.positionTicks)
            ResumeRing(item.progress, "Reprendre à $at" + (left?.let { " · reste ${formatRuntime(it)}" } ?: ""), accent, { deps.play(scope, item, navigator = navigator) })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            CastButton(deps.cast, tint = accent, onOpenRemote = { castRemote = true })
            val favorite = data?.favorite == true
            ActionIcon(if (favorite) Icons.Rounded.Check else Icons.Rounded.Add, "Ma liste", favorite, accent) { scope.launch { deps.jellyfin.setFavorite(item, !favorite) } }
            if (item.type == "Series") FollowBell(item.id, item.name)
            if (!item.isBook) {
                val played = data?.played == true
                ActionIcon(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.Visibility, if (played) "Vu" else "Marquer vu", played, accent) {
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
    if (castRemote) CastRemoteSheet(deps.cast) { castRemote = false }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, active: Boolean = false, accent: Color = Nflix.Red, onClick: () -> Unit) {
    Column(Modifier.width(74.dp).pressable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(icon, transitionSpec = { (fadeIn(tween(200)) + androidx.compose.animation.scaleIn(initialScale = 0.6f)) togetherWith fadeOut(tween(120)) }, label = "action-icon") { current ->
            Icon(current, label, tint = if (active) accent else Nflix.Text, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(item: Item, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 20.dp)) {
        item.taglines.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Nflix.Text.copy(alpha = 0.9f)); Spacer(Modifier.height(6.dp)) }
        item.overview?.let { text ->
            Text(
                text, style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clickable(onClickLabel = if (expanded) "Réduire" else "Lire la suite") { expanded = !expanded },
            )
            if (!expanded && text.length > 220) Text("Lire la suite", style = MaterialTheme.typography.labelLarge, color = Nflix.Text, modifier = Modifier.padding(top = 4.dp).clickable { expanded = true })
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
private fun Seasons(series: Item, accent: Color) {
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
        val season = seasons[index]
        SeasonTools(season, accent)
        AnimatedContent(
            index,
            transitionSpec = {
                val forward = targetState >= initialState
                (androidx.compose.animation.slideInHorizontally(tween(280)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(250))) togetherWith fadeOut(tween(150))
            },
            label = "season",
        ) { shown ->
            EpisodeList(series.id, seasons[shown.coerceIn(0, seasons.lastIndex)].id, accent)
        }
        UpcomingEpisodes(series, accent)
    }
}

@Composable
private fun EpisodeList(seriesId: String, seasonId: String, accent: Color = Nflix.Red) {
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
                val played = episode.userData?.played == true
                IconButton(onClick = { scope.launch { deps.jellyfin.setPlayed(episode, !played) } }) {
                    AnimatedContent(played, transitionSpec = { androidx.compose.animation.scaleIn(spring(dampingRatio = 0.45f)) + fadeIn() togetherWith fadeOut(tween(90)) }, label = "played") { on ->
                        Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (on) "Vu, marquer non vu" else "Marquer vu", tint = if (on) accent else Nflix.Muted)
                    }
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


/** Next episode to watch and what is left in the series. */
@Composable
private fun SeriesProgress(series: Item, accent: Color, modifier: Modifier = Modifier) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val next = rememberLoad("next-up", series.id) { deps.jellyfin.nextUpFor(series.id) }.value ?: return
    val left = series.userData?.unplayed
    Row(
        modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
            .pressable({ deps.play(scope, next, navigator = navigator) }, { navigator.open(Route.Item(next.id)) }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            NImage(deps.jellyfin.thumb(next, 320), Modifier.fillMaxSize())
            ProgressBar(next.progress, Modifier.align(Alignment.BottomCenter))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Prochain épisode", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(next.episodeLabel, next.name).joinToString(" · "), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (left != null && left > 0) Text("Encore $left épisode${if (left > 1) "s" else ""} à voir", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
        }
        Icon(Icons.Rounded.PlayArrow, "Lire le prochain épisode", tint = Color.White)
    }
}

/** Season header tools: watched count and "mark the whole season". */
@Composable
private fun SeasonTools(season: Item, accent: Color) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val played = (userData[season.id] ?: season.userData)?.played == true
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val unplayed = season.userData?.unplayed
        Text(
            when { played -> "Saison vue"; unplayed != null && unplayed > 0 -> "$unplayed épisode${if (unplayed > 1) "s" else ""} non vu${if (unplayed > 1) "s" else ""}"; else -> "" },
            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = {
            scope.launch {
                val episodes = deps.jellyfin.episodes(season.seriesId ?: return@launch, season.id)
                episodes.forEach { deps.jellyfin.setPlayed(it, !played) }
                deps.jellyfin.setPlayed(season, !played)
            }
        }) {
            Icon(if (played) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (played) "Marquer la saison non vue" else "Marquer la saison vue", color = accent)
        }
    }
}

/** Announced episodes from the release calendar, greyed with their estimated date on Nflix. */
@Composable
private fun UpcomingEpisodes(series: Item, accent: Color) {
    val deps = LocalDeps.current
    val today = remember { java.time.LocalDate.now() }
    val page = rememberLoad("upcoming", series.id) { runCatching { deps.alerts.calendar(today, today.plusDays(90)) }.getOrNull() }.value
    val id = org.jellyfin.mobile.nflix.alerts.AlertsRepository.normalize(series.id)
    val upcoming = page?.events.orEmpty().filter { !it.available && (it.seriesId == id || it.itemId == id) }.distinctBy { it.id }.take(6)
    if (upcoming.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("À venir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        upcoming.forEach { event ->
            val day = org.jellyfin.mobile.nflix.alerts.CalendarModel.day(event.nflixAt)
            UpcomingEpisodeCard(event.title, event.subtitle, "${org.jellyfin.mobile.nflix.alerts.CalendarModel.dayTitle(day, today)} · ${org.jellyfin.mobile.nflix.alerts.CalendarModel.confidenceLabel(event)}", accent)
        }
    }
}
