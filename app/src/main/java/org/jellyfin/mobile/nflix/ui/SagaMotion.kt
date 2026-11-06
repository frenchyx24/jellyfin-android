package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import org.jellyfin.mobile.nflix.data.Item
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Franchise-specific motion drawn over title pages. Detection is name/genre based, so it works without extra metadata. */
enum class SagaFxKind { XWing, Warp }

object SagaFx {
    private val starWars = listOf(
        "star wars", "mandalorian", "andor", "ahsoka", "obi-wan", "kenobi", "boba fett",
        "clone wars", "bad batch", "acolyte", "skeleton crew", "rebels", "jedi", "sith",
        "book of boba", "solo: a", "rogue one",
    )
    private val starTrek = listOf(
        "star trek", "picard", "strange new worlds", "lower decks", "prodigy", "section 31", "kirk", "spock",
    )

    fun detect(item: Item): SagaFxKind? = detect(item.name, item.seriesName, item.genres)

    fun detect(name: String, seriesName: String? = null, genres: List<String> = emptyList()): SagaFxKind? {
        val haystack = buildString {
            append(name).append(' ')
            seriesName?.let { append(it).append(' ') }
            if (genres.isNotEmpty()) append(genres.joinToString(" "))
        }.lowercase()
        return when {
            starWars.any { haystack.contains(it) } -> SagaFxKind.XWing
            starTrek.any { haystack.contains(it) } -> SagaFxKind.Warp
            else -> null
        }
    }
}

private data class Flyby(val baseY: Float, val delay: Float, val span: Float, val scale: Float, val down: Boolean)

/**
 * Draws an ambient overlay (X-wings crossing the frame, or warp star streaks) on saga title pages.
 * Nothing is drawn when the saga is unknown or the system asks for reduced motion.
 */
@Composable
fun SagaMotion(item: Item, modifier: Modifier = Modifier) {
    val kind = remember(item.id, item.name, item.seriesName, item.genres) { SagaFx.detect(item) }
    SagaMotionCanvas(kind, modifier)
}

/** Name-based entry point for saga landing pages, which have no [Item] at hand. */
@Composable
fun SagaMotion(name: String, modifier: Modifier = Modifier) {
    val kind = remember(name) { SagaFx.detect(name) }
    SagaMotionCanvas(kind, modifier)
}

@Composable
private fun SagaMotionCanvas(kind: SagaFxKind?, modifier: Modifier = Modifier) {
    if (kind == null) return
    // No animation frames at all when the system asks for reduced motion.
    if (!org.jellyfin.mobile.nflix.ui.screens.rememberMotionEnabled()) return
    val transition = rememberInfiniteTransition(label = "saga-motion")
    val progress by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(if (kind == SagaFxKind.XWing) 18000 else 22000, easing = LinearEasing), RepeatMode.Restart),
        label = "saga-progress",
    )
    val ships = remember(kind) {
        val random = Random(kind.ordinal + 31)
        List(3) { index ->
            Flyby(
                baseY = 0.18f + index * 0.26f + random.nextFloat() * 0.06f,
                delay = index * 0.29f,
                span = 0.42f,
                scale = 0.75f + random.nextFloat() * 0.55f,
                down = index % 2 == 1,
            )
        }
    }
    val stars = remember(kind) { List(70) { Random(kind.ordinal * 97 + it).nextFloat() to Random(kind.ordinal * 31 + it * 7).nextFloat() } }
    Canvas(modifier) {
        when (kind) {
            SagaFxKind.XWing -> {
                drawXWingScene(progress, ships)
                drawLaserBolts(progress)
            }
            SagaFxKind.Warp -> drawWarpScene(progress, stars)
        }
    }
}

private fun DrawScope.drawXWingScene(progress: Float, ships: List<Flyby>) {
    // Faint star field drifting left, sells the depth behind the ships.
    val starAlpha = 0.22f
    repeat(48) { index ->
        val seed = index * 17
        val x = ((seed * 0.0137f) - progress * 0.25f).mod(1f)
        val y = ((seed * 0.0713f) + sin((progress * 2.0 * PI + index).toFloat()) * 0.004f).mod(1f)
        drawCircle(Color.White.copy(alpha = starAlpha), radius = if (index % 7 == 0) 2.2f else 1.2f, center = Offset(x * size.width, y * size.height))
    }
    ships.forEach { ship ->
        val local = ((progress - ship.delay) % 1f + 1f) % 1f
        val travel = local / ship.span
        if (travel > 1f) return@forEach
        val startX = -0.28f * size.width
        val endX = 1.28f * size.width
        val x = startX + (endX - startX) * travel
        val y = (ship.baseY + (if (ship.down) travel * 0.16f else -travel * 0.12f)) * size.height
        val angle = if (ship.down) 8f else -7f
        val alpha = (1f - ((travel - 0.82f) / 0.18f).coerceIn(0f, 1f) * 0.55f).coerceIn(0f, 1f)
        drawEngineTrail(x, y, angle, ship.scale, alpha)
        withTransform({
            translate(x, y)
            rotate(angle)
            scale(ship.scale, ship.scale, pivot = Offset.Zero)
        }) {
            drawXWing(alpha)
        }
    }
}

