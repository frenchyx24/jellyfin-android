package org.jellyfin.mobile.nflix.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// ---- Calendar ---------------------------------------------------------------------------------

/** The four seasons (northern hemisphere, astronomical dates rounded to the usual day). */
enum class Season(val label: String) {
    Winter("Hiver"),
    Spring("Printemps"),
    Summer("Été"),
    Autumn("Automne");

    companion object {
        fun on(date: LocalDate): Season {
            val day = MonthDay.from(date)
            return when {
                day.isBefore(MonthDay.of(3, 20)) -> Winter
                day.isBefore(MonthDay.of(6, 21)) -> Spring
                day.isBefore(MonthDay.of(9, 23)) -> Summer
                day.isBefore(MonthDay.of(12, 21)) -> Autumn
                else -> Winter
            }
        }
    }
}

/** Events that tint the current season for a few days. */
enum class SeasonEvent {
    Halloween,
    Christmas,
    NewYear;

    fun isActive(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        fun between(from: MonthDay, to: MonthDay) = !day.isBefore(from) && !day.isAfter(to)
        return when (this) {
            Halloween -> between(MonthDay.of(10, 24), MonthDay.of(10, 31))
            Christmas -> between(MonthDay.of(12, 1), MonthDay.of(12, 26))
            NewYear -> between(MonthDay.of(12, 30), MonthDay.of(12, 31)) || between(MonthDay.of(1, 1), MonthDay.of(1, 2))
        }
    }

    companion object {
        fun on(date: LocalDate): SeasonEvent? = entries.firstOrNull { it.isActive(date) }
    }
}

/** Ambient motion drawn by [SeasonAmbience]. */
enum class Ambient { None, Frost, Pollen, GoldenHour, Leaves, Embers, Snowfall, Champagne }

// ---- Theme ------------------------------------------------------------------------------------

/**
 * Everything a season changes in the interface. [Nflix], the Material colour scheme, the logo,
 * bars, cards, hero and skeletons all read [NflixSeason.theme].
 */
