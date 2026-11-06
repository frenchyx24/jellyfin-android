package org.jellyfin.mobile.player.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Dimension
import coil3.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.qualityoptions.QualityOption
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.coil.SubsetTransformation
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.Locale

/** Observable player state the fragment feeds to the Compose interface. */
@Stable
class NflixPlayerUiState {
    var controlsVisible by mutableStateOf(false)
    var source by mutableStateOf<JellyfinMediaSource?>(null)
    var hasNext by mutableStateOf(false)
    var decoder by mutableStateOf<DecoderType?>(null)
    var speed by mutableFloatStateOf(1f)
    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    var buffering by mutableStateOf(false)

    /** Bumped after a track change so the panels re-read the selected streams. */
    var revision by mutableIntStateOf(0)
}

/** Everything the interface can ask the player to do. */
interface NflixPlayerActions {
    fun player(): Player?
    fun back()
    fun keepAlive()
    fun holdControls(hold: Boolean)
    fun showControls()
    fun rewind()
    fun forward()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun lock()
    fun rotate()
    fun pip()
    fun setResizeMode(mode: Int)
    fun selectAudio(index: Int)
    fun selectSubtitle(index: Int)
    fun setSpeed(speed: Float)
    fun setBitrate(bitrate: Int?)
    fun setDecoder(type: DecoderType)
    fun qualityOptions(): List<QualityOption>
    fun imageUrl(source: JellyfinMediaSource, kind: ArtKind): String?
}

enum class ArtKind { Backdrop, Poster, Logo }

private enum class Panel { Tracks, Speed, Quality, Format, Chapters, Info }

private val Accent: Color get() = org.jellyfin.mobile.nflix.ui.Nflix.Red
private val PanelColor = Color(0xF2121214)
private val Muted = Color(0xFFA6A6B4)

/** Trickplay tiles (server-generated thumbnails) for the seek preview. */
class TrickplayThumbnails(private val context: Context, private val api: ApiClient, private val imageLoader: ImageLoader) {
    fun available(source: JellyfinMediaSource?) = source?.item?.trickplay?.get(source.id)?.values?.firstOrNull() != null

    fun tileOf(source: JellyfinMediaSource, positionMs: Long): Int {
        val info = source.item?.trickplay?.get(source.id)?.values?.firstOrNull() ?: return -1
        return positionMs.floorDiv(info.interval.coerceAtLeast(1)).toInt()
    }

