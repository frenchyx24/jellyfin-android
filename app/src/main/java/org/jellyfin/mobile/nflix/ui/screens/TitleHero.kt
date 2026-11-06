package org.jellyfin.mobile.nflix.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.Badge
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.formatRuntime
import org.jellyfin.mobile.nflix.ui.sharedPoster
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** Accent colour of a title, taken from its artwork; pure so it can be tested. */
object AccentColor {
    /**
     * Picks the dominant vivid hue from ARGB [pixels]: pixels are bucketed by hue and weighted by
     * saturation, then the result is brightened enough to read on black. Null for grey artwork.
     */
    fun from(pixels: IntArray): Color? {
        val buckets = DoubleArray(24)
        val sums = Array(24) { DoubleArray(3) }
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 128) continue
            val r = ((p shr 16) and 0xFF) / 255.0
            val g = ((p shr 8) and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val chroma = hi - lo
            if (chroma < 0.18 || hi < 0.15) continue
            val hue = when (hi) {
                r -> ((g - b) / chroma).mod(6.0)
                g -> (b - r) / chroma + 2
                else -> (r - g) / chroma + 4
            } * 60
            val index = (hue / 15).toInt().coerceIn(0, 23)
            val weight = chroma * chroma
            buckets[index] += weight
            sums[index][0] += r * weight; sums[index][1] += g * weight; sums[index][2] += b * weight
        }
        val best = buckets.indices.maxByOrNull { buckets[it] } ?: return null
        if (buckets[best] <= 0.0 || buckets[best] < pixels.size * 0.002) return null
        val w = buckets[best]
        var color = Color((sums[best][0] / w).toFloat(), (sums[best][1] / w).toFloat(), (sums[best][2] / w).toFloat())
        // Bring very dark accents up so buttons and halos stay visible on black.
        var guard = 0
        while (color.luminance() < 0.18f && guard++ < 6) color = Color(min(1f, color.red * 1.35f + 0.04f), min(1f, color.green * 1.35f + 0.04f), min(1f, color.blue * 1.35f + 0.04f))
        return color
    }
}

private val accentCache = ConcurrentHashMap<String, Int>()

/** Accent for [url], from cache or computed off the main thread; Nflix red until known. */
@Composable
fun rememberAccent(url: String?): Color {
    val context = LocalContext.current
    val state = produceState(url?.let { accentCache[it]?.let(::Color) } ?: Nflix.Red, url) {
        val source = url ?: return@produceState
        accentCache[source]?.let { value = Color(it); return@produceState }
        val color = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(source).size(48).allowHardware(false).build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
                val bitmap = result.image.toBitmap()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                AccentColor.from(pixels)
            }.getOrNull()
        } ?: Nflix.Red
        if (accentCache.size > 300) accentCache.clear()
        accentCache[source] = android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        value = color
    }
    val animated by animateColorAsState(state.value, tween(600), label = "accent")
    return animated
}

/** True unless the user turned system animations off. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

private fun unmetered(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

/** Labels shown for anime: dub/sub availability, studio, airing status and schedule. */
object AnimeInfo {
    private val FRENCH = setOf("FRE", "FRA", "FR", "FRENCH")

    fun isAnime(item: Item): Boolean = item.genres.any { it.equals("Anime", true) || it.equals("Animé", true) || it.equals("Animes", true) } ||
        item.tags.any { it.equals("anime", true) } ||
        (item.genres.any { it.startsWith("Anim", true) } && item.productionLocations.any { it.contains("Japan", true) || it.contains("Japon", true) })

    /** "VF", "VOSTFR" or both, from the item's streams; empty when unknown. */
    fun versions(item: Item): List<String> {
        val streams = item.mediaSources.firstOrNull()?.streams ?: item.mediaStreams
        val audio = streams.filter { it.type == "Audio" }.mapNotNull { it.language?.uppercase() }.toSet()
        val subs = streams.filter { it.type == "Subtitle" }.mapNotNull { it.language?.uppercase() }.toSet()
        return buildList {
            if (audio.any { it in FRENCH }) add("VF")
            if (audio.any { it !in FRENCH } && subs.any { it in FRENCH }) add("VOSTFR")
        }
    }

    private val DAYS = mapOf("Monday" to "lundi", "Tuesday" to "mardi", "Wednesday" to "mercredi", "Thursday" to "jeudi", "Friday" to "vendredi", "Saturday" to "samedi", "Sunday" to "dimanche")

    /** "Chaque samedi à 17h30" for a continuing series with a known schedule. */
    fun schedule(item: Item): String? {
        if (!item.status.equals("Continuing", true)) return null
        val days = item.airDays.mapNotNull { DAYS[it] }
        if (days.isEmpty()) return "En cours de diffusion"
        val time = item.airTime?.let { raw -> Regex("(\\d{1,2}):(\\d{2})").find(raw)?.let { "${it.groupValues[1].padStart(2, '0')}h${it.groupValues[2]}" } }
        return "Chaque ${days.joinToString(", ")}" + (time?.let { " à $it" } ?: "")
    }
}

