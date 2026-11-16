package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.nflix.data.CachedHome
import org.jellyfin.mobile.nflix.data.CachedHomeRow
import org.jellyfin.mobile.nflix.data.HomeCache
import timber.log.Timber
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.data.LibraryFilter
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.HorusStatusBadge
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.WideCard
import org.jellyfin.mobile.nflix.ui.formatRuntime
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.rowSection
import org.jellyfin.mobile.nflix.ui.value
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private enum class RowStyle { Poster, Wide, Square, Top10 }

private data class HomeRow(
    val key: String,
    val title: String,
    val items: List<Item>,
    val style: RowStyle = RowStyle.Poster,
    val viewId: String? = null,
    val types: String? = null,
)

private data class HomeData(
    val hero: List<Item>,
    val rows: List<HomeRow>,
    val requests: List<HorusRequest>,
    val upcoming: List<HorusMedia>,
    val trending: List<HorusMedia>,
    val distributors: List<Pair<org.jellyfin.mobile.nflix.data.Distributor, List<Item>>> = emptyList(),
)

private const val HeroHeightDp = 560

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val reloader = rememberReloader()
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val userData by deps.jellyfin.userDataChanges.collectAsState()

    // Reload after long-press actions (removed from "Reprendre", list changes) without flashing skeletons.
    val appContext = LocalContext.current.applicationContext
    // Last home shown: the disk snapshot first, then each progressive stage of the fresh load.
    var lastHome by remember {
        androidx.compose.runtime.mutableStateOf(HomeCache.peek(runCatching { deps.api.userId }.getOrNull())?.toHomeData())
    }
    val data = rememberLoad(org.jellyfin.mobile.nflix.ui.ItemMenu.revision, reloader = reloader) { force ->
        loadHome(deps, appContext, force || org.jellyfin.mobile.nflix.ui.ItemMenu.revision > 0) { lastHome = it }
    }
    data.value?.let { lastHome = it }
    val home = lastHome
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260 } }

    // Drop the spinner once the reload triggered by the swipe has really finished.
    LaunchedEffect(data) { if (data !is org.jellyfin.mobile.nflix.ui.Load.Loading) refreshing = false }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            deps.jellyfin.invalidateAll()
            reloader.reload()
            scope.launch {
                // Safety net: never leave the spinner up if the load hangs.
                kotlinx.coroutines.delay(15_000)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = ContentBottomPadding), modifier = Modifier.fillMaxSize()) {
            item(key = "hero", contentType = "hero") {
                val featured = home?.hero.orEmpty()
                when {
                    home == null && data !is Load.Failed -> Skeleton(Modifier.fillMaxWidth().height(HeroHeightDp.dp), RoundedCornerShape(0.dp))
                    featured.isNotEmpty() -> Hero(featured)
                    else -> Spacer(Modifier.statusBarsPadding().height(110.dp))
                }
            }
            item(key = "notices", contentType = "notices") { NoticeBanner() }
            when {
                home == null && data !is Load.Failed -> repeat(3) { index ->
                    item(key = "skeleton-$index") { SkeletonRow(if (index == 0) 2 else 4, if (index == 0) 240.dp else 120.dp, if (index == 0) 16f / 9f else 2f / 3f) }
                }
                data is Load.Failed -> item(key = "home-error") { EmptyState(Icons.Rounded.Info, "Accueil indisponible", data.message) }
                home != null -> {
                    // Distributor logos + rows sit right after "Nouveautés" (or at the end).
                    val distributorsAfter = home.rows.firstOrNull { it.key == "fresh" }?.key ?: home.rows.lastOrNull()?.key
                    if (distributorsAfter == null) distributorSection(home.distributors)
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
                            when (row.style) {
                                RowStyle.Wide -> WideCard(
                                    title = merged.seriesName ?: merged.name,
                                    subtitle = listOfNotNull(merged.episodeLabel, if (merged.seriesName != null) merged.name else null).joinToString(" · ").ifBlank { remainingLabel(merged) },
                                    imageUrl = deps.jellyfin.thumb(merged),
                                    progress = merged.progress,
                                    origin = origin,
                                    onClick = { deps.play(scope, merged, navigator = navigator) },
                                    onLongClick = { org.jellyfin.mobile.nflix.ui.ItemMenu.show(merged) },
                                    width = 260.dp,
                                ) {
                                    Box(
                                        Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.PlayArrow, "Lecture", tint = Color.White) }
                                }
                                RowStyle.Square -> PosterCard(merged, deps.jellyfin.primary(merged, 300), origin, { navigator.open(Route.Album(merged.id)) }, width = 140.dp, aspect = 1f, subtitle = merged.albumArtist)
                                RowStyle.Top10 -> TopTenCard(row.items.indexOf(item) + 1, merged, origin)
                                RowStyle.Poster -> PosterCard(merged, deps.jellyfin.primary(merged, 300), origin, { navigator.open(Route.Item(merged.id, origin)) }, subtitle = posterSubtitle(merged))
                            }
                        }
                        if (row.key == distributorsAfter) distributorSection(home.distributors)
                    }
                    rowSection("horus-upcoming", "Prochainement au cinéma", home.upcoming, { "up-${it.id}" }, onSeeAll = { navigator.selectTab(Route.Horus) }) { media ->
                        UpcomingCard(media)
                    }
                    rowSection("horus-requests", "Vos demandes", home.requests, { "req-${it.id}" }, onSeeAll = { navigator.open(Route.HorusRequests) }) { request ->
                        RequestMiniCard(request)
                    }
                    rowSection("horus-trending", "Tendances à demander", home.trending, { "tr-${it.type.api}-${it.id}" }, onSeeAll = { navigator.selectTab(Route.Horus) }) { media ->
                        HorusCard(media, onClick = { navigator.open(Route.HorusDetail(media.type, media.id)) })
                    }
                }
            }
        }
        // Festive layer over the hero only; it fades away once the user scrolls into the rows.
        val particlesAlpha by animateFloatAsState(if (scrolled) 0.45f else 1f, tween(500), label = "seasonal-particles")
        org.jellyfin.mobile.nflix.ui.SeasonalParticles(Modifier.fillMaxSize().graphicsLayer { alpha = particlesAlpha })
        TopBar(scrolled)
    }
}

