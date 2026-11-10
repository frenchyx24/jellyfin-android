package org.jellyfin.mobile.nflix.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Particle { Snow, Confetti, Bats, Hearts, Fireworks, Petals, Notes, Eggs, Tricolore }

/** Festive themes: France's public holidays plus the big popular celebrations. */
enum class SeasonalEvent(
    val label: String,
    val emoji: String,
    val greeting: String,
    val accent: Color,
    val deep: Color,
    val particle: Particle,
    val period: String,
) {
    NewYear("Nouvel An", "🥂", "Bonne année !", Color(0xFFFFC94A), Color(0xFF7A5A00), Particle.Confetti, "30 déc. – 2 janv."),
    Valentine("Saint-Valentin", "💘", "Joyeuse Saint-Valentin", Color(0xFFFF4F8B), Color(0xFF7A1238), Particle.Hearts, "12 – 14 févr."),
    Easter("Pâques", "🐣", "Joyeuses Pâques", Color(0xFF7FCB5B), Color(0xFF2F5E1E), Particle.Eggs, "Vendredi saint – lundi de Pâques"),
    LabourDay("Fête du Travail", "🌿", "Un brin de muguet pour vous", Color(0xFF5FD38D), Color(0xFF1D5E3A), Particle.Petals, "30 avr. – 1er mai"),
    Victory("Victoire 1945", "🇫🇷", "8 mai : souvenir et paix", Color(0xFF3D6BFF), Color(0xFF14286E), Particle.Tricolore, "8 mai"),
    MusicDay("Fête de la Musique", "🎶", "Montez le son !", Color(0xFFB46CFF), Color(0xFF4A1F7A), Particle.Notes, "21 juin"),
    Bastille("Fête nationale", "🎆", "Joyeux 14 Juillet !", Color(0xFF3D6BFF), Color(0xFF14286E), Particle.Fireworks, "13 – 14 juil."),
    Halloween("Halloween", "🎃", "Joyeux Halloween… si vous osez", Color(0xFFFF7A1A), Color(0xFF4B1A6B), Particle.Bats, "24 – 31 oct."),
    Armistice("Armistice", "🌼", "11 novembre : mémoire", Color(0xFF4A7BFF), Color(0xFF1A2C6B), Particle.Petals, "9 – 11 nov."),
    Christmas("Noël", "🎄", "Joyeux Noël !", Color(0xFFE31B3B), Color(0xFF0F6B3A), Particle.Snow, "1er – 26 déc.");

    /** Three colours the effects draw with. */
    val palette: List<Color>
        get() = when (this) {
            NewYear -> listOf(Color(0xFFFFC94A), Color(0xFFFFF1C2), Color(0xFFFF9EC7))
            Valentine -> listOf(Color(0xFFFF4F8B), Color(0xFFFF9EC0), Color(0xFFFFD1E1))
            Easter -> listOf(Color(0xFFFFD6E8), Color(0xFFCFF5D1), Color(0xFFD7E6FF))
            LabourDay -> listOf(Color(0xFFFFFFFF), Color(0xFFE8FFEF), Color(0xFF5FD38D))
            Victory, Bastille -> listOf(Color(0xFF2E5BFF), Color.White, Color(0xFFE5243B))
            MusicDay -> listOf(Color(0xFFB46CFF), Color(0xFF52E5FF), Color(0xFFFF6BD6))
            Halloween -> listOf(Color(0xFFFF7A1A), Color(0xFF9B5CFF), Color(0xFF7CFF6B))
            Armistice -> listOf(Color(0xFF4A7BFF), Color(0xFF8FB0FF), Color.White)
            Christmas -> listOf(Color.White, Color(0xFFDDEBFF), Color(0xFFFFE6A8))
        }

    fun isActive(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        fun between(from: MonthDay, to: MonthDay) = !day.isBefore(from) && !day.isAfter(to)
        return when (this) {
            NewYear -> between(MonthDay.of(12, 30), MonthDay.of(12, 31)) || between(MonthDay.of(1, 1), MonthDay.of(1, 2))
            Valentine -> between(MonthDay.of(2, 12), MonthDay.of(2, 14))
            Easter -> easterSunday(date.year).let { sunday -> !date.isBefore(sunday.minusDays(2)) && !date.isAfter(sunday.plusDays(1)) }
            LabourDay -> between(MonthDay.of(4, 30), MonthDay.of(5, 1))
            Victory -> day == MonthDay.of(5, 8)
            MusicDay -> day == MonthDay.of(6, 21)
            Bastille -> between(MonthDay.of(7, 13), MonthDay.of(7, 14))
            Halloween -> between(MonthDay.of(10, 24), MonthDay.of(10, 31))
            Armistice -> between(MonthDay.of(11, 9), MonthDay.of(11, 11))
            Christmas -> between(MonthDay.of(12, 1), MonthDay.of(12, 26))
        }
    }

    companion object {
        /** Anonymous Gregorian algorithm. */
        fun easterSunday(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val dayOfMonth = (h + l - 7 * m + 114) % 31 + 1
            return LocalDate.of(year, month, dayOfMonth)
        }

        fun activeOn(date: LocalDate, disabled: Set<String> = emptySet()): SeasonalEvent? =
            entries.firstOrNull { it.name !in disabled && it.isActive(date) }
    }
}

