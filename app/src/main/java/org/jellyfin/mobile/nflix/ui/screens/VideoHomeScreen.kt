package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.data.VideoCatalog
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SectionHeader
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.WideCard
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value
import kotlin.math.absoluteValue

private enum class VideoSection(val title: String) { ForYou("Pour vous"), Movies("Films"), Series("Séries"), Anime("Animés") }

private enum class GenreSort(val label: String, val sort: LibrarySort) { Recent("Récents", LibrarySort.Added), Rating("Mieux notés", LibrarySort.Rating), Name("A–Z", LibrarySort.Name) }

private enum class ShelfStyle { Poster, Wide }

private data class Shelf(val key: String, val title: String, val items: List<Item>, val style: ShelfStyle = ShelfStyle.Poster, val seeAll: Route? = null)

private data class SectionData(val hero: List<Item>, val shelves: List<Shelf>, val genres: List<String>)

/** Libraries and the anime catalogue, shared by every section. */
private class VideoContext(val movieView: Item?, val seriesView: Item?, val animeView: Item?, val anime: List<Item>) {
    val animeIds: Set<String> = anime.mapTo(HashSet()) { it.id }
    fun isAnime(item: Item) = item.id in animeIds || item.seriesId in animeIds || VideoCatalog.isAnime(item)
}

private object VideoCache {
    private const val TTL = 2 * 60_000L
    @Volatile private var context: Pair<Long, VideoContext>? = null
    private val sections = java.util.concurrent.ConcurrentHashMap<VideoSection, Pair<Long, SectionData>>()

    suspend fun context(deps: AppDeps, force: Boolean): VideoContext {
        context?.takeIf { !force && System.currentTimeMillis() - it.first < TTL }?.let { return it.second }
        val repo = deps.jellyfin
        val views = repo.views(force)
        val animeView = views.firstOrNull { it.collectionType in setOf("tvshows", "movies", "mixed", null) && VideoCatalog.isAnimeLibrary(it) }
        val movieView = views.firstOrNull { it.collectionType == "movies" && it.id != animeView?.id }
        val seriesView = views.firstOrNull { it.collectionType == "tvshows" && it.id != animeView?.id }
        val anime = runCatching {
            if (animeView != null) {
                repo.items(parentId = animeView.id, includeTypes = "Movie,Series", sort = LibrarySort.Added, limit = 400, force = force).items
            } else {
                // No anime library: "Anime" and "Animation" titles, then keep only Japanese animation.
                repo.items(includeTypes = "Movie,Series", sort = LibrarySort.Added, genre = "Anime|Animé|Animation", limit = 500, force = force).items.filter(VideoCatalog::isAnime)
            }
        }.getOrDefault(emptyList())
        return VideoContext(movieView, seriesView, animeView, anime).also { context = System.currentTimeMillis() to it }
    }

    fun cached(section: VideoSection): SectionData? = sections[section]?.second

    private val locks = java.util.concurrent.ConcurrentHashMap<VideoSection, kotlinx.coroutines.sync.Mutex>()

    /** One load per section at a time: the genre selector and the page share the same request. */
    suspend fun section(deps: AppDeps, section: VideoSection, force: Boolean): SectionData {
        sections[section]?.takeIf { !force && System.currentTimeMillis() - it.first < TTL }?.let { return it.second }
        val lock = locks.getOrPut(section) { kotlinx.coroutines.sync.Mutex() }
        return lock.withLock {
            sections[section]?.takeIf { !force && System.currentTimeMillis() - it.first < TTL }?.second
                ?: loadSection(deps, context(deps, force), section, force).also { sections[section] = System.currentTimeMillis() to it }
        }
    }
}

private fun heroOf(vararg lists: List<Item>): List<Item> =
    lists.flatMap { it.take(4) }.distinctBy { it.id }.filter { it.backdropTags.isNotEmpty() || it.parentBackdropTags.isNotEmpty() }.take(6)