@Immutable
data class NflixSeasonTheme(
    val key: String,
    val label: String,
    /** Short editorial line shown under the logo and in Settings. */
    val tagline: String,
    val period: String,
    val season: Season?,
    val event: SeasonEvent?,
    val accent: Color,
    val accentDeep: Color,
    /** Secondary accent: highlights, swatches, ambient light. */
    val accentSoft: Color,
    val night: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val muted: Color,
    /** Background light: a main glow at the top and a counter-glow at the bottom. */
    val glow: Color,
    val glowAlt: Color,
    val cardRadius: Dp,
    val chipRadius: Dp,
    val barSurface: Color,
    /** Colour wash laid over hero artwork. */
    val heroGrade: Color,
    val logoSheen: List<Color>,
    val skeletonBase: Color,
    val skeletonHighlight: Color,
    val ambient: Ambient,
) {
    val isSeasonal: Boolean get() = key != Brand.key
    val cardShape: RoundedCornerShape = RoundedCornerShape(cardRadius)
    val chipShape: RoundedCornerShape = RoundedCornerShape(chipRadius)

    /** Three colours for swatches and particles. */
    val palette: List<Color> get() = listOf(accent, accentSoft, glowAlt)

    companion object {
        val Brand = NflixSeasonTheme(
            key = "brand", label = "Nflix", tagline = "", period = "",
            season = null, event = null,
            accent = Color(0xFFE50914), accentDeep = Color(0xFF8C0A1C), accentSoft = Color(0xFFFF6B7A),
            night = Color(0xFF000000), surface = Color(0xFF121212), surfaceHigh = Color(0xFF202020),
            outline = Color(0x29FFFFFF), muted = Color(0xFFA6A6B4),
            glow = Color.Transparent, glowAlt = Color.Transparent,
            cardRadius = 8.dp, chipRadius = 8.dp, barSurface = Color(0xFF121212), heroGrade = Color.Transparent,
            logoSheen = listOf(Color.White, Color.White),
            skeletonBase = Color(0xFF202020), skeletonHighlight = Color(0xFF26262F),
            ambient = Ambient.None,
        )

        val Winter = NflixSeasonTheme(
            key = "Winter", label = "Hiver", tagline = "Lumière froide", period = "21 déc. – 19 mars",
            season = Season.Winter, event = null,
            accent = Color(0xFF5B9BE8), accentDeep = Color(0xFF16304F), accentSoft = Color(0xFFBFDBFF),
            night = Color(0xFF04070B), surface = Color(0xFF0D131B), surfaceHigh = Color(0xFF172231),
            outline = Color(0x2EBFDBFF), muted = Color(0xFF97A6B8),
            glow = Color(0xFF2B5C8F), glowAlt = Color(0xFF7FB2E5),
            cardRadius = 12.dp, chipRadius = 10.dp, barSurface = Color(0xFF0A1018), heroGrade = Color(0xFF1B3A5C),
            logoSheen = listOf(Color.White, Color(0xFFCFE6FF)),
            skeletonBase = Color(0xFF141C27), skeletonHighlight = Color(0xFF22303F),
            ambient = Ambient.Frost,
        )

        val Spring = NflixSeasonTheme(
            key = "Spring", label = "Printemps", tagline = "Floraison", period = "20 mars – 20 juin",
            season = Season.Spring, event = null,
            accent = Color(0xFFE5739A), accentDeep = Color(0xFF5A1F38), accentSoft = Color(0xFFA8D5A2),
            night = Color(0xFF070908), surface = Color(0xFF111512), surfaceHigh = Color(0xFF1C221D),
            outline = Color(0x2EE7F5E3), muted = Color(0xFFA3AFA4),
            glow = Color(0xFF6E3A55), glowAlt = Color(0xFF4E7A52),
            cardRadius = 16.dp, chipRadius = 14.dp, barSurface = Color(0xFF0C100D), heroGrade = Color(0xFF4A2A3A),
            logoSheen = listOf(Color.White, Color(0xFFFFD6E4)),
            skeletonBase = Color(0xFF161B17), skeletonHighlight = Color(0xFF232B24),
            ambient = Ambient.Pollen,
        )

        val Summer = NflixSeasonTheme(
            key = "Summer", label = "Été", tagline = "Heure dorée", period = "21 juin – 22 sept.",
            season = Season.Summer, event = null,
            accent = Color(0xFFE8912E), accentDeep = Color(0xFF6B2E0E), accentSoft = Color(0xFFFFD08A),
            night = Color(0xFF0A0705), surface = Color(0xFF16110C), surfaceHigh = Color(0xFF221A12),
            outline = Color(0x2EFFE2B8), muted = Color(0xFFB3A595),
            glow = Color(0xFF8A4A16), glowAlt = Color(0xFFC2702A),
            cardRadius = 10.dp, chipRadius = 8.dp, barSurface = Color(0xFF120D08), heroGrade = Color(0xFF6B3A12),
            logoSheen = listOf(Color(0xFFFFF3D6), Color(0xFFFFC46B)),
            skeletonBase = Color(0xFF1B150F), skeletonHighlight = Color(0xFF2A2117),
            ambient = Ambient.GoldenHour,
        )

        val Autumn = NflixSeasonTheme(
            key = "Autumn", label = "Automne", tagline = "Cuivre et brume", period = "23 sept. – 20 déc.",
            season = Season.Autumn, event = null,
            accent = Color(0xFFC8622E), accentDeep = Color(0xFF4A1C0E), accentSoft = Color(0xFFD9A55B),
            night = Color(0xFF090605), surface = Color(0xFF15100D), surfaceHigh = Color(0xFF211813),
            outline = Color(0x2EF2D2B8), muted = Color(0xFFAE9F94),
            glow = Color(0xFF6A2E14), glowAlt = Color(0xFF8A5A1E),
            cardRadius = 6.dp, chipRadius = 6.dp, barSurface = Color(0xFF100B09), heroGrade = Color(0xFF4A2412),
            logoSheen = listOf(Color(0xFFFFE9D6), Color(0xFFE59A5C)),
            skeletonBase = Color(0xFF1A130F), skeletonHighlight = Color(0xFF2A1F18),
            ambient = Ambient.Leaves,
        )

        // Events are modifiers of their season: same structure, recoloured light and motion.

        val Halloween = Autumn.copy(
            key = "Halloween", label = "Halloween", tagline = "Nuits d'octobre", period = "24 – 31 oct.",
            event = SeasonEvent.Halloween,
            accent = Color(0xFFF26B1D), accentDeep = Color(0xFF2E0F45), accentSoft = Color(0xFF9C6BD6),
            night = Color(0xFF050308), surface = Color(0xFF110D16), surfaceHigh = Color(0xFF1C1524),
            outline = Color(0x2ED9C2F2), muted = Color(0xFFA79DB3),
            glow = Color(0xFF3D1660), glowAlt = Color(0xFFB34A12),
            cardRadius = 4.dp, chipRadius = 4.dp, barSurface = Color(0xFF0B0810), heroGrade = Color(0xFF2A0E3D),
            logoSheen = listOf(Color(0xFFFFD9B8), Color(0xFFFF7A2E)),
            skeletonBase = Color(0xFF17121D), skeletonHighlight = Color(0xFF251C2E),
            ambient = Ambient.Embers,
        )

        val Christmas = Winter.copy(
            key = "Christmas", label = "Noël", tagline = "Velours et or", period = "1er – 26 déc.",
            event = SeasonEvent.Christmas,
            accent = Color(0xFFC4213A), accentDeep = Color(0xFF4A0E18), accentSoft = Color(0xFFE3C27A),
            night = Color(0xFF070505), surface = Color(0xFF140F0E), surfaceHigh = Color(0xFF211816),
            outline = Color(0x2EF2DDB0), muted = Color(0xFFB0A399),
            glow = Color(0xFF5A1420), glowAlt = Color(0xFF8C6A2A),
            cardRadius = 14.dp, chipRadius = 12.dp, barSurface = Color(0xFF0F0B0A), heroGrade = Color(0xFF3A0E16),
            logoSheen = listOf(Color(0xFFFFF4DC), Color(0xFFE3C27A)),
            skeletonBase = Color(0xFF1A1312), skeletonHighlight = Color(0xFF2A1F1C),
            ambient = Ambient.Snowfall,
        )

        val NewYear = Winter.copy(
            key = "NewYear", label = "Nouvel An", tagline = "Minuit, champagne", period = "30 déc. – 2 janv.",
            event = SeasonEvent.NewYear,
            accent = Color(0xFFC9A44C), accentDeep = Color(0xFF3E3216), accentSoft = Color(0xFFF2E3B3),
            night = Color(0xFF050506), surface = Color(0xFF111113), surfaceHigh = Color(0xFF1C1C20),
            outline = Color(0x2EF2E3B3), muted = Color(0xFFA8A6A0),
            glow = Color(0xFF5C4A1E), glowAlt = Color(0xFF2E3A5C),
            cardRadius = 12.dp, chipRadius = 10.dp, barSurface = Color(0xFF0B0B0D), heroGrade = Color(0xFF2A2412),
            logoSheen = listOf(Color.White, Color(0xFFF2E3B3)),
            skeletonBase = Color(0xFF151517), skeletonHighlight = Color(0xFF24232A),
            ambient = Ambient.Champagne,
        )

        /** Everything Settings can force. */
        val selectable: List<NflixSeasonTheme> = listOf(Winter, Spring, Summer, Autumn, Halloween, Christmas, NewYear)

        fun byKey(key: String): NflixSeasonTheme? = selectable.firstOrNull { it.key == key }

        fun forSeason(season: Season) = when (season) {
            Season.Winter -> Winter
            Season.Spring -> Spring
            Season.Summer -> Summer
            Season.Autumn -> Autumn
        }

        fun forEvent(event: SeasonEvent) = when (event) {
            SeasonEvent.Halloween -> Halloween
            SeasonEvent.Christmas -> Christmas
            SeasonEvent.NewYear -> NewYear
        }

        /** [mode] is [NflixSeason.AUTO], [NflixSeason.OFF] or a theme key to force. */
        fun resolve(mode: String, date: LocalDate, events: Boolean): NflixSeasonTheme = when (mode) {
            NflixSeason.OFF -> Brand
            NflixSeason.AUTO -> SeasonEvent.on(date)?.takeIf { events }?.let(::forEvent) ?: forSeason(Season.on(date))
            else -> byKey(mode) ?: resolve(NflixSeason.AUTO, date, events)
        }
    }
}