/** Full-bleed header: muted trailer when possible, slow Ken Burns artwork otherwise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinematicHeader(item: Item, origin: String?, scroll: Float, accent: Color, trailerUrl: String?) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val motion = rememberMotionEnabled()
    var showTrailer by remember(item.id) { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    LaunchedEffect(item.id, trailerUrl) {
        showTrailer = false
        if (trailerUrl != null && motion && unmetered(context)) {
            delay(2200)
            showTrailer = true
        }
    }
    val kenBurns = rememberInfiniteTransition(label = "ken-burns")
    val zoom by kenBurns.animateFloat(1f, if (motion) 1.12f else 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val pan by kenBurns.animateFloat(-1f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "pan")
    val logoIn = remember(item.id) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(item.id) { logoIn.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val dim = (scroll / 900f).coerceIn(0f, 0.75f)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = if (maxWidth > 600.dp) 520.dp else 600.dp
        Box(Modifier.fillMaxWidth().height(height)) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = scroll * 0.45f }) {
                NImage(
                    deps.jellyfin.backdrop(item) ?: deps.jellyfin.primary(item, 900),
                    Modifier.fillMaxSize().sharedPoster(origin ?: "detail/${item.id}")
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan * size.width * 0.03f },
                    contentScale = ContentScale.Crop,
                    placeholderIcon = null,
                )
                androidx.compose.animation.AnimatedVisibility(showTrailer && trailerUrl != null, enter = fadeIn(tween(900)), exit = fadeOut(tween(400))) {
                    trailerUrl?.let { HeroTrailer(it, muted) { showTrailer = false } }
                }
            }
            // Layers: top scrim for the status bar, accent glow, side vignette, deep bottom fade.
            Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), radius = 1400f)))
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.7f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.10f), Nflix.Night.copy(alpha = 0.85f), Nflix.Night))),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
                    .graphicsLayer { alpha = (1f - scroll / 700f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item.seriesName?.let { series ->
                    Text(
                        series.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { item.seriesId?.let { navigator.open(Route.Item(it)) } }.padding(4.dp),
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer {
                    val p = logoIn.value
                    alpha = p.coerceIn(0f, 1f); scaleX = 0.82f + 0.18f * p; scaleY = 0.82f + 0.18f * p; translationY = (1f - p) * 40f
                }) {
                    // Halo of the title colour behind the logo.
                    Box(Modifier.size(260.dp, 120.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))))
                    val logo = deps.jellyfin.logo(item)
                    if (logo != null && item.type != "Episode") {
                        NImage(logo, Modifier.fillMaxWidth(0.78f).height(110.dp), contentScale = ContentScale.Fit, placeholderIcon = null, background = Color.Transparent)
                    } else {
                        Text(
                            if (item.type == "Episode") listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.name,
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HeroMeta(item, accent)
            }
            if (showTrailer) {
                IconButton(
                    onClick = { muted = !muted },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 140.dp).size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                ) { Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Activer le son" else "Couper le son") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroMeta(item: Item, accent: Color) {
    val deps = LocalDeps.current
    val anime = AnimeInfo.isAnime(item)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (item.genres.isNotEmpty()) Text(item.genres.take(3).joinToString(" • "), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        item.communityRating?.let { Text("★ %.1f".format(it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Nflix.Warning) }
        item.year?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        item.episodeLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        formatRuntime(item.runtimeMinutes)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
        if (item.type == "Series" && (item.childCount ?: 0) > 0) Text("${item.childCount} saison${if ((item.childCount ?: 0) > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
        item.officialRating?.let { Badge(it, Color.White.copy(alpha = 0.16f)) }
        item.maxHeight?.let { h -> Badge(when { h >= 2000 -> "4K"; h >= 1000 -> "HD"; else -> "SD" }, Color.White.copy(alpha = 0.16f)) }
        if (anime) {
            Badge("Animé", accent.copy(alpha = 0.35f))
            AnimeInfo.versions(item).forEach { Badge(it, accent.copy(alpha = 0.22f)) }
        } else if (deps.plugins.enhanced.showAudioLanguages && item.audioLanguages.isNotEmpty()) {
            Badge(item.audioLanguages.take(4).joinToString(" · "), Color.White.copy(alpha = 0.16f))
        }
    }
    if (anime || item.type == "Series") {
        val parts = listOfNotNull(if (anime) item.studios.firstOrNull()?.name else null, AnimeInfo.schedule(item))
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.85f))
            }
        }
    }
}

/** Muted, looping trailer behind the header; released as soon as it leaves composition. */
@OptIn(UnstableApi::class)
@Composable
private fun HeroTrailer(url: String, muted: Boolean, onFailed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onFailed()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Bande-annonce en lecture" },
    )
}

/** Compact bar that slides in once the big title has scrolled away. */
@Composable
fun CollapsedTitleBar(visible: Boolean, title: String, accent: Color, trailing: @Composable () -> Unit = {}) {
    val navigator = LocalNavigator.current
    AnimatedVisibility(visible, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Nflix.Night, Nflix.Night.copy(alpha = 0.92f))))
                .statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.size(4.dp, 22.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/** "Reprendre à 42 min" with an animated progress ring around the play symbol. */
@Composable
fun ResumeRing(progress: Float, label: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(progress) { ring.animateTo(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * ring.value, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${(progress * 100).toInt()} % vu", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

/** One soft pulse of the play button when the page opens. */
@Composable
fun rememberArrivalPulse(key: Any): Float {
    val motion = rememberMotionEnabled()
    val pulse = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        if (!motion) return@LaunchedEffect
        delay(450)
        pulse.animateTo(1.05f, tween(260))
        pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
    }
    return pulse.value
}

/** Future episode announced in the release calendar, greyed with its estimated date. */
@Composable
fun UpcomingEpisodeCard(title: String, subtitle: String, whenText: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface.copy(alpha = 0.6f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), Nflix.SurfaceHigh), Offset.Zero, Offset.Infinite)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Schedule, null, tint = Nflix.Muted)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(subtitle.ifBlank { title }, style = MaterialTheme.typography.titleSmall, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = accent)
        }
    }
}
