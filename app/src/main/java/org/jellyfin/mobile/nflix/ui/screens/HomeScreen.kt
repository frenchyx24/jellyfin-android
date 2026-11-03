package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.WideCard
import org.jellyfin.mobile.nflix.ui.bottomScrim
import org.jellyfin.mobile.nflix.ui.formatRuntime
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.rowSection
import org.jellyfin.mobile.nflix.ui.value
import kotlin.math.absoluteValue

private data class HomeRow(val key: String, val title: String, val items: List<Item>, val wide: Boolean = false, val square: Boolean = false, val viewId: String? = null, val types: String? = null)

private data class HomeData(
    val hero: List<Item>,
    val rows: List<HomeRow>,
    val requests: List<HorusRequest>,
    val trending: List<HorusMedia>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val reloader = rememberReloader()
    val listState = rememberLazyListState()
    val userData by deps.jellyfin.userDataChanges.collectAsState()

    val data = rememberLoad(reloader = reloader) { force -> loadHome(deps, force) }
    val home = data.value

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { deps.jellyfin.invalidateAll(); reloader.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = ContentBottomPadding), modifier = Modifier.fillMaxSize()) {
            item(key = "hero", contentType = "hero") {
                if (home == null) Skeleton(Modifier.fillMaxWidth().height(560.dp), RoundedCornerShape(0.dp))
                else if (home.hero.isNotEmpty()) Hero(home.hero)
                else Spacer(Modifier.statusBarsPadding().height(72.dp))
            }
            item(key = "notices", contentType = "notices") { NoticeBanner() }
            if (home == null) {
                repeat(3) { index -> item(key = "skeleton-$index") { SkeletonRow(if (index == 0) 2 else 4, if (index == 0) 240.dp else 120.dp, if (index == 0) 16f / 9f else 2f / 3f) } }
            } else {
                home.rows.forEach { row ->
                    rowSection(
                        key = row.key,
                        title = row.title,
                        items = row.items,
                        itemKey = { it.id },
                        onSeeAll = row.viewId?.let { id -> { navigator.open(Route.Browse(id, row.title, row.types)) } },
                    ) { item ->
                        val origin = "${row.key}/${item.id}"
                        val merged = userData[item.id]?.let { item.copy(userData = it) } ?: item
                        when {
                            row.wide -> WideCard(
                                title = merged.seriesName ?: merged.name,
                                subtitle = listOfNotNull(merged.episodeLabel, if (merged.seriesName != null) merged.name else null).joinToString(" · ").ifBlank { merged.year?.toString() },
                                imageUrl = deps.jellyfin.thumb(merged),
                                progress = merged.progress,
                                origin = origin,
                                onClick = { navigator.open(Route.Item(merged.id, origin)) },
                            ) {
                                Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                                }
                            }
                            row.square -> PosterCard(merged, deps.jellyfin.primary(merged, 300), origin, { navigator.open(Route.Album(merged.id)) }, width = 140.dp, aspect = 1f, subtitle = merged.albumArtist)
                            else -> PosterCard(merged, deps.jellyfin.primary(merged, 300), origin, {
                                navigator.open(if (merged.type == "Book") Route.Item(merged.id, origin) else Route.Item(merged.id, origin))
                            })
                        }
                    }
                }
                rowSection("horus-requests", "Vos demandes Horus", home.requests, { "req-${it.id}" }, onSeeAll = { navigator.open(Route.HorusRequests) }) { request ->
                    Column {
                        HorusCard(request.media, onClick = {
                            val jellyfinId = request.media.jellyfinId
                            if (jellyfinId != null && request.media.status == org.jellyfin.mobile.nflix.data.HorusStatus.Available) navigator.open(Route.Item(jellyfinId))
                            else navigator.open(Route.HorusDetail(request.type, request.media.id))
                        })
                        Text(request.statusLabel, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                }
                rowSection("horus-trending", "Tendances à demander", home.trending, { "tr-${it.type.api}-${it.id}" }, onSeeAll = { navigator.selectTab(Route.Horus) }) { media ->
                    HorusCard(media, onClick = { navigator.open(Route.HorusDetail(media.type, media.id)) })
                }
            }
        }
        TopBar(scrolled = remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }.value)
    }
}