/** User choices and the theme the whole interface reads. */
object NflixSeason {
    const val AUTO = "auto"
    const val OFF = "off"
    private const val PREFS = "nflix_appearance"
    private var context: Context? = null

    /** [AUTO], [OFF] or the key of a forced theme. */
    var mode by mutableStateOf(AUTO)
        private set

    /** Halloween, Christmas and New Year override the season in automatic mode. */
    var events by mutableStateOf(true)
        private set

    /** Ambient motion layer (always off under reduce-motion or battery saver). */
    var ambient by mutableStateOf(true)
        private set

    private var today by mutableStateOf(LocalDate.now())

    val theme: NflixSeasonTheme by derivedStateOf { NflixSeasonTheme.resolve(mode, today, events) }

    /** What automatic mode would show today. */
    val automatic: NflixSeasonTheme get() = NflixSeasonTheme.resolve(AUTO, today, events)

    val summary: String
        get() = when (mode) {
            OFF -> "Désactivés"
            AUTO -> "Automatique · ${theme.label}"
            else -> "Forcé · ${theme.label}"
        }

    fun init(context: Context) {
        refreshDate()
        if (this.context != null) return
        this.context = context.applicationContext
        val prefs = prefs() ?: return
        // Older builds stored a single on/off switch.
        val legacyOff = !prefs.getBoolean("seasonal", true)
        mode = prefs.getString("season_mode", null) ?: if (legacyOff) OFF else AUTO
        events = prefs.getBoolean("season_events", true)
        ambient = prefs.getBoolean("season_ambient", true)
    }