    suspend fun load(source: JellyfinMediaSource, tile: Int): Bitmap? {
        val item = source.item ?: return null
        val info = item.trickplay?.get(source.id)?.values?.firstOrNull() ?: return null
        val mediaSourceId = source.id.toUUIDOrNull() ?: return null
        val perTile = info.tileWidth * info.tileHeight
        val offset = tile % perTile
        val url = api.trickplayApi.getTrickplayTileImageUrl(itemId = item.id, width = info.width, index = tile / perTile, mediaSourceId = mediaSourceId)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(coil3.size.Size.ORIGINAL)
            .maxBitmapSize(coil3.size.Size(Dimension.Undefined, Dimension.Undefined))
            .httpHeaders(
                NetworkHeaders.Builder().set(
                    "Authorization",
                    AuthorizationHeaderBuilder.buildHeader(api.clientInfo.name, api.clientInfo.version, api.deviceInfo.id, api.deviceInfo.name, api.accessToken),
                ).build(),
            )
            .transformations(SubsetTransformation((offset % info.tileWidth) * info.width, (offset / info.tileWidth) * info.height, info.width, info.height))
            .build()
        return imageLoader.execute(request).image?.toBitmap()
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun JellyfinMediaSource.titles(): Pair<String, String?> {
    val item = item
    return if (item?.type == BaseItemKind.EPISODE && item.seriesName != null) {
        val code = listOfNotNull(item.parentIndexNumber?.let { "S$it" }, item.indexNumber?.let { "É$it" }).joinToString(":")
        item.seriesName!! to listOfNotNull(code.ifBlank { null }, item.name).joinToString(" · ")
    } else {
        (item?.name ?: sourceInfo.name.orEmpty()) to listOfNotNull(item?.productionYear?.toString(), item?.officialRating).joinToString(" · ").ifBlank { null }
    }
}

@Composable
fun NflixPlayerUi(state: NflixPlayerUiState, actions: NflixPlayerActions, thumbnails: TrickplayThumbnails) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playWhenReady by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val source = state.source

    // Player clock: fast while the controls show, slow otherwise.
    LaunchedEffect(Unit) {
        while (true) {
            actions.player()?.let { p ->
                position = p.currentPosition
                duration = p.duration.takeIf { it > 0 } ?: source?.runTime?.inWholeMilliseconds ?: 0L
                buffered = p.bufferedPosition
                playWhenReady = p.playWhenReady
                ready = p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING
            }
            delay(if (state.controlsVisible || panel != null) 200 else 600)
        }
    }
    LaunchedEffect(panel) { actions.holdControls(panel != null) }

    // Pause screen: after the controls fade, a paused film shows its title card.
    var pauseShown by remember { mutableStateOf(false) }
    val paused = ready && !playWhenReady
    LaunchedEffect(paused, state.controlsVisible, panel) {
        pauseShown = false
        if (paused && !state.controlsVisible && panel == null) {
            delay(900)
            pauseShown = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(pauseShown && source != null, enter = fadeIn(tween(700)), exit = fadeOut(tween(250))) {
            if (source != null) PauseScreen(source, position, duration, actions)
        }

        AnimatedVisibility(state.controlsVisible && panel == null, enter = fadeIn(tween(180)), exit = fadeOut(tween(260))) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.78f),
                        0.22f to Color.Black.copy(alpha = 0.25f),
                        0.6f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            )
        }
        // Top bar
        AnimatedVisibility(
            state.controlsVisible && panel == null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(source, actions)
        }
        // Centre transport
        AnimatedVisibility(
            state.controlsVisible && panel == null,
            enter = fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 0.85f),
            exit = fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Transport(playWhenReady, state.buffering, state.hasNext, actions)
        }
        // Bottom: seek bar and tools
        AnimatedVisibility(
            state.controlsVisible && panel == null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(state, source, position, duration, buffered, actions, thumbnails) { panel = it }
        }
        // Next episode shortcut near the end, even with the controls hidden.
        val nearEnd = state.hasNext && duration > 0 && duration - position in 1..45_000
        AnimatedVisibility(
            nearEnd && panel == null,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 24.dp, bottom = if (state.controlsVisible) 128.dp else 32.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { actions.next() }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Épisode suivant", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Side panels
        AnimatedVisibility(panel != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { panel = null },
            )
        }
        AnimatedContent(
            panel,
            transitionSpec = { slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn() togetherWith slideOutHorizontally(tween(260)) { it } + fadeOut() },
            label = "player-panel",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { current ->
            if (current != null && source != null) {
                Box(
                    Modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth(0.92f)
                        .background(PanelColor)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (current) {
                                    Panel.Tracks -> "Audio et sous-titres"
                                    Panel.Speed -> "Vitesse de lecture"
                                    Panel.Quality -> "Qualité et décodage"
                                    Panel.Format -> "Format de l’image"
                                    Panel.Chapters -> "Chapitres"
                                    Panel.Info -> "Moteur de lecture"
                                },
                                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f),
                            )
                            IconCircle(Icons.Rounded.Close, "Fermer") { panel = null }
                        }
                        Spacer(Modifier.height(14.dp))
                        when (current) {
                            Panel.Tracks -> TracksPanel(state, source, actions)
                            Panel.Speed -> SpeedPanel(state, actions)
                            Panel.Quality -> QualityPanel(state, source, actions)
                            Panel.Format -> FormatPanel(state, actions)
                            Panel.Chapters -> ChaptersPanel(source, position, actions) { panel = null }
                            Panel.Info -> InfoPanel(state, source)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(source: JellyfinMediaSource?, actions: NflixPlayerActions) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") { actions.back() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val (title, subtitle) = source?.titles() ?: ("" to null)
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconCircle(Icons.Rounded.PictureInPictureAlt, "Image dans l’image") { actions.pip() }
        IconCircle(Icons.Rounded.ScreenRotation, "Pivoter") { actions.rotate(); actions.keepAlive() }
        IconCircle(Icons.Rounded.Lock, "Verrouiller") { actions.lock() }
    }
}

