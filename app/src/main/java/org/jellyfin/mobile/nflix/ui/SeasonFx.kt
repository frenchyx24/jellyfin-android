package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---- Shared clock -----------------------------------------------------------------------------

/**
 * One app-wide decoration clock (seconds). [SeasonClockHost] ticks it every frame (every other
 * frame on low-end devices) while the app is started, decorations may move and the system allows
 * animations. Every decor layer reads it in its draw phase, so a tick only redraws canvases.
 */
object SeasonClock {
    internal val time = mutableFloatStateOf(14f)
    internal var hosts by mutableIntStateOf(0)

    /** Current time; reading it in a draw block redraws that block every tick. */
    val now: Float get() = time.floatValue
}

/** Amplitude factor of the user's intensity setting, for draw code. */
internal val seasonAmp: Float get() = NflixSeason.intensity.amplitude()

/** True while the hosting lifecycle is at least STARTED (the clock stops in the background). */
@Composable
internal fun rememberLifecycleStarted(): Boolean {
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return started
}

/** Drives [SeasonClock]; place once near the root (NflixTheme, login screen). */
@Composable
fun SeasonClockHost() {
    val motion = NflixSeason.ambient && NflixSeason.decorations && NflixSeason.theme.isSeasonal &&
        rememberAmbientMotionAllowed() && rememberLifecycleStarted()
    val lowEnd = rememberLowEndDevice()
    DisposableEffect(Unit) {
        SeasonClock.hosts++
        onDispose { SeasonClock.hosts-- }
    }
    LaunchedEffect(motion, lowEnd) {
        if (!motion) return@LaunchedEffect
        val step = if (lowEnd) 33_000_000L else 0L
        var last = 0L
        val start = withFrameNanos { it } - (SeasonClock.time.floatValue * 1_000_000_000L).toLong()
        while (true) {
            withFrameNanos { now ->
                if (now - last >= step) {
                    last = now
                    SeasonClock.time.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }
}

/**
 * Seconds for a decoration. Uses the shared [SeasonClock] when a host runs; otherwise a local
 * frame loop with the same rules. Frozen (current value kept) when [active] is false, when the
 * user turned motion off or under the system "remove animations" setting.
 */
@Composable
fun rememberSeasonClock(active: Boolean = true): State<Float> {
    val motion = active && NflixSeason.ambient && rememberAmbientMotionAllowed()
    if (SeasonClock.hosts > 0) {
        if (motion) return SeasonClock.time
        return remember(motion) { mutableFloatStateOf(Snapshot.withoutReadObservation { SeasonClock.time.floatValue }) }
    }
    val started = rememberLifecycleStarted()
    val lowEnd = rememberLowEndDevice()
    val clock = remember { mutableFloatStateOf(14f) }
    LaunchedEffect(motion && started, lowEnd) {
        if (!(motion && started)) return@LaunchedEffect
        val step = if (lowEnd) 33_000_000L else 0L
        var last = 0L
        val start = withFrameNanos { it } - (clock.floatValue * 1_000_000_000L).toLong()
        while (true) {
            withFrameNanos { now ->
                if (now - last >= step) {
                    last = now
                    clock.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return clock
}

// ---- Theme crossfade --------------------------------------------------------------------------

/** Animates [NflixSeason.transition] towards [NflixSeason.target]; place once in NflixTheme. */
@Composable
fun SeasonThemeDriver() {
    val target = NflixSeason.target
    val motion = rememberAmbientMotionAllowed()
    val transition = NflixSeason.transition
    DisposableEffect(Unit) { onDispose { NflixSeason.driven = false } }
    LaunchedEffect(target.key) {
        if (!NflixSeason.driven || !motion) {
            transition.snap(target)
            NflixSeason.driven = true
            return@LaunchedEffect
        }
        if (!transition.start(target)) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val p = ((now - start) / 1_000_000f / SeasonTransition.DURATION_MS).coerceIn(0f, 1f)
            transition.advance(FastOutSlowInEasing.transform(p))
            if (p >= 1f) break
        }
    }
}

/**
 * Draws [content] for the theme on screen; during a theme change, the old and the new decor
 * crossfade (alpha read in the layer, so only the layers redraw).
 */
@Composable
fun SeasonCrossfade(modifier: Modifier, content: @Composable (NflixSeasonTheme, Modifier) -> Unit) {
    if (!NflixSeason.driven) { content(NflixSeason.target, modifier); return }
    val transition = NflixSeason.transition
    val from = transition.from
    val to = transition.to
    Box(modifier) {
        if (from != null) key(from.key) { content(from, Modifier.fillMaxSize().graphicsLayer { alpha = transition.alphaOf(from) }) }
        key(to.key) { content(to, Modifier.fillMaxSize().graphicsLayer { alpha = transition.alphaOf(to) }) }
    }
}

// ---- Animated glyphs --------------------------------------------------------------------------

internal fun glyphTint(glyph: Glyph, theme: NflixSeasonTheme): Color = when (glyph) {
    Glyph.Bat -> Color.White
    Glyph.Flower, Glyph.Petal -> theme.accentSoft
    else -> theme.accent
}

/** Offset (fraction of the radius), rotation and scale of an icon glyph at [t]. */
internal data class GlyphPose(val dy: Float, val rotation: Float, val scale: Float)

internal fun glyphPose(glyph: Glyph, t: Float, amp: Float, sober: Boolean): GlyphPose {
    val s = if (sober) 0.5f else 1f
    return when (glyph) {
        // Rotate
        Glyph.Snowflake, Glyph.Sun -> GlyphPose(0f, t * 28f * s * amp, 1f)
        Glyph.Star, Glyph.Spark -> GlyphPose(0f, sin(t * 1.3f) * 14f * amp, 0.92f + 0.08f * sin(t * 2.6f) * amp)
        // Pulse
        Glyph.Heart, Glyph.Poppy, Glyph.Cockade, Glyph.Dot ->
            GlyphPose(0f, 0f, 1f + 0.08f * s * amp * sin(t * if (glyph == Glyph.Heart) 3.4f else 1.6f).let { it * it })
        // Swing like an ornament
        Glyph.Bauble, Glyph.Lantern, Glyph.Balloon -> GlyphPose(0f, sin(t * 1.8f) * 10f * amp, 1f)
        // Bob
        else -> GlyphPose(sin(t * 2.1f) * 0.08f * s * amp, sin(t * 1.3f + 0.7f) * 6f * s * amp, 1f)
    }
}

/** Small glyph icon that bobs, rotates or pulses on the shared clock (static without motion). */
@Composable
fun AnimatedSeasonGlyph(glyph: Glyph, theme: NflixSeasonTheme, modifier: Modifier = Modifier, phase: Float = 0f, animated: Boolean = true) {
    val clock = rememberSeasonClock(animated && NflixSeason.decorations)
    Canvas(modifier) {
        val t = clock.value + phase
        val pose = glyphPose(glyph, t, seasonAmp, theme.decor.sober)
        val r = size.minDimension / 2f * 0.86f
        drawGlyph(glyph, center + Offset(0f, pose.dy * r), r * pose.scale, glyphTint(glyph, theme), 1f, rotation = pose.rotation, t = t)
    }
}

// ---- Loading indicator ------------------------------------------------------------------------

/**
 * Indeterminate loading indicator, drop-in for CircularProgressIndicator. In a seasonal theme
 * (36 dp and larger) it becomes a themed spinner: the badge glyph turning inside a comet ring.
 * Small sizes (buttons) and the brand look keep the Material indicator.
 */
@Composable
fun SeasonSpinner(
    modifier: Modifier = Modifier,
    color: Color = Nflix.Red,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val theme = NflixSeason.theme
    if (!theme.isSeasonal || !NflixSeason.decorations) {
        androidx.compose.material3.CircularProgressIndicator(modifier, color, strokeWidth, trackColor, strokeCap)
        return
    }
    val threshold = with(LocalDensity.current) { 36.dp.roundToPx() }
    var big by remember { mutableStateOf(true) }
    Box(modifier.size(40.dp).onSizeChanged { big = minOf(it.width, it.height) >= threshold }) {
        if (!big) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.fillMaxSize(), color, strokeWidth, trackColor, strokeCap)
        } else {
            val spin = rememberInfiniteTransition(label = "season-spinner")
            val turn by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "turn")
            val breath by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
            val glyph = theme.decor.badgeGlyph
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val ring = r * 0.86f
                drawCircle(Brush.radialGradient(listOf(theme.glow.copy(alpha = 0.35f + 0.15f * breath), Color.Transparent), center, r), r, center)
                drawCircle(theme.accent.copy(alpha = 0.16f), ring, center, style = Stroke(r * 0.07f))
                // Comet: 10 dots with a fading tail.
                for (i in 0 until 10) {
                    val a = (turn * 2f * PI.toFloat()) - i * 0.22f
                    val p = center + Offset(cos(a), sin(a)) * ring
                    drawCircle(if (i % 3 == 0) theme.accentSoft else theme.accent, r * (0.11f - i * 0.008f), p, alpha = (1f - i / 10f))
                }
                val pose = glyphPose(glyph, turn * 6f, 1f, theme.decor.sober)
                drawGlyph(glyph, center + Offset(0f, pose.dy * r * 0.5f), r * 0.42f * (0.95f + 0.08f * breath), glyphTint(glyph, theme), 1f, rotation = pose.rotation, t = turn * 8f)
            }
        }
    }
}

/** Determinate variant, so `SeasonSpinner as CircularProgressIndicator` imports keep compiling. */
@Composable
fun SeasonSpinner(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Nflix.Red,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round,
) = androidx.compose.material3.CircularProgressIndicator(progress, modifier, color, strokeWidth, trackColor, strokeCap)

// ---- Confetti of the theme --------------------------------------------------------------------

/** Confetti colours for celebrations (achievements, birthday): theme colours first. */
fun seasonConfettiColors(base: List<Color>): List<Color> {
    val theme = NflixSeason.theme
    if (!theme.isSeasonal) return base
    return listOf(theme.accent, theme.accentSoft, theme.glowAlt) + theme.decor.particles.flatMap { it.colors }.take(3) + base.take(3)
}

// ---- Themed glyph burst (achievement celebrations) --------------------------------------------

private class BurstParticle(val angle: Float, val speed: Float, val size: Float, val spin: Float, val tone: Int)

/**
 * One burst of the theme's own glyphs (pumpkins, snowflakes, hearts…) each time [trigger] becomes
 * positive: gravity, tumble, fade out over [durationMs]. Enabled only in a seasonal theme with
 * motion allowed; never takes touches.
 */
@Composable
fun SeasonGlyphBurst(
    trigger: Int,
    theme: NflixSeasonTheme = NflixSeason.theme,
    modifier: Modifier = Modifier,
    originX: Float = 0.5f,
    originY: Float = 0.34f,
    count: Int = 14,
    durationMs: Int = 2400,
) {
    if (!theme.isSeasonal || !NflixSeason.decorations) return
    val reduced = rememberAmbientMotionAllowed().not()
    if (reduced || trigger <= 0) return
    val glyph = theme.decor.particles.firstOrNull()?.glyph ?: theme.decor.badgeGlyph
    val colors = remember(theme.key) {
        theme.decor.particles.flatMap { it.colors } + listOf(theme.accent, theme.accentSoft, theme.glowAlt)
    }
    val particles = remember(trigger, count) {
        val r = java.util.Random(trigger * 7919L + 31)
        List(count) {
            BurstParticle(
                angle = -PI.toFloat() / 2f + (r.nextFloat() - 0.5f) * PI.toFloat() * 1.3f,
                speed = 0.35f + r.nextFloat() * 0.65f,
                size = 0.6f + r.nextFloat() * 0.8f,
                spin = (r.nextFloat() - 0.5f) * 360f,
                tone = r.nextInt(8),
            )
        }
    }
    var frame by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val p = (now - start) / 1_000_000f / durationMs
            frame = p.coerceAtMost(1f)
            if (p >= 1f) break
        }
        frame = -1f
    }
    Canvas(modifier) {
        val p = frame
        if (p < 0f) return@Canvas
        val d = density
        val ox = size.width * originX
        val oy = size.height * originY
        val fade = (1f - p).pow(0.8f)
        particles.forEach { b ->
            val dist = b.speed * size.height * 0.55f * (1f - (1f - p).pow(2.2f))
            val x = ox + cos(b.angle) * dist
            val y = oy + sin(b.angle) * dist + p * p * size.height * 0.35f
            drawGlyph(glyph, Offset(x, y), 7f * d * b.size, colors[b.tone % colors.size], fade, rotation = b.spin * p, t = p * 4f)
        }
    }
}