/** User choices and the theme currently applied. */
object SeasonalThemes {
    private const val PREFS = "nflix_appearance"
    private var context: Context? = null

    var enabled by mutableStateOf(true)
        private set
    var disabled by mutableStateOf(emptySet<String>())
        private set
    var preview by mutableStateOf<SeasonalEvent?>(null)
        private set

    /** Home hero scene. */
    var particles by mutableStateOf(true)
        private set

    /** Light effects drawn over every screen. */
    var everywhere by mutableStateOf(true)
        private set

    /** Decoration sitting on the navigation bar ("the roof"). */
    var roof by mutableStateOf(true)
        private set

    /** Bursts where the screen is touched. */
    var touch by mutableStateOf(true)
        private set

    /** 0 discreet, 1 normal, 2 festive. */
    var intensity by mutableStateOf(1)
        private set

    /** Event whose intro plays now (once a day). */
    var intro by mutableStateOf<SeasonalEvent?>(null)
        private set

    private var today by mutableStateOf(LocalDate.now())

    val active: SeasonalEvent?
        get() = preview ?: if (enabled) SeasonalEvent.activeOn(today, disabled) else null

    fun refreshDate() {
        LocalDate.now().takeIf { it != today }?.let { today = it }
        maybeIntro()
    }

    fun init(context: Context) {
        refreshDate()
        if (this.context != null) return
        this.context = context.applicationContext
        val prefs = prefs() ?: return
        enabled = prefs.getBoolean("seasonal", true)
        particles = prefs.getBoolean("particles", true)
        everywhere = prefs.getBoolean("everywhere", true)
        roof = prefs.getBoolean("roof", true)
        touch = prefs.getBoolean("touch", true)
        intensity = prefs.getInt("intensity", 1).coerceIn(0, 2)
        disabled = prefs.getStringSet("disabled", emptySet()).orEmpty().toSet()
        maybeIntro()
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun maybeIntro() {
        val event = active ?: return
        val prefs = prefs() ?: return
        val key = "intro_" + event.name
        if (prefs.getString(key, "") == today.toString()) return
        prefs.edit { putString(key, today.toString()) }
        intro = event
    }

    fun dismissIntro() { intro = null }

    fun updateEnabled(value: Boolean) { enabled = value; prefs()?.edit { putBoolean("seasonal", value) } }
    fun updateParticles(value: Boolean) { particles = value; prefs()?.edit { putBoolean("particles", value) } }
    fun updateEverywhere(value: Boolean) { everywhere = value; prefs()?.edit { putBoolean("everywhere", value) } }
    fun updateRoof(value: Boolean) { roof = value; prefs()?.edit { putBoolean("roof", value) } }
    fun updateTouch(value: Boolean) { touch = value; prefs()?.edit { putBoolean("touch", value) } }
    fun updateIntensity(value: Int) { intensity = value.coerceIn(0, 2); prefs()?.edit { putInt("intensity", intensity) } }

    fun setEventEnabled(event: SeasonalEvent, value: Boolean) {
        disabled = if (value) disabled - event.name else disabled + event.name
        prefs()?.edit { putStringSet("disabled", disabled) }
    }

    /** Session-only preview; plays the intro so the whole theme can be seen. */
    fun preview(event: SeasonalEvent?) {
        preview = event
        intro = event
    }
}

// ---- Shared clock and touches ---------------------------------------------------------------

@Composable
private fun rememberSceneTime(key: Any?): Float {
    val time by produceState(0f, key) {
        val start = withFrameMillis { it }
        while (true) value = withFrameMillis { (it - start) / 1000f }
    }
    return time
}

internal class Tap(val at: Offset, val start: Long, val seed: Int)

/** Taps anywhere (seen before the app handles them, never consumed). */
object SeasonalTaps {
    internal val taps = mutableStateListOf<Tap>()
    private var counter = 0

    internal fun add(at: Offset) {
        if (taps.size >= 6) taps.removeAt(0)
        taps.add(Tap(at, System.nanoTime(), counter++))
    }
}

/** Put on the root: records taps for the touch bursts without stealing them. */
fun Modifier.seasonalTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (SeasonalThemes.active == null || !SeasonalThemes.touch) continue
            event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { SeasonalTaps.add(it.position) }
        }
    }
}

// ---- Seeds ----------------------------------------------------------------------------------

private class Seed(index: Int, salt: Int) {
    private val r = java.util.Random(index * 7919L + salt * 104729L)
    val x = r.nextFloat()
    val phase = r.nextFloat()
    val depth = listOf(0.45f, 0.8f, 1.3f)[index % 3]
    val speed = (0.035f + r.nextFloat() * 0.06f) * depth
    val size = (0.6f + r.nextFloat() * 0.8f) * depth
    val alpha = 0.3f + 0.7f * ((depth - 0.45f) / 0.85f)
    val sway = r.nextFloat() * 2f * PI.toFloat()
    val spin = (r.nextFloat() - 0.5f) * 540f
    val color = r.nextInt(3)
    val angle = r.nextFloat() * 2f * PI.toFloat()
    val power = 0.5f + r.nextFloat()
}

// ---- Scenes ---------------------------------------------------------------------------------