@Composable
private fun Transport(playing: Boolean, buffering: Boolean, hasNext: Boolean, actions: NflixPlayerActions) {
    val scope = rememberCoroutineScope()
    val rewindSpin = remember { Animatable(0f) }
    val forwardSpin = remember { Animatable(0f) }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.SkipPrevious, "Précédent", size = 44.dp) { actions.previous(); actions.keepAlive() }
        Box(Modifier.graphicsLayer { rotationZ = rewindSpin.value }) {
            IconCircle(Icons.Rounded.Replay10, "Reculer de 10 s", size = 58.dp) {
                actions.rewind(); actions.keepAlive()
                scope.launch { rewindSpin.snapTo(0f); rewindSpin.animateTo(-360f, tween(420, easing = FastOutSlowInEasing)) }
            }
        }
        val scale by animateFloatAsState(if (buffering) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "play-scale")
        Box(
            Modifier.size(84.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable {
                    actions.player()?.let { if (it.playWhenReady) it.pause() else it.play() }
                    actions.keepAlive()
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(playing, transitionSpec = { (scaleIn(tween(220)) + fadeIn()) togetherWith (scaleOut(tween(160)) + fadeOut()) }, label = "play-pause") { isPlaying ->
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Lecture", tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
        Box(Modifier.graphicsLayer { rotationZ = forwardSpin.value }) {
            IconCircle(Icons.Rounded.Forward10, "Avancer de 10 s", size = 58.dp) {
                actions.forward(); actions.keepAlive()
                scope.launch { forwardSpin.snapTo(0f); forwardSpin.animateTo(360f, tween(420, easing = FastOutSlowInEasing)) }
            }
        }
        Box(Modifier.graphicsLayer { alpha = if (hasNext) 1f else 0.35f }) {
            IconCircle(Icons.Rounded.SkipNext, "Suivant", size = 44.dp) { if (hasNext) actions.next(); actions.keepAlive() }
        }
    }
}

@Composable
private fun BottomBar(
    state: NflixPlayerUiState,
    source: JellyfinMediaSource?,
    position: Long,
    duration: Long,
    buffered: Long,
    actions: NflixPlayerActions,
    thumbnails: TrickplayThumbnails,
    openPanel: (Panel) -> Unit,
) {
    var scrub by remember { mutableStateOf<Long?>(null) }
    var showRemaining by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(scrub ?: position), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            val chapters = remember(source) {
                val total = source?.runTime?.inWholeMilliseconds?.takeIf { it > 0 } ?: 0L
                if (total == 0L) emptyList() else source?.item?.chapters.orEmpty().map { (it.startPositionTicks / Constants.TICKS_PER_MILLISECOND).toFloat() / total }.filter { it > 0.005f && it < 0.995f }
            }
            SeekBar(
                position = scrub ?: position,
                duration = duration,
                buffered = buffered,
                chapters = chapters,
                source = source,
                thumbnails = thumbnails,
                onScrub = { scrub = it; actions.keepAlive() },
                onSeek = { target -> actions.seekTo(target); scrub = null; actions.keepAlive() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (showRemaining) "-" + formatTime(duration - (scrub ?: position)) else formatTime(duration),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showRemaining = !showRemaining; actions.keepAlive() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            item { ToolPill(Icons.Rounded.Subtitles, "Audio et sous-titres") { openPanel(Panel.Tracks) } }
            item { ToolPill(Icons.Rounded.Speed, if (state.speed == 1f) "Vitesse" else "Vitesse ${formatSpeed(state.speed)}") { openPanel(Panel.Speed) } }
            if (source is RemoteJellyfinMediaSource) item { ToolPill(Icons.Rounded.HighQuality, "Qualité") { openPanel(Panel.Quality) } }
            item { ToolPill(Icons.Rounded.AspectRatio, "Format") { openPanel(Panel.Format) } }
            if (!source?.item?.chapters.isNullOrEmpty()) item { ToolPill(Icons.Rounded.Toc, "Chapitres") { openPanel(Panel.Chapters) } }
            item { ToolPill(Icons.Rounded.Info, "Moteur") { openPanel(Panel.Info) } }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0').replace('.', ',')) + "×"

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    chapters: List<Float>,
    source: JellyfinMediaSource?,
    thumbnails: TrickplayThumbnails,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val thickness by animateDpAsState(if (dragging) 7.dp else 4.dp, label = "bar")
    val thumb by animateDpAsState(if (dragging) 11.dp else 7.dp, spring(dampingRatio = 0.5f), label = "thumb")
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        fun fractionAt(x: Float) = (x / widthPx).coerceIn(0f, 1f)
        Canvas(
            Modifier.fillMaxWidth().height(36.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> if (duration > 0) onSeek((fractionAt(offset.x) * duration).toLong()) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = true; dragFraction = fractionAt(offset.x); onScrub((dragFraction * duration).toLong()) },
                        onDragEnd = { dragging = false; onSeek((dragFraction * duration).toLong()) },
                        onDragCancel = { dragging = false; onScrub(null) },
                        onHorizontalDrag = { change, _ -> dragFraction = fractionAt(change.position.x); onScrub((dragFraction * duration).toLong()) },
                    )
                },
        ) {
            val h = thickness.toPx()
            val y = size.height / 2 - h / 2
            val radius = CornerRadius(h / 2, h / 2)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, h), radius)
            drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, y), Size(size.width * bufferFraction, h), radius)
            drawRoundRect(Accent, Offset(0f, y), Size(size.width * fraction, h), radius)
            chapters.forEach { c -> drawRect(Color.Black.copy(alpha = 0.7f), Offset(size.width * c - 1.5f, y), Size(3f, h)) }
            drawCircle(Accent, thumb.toPx(), Offset(size.width * fraction, size.height / 2))
            if (dragging) drawCircle(Accent.copy(alpha = 0.25f), thumb.toPx() * 2.2f, Offset(size.width * fraction, size.height / 2))
        }
        // Trickplay preview bubble above the thumb.
        if (dragging && source != null && duration > 0) {
            val previewWidth = 168.dp
            val density = LocalDensity.current
            val previewPx = with(density) { previewWidth.toPx() }
            val x = (widthPx * fraction - previewPx / 2).coerceIn(0f, (widthPx - previewPx).coerceAtLeast(0f))
            val tile = thumbnails.tileOf(source, position)
            val bitmap by produceState<ImageBitmap?>(null, tile) { if (tile >= 0) thumbnails.load(source, tile)?.let { value = it.asImageBitmap() } }
            val chapter = source.item?.chapters?.lastOrNull { it.startPositionTicks <= position * Constants.TICKS_PER_MILLISECOND }?.name
            Column(
                Modifier.offset { IntOffset(x.toInt(), with(density) { -132.dp.roundToPx() }) }.width(previewWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (thumbnails.available(source)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black).border(2.dp, Color.White, RoundedCornerShape(10.dp))) {
                        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (!chapter.isNullOrBlank()) Text(chapter, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatTime(position), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PauseScreen(source: JellyfinMediaSource, position: Long, duration: Long, actions: NflixPlayerActions) {
    val item = source.item
    val (title, subtitle) = source.titles()
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.showControls() },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.25f)))))
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 36.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VOUS REGARDEZ", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                val logo = actions.imageUrl(source, ArtKind.Logo)
                if (logo != null) {
                    AsyncImage(logo, title, Modifier.height(86.dp).fillMaxWidth(0.75f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                } else {
                    Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 38.sp)
                }
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                item?.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(220.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f).background(Accent))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${formatTime(position)} / ${formatTime(duration)}", color = Muted, fontSize = 13.sp)
                }
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                        .clickable { actions.player()?.play() }.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Reprendre", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            val poster = actions.imageUrl(source, ArtKind.Poster)
            if (poster != null) {
                // Slow breathing zoom on the poster.
                val breathe by androidx.compose.animation.core.rememberInfiniteTransition(label = "pause").animateFloat(
                    1f, 1.04f, androidx.compose.animation.core.infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "breathe",
                )
                AsyncImage(
                    poster, null,
                    Modifier.padding(start = 24.dp).fillMaxHeight(0.78f).aspectRatio(2f / 3f).graphicsLayer { scaleX = breathe; scaleY = breathe }
                        .clip(RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun TracksPanel(state: NflixPlayerUiState, source: JellyfinMediaSource, actions: NflixPlayerActions) {
    val revision = state.revision
    val audio = remember(source, revision) { source.audioStreams }
    val subtitles = remember(source, revision) { source.subtitleStreams }
    val selectedAudio = remember(source, revision) { source.selectedAudioStream?.index }
    val selectedSubtitle = remember(source, revision) { source.selectedSubtitleStream?.index ?: -1 }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { PanelHeader("Audio") }
        items(audio, key = { "a-${it.index}" }) { stream ->
            Choice(streamLabel(stream), stream.codec?.uppercase(), stream.index == selectedAudio) { actions.selectAudio(stream.index) }
        }
        item { PanelHeader("Sous-titres") }
        item(key = "s-none") { Choice("Désactivés", null, selectedSubtitle == -1) { actions.selectSubtitle(-1) } }
        items(subtitles, key = { "s-${it.index}" }) { stream ->
            Choice(streamLabel(stream), listOfNotNull(stream.codec?.uppercase(), "Forcés".takeIf { stream.isForced }, "Externe".takeIf { stream.isExternal }).joinToString(" · "), stream.index == selectedSubtitle) {
                actions.selectSubtitle(stream.index)
            }
        }
    }
}

private fun streamLabel(stream: MediaStream): String {
    val language = stream.language?.let { code ->
        runCatching { Locale.forLanguageTag(code).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) } }.getOrNull()?.takeIf { it.isNotBlank() && it != code }
    }
    return listOfNotNull(language, stream.title?.takeIf { it.isNotBlank() && it != language }).joinToString(" · ").ifBlank { stream.displayTitle ?: "Piste ${stream.index}" }
}