private suspend fun loadHome(deps: org.jellyfin.mobile.nflix.ui.AppDeps, force: Boolean): HomeData = coroutineScope {
    val jellyfin = deps.jellyfin
    val views = async { runCatching { jellyfin.views(force) }.getOrDefault(emptyList()) }
    val resume = async { runCatching { jellyfin.resume(force = force) }.getOrDefault(emptyList()) }
    val nextUp = async { runCatching { jellyfin.nextUp(force = force) }.getOrDefault(emptyList()) }
    val hero = async { runCatching { deps.plugins.heroItems(force) }.getOrDefault(emptyList()) }
    val sections = async { deps.plugins.homeSections() }
    val requests = async { runCatching { deps.horus.myRequests(force).take(15) }.getOrDefault(emptyList()) }
    val trending = async { runCatching { deps.horus.trendingAll().results.take(20) }.getOrDefault(emptyList()) }

    val rows = mutableListOf<HomeRow>()
    val resumeItems = resume.await()
    val next = nextUp.await()
    if (deps.plugins.moonfin.mergeContinueWatchingNextUp) {
        val merged = (resumeItems + next.filter { n -> resumeItems.none { it.seriesId != null && it.seriesId == n.seriesId } }).distinctBy { it.id }
        rows += HomeRow("resume", "Reprendre", merged, wide = true)
    } else {
        rows += HomeRow("resume", "Reprendre", resumeItems, wide = true)
        rows += HomeRow("nextup", "À suivre", next, wide = true)
    }
    sections.await().forEach { section ->
        val items = deps.plugins.homeSectionItems(section)
        if (items.isNotEmpty()) rows += HomeRow("section-${section.section}-${section.additionalData}", section.displayText, items)
    }
    val latest = views.await().filter { it.collectionType in setOf("movies", "tvshows", "music", "books", "boxsets") }.map { view ->
        async {
            val items = runCatching { jellyfin.latest(view.id, 20, force = force) }.getOrDefault(emptyList())
            HomeRow("latest-${view.id}", "Nouveautés · ${view.name}", items, square = view.collectionType == "music", viewId = view.id)
        }
    }
    rows += latest.map { it.await() }
    HomeData(hero.await(), rows.filter { it.items.isNotEmpty() }, requests.await(), trending.await())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(items: List<Item>) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { items.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    LaunchedEffect(dragged, items) {
        if (dragged || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(deps.plugins.mediaBar.shuffleInterval.coerceIn(4000, 20000))
            pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(900))
        }
    }
    Box(Modifier.fillMaxWidth().height(580.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].id }) { page ->
            val item = items[page]
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Box(Modifier.fillMaxSize().clickable { navigator.open(Route.Item(item.id, "hero/${item.id}")) }) {
                NImage(
                    deps.jellyfin.backdrop(item, 1280),
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = offset * size.width * 0.45f
                        val zoom = 1.08f - 0.08f * (1f - offset.absoluteValue.coerceIn(0f, 1f))
                        scaleX = zoom
                        scaleY = zoom
                    }.bottomScrim(0.25f),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Nflix.Night.copy(alpha = 0.55f), Color.Transparent), endY = 320f)))
            }
        }
        val current = items.getOrNull(pager.currentPage) ?: return@Box
        val merged = userData[current.id]?.let { current.copy(userData = it) } ?: current
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(merged, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }, contentKey = { it.id }, label = "hero-info") { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val logo = deps.jellyfin.logo(item)
                    if (logo != null) {
                        NImage(logo, Modifier.fillMaxWidth(0.7f).height(96.dp).background(Color.Transparent), contentScale = ContentScale.Fit, placeholderIcon = null)
                    } else {
                        Text(item.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        listOfNotNull(item.year?.toString(), item.genres.take(2).joinToString(" · ").ifBlank { null }, formatRuntime(item.runtimeMinutes), item.officialRating).joinToString("  •  "),
                        style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val favorite = merged.userData?.favorite == true
                FilledTonalButton(
                    onClick = { scope.launch { deps.jellyfin.setFavorite(merged, !favorite) } },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                ) {
                    AnimatedContent(favorite, label = "hero-fav") { fav -> Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Text("Ma liste")
                }
                Button(
                    onClick = { deps.play(scope, merged, navigator = navigator) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (merged.progress > 0f) "Reprendre" else "Lecture", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { navigator.open(Route.Item(merged.id, "hero/${merged.id}")) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Infos")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.indices.take(15).forEach { index ->
                    val active = index == pager.currentPage
                    val width by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
                    Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(if (active) Nflix.Red else Color.White.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun TopBar(scrolled: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val notices by deps.plugins.notices.collectAsState()
    val dismissed by deps.plugins.dismissed.collectAsState()
    val unread = notices.count { it.id !in dismissed }
    val alpha by animateFloatAsState(if (scrolled) 0.94f else 0f, tween(250), label = "topbar")
    Box(Modifier.fillMaxWidth().background(Nflix.Night.copy(alpha = alpha))) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NImage(deps.plugins.serverLogoUrl, Modifier.height(30.dp).width(92.dp).background(Color.Transparent), contentScale = ContentScale.Fit, placeholderIcon = null)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher", tint = Color.White) }
            Box {
                IconButton(onClick = { navigator.open(Route.Notices) }) { Icon(Icons.Rounded.Notifications, "Annonces", tint = Color.White) }
                if (unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).clip(CircleShape).background(Nflix.Red))
            }
            IconButton(onClick = { navigator.open(Route.Profile) }) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                    val userId = deps.session.userId
                    if (userId != null) NImage(deps.api.userImageUrl(userId, null), Modifier.fillMaxSize(), placeholderIcon = Icons.Rounded.Person)
                }
            }
        }
    }
}

/** Pinned NflixNotice announcement shown on the home screen. */
@Composable
private fun NoticeBanner() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val notices by deps.plugins.notices.collectAsState()
    val dismissed by deps.plugins.dismissed.collectAsState()
    val notice = notices.firstOrNull { it.pinned && it.id !in dismissed } ?: return
    val color = when (notice.level) {
        "urgent" -> Nflix.Red
        "warning" -> Nflix.Warning
        else -> Nflix.Info
    }
    Surface(
        color = Nflix.SurfaceHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable { navigator.open(Route.Notices) },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Campaign, null, tint = color)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(notice.message, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (notice.dismissible) IconButton(onClick = { deps.plugins.dismiss(notice) }) { Icon(Icons.Rounded.Close, "Masquer", tint = Nflix.Muted) }
        }
    }
}

@Suppress("unused")
private fun Modifier.posterAspect() = this.aspectRatio(2f / 3f)
