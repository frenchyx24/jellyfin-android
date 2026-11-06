package org.jellyfin.mobile.nflix.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.PI
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

/** User choice (on/off, per event, preview) and the theme currently applied. */
object SeasonalThemes {
    private const val PREFS = "nflix_appearance"
    private var context: Context? = null

    var enabled by mutableStateOf(true)
        private set
    var disabled by mutableStateOf(emptySet<String>())
        private set
    var preview by mutableStateOf<SeasonalEvent?>(null)
        private set
    var particles by mutableStateOf(true)
        private set

    private var today by mutableStateOf(LocalDate.now())

    /** Read on every accent lookup: cheap, the calendar is only checked when [refreshDate] runs. */
    val active: SeasonalEvent?
        get() = preview ?: if (enabled) SeasonalEvent.activeOn(today, disabled) else null

    /** Called when the app returns to the foreground, so a theme starts or ends on the right day. */
    fun refreshDate() {
        LocalDate.now().takeIf { it != today }?.let { today = it }
    }

    fun init(context: Context) {
        refreshDate()
        if (this.context != null) return
        this.context = context.applicationContext
        val prefs = prefs() ?: return
        enabled = prefs.getBoolean("seasonal", true)
        particles = prefs.getBoolean("particles", true)
        disabled = prefs.getStringSet("disabled", emptySet()).orEmpty().toSet()
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs()?.edit { putBoolean("seasonal", value) }
    }

    fun updateParticles(value: Boolean) {
        particles = value
        prefs()?.edit { putBoolean("particles", value) }
    }

    fun setEventEnabled(event: SeasonalEvent, value: Boolean) {
        disabled = if (value) disabled - event.name else disabled + event.name
        prefs()?.edit { putStringSet("disabled", disabled) }
    }

    /** Preview is session-only: the app returns to the calendar on next launch. */
    fun preview(event: SeasonalEvent?) {
        preview = event
    }
}

/**
 * Festive scene: three depth layers of particles plus an ambient effect per event
 * (Christmas lights, Halloween fog and lightning, fireworks, equalizer…) and a one-shot burst when it appears.
 * Draws only; never consumes touches. [intensity] scales density (1 = home hero).
 */