/**
 * Rich festive scene (home hero, previews): depth-layered particles, an ambient backdrop and a
 * foreground effect per event. Draws only; never takes touches.
 */
@Composable
fun SeasonalParticles(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val event = SeasonalThemes.active ?: return
    if (!SeasonalThemes.particles) return
    SeasonalScene(event, modifier, intensity * (0.6f + 0.4f * SeasonalThemes.intensity))
}

@Composable
fun SeasonalScene(event: SeasonalEvent, modifier: Modifier = Modifier, intensity: Float = 1f, ambient: Boolean = true) {
    val count = (46 * intensity).toInt().coerceIn(6, 90)
    val seeds = remember(event, count) { List(count) { Seed(it, event.ordinal) } }
    val burst = remember(event) { List(36) { Seed(1000 + it, event.ordinal) } }
    val time = rememberSceneTime(event)
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(modifier) {
        if (ambient) drawBackdrop(event, time)
        seeds.forEach { drawParticle(event, it, time, paint) }
        if (event == SeasonalEvent.NewYear || event == SeasonalEvent.Bastille) seeds.take(if (event == SeasonalEvent.Bastille) 10 else 6).forEach { drawFirework(event, it, time) }
        if (ambient) drawForeground(event, time)
        if (time < 2.4f) drawOpeningBurst(event, burst, time)
    }
}

/** Light layer over every screen: aurora at the top, a few particles, and the touch bursts. */
@Composable
fun SeasonalOverlay(modifier: Modifier = Modifier) {
    val event = SeasonalThemes.active ?: return
    if (!SeasonalThemes.everywhere && !SeasonalThemes.touch) return
    val count = listOf(8, 16, 28)[SeasonalThemes.intensity]
    val seeds = remember(event, count) { List(count) { Seed(500 + it, event.ordinal) } }
    val sparks = remember(event) { List(18) { Seed(2000 + it, event.ordinal) } }
    val time = rememberSceneTime(event)
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(modifier) {
        if (SeasonalThemes.everywhere) {
            drawAurora(event, time)
            seeds.forEach { drawParticle(event, it, time, paint, faint = true) }
        }
        val now = System.nanoTime()
        SeasonalTaps.taps.forEach { tap ->
            val age = (now - tap.start) / 1e9f
            if (age < 1.3f) drawTapBurst(event, tap.at, age, sparks, tap.seed, paint)
        }
    }
}

/** Top-of-screen glow that breathes in the event colours. */
private fun DrawScope.drawAurora(event: SeasonalEvent, time: Float) {
    val w = size.width
    val h = 150.dp.toPx()
    val shift = sin(time * 0.35f) * 0.25f
    val a = 0.20f + 0.06f * sin(time * 0.9f)
    drawRect(
        Brush.radialGradient(
            listOf(event.accent.copy(alpha = a), event.deep.copy(alpha = a * 0.5f), Color.Transparent),
            center = Offset(w * (0.3f + shift), 0f), radius = w * 0.8f,
        ),
        size = Size(w, h),
    )
    drawRect(
        Brush.radialGradient(
            listOf(event.palette[1].copy(alpha = a * 0.6f), Color.Transparent),
            center = Offset(w * (0.8f - shift), -h * 0.2f), radius = w * 0.55f,
        ),
        size = Size(w, h),
    )
}