    fun refreshDate() {
        LocalDate.now().takeIf { it != today }?.let { today = it }
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun updateMode(value: String) { mode = value; prefs()?.edit { putString("season_mode", value) } }
    fun updateEvents(value: Boolean) { events = value; prefs()?.edit { putBoolean("season_events", value) } }
    fun updateAmbient(value: Boolean) { ambient = value; prefs()?.edit { putBoolean("season_ambient", value) } }
}

// ---- Motion policy ----------------------------------------------------------------------------

/** False when system animations are off or battery saver is on. */
@Composable
fun rememberAmbientMotionAllowed(): Boolean {
    val context = LocalContext.current
    val animations = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saver by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { saver = power?.isPowerSaveMode == true }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return animations && !saver
}

// ---- Backdrop and hero ------------------------------------------------------------------------

/** Root background: the season's night colour with its two soft lights. */
fun Modifier.seasonBackdrop(): Modifier = drawWithCache {
    val theme = NflixSeason.theme
    val top = Brush.radialGradient(
        listOf(theme.glow.copy(alpha = 0.34f), theme.glow.copy(alpha = 0.08f), Color.Transparent),
        center = Offset(size.width * 0.18f, 0f), radius = size.width * 1.15f,
    )
    val bottom = Brush.radialGradient(
        listOf(theme.glowAlt.copy(alpha = 0.12f), Color.Transparent),
        center = Offset(size.width, size.height), radius = size.width * 1.1f,
    )
    onDrawBehind {
        drawRect(theme.night)
        if (theme.isSeasonal) {
            drawRect(top)
            drawRect(bottom)
        }
    }
}

/** Colour grade laid over hero artwork: a cinematic wash in the season's light. Static. */
@Composable
fun SeasonHeroGrade(modifier: Modifier = Modifier, strength: Float = 1f) {
    val theme = NflixSeason.theme
    if (!theme.isSeasonal) return
    Canvas(modifier) {
        val h = size.height.coerceAtMost(520.dp.toPx())
        drawRect(
            Brush.linearGradient(
                listOf(theme.heroGrade.copy(alpha = 0.42f * strength), theme.heroGrade.copy(alpha = 0.12f * strength), Color.Transparent),
                start = Offset(0f, 0f), end = Offset(size.width * 0.9f, h),
            ),
            size = Size(size.width, h),
        )
        drawRect(
            Brush.radialGradient(
                listOf(theme.glowAlt.copy(alpha = 0.16f * strength), Color.Transparent),
                center = Offset(size.width * 0.92f, h * 0.08f), radius = size.width * 0.7f,
            ),
            size = Size(size.width, h),
        )
    }
}

/** Kept for the home hero call sites: now the static colour grade, motion lives in [SeasonAmbience]. */
@Composable
fun SeasonalParticles(modifier: Modifier = Modifier, intensity: Float = 1f) = SeasonHeroGrade(modifier, intensity.coerceIn(0f, 1f))

/** Editorial line under the home logo: hairline, season name, tagline. */
@Composable
fun SeasonalGreeting(visible: Boolean) {
    val theme = NflixSeason.theme
    AnimatedVisibility(visible = visible && theme.isSeasonal, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(22.dp).height(1.dp).drawWithCache {
                    val brush = Brush.horizontalGradient(listOf(theme.accent, theme.accentSoft.copy(alpha = 0.2f)))
                    onDrawBehind { drawRect(brush) }
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(theme.label.uppercase(), color = theme.accentSoft, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(theme.tagline, color = Nflix.Text.copy(alpha = 0.72f), fontSize = 13.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
        }
    }
}

// ---- Ambient layer ----------------------------------------------------------------------------

private class SeasonMote(index: Int, salt: Int) {
    private val r = java.util.Random(index * 7919L + salt * 104729L)
    val x = r.nextFloat()
    val phase = r.nextFloat()
    val depth = DEPTHS[index % 3]
    val speed = 0.6f + r.nextFloat() * 0.8f
    val size = 0.6f + r.nextFloat() * 0.8f
    val sway = r.nextFloat() * 2f * PI.toFloat()
    val spin = (r.nextFloat() - 0.5f) * 120f
    val power = 0.5f + r.nextFloat()
    val tone = r.nextInt(3)

    private companion object { val DEPTHS = floatArrayOf(0.5f, 0.85f, 1.3f) }
}

private fun Ambient.count() = when (this) {
    Ambient.None -> 0
    Ambient.Frost -> 16
    Ambient.Pollen -> 14
    Ambient.GoldenHour -> 10
    Ambient.Leaves -> 8
    Ambient.Embers -> 16
    Ambient.Snowfall -> 22
    Ambient.Champagne -> 18
}

private fun wrap(v: Float) = v - floor(v)

/** Unit leaf (height 2, centred), scaled per leaf. */
private val LeafPath = Path().apply {
    moveTo(0f, -1f)
    cubicTo(0.62f, -0.55f, 0.5f, 0.45f, 0f, 1f)
    cubicTo(-0.5f, 0.45f, -0.62f, -0.55f, 0f, -1f)
    close()
}

/**
 * Subtle animated light for the season: frost, pollen, golden-hour shimmer, leaves with depth,
 * embers, soft snow or champagne. Low count, 30 fps, draw-phase only. Frozen when motion is not
 * allowed (reduce-motion, battery saver, user choice or [animated] false).
 */
@Composable
fun SeasonAmbience(
    modifier: Modifier = Modifier,
    theme: NflixSeasonTheme = NflixSeason.theme,
    strength: Float = 1f,
    animated: Boolean = true,
    topGlow: Boolean = true,
) {
    if (!theme.isSeasonal) return
    val motion = animated && NflixSeason.ambient && rememberAmbientMotionAllowed()
    val count = (theme.ambient.count() * strength).toInt().coerceAtLeast(if (theme.ambient == Ambient.None) 0 else 3)
    val motes = remember(theme.key, count) { List(count) { SeasonMote(it, theme.key.hashCode()) } }
    val clock = remember { mutableFloatStateOf(14f) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var last = 0L
        val start = withFrameMillis { it } - (clock.floatValue * 1000).toLong()
        while (true) {
            withFrameMillis { now ->
                // ~30 fps is plenty for slow drift and halves the redraw cost.
                if (now - last >= 33L) {
                    last = now
                    clock.floatValue = (now - start) / 1000f
                }
            }
        }
    }
    Canvas(modifier) {
        val t = clock.floatValue
        if (topGlow) drawTopGlow(theme, t)
        when (theme.ambient) {
            Ambient.None -> Unit
            Ambient.Frost -> drawFrost(theme, motes, t)
            Ambient.Pollen -> drawPollen(theme, motes, t)
            Ambient.GoldenHour -> drawGoldenHour(theme, motes, t)
            Ambient.Leaves -> drawLeaves(theme, motes, t)
            Ambient.Embers -> drawEmbers(theme, motes, t)
            Ambient.Snowfall -> drawSnowfall(theme, motes, t)
            Ambient.Champagne -> drawChampagne(theme, motes, t)
        }
    }
}

private fun DrawScope.drawTopGlow(theme: NflixSeasonTheme, t: Float) {
    val h = 180.dp.toPx().coerceAtMost(size.height)
    val breath = 0.85f + 0.15f * sin(t * 0.35f)
    drawRect(
        Brush.radialGradient(
            listOf(theme.glow.copy(alpha = 0.22f * breath), Color.Transparent),
            center = Offset(size.width * (0.25f + 0.05f * sin(t * 0.12f)), 0f), radius = size.width * 0.75f,
        ),
        size = Size(size.width, h),
    )
}

private fun DrawScope.drawFrost(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    val breath = 0.85f + 0.15f * sin(t * 0.4f)
    // Frosted corners, as if the screen were a cold window.
    drawCircle(Brush.radialGradient(listOf(theme.accentSoft.copy(alpha = 0.10f * breath), Color.Transparent), Offset.Zero, w * 0.55f), w * 0.55f, Offset.Zero)
    drawCircle(Brush.radialGradient(listOf(theme.accentSoft.copy(alpha = 0.06f * breath), Color.Transparent), Offset(w, 0f), w * 0.45f), w * 0.45f, Offset(w, 0f))
    motes.forEach { m ->
        val y = (wrap(m.phase + t * m.speed * 0.012f) * 1.1f - 0.05f) * h
        val x = m.x * w + sin(t * 0.3f + m.sway) * 14f * d * m.depth
        val twinkle = 0.6f + 0.4f * sin(t * 2f * m.power + m.sway)
        val r = 0.9f * d * m.size * m.depth
        drawCircle(theme.accentSoft.copy(alpha = 0.08f * twinkle), r * 4f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.45f * m.depth.coerceAtMost(1f) * twinkle), r, Offset(x, y))
    }
}

private fun DrawScope.drawPollen(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    val colors = listOf(theme.accentSoft, Color(0xFFFFE6A8), Color.White)
    motes.forEach { m ->
        val y = (1.05f - wrap(m.phase + t * m.speed * 0.010f) * 1.1f) * h
        val x = m.x * w + sin(t * 0.45f + m.sway) * 22f * d * m.depth
        val r = (3f + 5f * m.size) * d * m.depth
        val a = (0.10f + 0.12f * m.depth) * (0.7f + 0.3f * sin(t * 1.3f + m.sway))
        drawCircle(Brush.radialGradient(listOf(colors[m.tone].copy(alpha = a), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
    }
}

private fun DrawScope.drawGoldenHour(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    val breath = 0.85f + 0.15f * sin(t * 0.3f)
    // Low sun in the corner.
    drawCircle(
        Brush.radialGradient(listOf(theme.glowAlt.copy(alpha = 0.20f * breath), theme.accent.copy(alpha = 0.05f), Color.Transparent), Offset(w * 0.88f, 0f), w * 0.9f),
        w * 0.9f, Offset(w * 0.88f, 0f),
    )
    // Heat haze: faint bands that breathe up and down.
    val band = 12f * d
    for (i in 0 until 3) {
        val y = h * (0.16f + i * 0.11f) + sin(t * 0.6f + i * 1.7f) * 8f * d
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, theme.accentSoft.copy(alpha = 0.035f), Color.Transparent), y - band, y + band),
            topLeft = Offset(0f, y - band), size = Size(w, band * 2f),
        )
    }
    // Dust caught in the light.
    motes.forEach { m ->
        val x = wrap(m.x + t * m.speed * 0.006f) * w
        val y = m.phase * h * 0.55f + sin(t * 0.5f + m.sway) * 10f * d
        val glint = 0.5f + 0.5f * sin(t * 1.5f * m.power + m.sway)
        drawCircle(theme.accentSoft.copy(alpha = 0.30f * glint * m.depth.coerceAtMost(1f)), 1.1f * d * m.size, Offset(x, y))
    }
}

private fun DrawScope.drawLeaves(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    val colors = listOf(theme.accent, theme.accentSoft, Color(0xFF7A2A1A))
    motes.forEach { m ->
        val y = (wrap(m.phase + t * m.speed * 0.008f * m.depth) * 1.2f - 0.1f) * h
        val x = m.x * w + sin(t * 0.5f + m.sway) * 40f * d * m.depth
        val len = 8f * d * m.size * m.depth
        val flip = cos(t * 0.8f * m.power + m.sway)
        val alpha = 0.14f + 0.26f * ((m.depth - 0.5f) / 0.8f)
        val color = colors[m.tone]
        withTransform({
            translate(x, y)
            rotate(m.sway * 57f + t * m.spin * 0.25f, Offset.Zero)
            scale(len * (0.25f + 0.75f * kotlin.math.abs(flip)), len, Offset.Zero)
        }) {
            drawPath(LeafPath, color.copy(alpha = alpha))
            drawLine(Color.Black.copy(alpha = alpha * 0.5f), Offset(0f, -0.8f), Offset(0f, 0.9f), strokeWidth = 0.06f)
        }
    }
}

private fun DrawScope.drawEmbers(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    // Low fog in the night colours.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, theme.accentSoft.copy(alpha = 0.07f)), h * 0.72f, h),
        topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.28f),
    )
    for (i in 0 until 2) {
        val cx = wrap(0.2f + i * 0.5f + t * 0.004f * (i + 1)) * w * 1.4f - w * 0.2f
        drawCircle(Brush.radialGradient(listOf(theme.glow.copy(alpha = 0.10f), Color.Transparent), Offset(cx, h * 0.95f), w * 0.5f), w * 0.5f, Offset(cx, h * 0.95f))
    }
    motes.forEach { m ->
        val rise = wrap(m.phase + t * m.speed * 0.014f)
        val y = (1.02f - rise * 1.05f) * h
        val x = m.x * w + sin(t * 1.1f + m.sway) * 12f * d + rise * 20f * d
        val flicker = 0.55f + 0.45f * sin(t * 5f * m.power + m.sway)
        val fade = (1f - rise).coerceIn(0f, 1f)
        val r = 1.1f * d * m.size
        drawCircle(Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.22f * flicker * fade), Color.Transparent), Offset(x, y), r * 6f), r * 6f, Offset(x, y))
        drawCircle(Color(0xFFFFC28A).copy(alpha = 0.7f * flicker * fade), r, Offset(x, y))
    }
}