@Composable
fun SeasonalParticles(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val event = SeasonalThemes.active ?: return
    if (!SeasonalThemes.particles) return
    val count = (54 * intensity).toInt().coerceAtLeast(8)
    val seeds = remember(event, count) { List(count) { i -> ParticleSeed(i, event) } }
    val burst = remember(event) { List(40) { i -> ParticleSeed(1000 + i, event) } }
    val time by produceState(0f, event) {
        val start = withFrameMillis { it }
        while (true) value = withFrameMillis { (it - start) / 1000f }
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(modifier) {
        drawAmbientBack(event, time)
        seeds.forEach { seed -> drawParticle(event.particle, seed, time, event, paint) }
        if (event.particle == Particle.Fireworks || event == SeasonalEvent.NewYear) {
            // Extra fireworks layer on top of confetti for New Year.
            if (event == SeasonalEvent.NewYear) seeds.take(8).forEach { drawFirework(it, time, event) }
        }
        drawAmbientFront(event, time)
        if (time < 2.4f) drawBurst(burst, time, event)
    }
}

private class ParticleSeed(index: Int, event: SeasonalEvent) {
    private val random = java.util.Random(index * 7919L + event.ordinal)
    val x = random.nextFloat()
    val phase = random.nextFloat()

    /** 0.45 = far (small, slow, faint) … 1.35 = near (big, fast, bright). */
    val depth = listOf(0.45f, 0.8f, 1.35f)[index % 3]
    val speed = (0.04f + random.nextFloat() * 0.07f) * depth
    val size = (0.6f + random.nextFloat() * 0.8f) * depth
    val alpha = 0.35f + 0.65f * ((depth - 0.45f) / 0.9f)
    val sway = random.nextFloat() * 2f * PI.toFloat()
    val spin = (random.nextFloat() - 0.5f) * 540f
    val palette = random.nextInt(3)
    val angle = random.nextFloat() * 2f * PI.toFloat()
    val power = 0.5f + random.nextFloat()
}

private val confetti = listOf(Color(0xFFFFC94A), Color(0xFFFF4F8B), Color(0xFF6CB6FF))
private val tricolore = listOf(Color(0xFF2E5BFF), Color.White, Color(0xFFE5243B))
private val pastel = listOf(Color(0xFFFFD6E8), Color(0xFFCFF5D1), Color(0xFFD7E6FF))
private val bulbs = listOf(Color(0xFFFF3B3B), Color(0xFFFFD54A), Color(0xFF38E07B), Color(0xFF4FA8FF))

private fun DrawScope.drawParticle(type: Particle, seed: ParticleSeed, time: Float, event: SeasonalEvent, paint: android.graphics.Paint) {
    val w = size.width
    val h = size.height
    val progress = (seed.phase + time * seed.speed) % 1f
    val sway = sin(time * 1.4f + seed.sway) * 22f * seed.depth
    val x = seed.x * w + sway
    val falling = Offset(x, -20f + progress * (h + 40f))
    val rising = Offset(x, h + 20f - progress * (h + 40f))
    val fade = (seed.alpha * (1f - progress * 0.6f)).coerceIn(0f, 1f)
    val unit = 6.dp.toPx() * seed.size
    when (type) {
        Particle.Snow -> {
            drawCircle(Color.White.copy(alpha = fade * 0.25f), unit * 1.2f, falling)
            drawCircle(Color.White.copy(alpha = fade), unit * 0.55f, falling)
        }
        Particle.Confetti, Particle.Tricolore -> {
            val color = (if (type == Particle.Tricolore) tricolore else confetti)[seed.palette]
            // Flip effect: width oscillates like paper turning in the air.
            val flip = kotlin.math.abs(cos(time * 3f + seed.sway))
            rotate(time * seed.spin + seed.sway * 57f, falling) {
                drawRect(color.copy(alpha = fade), Offset(falling.x - unit / 2 * flip, falling.y - unit / 4), Size(unit * flip.coerceAtLeast(0.15f), unit / 2))
            }
        }
        Particle.Petals -> {
            val color = if (event == SeasonalEvent.Armistice) Color(0xFF6E95FF) else Color.White
            rotate(sin(time + seed.sway) * 50f, falling) {
                drawOval(color.copy(alpha = fade), Offset(falling.x - unit / 2, falling.y - unit / 3), Size(unit, unit * 0.66f))
                drawOval(color.copy(alpha = fade * 0.6f), Offset(falling.x - unit / 3, falling.y), Size(unit * 0.66f, unit * 0.5f))
            }
        }
        Particle.Eggs -> rotate(sin(time * 2f + seed.sway) * 25f, falling) {
            val egg = pastel[seed.palette].copy(alpha = fade)
            drawOval(egg, Offset(falling.x - unit / 2, falling.y - unit * 0.65f), Size(unit, unit * 1.3f))
            drawRect(Color.White.copy(alpha = fade * 0.7f), Offset(falling.x - unit / 2, falling.y - unit * 0.08f), Size(unit, unit * 0.16f))
        }
        Particle.Hearts -> {
            val beat = 1f + 0.12f * sin(time * 6f + seed.sway)
            drawHeart(rising, unit * 1.1f * beat, event.accent.copy(alpha = fade))
        }
        Particle.Bats -> {
            val flight = Offset((seed.x * w + time * 60f * seed.depth) % (w + 80f) - 40f, seed.phase * h * 0.75f + sin(time * 2f + seed.sway) * 18f)
            drawBat(flight, unit * 1.7f, sin(time * 14f + seed.sway), if (seed.palette == 0) event.accent.copy(alpha = 0.75f * seed.alpha) else Color(0xFF12051C).copy(alpha = 0.9f * seed.alpha))
        }
        Particle.Notes -> {
            paint.color = android.graphics.Color.argb((255 * fade).toInt(), 214, 176, 255)
            paint.textSize = unit * 3.2f
            rotate(sin(time * 2f + seed.sway) * 20f, rising) {
                drawContext.canvas.nativeCanvas.drawText(if (seed.palette == 0) "♪" else "♫", rising.x, rising.y, paint)
            }
        }
        Particle.Fireworks -> drawFirework(seed, time, event)
    }
}

/** A rocket climbs, then bursts into a ring of sparks that fall and fade. */
private fun DrawScope.drawFirework(seed: ParticleSeed, time: Float, event: SeasonalEvent) {
    val w = size.width
    val h = size.height
    val cycle = (time * 0.32f + seed.phase) % 1f
    val target = Offset(seed.x * w, h * (0.12f + seed.sway / (2f * PI.toFloat()) * 0.45f))
    val colors = if (event == SeasonalEvent.NewYear) confetti else tricolore
    val color = colors[seed.palette]
    val unit = 6.dp.toPx()
    if (cycle < 0.25f) {
        val climb = cycle / 0.25f
        val pos = Offset(target.x, h - (h - target.y) * climb)
        drawCircle(Color.White.copy(alpha = 0.9f), unit * 0.35f, pos)
        drawLine(color.copy(alpha = 0.5f), pos, Offset(pos.x, pos.y + unit * 5f), strokeWidth = unit * 0.25f)
    } else {
        val t = (cycle - 0.25f) / 0.75f
        val radius = unit * 9f * seed.power * kotlin.math.sqrt(t)
        val gravity = unit * 4f * t * t
        repeat(14) { ray ->
            val angle = ray / 14f * 2f * PI.toFloat() + seed.angle
            val p = Offset(target.x + cos(angle) * radius, target.y + sin(angle) * radius + gravity)
            val tail = Offset(target.x + cos(angle) * radius * 0.82f, target.y + sin(angle) * radius * 0.82f + gravity * 0.8f)
            drawLine(color.copy(alpha = (1f - t) * 0.6f), tail, p, strokeWidth = unit * 0.22f)
            drawCircle(color.copy(alpha = (1f - t)), unit * 0.3f, p)
        }
        if (t < 0.15f) drawCircle(Color.White.copy(alpha = (0.15f - t) * 3f), unit * 4f, target)
    }
}

/** Effects drawn behind particles. */
private fun DrawScope.drawAmbientBack(event: SeasonalEvent, time: Float) {
    val w = size.width
    val h = size.height
    when (event) {
        SeasonalEvent.Halloween -> {
            // Rolling purple fog along the bottom.
            repeat(3) { layer ->
                val y = h * (0.72f + 0.08f * layer) + sin(time * 0.6f + layer) * 12f
                drawOval(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF6A2A9C).copy(alpha = 0.32f), Color.Transparent)),
                    Offset(((time * 30f * (layer + 1)) % (w * 1.6f)) - w * 0.6f, y - h * 0.15f),
                    Size(w * 1.2f, h * 0.3f),
                )
            }
        }
        SeasonalEvent.Valentine -> {
            val pulse = 0.18f + 0.1f * sin(time * 2.4f)
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(event.accent.copy(alpha = pulse), Color.Transparent), center = Offset(w / 2, h * 0.35f), radius = w * 0.9f))
        }
        SeasonalEvent.Christmas -> {
            // Warm glow from the top, as if lit by the garland.
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.10f + 0.04f * sin(time * 1.5f)), Color.Transparent), endY = h * 0.4f))
        }
        else -> Unit
    }
}