private fun DrawScope.drawParticle(event: SeasonalEvent, s: Seed, time: Float, paint: android.graphics.Paint, faint: Boolean = false) {
    val w = size.width
    val h = size.height
    val d = density
    val fade = if (faint) 0.55f else 1f
    val color = event.palette[s.color]
    when (event.particle) {
        Particle.Snow -> {
            val y = ((s.phase + time * s.speed) % 1.15f - 0.08f) * h
            val x = (s.x * w + sin(time * 0.8f + s.sway) * 18f * d * s.depth)
            if (s.depth < 0.6f) {
                // Far flakes: soft bokeh.
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * fade), Color.Transparent), Offset(x, y), 5f * d * s.size), 5f * d * s.size, Offset(x, y))
            } else {
                val r = 4.2f * d * s.size
                rotate(time * s.spin * 0.3f, Offset(x, y)) {
                    for (k in 0 until 6) {
                        val ang = k * PI.toFloat() / 3f
                        val end = Offset(x + cos(ang) * r, y + sin(ang) * r)
                        drawLine(color.copy(alpha = s.alpha * fade), Offset(x, y), end, strokeWidth = 1.1f * d, cap = StrokeCap.Round)
                        val mid = Offset(x + cos(ang) * r * 0.55f, y + sin(ang) * r * 0.55f)
                        drawLine(color.copy(alpha = s.alpha * fade * 0.8f), mid, Offset(mid.x + cos(ang + 0.7f) * r * 0.3f, mid.y + sin(ang + 0.7f) * r * 0.3f), strokeWidth = 0.9f * d)
                        drawLine(color.copy(alpha = s.alpha * fade * 0.8f), mid, Offset(mid.x + cos(ang - 0.7f) * r * 0.3f, mid.y + sin(ang - 0.7f) * r * 0.3f), strokeWidth = 0.9f * d)
                    }
                }
            }
        }
        Particle.Confetti -> {
            val y = ((s.phase + time * s.speed * 1.4f) % 1.15f - 0.08f) * h
            val x = s.x * w + sin(time * 1.6f + s.sway) * 24f * d
            val flip = cos(time * 5f * s.power + s.sway)
            rotate(time * s.spin, Offset(x, y)) {
                drawRect(color.copy(alpha = s.alpha * fade), Offset(x - 3.5f * d * s.size * abs(flip), y - 2f * d * s.size), Size(7f * d * s.size * abs(flip) + 0.5f, 4f * d * s.size))
            }
        }
        Particle.Bats -> {
            val lane = s.phase * h * 0.75f + h * 0.05f
            val x = ((s.x + time * s.speed * 0.9f) % 1.3f - 0.15f) * w
            val y = lane + sin(time * 2.2f + s.sway) * 20f * d
            drawBat(Offset(x, y), 12f * d * s.size, (sin(time * 12f * s.power + s.sway) + 1f) / 2f, Color(0xFF14081F).copy(alpha = 0.85f * fade))
            if (s.depth > 1f) drawCircle(event.accent.copy(alpha = 0.10f * fade), 18f * d * s.size, Offset(x, y))
        }
        Particle.Hearts -> {
            val y = (1.1f - (s.phase + time * s.speed) % 1.2f) * h
            val x = s.x * w + sin(time * 1.2f + s.sway) * 22f * d
            val pulse = 1f + 0.12f * sin(time * 6f + s.sway)
            drawHeart(Offset(x, y), 9f * d * s.size * pulse, color.copy(alpha = s.alpha * fade))
        }
        Particle.Fireworks -> {
            // Glitter falling between the rockets.
            val y = ((s.phase + time * s.speed * 0.6f) % 1.1f) * h
            val x = s.x * w + sin(time + s.sway) * 10f * d
            val tw = 0.5f + 0.5f * sin(time * 8f + s.sway)
            drawCircle(color.copy(alpha = s.alpha * tw * fade * 0.8f), 1.6f * d * s.size, Offset(x, y))
        }
        Particle.Petals -> {
            val y = ((s.phase + time * s.speed) % 1.15f - 0.08f) * h
            val x = s.x * w + sin(time * 0.9f + s.sway) * 34f * d
            rotate(time * s.spin * 0.5f + sin(time + s.sway) * 40f, Offset(x, y)) {
                drawOval(color.copy(alpha = s.alpha * fade), Offset(x - 4f * d * s.size, y - 2.4f * d * s.size), Size(8f * d * s.size, 4.8f * d * s.size))
            }
        }
        Particle.Notes -> {
            val y = (1.1f - (s.phase + time * s.speed) % 1.2f) * h
            val x = s.x * w + sin(time * 1.8f + s.sway) * 18f * d
            paint.color = android.graphics.Color.argb((255 * s.alpha * fade).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
            paint.textSize = 16f * d * s.size
            rotate(sin(time * 2f + s.sway) * 18f, Offset(x, y)) { drawContext.canvas.nativeCanvas.drawText(if (s.color == 0) "♪" else "♫", x, y, paint) }
        }
        Particle.Eggs -> {
            val y = ((s.phase + time * s.speed * 0.8f) % 1.15f - 0.08f) * h
            val x = s.x * w + sin(time * 0.9f + s.sway) * 16f * d
            val ew = 7f * d * s.size
            rotate(sin(time * 2f + s.sway) * 25f, Offset(x, y)) {
                drawOval(color.copy(alpha = s.alpha * fade), Offset(x - ew, y - ew * 1.3f), Size(ew * 2, ew * 2.6f))
                drawLine(event.palette[(s.color + 1) % 3].copy(alpha = s.alpha * fade), Offset(x - ew * 0.9f, y), Offset(x + ew * 0.9f, y), strokeWidth = 2f * d)
            }
        }
        Particle.Tricolore -> {
            val y = ((s.phase + time * s.speed) % 1.15f - 0.08f) * h
            val x = s.x * w + sin(time * 1.1f + s.sway) * 26f * d
            val ribbon = Path()
            val len = 18f * d * s.size
            ribbon.moveTo(x, y)
            for (k in 1..6) {
                val t = k / 6f
                ribbon.lineTo(x + sin(time * 4f + t * 5f + s.sway) * 4f * d, y + t * len)
            }
            drawPath(ribbon, color.copy(alpha = s.alpha * fade), style = Stroke(width = 3f * d * s.size, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawFirework(event: SeasonalEvent, s: Seed, time: Float) {
    val w = size.width
    val h = size.height
    val period = 3.2f + s.power
    val t = (time + s.phase * period) % period
    val origin = Offset(s.x * w, h * (0.18f + s.phase * 0.35f))
    val color = event.palette[s.color]
    if (t < 0.7f) {
        // Rocket climbing with its trail.
        val p = t / 0.7f
        val y = h + (origin.y - h) * FastOutSlowInEasing.transform(p)
        drawLine(Brush.verticalGradient(listOf(color, Color.Transparent), y, y + 60f * density), Offset(origin.x, y), Offset(origin.x, y + 60f * density), strokeWidth = 2f * density, cap = StrokeCap.Round)
        return
    }
    val p = ((t - 0.7f) / 1.8f).coerceAtMost(1f)
    if (p >= 1f) return
    val radius = 70f * density * s.power * FastOutSlowInEasing.transform(p)
    val alpha = (1f - p)
    drawCircle(color.copy(alpha = 0.18f * alpha), radius * 0.6f, origin, blendMode = BlendMode.Plus)
    for (k in 0 until 22) {
        val ang = k * 2f * PI.toFloat() / 22f + s.angle
        val gravity = p * p * 40f * density
        val end = Offset(origin.x + cos(ang) * radius, origin.y + sin(ang) * radius + gravity)
        val start = Offset(origin.x + cos(ang) * radius * 0.7f, origin.y + sin(ang) * radius * 0.7f + gravity * 0.7f)
        drawLine(color.copy(alpha = alpha), start, end, strokeWidth = 2f * density, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = alpha * 0.9f), 1.4f * density, end)
    }
}

private fun DrawScope.drawBackdrop(event: SeasonalEvent, time: Float) {
    val w = size.width
    val h = size.height
    when (event) {
        SeasonalEvent.Christmas -> {
            // Soft bokeh lights in the distance.
            for (k in 0 until 9) {
                val x = (k * 0.137f + 0.05f) % 1f * w
                val y = h * (0.15f + (k * 0.21f) % 0.6f)
                val c = listOf(Color(0xFFFF3B3B), Color(0xFFFFD54A), Color(0xFF38E07B), Color(0xFF4FA8FF))[k % 4]
                val a = 0.10f + 0.08f * sin(time * 1.3f + k)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(x, y), 46f * density), 46f * density, Offset(x, y))
            }
        }
        SeasonalEvent.Halloween -> {
            // Moon and drifting fog.
            val moon = Offset(w * 0.82f, h * 0.16f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9B0).copy(alpha = 0.35f), Color.Transparent), moon, 90f * density), 90f * density, moon)
            drawCircle(Color(0xFFFFF3D1).copy(alpha = 0.55f), 22f * density, moon)
            for (k in 0 until 3) {
                val y = h * (0.62f + k * 0.12f)
                val x = ((time * (0.02f + k * 0.01f) + k * 0.3f) % 1f) * w
                drawOval(Brush.radialGradient(listOf(Color(0xFF9B5CFF).copy(alpha = 0.10f), Color.Transparent), Offset(x, y), w * 0.5f), Offset(x - w * 0.6f, y - 40f * density), Size(w * 1.2f, 80f * density))
            }
        }
        SeasonalEvent.MusicDay -> {
            // Stage beams sweeping.
            for (k in 0 until 3) {
                val base = Offset(w * (0.2f + k * 0.3f), h)
                val ang = -PI.toFloat() / 2 + sin(time * 0.8f + k * 2f) * 0.5f
                val tip = Offset(base.x + cos(ang) * h, base.y + sin(ang) * h)
                val beam = Path().apply {
                    moveTo(base.x, base.y)
                    lineTo(tip.x - 60f * density, tip.y)
                    lineTo(tip.x + 60f * density, tip.y)
                    close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, event.palette[k].copy(alpha = 0.14f))))
            }
        }
        else -> drawAurora(event, time)
    }
}

