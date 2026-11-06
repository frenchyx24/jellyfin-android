package org.jellyfin.mobile.nflix.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jellyfin.mobile.nflix.data.AchievementBadge
import org.jellyfin.mobile.nflix.ui.Nflix
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Motion preferences
// ---------------------------------------------------------------------------------------------

/** True when the system animator scale is 0 (animations turned off in developer / accessibility settings). */
@Composable
internal fun rememberAchievementReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** 0 for common badges up to 5 for mythic ones; drives glow strength. */
internal fun rarityRank(value: String): Int = when (value) {
    "Uncommon" -> 1
    "Rare" -> 2
    "Epic" -> 3
    "Legendary" -> 4
    "Mythic" -> 5
    else -> 0
}

// ---------------------------------------------------------------------------------------------
// Seen badges: celebrate what was unlocked since the last visit
// ---------------------------------------------------------------------------------------------

/** Unlocked badge ids already shown to the user, per account, so each unlock is celebrated once. */
internal object AchievementSeenStore {
    private const val PREFS = "nflix_achievements_seen"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Null when this account never opened the achievements screen on this device. */
    fun read(context: Context, user: String): Set<String>? =
        prefs(context).getStringSet("seen-$user", null)?.toSet()

    fun write(context: Context, user: String, ids: Set<String>) {
        prefs(context).edit().putStringSet("seen-$user", HashSet(ids)).apply()
    }
}

/**
 * Badges to celebrate, rarest first. On a first visit ([previous] null) only badges unlocked
 * in the last 24 hours count, so an existing collection is not celebrated all at once.
 */
internal fun newlyUnlocked(previous: Set<String>?, badges: List<AchievementBadge>, now: OffsetDateTime = OffsetDateTime.now()): List<AchievementBadge> {
    val fresh = if (previous == null) {
        badges.filter { badge ->
            badge.unlocked && badge.unlockedAt?.let { at ->
                runCatching { Duration.between(OffsetDateTime.parse(at), now).toHours() in 0L..23L }.getOrDefault(false)
            } == true
        }
    } else {
        badges.filter { it.unlocked && it.id !in previous }
    }
    return fresh.sortedWith(compareByDescending<AchievementBadge> { rarityRank(it.rarity) }.thenByDescending { it.unlockedAt.orEmpty() })
}

// ---------------------------------------------------------------------------------------------
// Medallion effects
// ---------------------------------------------------------------------------------------------

/**
 * Diagonal light sweep across the content, once every few seconds. Put it after a clip so the
 * sheen follows the shape. The animated value is only read while drawing: no recomposition.
 */
@Composable
internal fun Modifier.achievementShine(enabled: Boolean, seed: Int = 0, periodMs: Int = 4200): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "badge-shine")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(((seed % periodMs) + periodMs) % periodMs),
        ),
        label = "sheen",
    )
    return this.drawWithCache {
        val band = size.width * 0.5f
        val brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f), Color.Transparent),
            startX = 0f,
            endX = band,
        )
        val bandSize = Size(band, size.height * 2f)
        val bandTop = Offset(0f, -size.height * 0.5f)
        onDrawWithContent {
            drawContent()
            // The sweep takes the first 28 % of the cycle, the rest is rest time.
            val sweep = phase.value / 0.28f
            if (sweep < 1f) {
                val x = -band * 1.6f + (size.width + band * 2.2f) * sweep
                rotate(24f) {
                    translate(left = x) { drawRect(brush, topLeft = bandTop, size = bandSize) }
                }
            }
        }
    }
}

/** Soft halo behind a round badge, stronger for rarer badges; legendary and mythic ones breathe. */
@Composable
internal fun Modifier.rarityGlow(color: Color, rank: Int, animated: Boolean): Modifier {
    val breathe = animated && rank >= 4
    val pulse = if (breathe) {
        rememberInfiniteTransition(label = "badge-glow").animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )
    } else {
        null
    }
    return this.drawWithCache {
        val radius = size.minDimension / 2f * (1.16f + rank * 0.055f)
        val brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.16f + rank * 0.07f), color.copy(alpha = 0.05f + rank * 0.02f), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        )
        onDrawBehind { drawCircle(brush, radius = radius, alpha = pulse?.value ?: 1f) }
    }
}