private suspend fun loadSection(deps: AppDeps, ctx: VideoContext, section: VideoSection, force: Boolean): SectionData = coroutineScope {
    val repo = deps.jellyfin
    val resume = async { runCatching { repo.resume("Video", 30, force) }.getOrDefault(emptyList()) }
    val nextUp = async { runCatching { repo.nextUp(30, force) }.getOrDefault(emptyList()) }
    when (section) {
        VideoSection.Movies, VideoSection.Series -> {
            val movies = section == VideoSection.Movies
            val type = if (movies) "Movie" else "Series"
            val parent = if (movies) ctx.movieView?.id else ctx.seriesView?.id
            val fresh = async { repo.items(parentId = parent, includeTypes = type, sort = LibrarySort.Added, limit = 48, force = force).items.filterNot(ctx::isAnime).take(24) }
            val top = async {
                repo.items(parentId = parent, includeTypes = type, sort = LibrarySort.Rating, limit = 48, extra = mapOf("minCommunityRating" to 7), force = force).items.filterNot(ctx::isAnime).take(24)
            }
            val genres = VideoCatalog.sortGenres(repo.genres(parent, type))
            val genreShelves = genres.take(6).map { genre ->
                async {
                    val items = runCatching { repo.items(parentId = parent, includeTypes = type, sort = LibrarySort.Random, genre = genre, limit = 36, force = force).items }.getOrDefault(emptyList())
                    Shelf("genre-$genre", genre, items.filterNot(ctx::isAnime).take(20))
                }
            }
            val freshItems = fresh.await()
            val topItems = top.await()
            val resumeItems = resume.await().filter { (if (movies) it.type == "Movie" else it.type == "Episode") && !ctx.isAnime(it) }
            val shelves = buildList {
                add(Shelf("resume", "Reprendre", resumeItems, ShelfStyle.Wide))
                if (!movies) add(Shelf("next", "À suivre", nextUp.await().filterNot(ctx::isAnime), ShelfStyle.Wide))
                add(Shelf("fresh", if (movies) "Nouveaux films" else "Nouvelles séries", freshItems, seeAll = parent?.let { Route.Browse(it, if (movies) "Films" else "Séries", type) }))
                add(Shelf("top", if (movies) "Les mieux notés" else "Séries incontournables", topItems))
                addAll(genreShelves.awaitAll())
            }
            SectionData(heroOf(freshItems, topItems), shelves, genres)
        }
        VideoSection.Anime -> {
            val pool = ctx.anime
            val frequency = pool.flatMap { it.genres }.filterNot { VideoCatalog.isAnimeLabel(it) || VideoCatalog.isAnimation(it) }
                .groupingBy(VideoCatalog::normalize).eachCount()
            val genres = VideoCatalog.sortGenres(pool.flatMap { it.genres }.filterNot(VideoCatalog::isAnimation))
                .sortedByDescending { frequency[VideoCatalog.normalize(it)] ?: 0 }
            val top = pool.sortedByDescending { it.communityRating ?: 0.0 }.take(24)
            val shelves = buildList {
                add(Shelf("resume", "Reprendre", resume.await().filter(ctx::isAnime), ShelfStyle.Wide))
                add(Shelf("next", "Épisodes suivants", nextUp.await().filter(ctx::isAnime), ShelfStyle.Wide))
                add(Shelf("fresh", "Nouveaux animés", pool.take(24), seeAll = ctx.animeView?.let { Route.Browse(it.id, it.name, "Movie,Series") }))
                add(Shelf("series", "Séries animées", pool.filter { it.type == "Series" }.take(24)))
                add(Shelf("movies", "Films d’animation japonais", pool.filter { it.type == "Movie" }.take(24)))
                add(Shelf("top", "Les mieux notés", top))
                genres.take(6).forEach { genre -> add(Shelf("genre-$genre", genre, pool.filter { VideoCatalog.hasGenre(it, genre) }.shuffled().take(20))) }
            }
            SectionData(heroOf(pool, top), shelves, genres)
        }
        VideoSection.ForYou -> {
            val movies = async { repo.items(parentId = ctx.movieView?.id, includeTypes = "Movie", sort = LibrarySort.Added, limit = 36, force = force).items.filterNot(ctx::isAnime).take(20) }
            val series = async { repo.items(parentId = ctx.seriesView?.id, includeTypes = "Series", sort = LibrarySort.Added, limit = 36, force = force).items.filterNot(ctx::isAnime).take(20) }
            val top = async {
                repo.items(includeTypes = "Movie,Series", sort = LibrarySort.Rating, limit = 40, extra = mapOf("minCommunityRating" to 7.5), force = force).items.filterNot(ctx::isAnime).take(20)
            }
            val resumeItems = resume.await()
            val shelves = listOf(
                Shelf("resume", "Reprendre", resumeItems, ShelfStyle.Wide),
                Shelf("next", "À suivre", nextUp.await(), ShelfStyle.Wide),
                Shelf("movies", "Films récents", movies.await(), seeAll = ctx.movieView?.let { Route.Browse(it.id, "Films", "Movie") }),
                Shelf("series", "Séries récentes", series.await(), seeAll = ctx.seriesView?.let { Route.Browse(it.id, "Séries", "Series") }),
                Shelf("anime", "Animés", ctx.anime.take(20)),
                Shelf("top", "Les incontournables", top.await()),
            )
            SectionData(heroOf(movies.await(), series.await(), ctx.anime), shelves, emptyList())
        }
    }
}