private fun DrawScope.drawForeground(event: SeasonalEvent, time: Float) {
    val w = size.width
    val h = size.height
    when (event) {
        SeasonalEvent.Christmas -> drawGarland(0f, w, 10f * density, time, sag = 26f * density)
        SeasonalEvent.Halloween -> {
            // Rare lightning flash.
            val cycle = time % 7f
            if (cycle in 5.0f..5.25f) {
                val a = (1f - abs(cycle - 5.1f) * 6f).coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.07f * a))
                val bolt = Path().apply {
                    moveTo(w * 0.7f, 0f); lineTo(w * 0.64f, h * 0.12f); lineTo(w * 0.69f, h * 0.13f); lineTo(w * 0.6f, h * 0.3f)
                }
                drawPath(bolt, Color.White.copy(alpha = 0.6f * a), style = Stroke(2.2f * density))
            }
            drawCobweb(Offset(0f, 0f), 70f * density, Color.White.copy(alpha = 0.18f))
            drawSpider(Offset(w * 0.86f, 0f), (60f + 40f * sin(time * 0.7f)) * density, sin(time * 1.4f) * 8f)
        }
        SeasonalEvent.Valentine -> drawRect(Brush.radialGradient(listOf(Color.Transparent, event.accent.copy(alpha = 0.10f)), Offset(w / 2, h / 2), maxOf(w, h) * 0.8f))
        else -> {}
    }
}

private fun DrawScope.drawOpeningBurst(event: SeasonalEvent, seeds: List<Seed>, time: Float) {
    val origin = Offset(96.dp.toPx(), 70.dp.toPx())
    val p = (time / 2.4f).coerceIn(0f, 1f)
    seeds.forEach { s ->
        val dist = 160f * density * s.power * FastOutSlowInEasing.transform(p)
        val pos = Offset(origin.x + cos(s.angle) * dist, origin.y + sin(s.angle) * dist + p * p * 60f * density)
        drawCircle(event.palette[s.color].copy(alpha = (1f - p) * 0.9f), 2.6f * density * s.size, pos)
    }
}

