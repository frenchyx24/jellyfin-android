package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusPage
import org.jellyfin.mobile.nflix.data.HorusRepository
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.sharedPoster
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------

internal enum class HorusRowStyle { Poster, Square, Wide }

@Immutable
internal data class HorusHomeRow(
    val key: String,
    val title: String,
    val items: List<HorusMedia>,
    val style: HorusRowStyle = HorusRowStyle.Poster,
)

@Immutable
internal data class HorusHomeData(
    val hero: List<HorusMedia>,
    val rows: List<HorusHomeRow>,
    /** "rowKey/mediaKey" of the first card showing each title: only that one morphs into the detail page. */
    val shared: Set<String>,
)

internal val HorusMedia.horusKey: String get() = "${type.api}-$id"

/**
 * In-memory cache of the Horus discover page: coming back to the tab shows the last content at
 * once (no skeleton flash) while it refreshes, and quick requests keep their new status even though
 * the repository's discover pages are cached for a few minutes.
 */
internal object HorusHomeCache {
    private val pages = ConcurrentHashMap<String, HorusHomeData>()
    @Volatile var requests: List<HorusRequest>? = null

    /** Statuses changed from this page (quick "Demander"), by [horusKey]. Snapshot state: cards recompose. */
    val overrides = mutableStateMapOf<String, HorusStatus>()

    /** Elements that already played their entrance animation. */
    val appeared: MutableSet<String> = ConcurrentHashMap.newKeySet()

    operator fun get(key: String): HorusHomeData? = pages[key]
    operator fun set(key: String, data: HorusHomeData) {
        pages[key] = data
    }
}

internal fun HorusMedia.withOverride(): HorusMedia = HorusHomeCache.overrides[horusKey]?.let { copy(status = it) } ?: this

private fun CoroutineScope.horusFetch(block: suspend () -> HorusPage): Deferred<Result<List<HorusMedia>>> = async { runCatching { block().results } }

private fun horusReleaseOrder(media: HorusMedia): String = media.date ?: media.year?.let { "$it-12-31" } ?: "9999"

/** Loads every row of the discover page in parallel; fails only when Horus answered nothing at all. */
internal suspend fun loadHorusHome(horus: HorusRepository, type: HorusType?): HorusHomeData = coroutineScope {
    val rows = mutableListOf<HorusHomeRow>()
    val heroPool: List<HorusMedia>
    val results: List<Result<List<HorusMedia>>>
    if (type == null) {
        val trending = horusFetch { horus.trendingAll() }
        val movies = horusFetch { horus.trending(HorusType.Movie) }
        val series = horusFetch { horus.trending(HorusType.Tv) }
        val upMovies = horusFetch { horus.upcoming(HorusType.Movie) }
        val upSeries = horusFetch { horus.upcoming(HorusType.Tv) }
        val music = horusFetch { horus.trending(HorusType.Music) }
        val newMusic = horusFetch { horus.upcoming(HorusType.Music) }
        val books = horusFetch { horus.trending(HorusType.Book) }
        val popularBooks = horusFetch { horus.upcoming(HorusType.Book) }
        results = listOf(trending, movies, series, upMovies, upSeries, music, newMusic, books, popularBooks).map { it.await() }
        val list = results.map { it.getOrDefault(emptyList()) }
        val today = LocalDate.now().toString()
        val upcoming = (list[3] + list[4]).filter { (it.date ?: "9999") >= today }.sortedBy(::horusReleaseOrder)
        rows += HorusHomeRow("trending", "Tendances", list[0])
        rows += HorusHomeRow("movies", "Films populaires", list[1])
        rows += HorusHomeRow("series", "Séries populaires", list[2])
        rows += HorusHomeRow("upcoming", "À venir", upcoming, HorusRowStyle.Wide)
        rows += HorusHomeRow("music", "Albums tendances", list[5], HorusRowStyle.Square)
        rows += HorusHomeRow("music-new", "Nouveaux albums", list[6], HorusRowStyle.Square)
        rows += HorusHomeRow("books", "Livres tendances", list[7])
        rows += HorusHomeRow("books-popular", "Livres populaires", list[8])
        heroPool = list[0].ifEmpty { list[1] + list[2] }
    } else {
        val popular = horusFetch { horus.trending(type) }
        val upcoming = horusFetch { horus.upcoming(type) }
        val trending = if (type == HorusType.Movie || type == HorusType.Tv) horusFetch { horus.trendingAll() } else null
        results = listOfNotNull(popular, upcoming, trending).map { it.await() }
        val popularList = results[0].getOrDefault(emptyList())
        val upcomingList = results[1].getOrDefault(emptyList())
        val trendingList = results.getOrNull(2)?.getOrDefault(emptyList())?.filter { it.type == type }.orEmpty()
        when (type) {
            HorusType.Movie, HorusType.Tv -> {
                val noun = if (type == HorusType.Movie) "Films" else "Séries"
                val today = LocalDate.now().toString()
                rows += HorusHomeRow("trending", "Tendances", trendingList)
                rows += HorusHomeRow("popular", "$noun populaires", popularList)
                rows += HorusHomeRow("upcoming", "À venir", upcomingList.filter { (it.date ?: "9999") >= today }.sortedBy(::horusReleaseOrder), HorusRowStyle.Wide)
            }
            HorusType.Music -> {
                rows += HorusHomeRow("trending", "Albums tendances", popularList, HorusRowStyle.Square)
                rows += HorusHomeRow("new", "Nouvelles sorties", upcomingList, HorusRowStyle.Square)
            }
            else -> {
                rows += HorusHomeRow("trending", "Livres tendances", popularList)
                rows += HorusHomeRow("popular", "Livres populaires", upcomingList)
            }
        }
        heroPool = trendingList.ifEmpty { popularList }
    }
    if (results.none { it.isSuccess }) throw results.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IllegalStateException("Horus est injoignable")

    val withArt = heroPool.filter { it.backdrop != null }
    val hero = (withArt.ifEmpty { heroPool.filter { it.poster != null } }).distinctBy { it.horusKey }.take(8)
    val visibleRows = rows.map { row -> row.copy(items = row.items.distinctBy { it.horusKey }) }.filter { it.items.isNotEmpty() }
    val seen = HashSet<String>()
    val shared = HashSet<String>()
    visibleRows.forEach { row -> row.items.forEach { if (seen.add(it.horusKey)) shared += "${row.key}/${it.horusKey}" } }
    HorusHomeData(hero, visibleRows, shared)
}