private suspend fun loadGenre(deps: AppDeps, section: VideoSection, genre: String, sort: GenreSort): List<Item> {
    val ctx = VideoCache.context(deps, false)
    if (section == VideoSection.Anime) {
        val items = ctx.anime.filter { VideoCatalog.hasGenre(it, genre) }
        return when (sort) {
            GenreSort.Recent -> items
            GenreSort.Rating -> items.sortedByDescending { it.communityRating ?: 0.0 }
            GenreSort.Name -> items.sortedBy { VideoCatalog.normalize(it.name) }
        }
    }
    val movies = section == VideoSection.Movies
    return deps.jellyfin.items(
        parentId = if (movies) ctx.movieView?.id else ctx.seriesView?.id,
        includeTypes = if (movies) "Movie" else "Series",
        sort = sort.sort,
        genre = genre,
        limit = 240,
    ).items.filterNot(ctx::isAnime)
}

@Composable
fun VideoHomeScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var section by rememberSaveable { mutableStateOf(VideoSection.ForYou) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var genreSheet by remember { mutableStateOf(false) }
    // Genres of the visible section, for the selector (cached, so switching back is instant).
    val genres = rememberLoad("video-genres", section, initial = VideoCache.cached(section)?.genres) { force -> VideoCache.section(deps, section, force).genres }.value.orEmpty()

    if (genreSheet) GenreSheet(section, genres, genre, onPick = { genre = it; genreSheet = false }, onDismiss = { genreSheet = false })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vidéos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher") }
        }
        SectionTabs(section) { picked ->
            if (picked != section) {
                section = picked
                genre = null
            }
        }
        AnimatedVisibility(section != VideoSection.ForYou, enter = expandVertically(tween(260)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut()) {
            GenreSelector(genres, genre, onOpen = { genreSheet = true }, onPick = { genre = if (genre == it) null else it })
        }
        AnimatedContent(
            targetState = section to genre,
            transitionSpec = {
                val forward = targetState.first.ordinal > initialState.first.ordinal ||
                    (targetState.first == initialState.first && targetState.second != null)
                val sign = if (forward) 1 else -1
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { sign * it / 6 } + fadeIn(tween(260, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -sign * it / 8 } + fadeOut(tween(180)))
            },
            label = "video-page",
            modifier = Modifier.fillMaxSize(),
        ) { (current, currentGenre) ->
            if (currentGenre == null) SectionPage(current) else GenrePage(current, currentGenre) { genre = null }
        }
    }
}