private fun DrawScope.drawTapBurst(event: SeasonalEvent, at: Offset, age: Float, sparks: List<Seed>, salt: Int, paint: android.graphics.Paint) {
    val p = (age / 1.3f).coerceIn(0f, 1f)
    val e = FastOutSlowInEasing.transform(p)
    // Shock ring.
    drawCircle(event.accent.copy(alpha = 0.45f * (1f - p)), 10f * density + 50f * density * e, at, style = Stroke(2f * density * (1f - p) + 0.5f))
    sparks.forEachIndexed { i, s ->
        val ang = s.angle + salt
        val dist = 64f * density * s.power * e
        val pos = Offset(at.x + cos(ang) * dist, at.y + sin(ang) * dist + p * p * 30f * density)
        val c = event.palette[(s.color + i) % 3].copy(alpha = 1f - p)
        val sz = 5f * density * s.size * (1f - p * 0.5f)
        when (event.particle) {
            Particle.Hearts -> drawHeart(pos, sz * 1.3f, c)
            Particle.Bats -> drawBat(pos, sz * 1.6f, (sin(age * 30f + i) + 1f) / 2f, Color(0xFF14081F).copy(alpha = 1f - p))
            Particle.Snow -> drawCircle(c, sz * 0.5f, pos)
            Particle.Notes -> {
                paint.color = android.graphics.Color.argb((255 * (1f - p)).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                paint.textSize = sz * 3f
                drawContext.canvas.nativeCanvas.drawText("♪", pos.x, pos.y, paint)
            }
            else -> rotate(age * 400f + i * 30f, pos) { drawRect(c, Offset(pos.x - sz / 2, pos.y - sz / 4), Size(sz, sz / 2)) }
        }
    }
}

// ---- The roof: decoration sitting on the navigation bar ------------------------------------

/**
 * Zero-height strip placed right above the navigation bar: draws upward (and a little downward,
 * icicles and drips) without taking any space or touch.
 */
@Composable
fun SeasonalRoof(modifier: Modifier = Modifier) {
    val event = SeasonalThemes.active
    if (event == null || !SeasonalThemes.roof) return
    val time = rememberSceneTime(event)
    Box(modifier.height(0.dp).drawBehind { drawRoof(event, time) })
}

private fun DrawScope.drawRoof(event: SeasonalEvent, time: Float) {
    val w = size.width
    val d = density
    when (event) {
        SeasonalEvent.Christmas -> {
            // Snow cap with lumps, a blue shade and icicles.
            val cap = Path().apply {
                moveTo(0f, 4f * d)
                var x = 0f
                while (x <= w) {
                    val bump = 7f * d + 4f * d * sin(x / (37f * d)) + 3f * d * sin(x / (13f * d) + 1.3f)
                    lineTo(x, -bump)
                    x += 6f * d
                }
                lineTo(w, 4f * d)
                close()
            }
            drawPath(cap, Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8FF)), -14f * d, 4f * d))
            for (k in 0 until (w / (42f * d)).toInt()) {
                val x = k * 42f * d + 18f * d
                val len = (6f + (k * 7 % 5) * 2.5f) * d * (0.85f + 0.15f * sin(time * 0.5f + k))
                val ice = Path().apply { moveTo(x - 3f * d, 3f * d); lineTo(x + 3f * d, 3f * d); lineTo(x, 3f * d + len); close() }
                drawPath(ice, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFBFE3FF).copy(alpha = 0.4f)), 3f * d, 3f * d + len))
            }
            drawGarland(0f, w, -16f * d, time, sag = 10f * d, bulbSize = 3.2f)
        }
        SeasonalEvent.Halloween -> {
            for (k in 0 until 3) {
                val x = w * (0.12f + k * 0.38f)
                val flicker = 0.75f + 0.25f * sin(time * 9f + k * 2f) * sin(time * 3.3f + k)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A2E).copy(alpha = 0.35f * flicker), Color.Transparent), Offset(x, -8f * d), 28f * d), 28f * d, Offset(x, -8f * d))
                drawOval(Color(0xFFE8650E), Offset(x - 11f * d, -18f * d), Size(22f * d, 17f * d))
                drawOval(Color(0xFFFF8A2B), Offset(x - 6f * d, -18f * d), Size(12f * d, 17f * d))
                drawRect(Color(0xFF3B6B1E), Offset(x - 1.5f * d, -22f * d), Size(3f * d, 5f * d))
                val face = Color(0xFFFFE27A).copy(alpha = flicker)
                drawCircle(face, 2f * d, Offset(x - 4f * d, -12f * d)); drawCircle(face, 2f * d, Offset(x + 4f * d, -12f * d))
                drawRect(face, Offset(x - 4.5f * d, -7.5f * d), Size(9f * d, 2f * d))
            }
            drawCobweb(Offset(w, 0f), 34f * d, Color.White.copy(alpha = 0.25f), flip = true, up = true)
        }
        SeasonalEvent.NewYear -> {
            val tinsel = Path().apply {
                moveTo(0f, -4f * d)
                var x = 0f
                while (x <= w) { lineTo(x, -4f * d - 3f * d * sin(x / (11f * d) + time * 3f)); x += 4f * d }
            }
            drawPath(tinsel, Brush.horizontalGradient(listOf(Color(0xFFFFC94A), Color(0xFFFFF1C2), Color(0xFFFFC94A))), style = Stroke(3f * d))
            for (k in 0 until 14) {
                val x = (k * 0.0731f + (time * 0.05f)) % 1f * w
                val tw = (sin(time * 6f + k * 1.7f) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = tw), 1.6f * d, Offset(x, -10f * d - (k % 3) * 4f * d))
            }
        }
        SeasonalEvent.Valentine -> drawBunting(w, time, d) { i, c, center, s -> drawHeart(center, s, listOf(Color(0xFFFF4F8B), Color(0xFFFF9EC0))[i % 2]) }
        SeasonalEvent.Easter, SeasonalEvent.LabourDay -> {
            for (k in 0 until (w / (5f * d)).toInt()) {
                val x = k * 5f * d
                val hgt = (6f + (k * 13 % 7)) * d
                val bend = sin(time * 1.6f + k * 0.4f) * 3f * d
                drawLine(Color(0xFF5FB847), Offset(x, 2f * d), Offset(x + bend, 2f * d - hgt), strokeWidth = 1.6f * d, cap = StrokeCap.Round)
            }
            for (k in 0 until 5) {
                val x = w * (0.1f + k * 0.2f)
                if (event == SeasonalEvent.Easter) {
                    drawOval(event.palette[k % 3], Offset(x - 5f * d, -13f * d), Size(10f * d, 13f * d))
                } else {
                    for (b in 0 until 3) drawCircle(Color.White, 2.6f * d, Offset(x + b * 4f * d, -12f * d + b * 2f * d + sin(time * 2f + k) * d))
                }
            }
        }
        SeasonalEvent.MusicDay -> {
            val bars = (w / (9f * d)).toInt()
            for (k in 0 until bars) {
                val v = (sin(time * 7f + k * 0.7f) * sin(time * 2.3f + k * 0.21f) + 1f) / 2f
                val hgt = (4f + 18f * v) * d
                drawRoundRect(Brush.verticalGradient(listOf(event.palette[k % 3], event.accent.copy(alpha = 0.3f)), -hgt, 0f), Offset(k * 9f * d + 1f * d, -hgt), Size(6f * d, hgt), CornerRadius(2f * d))
            }
        }
        SeasonalEvent.Victory, SeasonalEvent.Bastille, SeasonalEvent.Armistice -> drawBunting(w, time, d) { i, _, center, s ->
            val c = listOf(Color(0xFF2E5BFF), Color.White, Color(0xFFE5243B))[i % 3]
            val flag = Path().apply { moveTo(center.x - s, center.y - s); lineTo(center.x + s, center.y - s); lineTo(center.x, center.y + s * 1.2f); close() }
            drawPath(flag, c)
        }
    }
}