@Composable
private fun SpeedPanel(state: NflixPlayerUiState, actions: NflixPlayerActions) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speeds.forEach { speed ->
                val selected = state.speed == speed
                val background by animateColorAsState(if (selected) Accent else Color.White.copy(alpha = 0.08f), label = "speed")
                Box(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp)).background(background).clickable { actions.setSpeed(speed) },
                    contentAlignment = Alignment.Center,
                ) { Text(formatSpeed(speed), color = Color.White, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, fontSize = 13.sp) }
            }
        }
        Text("Astuce : un appui long sur l’image accélère temporairement.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun QualityPanel(state: NflixPlayerUiState, source: JellyfinMediaSource, actions: NflixPlayerActions) {
    val options = remember(source) { actions.qualityOptions() }
    val current = (source as? RemoteJellyfinMediaSource)?.maxStreamingBitrate
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { PanelHeader("Qualité") }
        items(options, key = { "q-${it.bitrate}" }) { option ->
            val auto = option.bitrate == 0
            Choice(
                if (auto) "Automatique" else "${option.maxHeight}p",
                if (auto) "Original quand c’est possible" else "%.1f Mb/s".format(Locale.FRANCE, option.bitrate / 1_000_000f),
                if (auto) current == null || current == 0 else current == option.bitrate,
            ) { actions.setBitrate(option.bitrate.takeIf { it != 0 }) }
        }
        item { PanelHeader("Décodage vidéo") }
        item { Choice("Matériel", "Le plus économe, recommandé", state.decoder == DecoderType.HARDWARE) { actions.setDecoder(DecoderType.HARDWARE) } }
        item { Choice("Logiciel", "Plus compatible, consomme plus", state.decoder == DecoderType.SOFTWARE) { actions.setDecoder(DecoderType.SOFTWARE) } }
    }
}