/** Rotating light halo with rays, for the badge detail and the unlock celebration. */
@Composable
internal fun AchievementHalo(color: Color, size: Dp, animated: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val angle = if (animated) {
        rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
            label = "halo-angle",
        )
    } else {
        null
    }
    Box(
        modifier.size(size).drawWithCache {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f
            val ring = outer * 0.80f
            val strokeWidth = outer * 0.045f
            val glow = Brush.radialGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.10f), Color.Transparent), center = c, radius = outer)
            val sweep = Brush.sweepGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.9f), Color.White.copy(alpha = 0.85f), color.copy(alpha = 0.9f), Color.Transparent, Color.Transparent, color.copy(alpha = 0.6f), Color.Transparent),
                center = c,
            )
            val rayWidth = outer * 0.07f
            val rayBrush = Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.32f)), startY = c.y - outer, endY = c.y - outer * 0.35f)
            val rayTop = Offset(c.x - rayWidth / 2f, c.y - outer)
            val raySize = Size(rayWidth, outer * 0.65f)
            val ringStroke = Stroke(strokeWidth, cap = StrokeCap.Round)
            onDrawBehind {
                val a = angle?.value ?: 0f
                drawCircle(glow, radius = outer, center = c)
                rotate(-a * 0.5f, pivot = c) {
                    for (i in 0 until 12) {
                        rotate(i * 30f, pivot = c) { drawRect(rayBrush, topLeft = rayTop, size = raySize) }
                    }
                }
                rotate(a, pivot = c) { drawCircle(sweep, radius = ring, center = c, style = ringStroke) }
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Progress and counters: animate from zero once, then follow value changes
// ---------------------------------------------------------------------------------------------

/**
 * Animatable that goes from 0 to [target] the first time it is shown; later changes animate
 * from the current value. The "played" flag survives lazy-list scrolling and rotation.
 */
@Composable
internal fun rememberAchievementProgress(target: Float, delayMs: Int = 0, durationMs: Int = 900): Animatable<Float, AnimationVector1D> {
    val reduced = rememberAchievementReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (played || reduced) target else 0f) }
    LaunchedEffect(target) {
        if (reduced) {
            anim.snapTo(target)
        } else {
            anim.animateTo(target, tween(durationMs, delayMillis = if (played) 0 else delayMs, easing = FastOutSlowInEasing))
        }
        played = true
    }
    return anim
}

/** Integer that counts up from 0 to [target] on first display. */
@Composable
internal fun achievementCountUp(target: Int, durationMs: Int = 1100): Int {
    val anim = rememberAchievementProgress(target.toFloat(), delayMs = 120, durationMs = durationMs)
    return anim.value.roundToInt()
}

/** [StatTile] whose number counts up. */
@Composable
internal fun AchievementStatTile(label: String, value: Int, modifier: Modifier = Modifier, caption: String? = null, format: (Int) -> String = { it.toString() }) {
    StatTile(label, format(achievementCountUp(value)), modifier, caption)
}

/** Rounded progress bar that fills from 0 on first show, with a bright tip. Drawn, not laid out, per frame. */
@Composable
internal fun AchievementProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, delayMs: Int = 150) {
    val anim = rememberAchievementProgress(progress.coerceIn(0f, 1f), delayMs)
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).drawBehind {
            val w = size.width * anim.value
            if (w > 0f) {
                val r = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
                val tip = size.height * 3f
                if (w > tip) drawRoundRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(w - tip, 0f), size = Size(tip, size.height), cornerRadius = r)
            }
        },
    )
}

/** Circular progress ring, animated from 0. */
@Composable
internal fun AchievementProgressRing(progress: Float, color: Color, size: Dp, stroke: Dp = 6.dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val anim = rememberAchievementProgress(progress.coerceIn(0f, 1f), delayMs = 200, durationMs = 1100)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * anim.value, false, topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Fade, lift and slight zoom on first appearance; [index] staggers the first dozen items only. */
@Composable
internal fun Modifier.achievementAppear(index: Int): Modifier {
    val reduced = rememberAchievementReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (anim.value < 1f) anim.animateTo(1f, tween(440, delayMillis = if (index in 0..11) index * 45 else 0, easing = FastOutSlowInEasing))
        played = true
    }
    return this.graphicsLayer {
        val v = anim.value
        alpha = v
        translationY = (1f - v) * 28.dp.toPx()
        scaleX = 0.94f + 0.06f * v
        scaleY = 0.94f + 0.06f * v
    }
}

// ---------------------------------------------------------------------------------------------
// Confetti
// ---------------------------------------------------------------------------------------------

/** Particle state in flat arrays: allocated once per burst, updated in place every frame. */
private class ConfettiSystem(val count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val rot = FloatArray(count)
    val vrot = FloatArray(count)
    val w = FloatArray(count)
    val h = FloatArray(count)
    val phase = FloatArray(count)
    val tint = IntArray(count)
    val circle = BooleanArray(count)
    var alpha = 0f

    fun reset(density: Float, random: Random) {
        for (i in 0 until count) {
            val angle = (-90.0 + random.nextDouble(-72.0, 72.0)) * PI / 180.0
            val speed = random.nextDouble(420.0, 1150.0).toFloat() * density
            x[i] = random.nextDouble(-8.0, 8.0).toFloat() * density
            y[i] = random.nextDouble(-8.0, 8.0).toFloat() * density
            vx[i] = cos(angle).toFloat() * speed
            vy[i] = sin(angle).toFloat() * speed
            rot[i] = random.nextDouble(0.0, 360.0).toFloat()
            vrot[i] = random.nextDouble(-520.0, 520.0).toFloat()
            circle[i] = i % 4 == 0
            w[i] = (if (circle[i]) random.nextDouble(2.5, 4.5) else random.nextDouble(5.0, 9.0)).toFloat() * density
            h[i] = w[i] * (if (circle[i]) 1f else random.nextDouble(0.4, 0.75).toFloat())
            phase[i] = random.nextDouble(0.0, 2 * PI).toFloat()
            tint[i] = random.nextInt(0, 1024)
        }
        alpha = 1f
    }

    fun step(dt: Float, density: Float, progress: Float) {
        val gravity = 1500f * density
        val terminal = 480f * density
        val drag = (1f - 1.5f * dt).coerceIn(0f, 1f)
        for (i in 0 until count) {
            vx[i] *= drag
            vy[i] = (vy[i] * drag + gravity * dt).coerceAtMost(terminal)
            phase[i] += dt * 7f
            x[i] += vx[i] * dt + sin(phase[i]) * 22f * density * dt
            y[i] += vy[i] * dt
            rot[i] += vrot[i] * dt
        }
        alpha = if (progress < 0.7f) 1f else ((1f - progress) / 0.3f).coerceIn(0f, 1f)
    }
}