// ---- Sound ------------------------------------------------------------------------------------

/**
 * Very short synthesized chimes for themed interactions. Off by default (Settings). Silent in
 * vibrate/silent mode, under DND, when the media volume is 0, and while music/video plays.
 * Uses the media stream (Usage.MEDIA) so it follows the media volume.
 */
object SeasonSound {
    enum class Cue { Play, Theme }

    @Volatile private var last = 0L
    private val player = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "season-sound").apply { isDaemon = true } }

    fun play(context: android.content.Context, theme: NflixSeasonTheme, cue: Cue = Cue.Play) {
        if (!NflixSeason.sounds || !theme.isSeasonal) return
        val ctx = context.applicationContext
        val now = System.currentTimeMillis()
        if (now - last < 900) return
        last = now
        val can = allowed(ctx)
        if (!can) return
        player.execute { runCatching { chime(theme, cue) } }
    }

    internal fun allowed(context: android.content.Context): Boolean = runCatching {
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        if (audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return false
        if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) <= 0) return false
        val notification = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notification.isNotificationPolicyAccessGranted && notification.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
            audio.isMusicActive.not()
    }.getOrDefault(false)

    /** Notes of the theme: a soft rising triad, a bell for the play button, a sparkle for a theme pick. */
    internal fun notes(theme: NflixSeasonTheme, cue: Cue): FloatArray = when (cue) {
        Cue.Play -> if (theme.decor.sober) floatArrayOf(392f, 494f) else floatArrayOf(523.25f, 659.25f, 783.99f)
        Cue.Theme -> if (theme.decor.sober) floatArrayOf(440f) else floatArrayOf(880f, 1174.66f, 1567.98f)
    }

    internal fun samples(theme: NflixSeasonTheme, cue: Cue, rate: Int = 44_100): ShortArray {
        val notes = notes(theme, cue)
        val noteMs = if (cue == Cue.Play) 70 else 45
        val gapMs = 18
        val tailMs = 90
        val frames = notes.size * (noteMs + gapMs) + tailMs
        val total = rate * frames / 1000
        val out = ShortArray(total)
        val amp = if (theme.decor.sober) 0.10 else 0.16
        notes.forEachIndexed { i, freq ->
            val start = rate * i * (noteMs + gapMs) / 1000
            val length = rate * (noteMs + tailMs) / 1000
            for (n in 0 until length) {
                val idx = start + n
                if (idx >= total) break
                val t = n.toFloat() / rate
                val env = kotlin.math.exp(-t * 26f).toFloat()
                val attack = (n / (rate * 0.004f)).coerceAtMost(1f)
                val s = sin(2f * PI.toFloat() * freq * t) + 0.32f * sin(4f * PI.toFloat() * freq * t) + 0.12f * sin(6f * PI.toFloat() * freq * t)
                out[idx] = (s * env * attack * amp * Short.MAX_VALUE * 0.45f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private fun chime(theme: NflixSeasonTheme, cue: Cue) {
        val rate = 44_100
        val data = samples(theme, cue, rate)
        val bytes = java.nio.ByteBuffer.allocate(data.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply { data.forEach { putShort(it) } }.array()
        val track = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes.size)
            .build()
        track.write(bytes, 0, bytes.size)
        track.notifyAll()
        val listener = object : android.media.AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: android.media.AudioTrack?) { runCatching { t?.release() } }
            override fun onPeriodicNotification(t: android.media.AudioTrack?) = Unit
        }
        track.setNotificationMarkerPosition(data.size / 4)
        track.setPlaybackPositionUpdateListener(listener)
        track.play()
    }
}