/** Rope along the roof with little pennants swaying. */
private fun DrawScope.drawBunting(w: Float, time: Float, d: Float, item: DrawScope.(Int, Color, Offset, Float) -> Unit) {
    val step = 30f * d
    val count = (w / step).toInt() + 1
    val rope = Path().apply { moveTo(0f, -16f * d) }
    for (k in 0..count) {
        val x = k * step
        rope.quadraticTo(x - step / 2, -16f * d + 7f * d, x, -16f * d)
    }
    drawPath(rope, Color.White.copy(alpha = 0.5f), style = Stroke(1f * d))
    for (k in 0 until count) {
        val x = k * step + step / 2
        val swing = sin(time * 2f + k * 0.8f) * 8f
        rotate(swing, Offset(x, -12f * d)) { item(k, Color.White, Offset(x, -5f * d), 6f * d) }
    }
}

private fun DrawScope.drawGarland(from: Float, to: Float, y: Float, time: Float, sag: Float, bulbSize: Float = 4f) {
    val colors = listOf(Color(0xFFFF3B3B), Color(0xFFFFD54A), Color(0xFF38E07B), Color(0xFF4FA8FF))
    val span = 120f * density
    var x = from
    var index = 0
    while (x < to) {
        val wire = Path().apply { moveTo(x, y); quadraticTo(x + span / 2, y + sag * 2f, x + span, y) }
        drawPath(wire, Color(0xFF1E3B22), style = Stroke(1.4f * density))
        for (k in 1..5) {
            val t = k / 6f
            val bx = x + span * t
            val by = y + sag * 2f * 2f * t * (1f - t)
            val c = colors[(index + k) % 4]
            val on = 0.55f + 0.45f * sin(time * 3f + (index + k) * 1.9f)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * on), Color.Transparent), Offset(bx, by + 3f * density), 12f * density), 12f * density, Offset(bx, by + 3f * density))
            drawOval(c.copy(alpha = 0.6f + 0.4f * on), Offset(bx - bulbSize * density * 0.7f, by), Size(bulbSize * density * 1.4f, bulbSize * density * 2f))
        }
        x += span
        index += 5
    }
}