/** Effects drawn over particles. */
private fun DrawScope.drawAmbientFront(event: SeasonalEvent, time: Float) {
    val w = size.width
    val h = size.height
    val unit = 6.dp.toPx()
    when (event) {
        SeasonalEvent.Christmas, SeasonalEvent.NewYear -> {
            // Garland of blinking bulbs hanging under the top bar.
            val top = 64.dp.toPx() + 28.dp.toPx()
            val count = 13
            val path = Path()
            for (i in 0..count) {
                val x = w * i / count
                val y = top + sin(i / count.toFloat() * PI.toFloat() * 3f) * 10f + 14f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF1E3A22).copy(alpha = 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = unit * 0.35f))
            for (i in 0 until count) {
                val x = w * (i + 0.5f) / count
                val y = top + sin((i + 0.5f) / count * PI.toFloat() * 3f) * 10f + 14f + unit * 0.9f
                val color = if (event == SeasonalEvent.NewYear) confetti[i % 3] else bulbs[i % 4]
                val on = 0.45f + 0.55f * ((sin(time * 3f + i * 1.7f) + 1f) / 2f)
                drawCircle(color.copy(alpha = 0.25f * on), unit * 1.6f, Offset(x, y))
                drawOval(color.copy(alpha = on), Offset(x - unit * 0.45f, y - unit * 0.6f), Size(unit * 0.9f, unit * 1.2f))
            }
        }
        SeasonalEvent.Halloween -> {
            // Lightning: two quick flashes roughly every nine seconds.
            val t = time % 9f
            val flash = when {
                t in 6.0f..6.08f -> 0.35f
                t in 6.18f..6.24f -> 0.22f
                else -> 0f
            }
            if (flash > 0f) drawRect(Color(0xFFE9D9FF).copy(alpha = flash))
        }
        SeasonalEvent.MusicDay -> {
            // Equalizer at the bottom of the hero.
            val bars = 28
            val barWidth = w / bars
            repeat(bars) { i ->
                val level = (sin(time * (4f + i % 5) + i * 0.9f) + 1f) / 2f * 0.6f + 0.1f
                val height = h * 0.12f * level
                drawRect(event.accent.copy(alpha = 0.35f), Offset(i * barWidth + barWidth * 0.2f, h - height), Size(barWidth * 0.6f, height))
            }
        }
        SeasonalEvent.Bastille, SeasonalEvent.Victory, SeasonalEvent.Armistice -> {
            // Tricolour light sweep crossing the top.
            val x = ((time * 0.12f) % 1.6f - 0.3f) * w
            val stripe = w * 0.08f
            tricolore.forEachIndexed { i, color ->
                drawRect(color.copy(alpha = 0.10f), Offset(x + i * stripe, 0f), Size(stripe, h * 0.5f))
            }
        }
        else -> Unit
    }
}