private fun remainingLabel(item: Item): String? {
    val runtime = item.runtimeMinutes ?: return item.year?.toString()
    val left = (runtime * (1f - item.progress)).toInt()
    return if (item.progress > 0f && left > 0) "Reste ${formatRuntime(left)}" else item.year?.toString()
}

private fun posterSubtitle(item: Item): String? = listOfNotNull(
    when (item.type) {
        "Movie" -> "Film"
        "Series" -> "Série"
        "Episode" -> "Épisode"
        "Season" -> "Saison"
        "Book", "AudioBook" -> "Livre"
        "BoxSet" -> "Collection"
        else -> null
    },
    item.year?.toString(),
).joinToString(" · ").ifBlank { null }

private const val CORE_TIMEOUT_MS = 6_000L
private const val ROW_TIMEOUT_MS = 12_000L

/** Awaits at most [ms]; on timeout the request is cancelled so it never holds the home load open. */
private suspend fun <T> Deferred<T>.within(ms: Long): T? = withTimeoutOrNull(ms) { await() } ?: run { cancel(); null }

private fun CachedHome.toHomeData(): HomeData = HomeData(
    hero = hero,
    rows = rows.map { row ->
        HomeRow(row.key, row.title, row.items, runCatching { RowStyle.valueOf(row.style) }.getOrDefault(RowStyle.Poster), row.viewId, row.types)
    },
    requests = emptyList(),
    upcoming = emptyList(),
    trending = emptyList(),
)

private fun HomeData.toCache(userId: String): CachedHome = CachedHome(
    userId = userId,
    savedAt = System.currentTimeMillis(),
    hero = hero,
    rows = rows.map { CachedHomeRow(it.key, it.title, it.items, it.style.name, it.viewId, it.types) },
)

/**
 * Loads the home progressively: the disk snapshot first, then the core rows (resume, next up, newest, hero),
 * then everything else. A row that does not answer in time keeps its cached copy.
 */