@Composable
private fun SectionTabs(selected: VideoSection, onSelect: (VideoSection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VideoSection.entries, key = { it.name }) { option ->
            val chosen = option == selected
            val background by animateColorAsState(if (chosen) Nflix.Red else Nflix.SurfaceHigh, tween(240), label = "tab-bg")
            val scale by animateFloatAsState(if (chosen) 1.04f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "tab-scale")
            Box(
                Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(background)
                    .clickable { onSelect(option) }.padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(option.title, color = if (chosen) Color.White else Nflix.Muted, style = MaterialTheme.typography.labelLarge, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** The mini selector: a "Genre" button for the full list plus the most common genres one tap away. */
@Composable
private fun GenreSelector(genres: List<String>, selected: String?, onOpen: () -> Unit, onPick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item(key = "genre-button") {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected != null) Nflix.Red.copy(alpha = 0.22f) else Nflix.Surface)
                    .clickable(onClick = onOpen).padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tune, null, tint = if (selected != null) Nflix.Red else Nflix.Text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(selected ?: "Genres", style = MaterialTheme.typography.labelLarge, color = Nflix.Text)
                Icon(Icons.Rounded.ExpandMore, null, tint = Nflix.Muted, modifier = Modifier.size(18.dp))
            }
        }
        items(genres.take(10), key = { "quick-$it" }) { genre ->
            val chosen = VideoCatalog.normalize(genre) == selected?.let(VideoCatalog::normalize)
            val background by animateColorAsState(if (chosen) Nflix.Text else Color.Transparent, tween(200), label = "genre-bg")
            Text(
                genre,
                style = MaterialTheme.typography.labelMedium,
                color = if (chosen) Color.Black else Nflix.Muted,
                modifier = Modifier.animateItem().clip(CircleShape).background(background).clickable { onPick(genre) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GenreSheet(section: VideoSection, genres: List<String>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Genres · ${section.title}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GenreChip("Tous", selected == null, 0) { onPick(null) }
                genres.forEachIndexed { index, genre -> GenreChip(genre, VideoCatalog.normalize(genre) == selected?.let(VideoCatalog::normalize), index + 1) { onPick(genre) } }
            }
            if (genres.isEmpty()) Text("Aucun genre dans cette section.", color = Nflix.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, index: Int, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else Nflix.Text,
        modifier = Modifier.appear((index * 18).coerceAtMost(360)).clip(RoundedCornerShape(12.dp)).background(if (selected) Nflix.Red else Nflix.Surface)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun SectionPage(section: VideoSection) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    val load = rememberLoad("video-section", section, initial = VideoCache.cached(section)) { force -> VideoCache.section(deps, section, force) }
    val data = load.value
    when {
        load is Load.Failed && data == null -> EmptyState(Icons.Rounded.Info, "Vidéothèque indisponible", load.message)
        data == null -> Column {
            Skeleton(Modifier.padding(16.dp).fillMaxWidth().height(420.dp), RoundedCornerShape(22.dp))
            SkeletonRow(2, 240.dp, 16f / 9f)
        }
        data.hero.isEmpty() && data.shelves.all { it.items.isEmpty() } -> EmptyState(
            Icons.Rounded.Info,
            if (section == VideoSection.Anime) "Aucun animé trouvé" else "Rien à afficher",
            if (section == VideoSection.Anime) "Ajoutez le genre « Anime » ou une bibliothèque « Animés » sur le serveur." else "Ajoutez des contenus à votre bibliothèque.",
        )
        else -> LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            if (data.hero.isNotEmpty()) item(key = "hero") { HeroCarousel(data.hero, section) }
            data.shelves.filter { it.items.isNotEmpty() }.forEachIndexed { index, shelf ->
                item(key = "shelf-${shelf.key}", contentType = shelf.style) {
                    Column(Modifier.animateItem().appear((index * 50).coerceAtMost(300))) {
                        SectionHeader(shelf.title, onAction = shelf.seeAll?.let { route -> { navigator.open(route) } })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(shelf.items, key = { "${shelf.key}-${it.id}" }) { base ->
                                val item = userData[base.id]?.let { base.copy(userData = it) } ?: base
                                val origin = "video-${shelf.key}-${item.id}"
                                when (shelf.style) {
                                    ShelfStyle.Wide -> WideCard(
                                        title = item.seriesName ?: item.name,
                                        subtitle = listOfNotNull(item.episodeLabel, item.name.takeIf { item.seriesName != null }).joinToString(" · ").ifBlank { item.year?.toString() },
                                        imageUrl = deps.jellyfin.thumb(item, 640),
                                        progress = item.progress,
                                        origin = origin,
                                        onClick = { deps.play(scope, item, navigator = navigator) },
                                        width = 260.dp,
                                        typeLabel = if (shelf.key == "next") "ÉPISODE SUIVANT" else null,
                                    )
                                    ShelfStyle.Poster -> PosterCard(
                                        item = item,
                                        imageUrl = deps.jellyfin.primary(item, 360),
                                        origin = origin,
                                        onClick = { navigator.open(Route.Item(item.id, origin)) },
                                        subtitle = item.year?.toString(),
                                        typeLabel = when {
                                            section == VideoSection.ForYou && shelf.key == "anime" -> null
                                            section != VideoSection.Anime && VideoCatalog.isAnime(item) -> "ANIMÉ"
                                            section == VideoSection.Anime || section == VideoSection.ForYou -> if (item.type == "Series") "SÉRIE" else null
                                            else -> null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenrePage(section: VideoSection, genre: String, onClear: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var sort by rememberSaveable(section, genre) { mutableStateOf(GenreSort.Recent) }
    val load = rememberLoad("video-genre", section, genre, sort) { loadGenre(deps, section, genre, sort) }
    var shown by remember(section, genre) { mutableStateOf<List<Item>?>(null) }
    load.value?.let { shown = it }
    val items = shown
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = ContentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "genre-header") {
            Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Black)
                        Text(genre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(items?.let { "${it.size} titre${if (it.size > 1) "s" else ""}" } ?: "Chargement…", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    }
                    IconButton(onClick = onClear, modifier = Modifier.clip(CircleShape).background(Nflix.SurfaceHigh)) { Icon(Icons.Rounded.Close, "Retirer le genre") }
                }
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Nflix.Surface).padding(3.dp)) {
                    GenreSort.entries.forEach { option ->
                        val chosen = option == sort
                        val background by animateColorAsState(if (chosen) Nflix.SurfaceHigh else Color.Transparent, tween(200), label = "sort-bg")
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (chosen) Nflix.Text else Nflix.Muted,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background).clickable { sort = option }.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        when {
            items == null && load is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Info, "Chargement impossible", load.message) }
            items == null -> items(9, key = { "sk-$it" }) { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }
            items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Info, "Aucun titre", "Rien en $genre dans ${section.title.lowercase()}.") }
            else -> items(items, key = { it.id }) { item ->
                val origin = "genre-${item.id}"
                PosterCard(
                    item, deps.jellyfin.primary(item, 300), origin, { navigator.open(Route.Item(item.id, origin)) },
                    modifier = Modifier.animateItem().fillMaxWidth().appear(),
                    width = Dp.Unspecified,
                    typeLabel = if (item.type == "Series" && section == VideoSection.Anime) "SÉRIE" else null,
                )
            }
        }
    }
}

/** Auto-rotating hero with a slow Ken Burns zoom and the title logo when the server has one. */
@Composable
private fun HeroCarousel(items: List<Item>, section: VideoSection) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (items.size > 1) {
            delay(7_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
    }
    val zoom by rememberInfiniteTransition(label = "ken-burns").animateFloat(1f, 1.08f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    Box(Modifier.fillMaxWidth().height(470.dp)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { items[it].id }) { page ->
            val item = items[page]
            val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset * 0.5f }) {
                NImage(
                    deps.jellyfin.backdrop(item, 1400),
                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset * size.width * 0.25f },
                    description = item.name,
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.1f), 0.4f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.5f), 1f to Nflix.Night)))
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 34.dp).graphicsLayer { alpha = 1f - offset; translationY = offset * 40f },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when {
                            section == VideoSection.Anime || VideoCatalog.isAnime(item) -> "ANIMÉ"
                            item.type == "Series" -> "SÉRIE"
                            else -> "FILM"
                        },
                        style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.Black,
                    )
                    val logo = deps.jellyfin.logo(item, 600)
                    if (logo != null) {
                        NImage(logo, Modifier.height(78.dp).fillMaxWidth(0.72f), contentScale = ContentScale.Fit, description = item.name, placeholderIcon = null, background = Color.Transparent)
                    } else {
                        Text(item.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOfNotNull(item.year?.toString(), item.genres.take(2).joinToString(" · ").ifBlank { null }, item.officialRating).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { deps.play(scope, item, navigator = navigator) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (item.progress > 0f) "Reprendre" else "Lecture", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)).pressable({ navigator.open(Route.Item(item.id, "video-hero-${item.id}")) })
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Info, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Infos", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        org.jellyfin.mobile.nflix.ui.SeasonalParticles(Modifier.fillMaxSize(), intensity = 0.45f)
        if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.indices.forEach { index ->
                    val active = index == pager.currentPage
                    val width by androidx.compose.animation.core.animateDpAsState(if (active) 18.dp else 6.dp, spring(dampingRatio = 0.7f), label = "dot")
                    Box(Modifier.height(5.dp).width(width).clip(CircleShape).background(if (active) Nflix.Red else Color.White.copy(alpha = 0.35f)))
                }
            }
        }
    }
}