private fun DrawScope.drawSnowfall(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    motes.forEach { m ->
        val y = (wrap(m.phase + t * m.speed * 0.011f * m.depth) * 1.1f - 0.05f) * h
        val x = m.x * w + sin(t * 0.6f + m.sway) * 16f * d * m.depth
        val tint = if (m.tone == 0) theme.accentSoft else Color.White
        when {
            // Out-of-focus flakes close to the lens.
            m.depth > 1f -> {
                val r = 5f * d * m.size
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
            }
            m.depth > 0.6f -> drawCircle(Color.White.copy(alpha = 0.38f), 1.6f * d * m.size, Offset(x, y))
            else -> drawCircle(Color.White.copy(alpha = 0.22f), 1f * d * m.size, Offset(x, y))
        }
    }
}

private fun DrawScope.drawChampagne(theme: NflixSeasonTheme, motes: List<SeasonMote>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    motes.forEachIndexed { i, m ->
        if (i % 4 == 0) {
            // Glints: brief four-point flashes.
            val flash = sin(t * 0.9f * m.power + m.sway * 6f).coerceAtLeast(0f).pow(10)
            if (flash > 0.02f) {
                val c = Offset(m.x * w, m.phase * h * 0.6f)
                val l = 7f * d * m.size * flash
                val color = theme.accentSoft.copy(alpha = 0.75f * flash)
                drawLine(color, Offset(c.x - l, c.y), Offset(c.x + l, c.y), strokeWidth = 1f * d, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y - l), Offset(c.x, c.y + l), strokeWidth = 1f * d, cap = StrokeCap.Round)
            }
        } else {
            // Bubbles rising.
            val y = (1.05f - wrap(m.phase + t * m.speed * 0.02f) * 1.1f) * h
            val x = m.x * w + sin(t * 2.2f + m.sway) * 3f * d
            val r = (1.4f + 1.8f * m.size) * d * m.depth.coerceAtMost(1f)
            drawCircle(theme.accentSoft.copy(alpha = 0.28f), r, Offset(x, y), style = Stroke(0.8f * d))
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
        }
    }
}