private fun DrawScope.drawEngineTrail(x: Float, y: Float, angle: Float, scale: Float, alpha: Float) {
    val length = 90f * scale
    val rad = (angle * PI / 180.0).toFloat()
    val tail = Offset(x - cos(rad) * length, y - sin(rad) * length)
    val paint = androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(Color.Transparent, Color(0xFFFF7A45).copy(alpha = 0.32f * alpha), Color(0xFFFFB26B).copy(alpha = 0.55f * alpha)),
        startX = tail.x,
        endX = x,
    )
    drawLine(
        brush = paint,
        start = tail,
        end = Offset(x - cos(rad) * 12f, y - sin(rad) * 12f),
        strokeWidth = 3.2f * scale,
    )
}

private fun DrawScope.drawXWing(alpha: Float) {
    val hull = Color(0xFFD8DEE9).copy(alpha = 0.82f * alpha)
    val shade = Color(0xFF8B93A1).copy(alpha = 0.75f * alpha)
    val engine = Color(0xFFFF6B4A).copy(alpha = 0.95f * alpha)

    // Fuselage with nose cone, nose pointing right (+X).
    val body = Path().apply {
        moveTo(-16f, -3.4f)
        lineTo(6f, -4.6f)
        lineTo(19f, -1.4f)
        lineTo(21f, 0f)
        lineTo(19f, 1.4f)
        lineTo(6f, 4.6f)
        lineTo(-16f, 3.4f)
        close()
    }
    drawPath(body, hull)

    // Wings swept back, both sides.
    val wings = Path().apply {
        moveTo(-4f, -3.2f)
        lineTo(-19f, -16f)
        lineTo(-23f, -16.6f)
        lineTo(-13f, -2.6f)
        close()
        moveTo(-4f, 3.2f)
        lineTo(-19f, 16f)
        lineTo(-23f, 16.6f)
        lineTo(-13f, 2.6f)
        close()
    }
    drawPath(wings, shade)

    // Wing-tip laser cannons plus the four engine glows.
    drawLine(hull, Offset(-18f, -16f), Offset(-6f, -15.2f), strokeWidth = 1.6f)
    drawLine(hull, Offset(-18f, 16f), Offset(-6f, 15.2f), strokeWidth = 1.6f)
    listOf(Offset(-14f, -5.4f), Offset(-14f, 5.4f), Offset(-6f, -2.6f), Offset(-6f, 2.6f)).forEach { point ->
        drawCircle(engine, radius = 3.1f, center = point)
        drawCircle(Color(0xFFFFE3B0).copy(alpha = 0.55f * alpha), radius = 1.4f, center = point)
    }
}

private fun DrawScope.drawLaserBolts(progress: Float) {
    repeat(4) { index ->
        val phase = ((progress * 3.0f + index * 0.37f) % 1f)
        val visible = phase < 0.12f
        if (!visible) return@repeat
        val t = phase / 0.12f
        val y = (0.22f + index * 0.19f) * size.height
        val x = (0.1f + t * 0.9f) * size.width
        val color = if (index % 2 == 0) Color(0xFFFF4C4C) else Color(0xFF63E6A6)
        drawLine(
            color = color.copy(alpha = 0.75f * (1f - t)),
            start = Offset(x - 46f, y),
            end = Offset(x, y),
            strokeWidth = 3f,
        )
    }
}

private fun DrawScope.drawWarpScene(progress: Float, stars: List<Pair<Float, Float>>) {
    val center = Offset(size.width * 0.5f, size.height * 0.42f)
    stars.forEachIndexed { index, (sx, sy) ->
        val angle = sx * 2f * PI.toFloat()
        val radius = (sy + progress * (0.4f + sy * 0.6f)).mod(1f)
        val distance = radius * size.width * 0.9f
        val from = Offset(center.x + cos(angle) * distance * 0.72f, center.y + sin(angle) * distance * 0.72f)
        val to = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
        val color = if (index % 6 == 0) Color(0xFF7FD1FF) else Color.White
        drawLine(
            color = color.copy(alpha = 0.35f + 0.4f * (1f - radius)),
            start = from,
            end = to,
            strokeWidth = 1.2f + (1f - radius) * 2.4f,
        )
    }
}