// ---------------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------------

/** Fade + rise entrance, played once per element and skipped when system animations are off. */
@Composable
internal fun Modifier.horusAppear(key: String, delayMs: Int = 0): Modifier {
    val motion = rememberMotionEnabled()
    val played = remember(key) { !motion || key in HorusHomeCache.appeared }
    val anim = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            anim.animateTo(1f, tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing))
            HorusHomeCache.appeared += key
        }
    }
    return graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 36f
    }
}

/** Network image decoded at the card's size (no full-resolution bitmaps in long rows). */
@Composable
internal fun HorusSizedImage(
    url: String?,
    width: Dp,
    aspect: Float,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholderIcon: ImageVector? = Icons.Rounded.Movie,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val heightPx = (widthPx / aspect).roundToInt().coerceAtLeast(1)
    Box(modifier.background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (url == null) {
            if (placeholderIcon != null) Icon(placeholderIcon, null, tint = Nflix.Muted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
        } else {
            val request = remember(context, url, widthPx, heightPx) {
                ImageRequest.Builder(context).data(url).size(widthPx, heightPx).crossfade(220).build()
            }
            AsyncImage(model = request, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HorusPill(label: String, color: Color, icon: ImageVector?, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.92f), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** "Disponible" / "Demandé" corner badge of a poster; nothing for titles nobody asked for. */
@Composable
internal fun HorusAvailability(status: HorusStatus, modifier: Modifier = Modifier) {
    when (status) {
        HorusStatus.Available -> HorusPill("Disponible", Nflix.Success, Icons.Rounded.CheckCircle, modifier)
        HorusStatus.Partial -> HorusPill("Partiel", Nflix.Info, Icons.Rounded.CheckCircle, modifier)
        HorusStatus.Pending -> HorusPill("Demandé", Nflix.Warning, Icons.Rounded.HourglassTop, modifier)
        HorusStatus.Processing -> HorusPill("En cours", Nflix.Info, Icons.Rounded.HourglassTop, modifier)
        HorusStatus.Blocked -> HorusPill("Bloqué", Color.Gray, null, modifier)
        else -> Unit
    }
}

@Composable
private fun HorusRatingPill(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.66f)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, null, tint = Nflix.Warning, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(2.dp))
        Text(String.format(Locale.FRANCE, "%.1f", rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HorusPosterCard(media: HorusMedia, shared: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 124.dp) {
    val aspect = if (media.type == HorusType.Music) 1f else 2f / 3f
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width).pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).border(1.dp, Nflix.Outline, shape)) {
            HorusSizedImage(
                media.poster, width, aspect,
                Modifier.fillMaxSize().then(if (shared) Modifier.sharedPoster("horus-${media.type.api}-${media.id}") else Modifier),
                description = media.title,
            )
            HorusAvailability(media.status, Modifier.align(Alignment.TopStart).padding(6.dp))
            media.rating?.takeIf { it > 0 }?.let { HorusRatingPill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(media.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(media.subtitle, media.year).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val horusReleaseFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

/** Upcoming release: wide backdrop card with its date block. */
@Composable
internal fun HorusUpcomingCard(media: HorusMedia, onClick: () -> Unit, width: Dp = 264.dp) {
    val release = remember(media.date) { media.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(width).pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).border(1.dp, Nflix.Outline, shape)) {
            HorusSizedImage(media.backdrop ?: media.poster, width, 16f / 9f, Modifier.fillMaxSize(), description = media.title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 110f)))
            if (release != null) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Nflix.Red).padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(release.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        release.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.FRENCH).uppercase(Locale.FRENCH),
                        style = MaterialTheme.typography.labelSmall, color = Color.White,
                    )
                }
            }
            HorusAvailability(media.status, Modifier.align(Alignment.TopEnd).padding(8.dp))
            Text(
                media.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(media.type.label, release?.let { "Sortie le ${horusReleaseFormat.format(it)}" } ?: media.year).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val HorusDeclined = Color(0xFFFF6B7A)

/** Label, colour and icon of a request's progress, from the request and its media status. */
internal fun horusRequestState(request: HorusRequest): Triple<String, Color, ImageVector?> = when {
    request.status == 3 -> Triple("Refusée", HorusDeclined, null)
    request.status == 4 -> Triple("Échec", HorusDeclined, null)
    request.media.status == HorusStatus.Available || request.status == 5 -> Triple("Disponible", Nflix.Success, Icons.Rounded.CheckCircle)
    request.downloading -> Triple("Téléchargement", Nflix.Info, Icons.Rounded.HourglassTop)
    request.media.status == HorusStatus.Processing || request.media.status == HorusStatus.Partial -> Triple("En cours", Nflix.Info, Icons.Rounded.HourglassTop)
    request.status == 1 -> Triple("En attente", Nflix.Warning, Icons.Rounded.HourglassTop)
    request.status == 2 -> Triple("Approuvée", Color(0xFF8BD17C), Icons.Rounded.Check)
    else -> Triple("Demandée", Nflix.Muted, null)
}

internal fun HorusRequest.isInProgress(): Boolean = status != 3 && status != 5 && media.status != HorusStatus.Available

@Composable
internal fun HorusRequestTile(request: HorusRequest, onClick: () -> Unit, width: Dp = 116.dp) {
    val media = request.media
    val aspect = if (request.type == HorusType.Music) 1f else 2f / 3f
    val shape = RoundedCornerShape(10.dp)
    val (label, color, icon) = horusRequestState(request)
    Column(Modifier.width(width).pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).border(1.dp, Nflix.Outline, shape)) {
            HorusSizedImage(media.poster, width, aspect, Modifier.fillMaxSize(), description = media.title)
            if (request.is4k) HorusPill("4K", Color.Black.copy(alpha = 0.7f), null, Modifier.align(Alignment.TopEnd).padding(6.dp))
            val progress = request.progress
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp),
            ) {
                Column {
                    HorusPill(if (progress != null) "${(progress * 100).toInt()} %" else label, color, icon)
                    if (progress != null) {
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Nflix.Red))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(media.title.ifBlank { request.type.label }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(request.type.label, media.year).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Last card of the requests row: opens the full requests page. */
@Composable
internal fun HorusSeeAllTile(count: Int, onClick: () -> Unit, width: Dp = 116.dp) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(width).pressable(onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .background(Brush.verticalGradient(listOf(Nflix.Red.copy(alpha = 0.28f), Nflix.SurfaceHigh)))
                .border(1.dp, Nflix.Outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Inbox, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tout voir", style = MaterialTheme.typography.labelLarge, color = Color.White)
                if (count > 0) Text("$count demande${if (count > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            }
        }
    }
}

@Composable
internal fun HorusRequestSummary(requests: List<HorusRequest>, modifier: Modifier = Modifier) {
    val pending = requests.count { it.status == 1 }
    val working = requests.count { it.isInProgress() && it.status != 1 }
    val available = requests.count { it.media.status == HorusStatus.Available || it.status == 5 }
    Row(modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pending > 0) HorusPill("$pending en attente", Nflix.Warning, Icons.Rounded.HourglassTop)
        if (working > 0) HorusPill("$working en cours", Nflix.Info, Icons.Rounded.HourglassTop)
        if (available > 0) HorusPill("$available disponible${if (available > 1) "s" else ""}", Nflix.Success, Icons.Rounded.CheckCircle)
    }
}

// ---------------------------------------------------------------------------------------------
// Hero carousel
// ---------------------------------------------------------------------------------------------

internal const val HorusHeroHeightDp = 500

@Composable
internal fun HorusHeroSkeleton() {
    Skeleton(Modifier.fillMaxWidth().height(HorusHeroHeightDp.dp), RoundedCornerShape(0.dp))
}

/**
 * Large swipeable header over the trending titles: backdrop with parallax, scrims, title, overview
 * and the main actions ("Voir" when on Nflix, "Demander" otherwise, "Infos").
 */
@Composable
internal fun HorusHero(
    items: List<HorusMedia>,
    busy: Set<String>,
    onOpen: (HorusMedia) -> Unit,
    onWatch: (HorusMedia) -> Unit,
    onRequest: (HorusMedia) -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val motion = rememberMotionEnabled()
    val density = LocalDensity.current

    LaunchedEffect(items.size, dragged, motion) {
        if (items.size < 2 || dragged || !motion) return@LaunchedEffect
        while (true) {
            delay(7_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(700))
        }
    }

    Box(Modifier.fillMaxWidth().height(HorusHeroHeightDp.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].horusKey }) { page ->
            val media = items[page]
            val art = media.backdrop
            val context = LocalPlatformContext.current
            // Decode at screen width: TMDB backdrops are w1280.
            val widthPx = with(density) { 600.dp.roundToPx() }
            val request = remember(context, art ?: media.poster, widthPx) {
                ImageRequest.Builder(context).data(art ?: media.poster).size(widthPx, (widthPx * 9) / 16).crossfade(300).build()
            }
            Box(
                Modifier.fillMaxSize().background(Nflix.Surface).clickable { onOpen(media) }.graphicsLayer {
                    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                    translationX = offset * size.width * 0.35f
                    val zoom = 1.06f - 0.06f * (1f - offset.absoluteValue.coerceIn(0f, 1f))
                    scaleX = zoom
                    scaleY = zoom
                },
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    // Albums and books have no backdrop: their cover, blurred, fills the header.
                    modifier = Modifier.fillMaxSize().then(if (art == null) Modifier.blur(28.dp) else Modifier),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Nflix.Night.copy(alpha = 0.85f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.7f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night.copy(alpha = 0.75f), Nflix.Night))),
        )

        val current = items.getOrNull(pager.currentPage)?.withOverride() ?: return@Box
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 14.dp)) {
            AnimatedContent(current, transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(220)) }, contentKey = { it.horusKey }, label = "horus-hero-info") { media ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorusPill(media.type.label.uppercase(Locale.FRENCH), Nflix.Red, null)
                        HorusAvailability(media.status)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        media.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        media.rating?.takeIf { it > 0 }?.let {
                            Icon(Icons.Rounded.Star, null, tint = Nflix.Warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(String.format(Locale.FRANCE, "%.1f", it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("  •  ", style = MaterialTheme.typography.labelMedium, color = Nflix.Red)
                        }
                        Text(
                            listOfNotNull(media.subtitle, media.year, media.genres.take(2).joinToString(" · ").ifBlank { null }).joinToString("  •  "),
                            style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    media.overview?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.78f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val onNflix = current.jellyfinId != null && current.status in setOf(HorusStatus.Available, HorusStatus.Partial)
                val sending = current.horusKey in busy
                when {
                    onNflix -> HorusHeroButton("Voir", Icons.Rounded.PlayArrow, Color.White, Color.Black) { onWatch(current) }
                    current.requestable -> HorusHeroButton("Demander", Icons.Rounded.Send, Nflix.Red, Color.White, busy = sending) { onRequest(current) }
                    current.status in setOf(HorusStatus.Pending, HorusStatus.Processing) ->
                        HorusHeroButton("Déjà demandé", Icons.Rounded.HourglassTop, Color.White.copy(alpha = 0.16f), Color.White) { onOpen(current) }
                }
                HorusHeroButton("Infos", Icons.Rounded.Info, Color.White.copy(alpha = 0.16f), Color.White) { onOpen(current) }
            }
            if (items.size > 1) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                    items.indices.forEach { index ->
                        val active = index == pager.currentPage
                        val dot by animateDpAsState(if (active) 18.dp else 5.dp, label = "horus-dot")
                        Box(Modifier.height(5.dp).width(dot).clip(CircleShape).background(if (active) Nflix.Red else Color.White.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun HorusHeroButton(label: String, icon: ImageVector, container: Color, foreground: Color, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = foreground,
            disabledContainerColor = container.copy(alpha = 0.6f), disabledContentColor = foreground,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.height(46.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = foreground, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Section title with an optional "Tout voir" action (same look as the home rows). */
@Composable
internal fun HorusSectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 18.dp).clip(CircleShape).background(Nflix.Red))
        Spacer(Modifier.width(8.dp))
        Text(
            title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Nflix.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (onAction != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action ?: "Tout voir", style = MaterialTheme.typography.labelMedium, color = Nflix.Text)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = Nflix.Red, modifier = Modifier.size(10.dp))
            }
        }
    }
}