/**
 * One confetti burst each time [trigger] changes to a positive value, from ([originX], [originY])
 * as fractions of the canvas. Single Canvas, frame loop via withFrameNanos, ~[durationMs] long.
 * Nothing is drawn when [enabled] is false (reduced motion).
 */
@Composable
internal fun AchievementConfetti(
    trigger: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    originX: Float = 0.5f,
    originY: Float = 0.36f,
    count: Int = 60,
    durationMs: Int = 2500,
) {
    if (!enabled || colors.isEmpty()) return
    val density = LocalDensity.current.density
    val system = remember(count) { ConfettiSystem(count) }
    // -1 = idle. Read only in the draw block, so each frame only redraws the Canvas.
    var frame by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        system.reset(density, Random(trigger * 7919 + 17))
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val elapsed = (now - start) / 1_000_000f
            system.step(dt, density, elapsed / durationMs)
            frame = now
            if (elapsed >= durationMs) break
        }
        frame = -1L
    }
    Canvas(modifier) {
        if (frame < 0L || system.alpha <= 0f) return@Canvas
        val ox = size.width * originX
        val oy = size.height * originY
        val a = system.alpha
        for (i in 0 until system.count) {
            val px = ox + system.x[i]
            val py = oy + system.y[i]
            if (py > size.height + 40f) continue
            val color = colors[system.tint[i] % colors.size]
            if (system.circle[i]) {
                drawCircle(color, radius = system.w[i], center = Offset(px, py), alpha = a)
            } else {
                // Tumbling: the visible width shrinks and grows like a spinning paper strip.
                val pw = system.w[i] * (0.25f + 0.75f * abs(cos(system.phase[i])))
                val ph = system.h[i]
                rotate(system.rot[i], pivot = Offset(px, py)) {
                    drawRect(color, topLeft = Offset(px - pw / 2f, py - ph / 2f), size = Size(pw, ph), alpha = a)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Unlock celebration
// ---------------------------------------------------------------------------------------------

/** Full-screen burst for newly unlocked badges: medallion pop, halo, confetti and a haptic tick. */
@Composable
internal fun AchievementCelebration(badges: List<AchievementBadge>, onOpen: (AchievementBadge) -> Unit, onDismiss: () -> Unit) {
    val hero = badges.firstOrNull() ?: return
    val reduced = rememberAchievementReducedMotion()
    val haptic = LocalHapticFeedback.current
    val color = rarityColor(hero.rarity)
    val pop = remember { Animatable(if (reduced) 1f else 0.2f) }
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(hero.id) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        if (!reduced) {
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(hero.id) {
        if (!reduced) reveal.animateTo(1f, tween(520, delayMillis = 260, easing = FastOutSlowInEasing))
    }
    val confettiColors = remember(color) { listOf(color, Nflix.Red, Nflix.Warning, Color.White, Nflix.Success, Nflix.Info) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.86f))))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)
                    // Taps on the card itself do not dismiss.
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AchievementHalo(color, 230.dp, animated = !reduced, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                    Medallion(hero, 124.dp, showLocked = false)
                }
                Column(
                    Modifier.graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 24.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (badges.size > 1) "${badges.size} nouveaux succès !" else "Nouveau succès !",
                        style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(hero.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    RarityPill(hero.rarity)
                    Spacer(Modifier.height(10.dp))
                    Text(hero.description, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val others = badges.drop(1).take(5)
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            others.forEach { Medallion(it, 40.dp, showLocked = false) }
                            if (badges.size > 6) Text("+${badges.size - 6}", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { onOpen(hero) },
                        colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Voir le succès", fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Continuer", color = Nflix.Text) }
                }
            }
            AchievementConfetti(trigger = 1, colors = confettiColors, modifier = Modifier.fillMaxSize(), enabled = !reduced, originY = 0.34f)
        }
    }
}

/** Small "NOUVEAU" tag for badges unlocked since the last visit. */
@Composable
internal fun NewBadgeTag(modifier: Modifier = Modifier) {
    Text(
        "NOUVEAU",
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White,
        modifier = modifier.clip(CircleShape).background(Nflix.Red).achievementShine(enabled = !rememberAchievementReducedMotion(), periodMs = 2600)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