@Composable
private fun FormatPanel(state: NflixPlayerUiState, actions: NflixPlayerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Choice("Ajuster", "Image entière, bandes noires possibles", state.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) { actions.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
        Choice("Remplir l’écran", "Rogne les bords, sans bandes noires", state.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) { actions.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
        Choice("Étirer", "Déforme l’image pour remplir", state.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) { actions.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
        Spacer(Modifier.height(6.dp))
        Text("Astuce : pincez l’image pour zoomer.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ChaptersPanel(source: JellyfinMediaSource, position: Long, actions: NflixPlayerActions, onDone: () -> Unit) {
    val chapters = source.item?.chapters.orEmpty()
    val current = chapters.indexOfLast { it.startPositionTicks / Constants.TICKS_PER_MILLISECOND <= position }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(chapters.size) { index ->
            val chapter = chapters[index]
            val start = chapter.startPositionTicks / Constants.TICKS_PER_MILLISECOND
            Choice(chapter.name ?: "Chapitre ${index + 1}", formatTime(start), index == current) { actions.seekTo(start); onDone() }
        }
    }
}

@Composable
private fun InfoPanel(state: NflixPlayerUiState, source: JellyfinMediaSource) {
    val video = source.selectedVideoStream
    val audio = source.selectedAudioStream
    val method = when {
        source is LocalJellyfinMediaSource -> "Fichier hors ligne"
        source.playMethod.name == "TRANSCODE" -> "Converti par le serveur"
        source.playMethod.name == "DIRECT_STREAM" -> "Flux direct (remuxé)"
        else -> "Lecture directe, qualité d’origine"
    }
    val rows = listOfNotNull(
        "Mode" to method,
        "Décodeur" to when (state.decoder) {
            DecoderType.HARDWARE -> "Matériel (MediaCodec)"
            DecoderType.SOFTWARE -> "Logiciel / FFmpeg"
            null -> "Automatique"
        },
        video?.let { "Vidéo" to listOfNotNull(it.codec?.uppercase(), it.width?.let { w -> "${w}×${it.height}" }, it.videoRangeType?.toString()?.takeIf { r -> r != "SDR" && r != "UNKNOWN" }, it.bitRate?.let { b -> "%.1f Mb/s".format(Locale.FRANCE, b / 1_000_000f) }).joinToString(" · ") },
        audio?.let { "Audio" to listOfNotNull(it.codec?.uppercase(), it.channelLayout, it.language).joinToString(" · ") },
        source.sourceInfo.container?.let { "Conteneur" to it.uppercase() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { (label, value) ->
            Row {
                Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Le moteur Nflix lit le fichier d’origine avec le décodeur matériel, bascule sur FFmpeg si besoin, et ne demande une conversion au serveur qu’en dernier recours.",
            color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Choice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent, label = "choice")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(background).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconCircle(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(size * 0.58f)) }
}