/** Celebration burst from the logo when a theme appears. */
private fun DrawScope.drawBurst(seeds: List<ParticleSeed>, time: Float, event: SeasonalEvent) {
    val origin = Offset(96.dp.toPx(), 70.dp.toPx())
    val t = time / 2.4f
    val unit = 6.dp.toPx()
    val colors = when (event.particle) {
        Particle.Tricolore, Particle.Fireworks -> tricolore
        Particle.Hearts -> listOf(event.accent, Color(0xFFFF9CC0), Color.White)
        Particle.Bats -> listOf(event.accent, Color(0xFF9C5BD6), Color(0xFF3A1452))
        else -> listOf(event.accent, Color.White, event.deep)
    }
    seeds.forEach { seed ->
        val distance = unit * 40f * seed.power * (1f - (1f - t) * (1f - t))
        val gravity = unit * 18f * t * t
        val p = Offset(origin.x + cos(seed.angle) * distance, origin.y + sin(seed.angle) * distance * 0.7f + gravity)
        rotate(seed.spin * t, p) {
            drawRect(colors[seed.palette].copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(p.x - unit * 0.4f, p.y - unit * 0.2f), Size(unit * 0.8f, unit * 0.4f))
        }
    }
}

/** Profile sheet: master switch, particles, one switch per event, and a live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalThemesSheet(onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Thèmes saisonniers", style = MaterialTheme.typography.headlineSmall)
                    Text("Couleurs et animations aux fêtes françaises.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
            item { SwitchRow("Activer les thèmes", "Suit le calendrier automatiquement", SeasonalThemes.enabled, SeasonalThemes::updateEnabled) }
            item { SwitchRow("Animations décoratives", "Neige, confettis, chauves-souris…", SeasonalThemes.particles, SeasonalThemes::updateParticles) }
            item {
                Text(
                    "Fêtes",
                    style = MaterialTheme.typography.labelLarge,
                    color = Nflix.Muted,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(SeasonalEvent.entries, key = { it.name }) { event ->
                val previewing = SeasonalThemes.preview == event
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(if (previewing) event.accent.copy(alpha = 0.16f) else Nflix.Surface).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(event.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { Text(event.emoji) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(event.label, style = MaterialTheme.typography.titleSmall)
                            if (event.isActive(today)) {
                                Spacer(Modifier.width(6.dp))
                                Text("EN COURS", style = MaterialTheme.typography.labelSmall, color = event.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(event.period, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    }
                    Text(
                        if (previewing) "Arrêter" else "Aperçu",
                        color = event.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { SeasonalThemes.preview(if (previewing) null else event) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Switch(
                        checked = event.name !in SeasonalThemes.disabled,
                        onCheckedChange = { SeasonalThemes.setEventEnabled(event, it) },
                        enabled = SeasonalThemes.enabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = event.accent),
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.35f)
        cubicTo(center.x - size, center.y - size * 0.3f, center.x - size * 0.4f, center.y - size, center.x, center.y - size * 0.4f)
        cubicTo(center.x + size * 0.4f, center.y - size, center.x + size, center.y - size * 0.3f, center.x, center.y + size * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBat(center: Offset, size: Float, flap: Float, color: Color) {
    val wing = size * (0.35f + 0.25f * flap)
    val path = Path().apply {
        moveTo(center.x, center.y)
        quadraticTo(center.x - size * 0.5f, center.y - wing, center.x - size, center.y - wing * 0.2f)
        quadraticTo(center.x - size * 0.55f, center.y + size * 0.05f, center.x, center.y + size * 0.2f)
        quadraticTo(center.x + size * 0.55f, center.y + size * 0.05f, center.x + size, center.y - wing * 0.2f)
        quadraticTo(center.x + size * 0.5f, center.y - wing, center.x, center.y)
        close()
    }
    drawPath(path, color)
    // Glowing eyes.
    drawCircle(Color(0xFFFFD54A).copy(alpha = color.alpha), size * 0.05f, Offset(center.x - size * 0.06f, center.y + size * 0.05f))
    drawCircle(Color(0xFFFFD54A).copy(alpha = color.alpha), size * 0.05f, Offset(center.x + size * 0.06f, center.y + size * 0.05f))
}