private suspend fun loadHome(deps: AppDeps, context: Context, force: Boolean, emit: (HomeData) -> Unit): HomeData = coroutineScope {
    val started = System.currentTimeMillis()
    val jellyfin = deps.jellyfin
    val userId = runCatching { deps.api.userId }.getOrNull()
    val cached = HomeCache.read(context, userId)?.toHomeData()
    cached?.let(emit)
    val cachedRows = cached?.rows.orEmpty().associateBy { it.key }

    val views = async { runCatching { jellyfin.views(force) }.getOrDefault(emptyList()) }
    val resume = async { runCatching { jellyfin.resume(force = force) }.getOrDefault(emptyList()) }
    val nextUp = async { runCatching { jellyfin.nextUp(force = force) }.getOrDefault(emptyList()) }
    val hero = async { runCatching { deps.plugins.heroItems(force) }.getOrDefault(emptyList()) }
    val sections = async { deps.plugins.homeSections() }
    val fresh = async { runCatching { jellyfin.items(includeTypes = "Movie,Series", sort = LibrarySort.Added, limit = 24, force = force).items }.getOrDefault(emptyList()) }
    val episodes = async { runCatching { jellyfin.items(includeTypes = "Episode", sort = LibrarySort.Added, limit = 20, force = force).items }.getOrDefault(emptyList()) }
    val topRated = async {
        runCatching {
            jellyfin.items(includeTypes = "Movie,Series", sort = LibrarySort.Rating, limit = 40, extra = mapOf("minCommunityRating" to 6.5), force = force).items
        }.getOrDefault(emptyList())
    }
    val favorites = async { runCatching { jellyfin.items(includeTypes = "Movie,Series", filter = LibraryFilter.Favorites, limit = 30, force = force).items }.getOrDefault(emptyList()) }
    val requests = async { runCatching { deps.horus.myRequests(force).take(15) }.getOrDefault(emptyList()) }
    val upcoming = async { runCatching { deps.horus.upcoming(HorusType.Movie).results.filter { it.backdrop != null }.take(15) }.getOrDefault(emptyList()) }
    val trending = async { runCatching { deps.horus.trendingAll().results.take(20) }.getOrDefault(emptyList()) }
    // Distributor rows configured by an administrator ("Netflix", "Disney+"…), only those with titles.
    val distributorRows = async {
        runCatching {
            deps.admin.distributors(force = force).filter { it.enabled }
                .map { d -> async { d to runCatching { deps.admin.titles(d, limit = 1, force = force).items }.getOrDefault(emptyList()) } }
                .map { it.await() }.filter { it.second.isNotEmpty() }
        }.getOrDefault(emptyList())
    }
    // Plugin sections and per-library "latest" rows load in parallel with everything else.
    val sectionRows = async {
        runCatching { sections.await() }.getOrDefault(emptyList()).map { section ->
            async {
                val items = runCatching { deps.plugins.homeSectionItems(section) }.getOrDefault(emptyList())
                HomeRow("section-${section.section}-${section.additionalData}", section.displayText, items)
            }
        }.map { it.await() }
    }
    val latestRows = async {
        views.await().filter { it.collectionType in setOf("movies", "tvshows", "music", "books", "boxsets") }.map { view ->
            async {
                val items = runCatching { jellyfin.latest(view.id, 20, force = force) }.getOrDefault(emptyList())
                HomeRow("latest-${view.id}", "Ajouts récents · ${view.name}", items, if (view.collectionType == "music") RowStyle.Square else RowStyle.Poster, viewId = view.id)
            }
        }.map { it.await() }
    }

    fun HomeRow.orCached(): HomeRow = if (items.isEmpty()) cachedRows[key] ?: this else this
    fun cachedRow(key: String, title: String, style: RowStyle = RowStyle.Poster, viewId: String? = null, types: String? = null) =
        cachedRows[key] ?: HomeRow(key, title, emptyList(), style, viewId, types)

    // Stage 1: core rows, each bounded, so the top of the screen fills in fast.
    val resumeItems = resume.within(CORE_TIMEOUT_MS)
    val next = nextUp.within(CORE_TIMEOUT_MS)
    val freshItems = fresh.within(CORE_TIMEOUT_MS)
    val heroFetched = hero.within(CORE_TIMEOUT_MS)
    val coreRows = mutableListOf<HomeRow>()
    if (deps.plugins.moonfin.mergeContinueWatchingNextUp) {
        val merged = if (resumeItems == null && next == null) null else {
            val r = resumeItems.orEmpty()
            (r + next.orEmpty().filter { n -> r.none { it.seriesId != null && it.seriesId == n.seriesId } }).distinctBy { it.id }
        }
        coreRows += merged?.let { HomeRow("resume", "Reprendre la lecture", it, RowStyle.Wide) } ?: cachedRow("resume", "Reprendre la lecture", RowStyle.Wide)
    } else {
        coreRows += resumeItems?.let { HomeRow("resume", "Reprendre la lecture", it, RowStyle.Wide) } ?: cachedRow("resume", "Reprendre la lecture", RowStyle.Wide)
        coreRows += next?.let { HomeRow("nextup", "À suivre", it, RowStyle.Wide) } ?: cachedRow("nextup", "À suivre", RowStyle.Wide)
    }
    coreRows += freshItems?.let { HomeRow("fresh", "Nouveautés sur Nflix", it, viewId = "all", types = "Movie,Series") }
        ?: cachedRow("fresh", "Nouveautés sur Nflix", viewId = "all", types = "Movie,Series")
    // Hero: curated items from the server plugin, otherwise the newest titles that have artwork.
    val heroItems = heroFetched?.ifEmpty { null }
        ?: freshItems?.filter { it.backdropTags.isNotEmpty() || it.parentBackdropTags.isNotEmpty() }?.take(8)?.ifEmpty { null }
        ?: cached?.hero.orEmpty()
    heroItems.firstOrNull()?.let { first ->
        (jellyfin.backdrop(first, 1280) ?: jellyfin.primary(first, 900))?.let { url ->
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }
    val coreKeys = coreRows.map { it.key }.toSet()
    // Rows not loaded yet keep their cached copy meanwhile.
    val pending = cached?.rows.orEmpty().filter { it.key !in coreKeys }
    emit(
        HomeData(
            heroItems,
            (coreRows + pending).filter { it.items.isNotEmpty() }.distinctBy { it.key },
            cached?.requests.orEmpty(), cached?.upcoming.orEmpty(), cached?.trending.orEmpty(), cached?.distributors.orEmpty(),
        ),
    )
    Timber.i("Startup: home core rows in %d ms", System.currentTimeMillis() - started)

    // Stage 2: everything else; a row that times out keeps its cached copy.
    val rows = coreRows.toMutableList()
    rows += episodes.within(ROW_TIMEOUT_MS)?.let { HomeRow("episodes", "Nouveaux épisodes", it.distinctBy { e -> e.seriesId ?: e.id }, RowStyle.Wide) }
        ?: cachedRow("episodes", "Nouveaux épisodes", RowStyle.Wide)
    // Top 10: highest community rating among movies and series, unrated titles excluded.
    rows += topRated.within(ROW_TIMEOUT_MS)?.let { HomeRow("top", "Top 10 des mieux notés", it.filter { t -> (t.communityRating ?: 0.0) > 0 }.take(10), RowStyle.Top10) }
        ?: cachedRow("top", "Top 10 des mieux notés", RowStyle.Top10)
    rows += favorites.within(ROW_TIMEOUT_MS)?.let { HomeRow("favorites", "Ma liste", it, viewId = "all", types = "Movie,Series") }
        ?: cachedRow("favorites", "Ma liste", viewId = "all", types = "Movie,Series")
    rows += sectionRows.within(ROW_TIMEOUT_MS)?.map { it.orCached() } ?: cached?.rows.orEmpty().filter { it.key.startsWith("section-") }
    rows += latestRows.within(ROW_TIMEOUT_MS)?.map { it.orCached() } ?: cached?.rows.orEmpty().filter { it.key.startsWith("latest-") }

    val result = HomeData(
        heroItems,
        rows.filter { it.items.isNotEmpty() }.distinctBy { it.key },
        requests.within(ROW_TIMEOUT_MS).orEmpty(),
        upcoming.within(ROW_TIMEOUT_MS).orEmpty(),
        trending.within(ROW_TIMEOUT_MS).orEmpty(),
        distributorRows.within(ROW_TIMEOUT_MS).orEmpty(),
    )
    if (userId != null) HomeCache.write(context, result.toCache(userId))
    Timber.i("Startup: home complete in %d ms", System.currentTimeMillis() - started)
    result
}

/** "Plateformes" logo strip; each logo opens that platform's full catalogue. */
private fun androidx.compose.foundation.lazy.LazyListScope.distributorSection(rows: List<Pair<org.jellyfin.mobile.nflix.data.Distributor, List<Item>>>) {
    if (rows.isEmpty()) return
    item(key = "platforms") {
        Column {
            org.jellyfin.mobile.nflix.ui.SectionHeader("Plateformes")
            DistributorStrip(rows.map { it.first })
        }
    }
}

@Composable
private fun Hero(items: List<Item>) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { items.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val animations = rememberAnimationsEnabled()

    // Gentle auto-advance, paused while the user swipes and disabled when system animations are off.
    LaunchedEffect(items.size, dragged, animations) {
        if (items.size < 2 || dragged || !animations) return@LaunchedEffect
        while (true) {
            delay(8_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(700))
        }
    }

    Box(Modifier.fillMaxWidth().height(HeroHeightDp.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].id }) { page ->
            val item = items[page]
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            NImage(
                deps.jellyfin.backdrop(item, 1280) ?: deps.jellyfin.primary(item, 900),
                Modifier.fillMaxSize().clickable { navigator.open(Route.Item(item.id, "hero/${item.id}")) }.graphicsLayer {
                    translationX = offset * size.width * 0.35f
                    val zoom = 1.06f - 0.06f * (1f - offset.absoluteValue.coerceIn(0f, 1f))
                    scaleX = zoom
                    scaleY = zoom
                },
                contentScale = ContentScale.Crop,
                placeholderIcon = null,
            )
        }
        // Top shade keeps the status bar and top bar legible; bottom fade melts into the rows.
        Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night.copy(alpha = 0.7f), Nflix.Night))),
        )

        val current = items.getOrNull(pager.currentPage) ?: return@Box
        val merged = userData[current.id]?.let { current.copy(userData = it) } ?: current
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(merged, transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) }, contentKey = { it.id }, label = "hero-info") { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val logo = deps.jellyfin.logo(item)
                    if (logo != null) {
                        NImage(logo, Modifier.fillMaxWidth(0.72f).height(100.dp), contentScale = ContentScale.Fit, placeholderIcon = null, background = Color.Transparent)
                    } else {
                        Text(item.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        item.communityRating?.takeIf { it > 0 }?.let {
                            Icon(Icons.Rounded.Star, null, tint = Nflix.Warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(String.format(Locale.FRANCE, "%.1f", it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("  •  ", style = MaterialTheme.typography.labelMedium, color = Nflix.Red)
                        }
                        Text(
                            listOfNotNull(item.genres.take(3).joinToString("  •  ").ifBlank { null }, item.year?.toString(), formatRuntime(item.runtimeMinutes))
                                .joinToString("  •  "),
                            style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val favorite = merged.userData?.favorite == true
                HeroIconAction(if (favorite) Icons.Rounded.Check else Icons.Rounded.Add, "Ma liste") {
                    scope.launch { deps.jellyfin.setFavorite(merged, !favorite) }
                }
                Spacer(Modifier.width(22.dp))
                Button(
                    onClick = { deps.play(scope, merged, navigator = navigator) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (merged.progress > 0f) "Reprendre" else "Lecture", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(22.dp))
                HeroIconAction(Icons.Rounded.Info, "Infos") { navigator.open(Route.Item(merged.id, "hero/${merged.id}")) }
            }
            if (merged.progress > 0f) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(140.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(merged.progress).background(Nflix.Red))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.indices.take(12).forEach { index ->
                    val active = index == pager.currentPage
                    val width by animateDpAsState(if (active) 18.dp else 5.dp, label = "dot")
                    Box(Modifier.height(5.dp).width(width).clip(CircleShape).background(if (active) Nflix.Red else Color.White.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun HeroIconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** Large outlined rank number tucked behind the poster, streaming-service style. */
@Composable
private fun TopTenCard(rank: Int, item: Item, origin: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Box(Modifier.width(if (rank == 10) 186.dp else 160.dp).height(170.dp).pressable({ navigator.open(Route.Item(item.id, origin)) })) {
        Text(
            rank.toString(),
            style = TextStyle(fontSize = 150.sp, fontWeight = FontWeight.Black, drawStyle = Stroke(width = 6f), letterSpacing = (-10).sp),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomStart).offset(y = 30.dp),
        )
        NImage(
            deps.jellyfin.primary(item, 300),
            Modifier.align(Alignment.BottomEnd).width(113.dp).height(170.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(8.dp)),
            description = item.name,
        )
    }
}

private val releaseFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/** Upcoming theatrical release from Horus, shown as a trailer-like wide card. */
@Composable
private fun UpcomingCard(media: HorusMedia) {
    val navigator = LocalNavigator.current
    val release = media.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Column(Modifier.width(280.dp).pressable({ navigator.open(Route.HorusDetail(media.type, media.id)) })) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(10.dp))) {
            NImage(media.backdrop, Modifier.fillMaxSize(), description = media.title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 120f)))
            if (release != null) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Nflix.Red).padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(release.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Text(release.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.FRENCH).uppercase(Locale.FRENCH), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            HorusStatusBadge(media.status, Modifier.align(Alignment.TopEnd).padding(8.dp))
            Text(
                media.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(release?.let { "Sortie le ${releaseFormat.format(it)}" }, media.genres.take(2).joinToString(" · ").ifBlank { null }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RequestMiniCard(request: HorusRequest) {
    val navigator = LocalNavigator.current
    val media = request.media
    Column(Modifier.width(120.dp).pressable({
        val jellyfinId = media.jellyfinId
        if (jellyfinId != null && media.status == HorusStatus.Available) navigator.open(Route.Item(jellyfinId))
        else navigator.open(Route.HorusRequests)
    })) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (request.type == HorusType.Music) 1f else 2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(8.dp))) {
            NImage(media.poster, Modifier.fillMaxSize(), description = media.title)
            HorusStatusBadge(media.status, Modifier.align(Alignment.TopStart).padding(6.dp))
            request.progress?.let { progress ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(6.dp)) {
                    Column {
                        Text("${(progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Nflix.Red))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(media.title.ifBlank { request.type.label }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (request.downloading) "Téléchargement" else request.statusLabel,
            style = MaterialTheme.typography.bodySmall, color = if (request.downloading) Nflix.Info else Nflix.Muted, maxLines = 1,
        )
    }
}

@Composable
private fun TopBar(scrolled: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val notices by deps.plugins.notices.collectAsState()
    val dismissed by deps.plugins.dismissed.collectAsState()
    val read by deps.plugins.read.collectAsState()
    val unread = notices.count { it.id !in dismissed && it.id !in read }
    val alpha by animateFloatAsState(if (scrolled) 0.96f else 0f, tween(250), label = "topbar")
    Column(Modifier.fillMaxWidth().background(Nflix.Night.copy(alpha = alpha))) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            org.jellyfin.mobile.nflix.ui.AnimatedNflixLogo(deps.plugins.serverLogoUrl, compact = scrolled)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher", tint = Color.White) }
            IconButton(onClick = { navigator.open(Route.Releases) }) { Icon(Icons.Rounded.CalendarMonth, "Prochaines sorties", tint = Color.White) }
            Box {
                IconButton(onClick = { navigator.open(Route.Notices) }) { Icon(Icons.Rounded.Notifications, "Annonces", tint = Color.White) }
                if (unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).clip(CircleShape).background(Nflix.Red))
            }
            Box {
                IconButton(onClick = { navigator.open(Route.Profile) }) { Avatar() }
                if (org.jellyfin.mobile.nflix.family.FamilyBadges.total > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(Nflix.Red))
            }
        }
        org.jellyfin.mobile.nflix.ui.SeasonalGreeting(visible = !scrolled)
        // Category shortcuts: always labelled, so the user knows where each one leads.
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryPill("Séries") { navigator.open(Route.Browse("all", "Séries", "Series")) } }
            item { CategoryPill("Films") { navigator.open(Route.Browse("all", "Films", "Movie")) } }
            item { CategoryPill("Livres") { navigator.selectTab(Route.Books) } }
            item { CategoryPill("Musique") { navigator.selectTab(Route.Music) } }
            item { CategoryPill("Mes demandes") { navigator.open(Route.HorusRequests) } }
            item { CategoryPill("Succès") { navigator.open(Route.Badges) } }
        }
    }
}

@Composable
private fun CategoryPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White) }
}

/** Authentik order: attributes.avatar (relayed by Mira), then initials. Never an empty circle. */
@Composable
private fun Avatar() {
    val deps = LocalDeps.current
    val name = deps.session.miraName ?: deps.session.userName
    val miraAvatar = org.jellyfin.mobile.nflix.MiraProfile.safeAvatar(deps.session.miraAvatar)
    org.jellyfin.mobile.nflix.ui.AvatarImage(
        miraAvatar,
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Nflix.Red),
    ) {
        Text(org.jellyfin.mobile.nflix.MiraProfile.initials(name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** True unless the user turned system animations off (accessibility "remove animations"). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
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
        shape = RoundedCornerShape(14.dp),
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