private fun DrawScope.drawCobweb(corner: Offset, r: Float, color: Color, flip: Boolean = false, up: Boolean = false) {
    val sx = if (flip) -1f else 1f
    val sy = if (up) -1f else 1f
    for (k in 0..4) {
        val ang = k * (PI.toFloat() / 2f) / 4f
        drawLine(color, corner, Offset(corner.x + sx * cos(ang) * r, corner.y + sy * sin(ang) * r), strokeWidth = 0.8f * density)
    }
    for (ring in 1..3) {
        val rr = r * ring / 3.5f
        val web = Path()
        for (k in 0..4) {
            val ang = k * (PI.toFloat() / 2f) / 4f
            val p = Offset(corner.x + sx * cos(ang) * rr, corner.y + sy * sin(ang) * rr)
            if (k == 0) web.moveTo(p.x, p.y) else web.quadraticTo(corner.x + sx * cos(ang - 0.2f) * rr * 0.8f, corner.y + sy * sin(ang - 0.2f) * rr * 0.8f, p.x, p.y)
        }
        drawPath(web, color, style = Stroke(0.8f * density))
    }
}

private fun DrawScope.drawSpider(anchor: Offset, drop: Float, swing: Float) {
    rotate(swing, anchor) {
        val body = Offset(anchor.x, anchor.y + drop)
        drawLine(Color.White.copy(alpha = 0.35f), anchor, body, strokeWidth = 0.8f * density)
        for (k in 0 until 4) {
            val a = 0.5f + k * 0.35f
            drawLine(Color(0xFF14081F), body, Offset(body.x - cos(a) * 9f * density, body.y + sin(a) * 6f * density - 3f * density), strokeWidth = 1.3f * density)
            drawLine(Color(0xFF14081F), body, Offset(body.x + cos(a) * 9f * density, body.y + sin(a) * 6f * density - 3f * density), strokeWidth = 1.3f * density)
        }
        drawCircle(Color(0xFF14081F), 4.5f * density, body)
        drawCircle(Color(0xFFFF4040), 0.9f * density, Offset(body.x - 1.5f * density, body.y + 1.5f * density))
        drawCircle(Color(0xFFFF4040), 0.9f * density, Offset(body.x + 1.5f * density, body.y + 1.5f * density))
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.35f)
        cubicTo(center.x - size, center.y - size * 0.4f, center.x - size * 0.4f, center.y - size, center.x, center.y - size * 0.35f)
        cubicTo(center.x + size * 0.4f, center.y - size, center.x + size, center.y - size * 0.4f, center.x, center.y + size * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBat(center: Offset, size: Float, flap: Float, color: Color) {
    val wing = size * (0.35f + 0.45f * flap)
    val path = Path().apply {
        moveTo(center.x, center.y)
        quadraticTo(center.x - size * 0.5f, center.y - wing, center.x - size, center.y - wing * 0.3f)
        quadraticTo(center.x - size * 0.7f, center.y + size * 0.05f, center.x - size * 0.45f, center.y + size * 0.1f)
        quadraticTo(center.x - size * 0.2f, center.y, center.x, center.y + size * 0.2f)
        quadraticTo(center.x + size * 0.2f, center.y, center.x + size * 0.45f, center.y + size * 0.1f)
        quadraticTo(center.x + size * 0.7f, center.y + size * 0.05f, center.x + size, center.y - wing * 0.3f)
        quadraticTo(center.x + size * 0.5f, center.y - wing, center.x, center.y)
        close()
    }
    drawPath(path, color)
}

// ---- Intro ---------------------------------------------------------------------------------

/** Full-screen welcome, once a day per event (and on preview): emoji pop, shock rings, greeting. */
@Composable
fun SeasonalIntro() {
    val event = SeasonalThemes.intro ?: return
    val progress = remember(event) { Animatable(0f) }
    val pop = remember(event) { Animatable(0f) }
    LaunchedEffect(event) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 180f)) }
        progress.animateTo(1f, tween(2800))
        SeasonalThemes.dismissIntro()
    }
    val p = progress.value
    val fadeOut = if (p > 0.82f) (1f - (p - 0.82f) / 0.18f) else 1f
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fadeOut.coerceIn(0f, 1f) }
            .background(Brush.radialGradient(listOf(event.deep.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.82f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { SeasonalThemes.dismissIntro() },
        contentAlignment = Alignment.Center,
    ) {
        SeasonalScene(event, Modifier.fillMaxSize(), intensity = 1.2f, ambient = false)
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 40.dp.toPx())
            for (k in 0 until 3) {
                val rp = ((p * 1.6f) - k * 0.18f).coerceIn(0f, 1f)
                if (rp > 0f) drawCircle(event.accent.copy(alpha = 0.6f * (1f - rp)), 40.dp.toPx() + rp * size.width * 0.6f, c, style = Stroke((6f * (1f - rp) + 1f) * density))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(event.emoji, fontSize = 96.sp, modifier = Modifier.graphicsLayer {
                val s = pop.value
                scaleX = s; scaleY = s
                rotationZ = (1f - s) * -40f + sin(p * 12f) * 4f
            })
            Spacer(Modifier.height(18.dp))
            val letters = event.greeting
            val shown = (letters.length * (p / 0.45f)).toInt().coerceIn(0, letters.length)
            Text(
                letters.substring(0, shown),
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(width = (160f * (p / 0.5f).coerceAtMost(1f)).dp, height = 3.dp).clip(CircleShape).background(Brush.horizontalGradient(event.palette)))
        }
    }
}
