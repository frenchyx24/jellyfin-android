package org.jellyfin.mobile.nflix.ui

import android.app.ActivityManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---- Theme ------------------------------------------------------------------------------------

/**
 * Everything a season or holiday changes in the interface. [Nflix], the Material colour scheme,
 * the logo, bars, cards, hero and skeletons read [NflixSeason.theme]; the drawn decorations
 * (header backdrop, corners, play button, silhouette, particles) read [decor].
 */
@Immutable
data class NflixSeasonTheme(
    val key: String,
    val label: String,
    /** Short editorial line used in Settings. */
    val tagline: String,
    /** Static period for seasons; holidays compute theirs from [FrenchCalendar]. */
    val period: String,
    val season: Season?,
    val event: SeasonEvent?,
    val accent: Color,
    val accentDeep: Color,
    /** Secondary accent: highlights, swatches, particles. */
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
    val decor: SeasonDecor = SeasonDecor.None,
) {
    val isSeasonal: Boolean get() = key != Brand.key
    val cardShape: RoundedCornerShape = RoundedCornerShape(cardRadius)
    val chipShape: RoundedCornerShape = RoundedCornerShape(chipRadius)

    /** Three colours for swatches. */
    val palette: List<Color> get() = listOf(accent, accentSoft, glowAlt)

    /** "24 – 31 oct." for [year], or the static season period. */
    fun periodIn(year: Int): String {
        val event = event ?: return period
        val window = FrenchCalendar.window(event, year)
        val short = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
        return if (window.start == window.end) short.format(window.start) else "${short.format(window.start)} – ${short.format(window.end)}"
    }

    /** Recoloured copy for a holiday on top of [this] season. */
    internal fun holiday(
        key: String, label: String, tagline: String, event: SeasonEvent,
        accent: Color, accentDeep: Color, accentSoft: Color, glow: Color, glowAlt: Color, heroGrade: Color,
        night: Color, decor: SeasonDecor, radius: Dp = cardRadius,
    ) = copy(
        key = key, label = label, tagline = tagline, period = "", event = event,
        accent = accent, accentDeep = accentDeep, accentSoft = accentSoft, glow = glow, glowAlt = glowAlt, heroGrade = heroGrade,
        night = night, surface = mix(night, accentDeep, 0.10f), surfaceHigh = mix(night, accentDeep, 0.22f),
        outline = accentSoft.copy(alpha = 0.18f), barSurface = mix(night, accentDeep, 0.06f),
        logoSheen = listOf(Color.White, accentSoft), skeletonBase = mix(night, accentDeep, 0.16f), skeletonHighlight = mix(night, accentDeep, 0.28f),
        cardRadius = radius, chipRadius = radius, decor = decor,
    )

    companion object {
        private fun mix(a: Color, b: Color, f: Float) = Color(
            a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
        )

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
        )

        val Winter = NflixSeasonTheme(
            key = "Winter", label = "Hiver", tagline = "Givre et nuits claires", period = "21 déc. – 19 mars",
            season = Season.Winter, event = null,
            accent = Color(0xFF5B9BE8), accentDeep = Color(0xFF16304F), accentSoft = Color(0xFFBFDBFF),
            night = Color(0xFF04070B), surface = Color(0xFF0D131B), surfaceHigh = Color(0xFF172231),
            outline = Color(0x2EBFDBFF), muted = Color(0xFF97A6B8),
            glow = Color(0xFF2B5C8F), glowAlt = Color(0xFF7FB2E5),
            cardRadius = 12.dp, chipRadius = 10.dp, barSurface = Color(0xFF0A1018), heroGrade = Color(0xFF1B3A5C),
            logoSheen = listOf(Color.White, Color(0xFFCFE6FF)),
            skeletonBase = Color(0xFF141C27), skeletonHighlight = Color(0xFF22303F),
            decor = Decors.Winter,
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
            decor = Decors.Spring,
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
            decor = Decors.Summer,
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
            decor = Decors.Autumn,
        )

        // Holidays: recoloured light, their own decorations and motion.

        val NewYear = Winter.holiday(
            "NewYear", "Nouvel An", "Minuit, feux d'artifice", SeasonEvent.NewYear,
            Color(0xFFD4AF37), Color(0xFF3E3216), Color(0xFFF2E3B3), Color(0xFF5C4A1E), Color(0xFF2E3A5C), Color(0xFF2A2412),
            Color(0xFF050506), Decors.NewYear,
        )
        val Epiphany = Winter.holiday(
            "Epiphany", "Épiphanie", "Galette et couronne", SeasonEvent.Epiphany,
            Color(0xFFE0B84A), Color(0xFF4A3510), Color(0xFFFFE7A3), Color(0xFF5A4214), Color(0xFF3A2A5C), Color(0xFF3A2A0E),
            Color(0xFF07060A), Decors.Epiphany,
        )
        val Candlemas = Winter.holiday(
            "Candlemas", "Chandeleur", "Crêpes dorées", SeasonEvent.Candlemas,
            Color(0xFFE8A23A), Color(0xFF5A3410), Color(0xFFFFDCA0), Color(0xFF6A3E14), Color(0xFF2E3A5C), Color(0xFF4A2A10),
            Color(0xFF080605), Decors.Candlemas, radius = 16.dp,
        )
        val Valentine = Winter.holiday(
            "Valentine", "Saint-Valentin", "Cœurs en apesanteur", SeasonEvent.Valentine,
            Color(0xFFF0457A), Color(0xFF5A0E2A), Color(0xFFFFB3C9), Color(0xFF6A1838), Color(0xFF8A2A5A), Color(0xFF4A0E28),
            Color(0xFF09040A), Decors.Valentine, radius = 16.dp,
        )
        val Easter = Spring.holiday(
            "Easter", "Pâques", "Œufs et pétales", SeasonEvent.Easter,
            Color(0xFFE59ACB), Color(0xFF4A2A5A), Color(0xFFFFE08A), Color(0xFF5A3A6A), Color(0xFF4E7A52), Color(0xFF3A2A4A),
            Color(0xFF07080A), Decors.Easter,
        )
        val LabourDay = Spring.holiday(
            "LabourDay", "Fête du Travail", "Brins de muguet", SeasonEvent.LabourDay,
            Color(0xFF6CC48A), Color(0xFF173A24), Color(0xFFF4FFF2), Color(0xFF1E4A2C), Color(0xFF6A7A4E), Color(0xFF14301E),
            Color(0xFF040806), Decors.LabourDay,
        )
        val VictoryDay = Spring.holiday(
            "VictoryDay", "8 Mai 1945", "Victoire et mémoire", SeasonEvent.VictoryDay,
            Color(0xFF5B7FC4), Color(0xFF14254A), Color(0xFFE8ECF5), Color(0xFF1A2A4A), Color(0xFF4A1A22), Color(0xFF14203A),
            Color(0xFF05060A), Decors.VictoryDay, radius = 8.dp,
        )
        val MothersDay = Spring.holiday(
            "MothersDay", "Fête des Mères", "Bouquets et tendresse", SeasonEvent.MothersDay,
            Color(0xFFF07AA8), Color(0xFF5A1E3A), Color(0xFFFFD1E2), Color(0xFF6A2A4A), Color(0xFF4E7A52), Color(0xFF4A1E34),
            Color(0xFF09060A), Decors.MothersDay,
        )
        val FathersDay = Summer.holiday(
            "FathersDay", "Fête des Pères", "Cravates et câlins", SeasonEvent.FathersDay,
            Color(0xFF4FA3D9), Color(0xFF123048), Color(0xFFB8E0FF), Color(0xFF1A3A5A), Color(0xFF8A5A1E), Color(0xFF12304A),
            Color(0xFF04070A), Decors.FathersDay,
        )
        val MusicDay = Summer.holiday(
            "MusicDay", "Fête de la Musique", "Notes dans la nuit", SeasonEvent.MusicDay,
            Color(0xFFB36BFF), Color(0xFF2E1450), Color(0xFFFFD36B), Color(0xFF3A1A6A), Color(0xFF8A3A6A), Color(0xFF2A1048),
            Color(0xFF06040A), Decors.MusicDay, radius = 14.dp,
        )
        val BastilleDay = Summer.holiday(
            "BastilleDay", "14 Juillet", "Bleu, blanc, rouge", SeasonEvent.BastilleDay,
            Color(0xFFEF4135), Color(0xFF0E1E4A), Color(0xFF8FB0FF), Color(0xFF14285A), Color(0xFF5A1418), Color(0xFF0E1E4A),
            Color(0xFF04050A), Decors.BastilleDay,
        )
        val BackToSchool = Summer.holiday(
            "BackToSchool", "Rentrée", "Cahiers neufs", SeasonEvent.BackToSchool,
            Color(0xFFF2B33D), Color(0xFF3A2A10), Color(0xFF9FD3FF), Color(0xFF4A3410), Color(0xFF1E3A5A), Color(0xFF3A2A10),
            Color(0xFF07070A), Decors.BackToSchool,
        )
        val Halloween = Autumn.holiday(
            "Halloween", "Halloween", "Nuits d'octobre", SeasonEvent.Halloween,
            Color(0xFFF7781C), Color(0xFF2E0F45), Color(0xFFFFB25C), Color(0xFF3D1660), Color(0xFFB34A12), Color(0xFF2A0E3D),
            Color(0xFF060309), Decors.Halloween, radius = 10.dp,
        )
        val AllSaints = Autumn.holiday(
            "AllSaints", "Toussaint", "Feuilles et brume", SeasonEvent.AllSaints,
            Color(0xFFB8743A), Color(0xFF3A1E10), Color(0xFFE0C08A), Color(0xFF3A2414), Color(0xFF2E2A3A), Color(0xFF2A1A10),
            Color(0xFF070605), Decors.AllSaints,
        )
        val Armistice = Autumn.holiday(
            "Armistice", "11 Novembre", "Coquelicots, souvenir", SeasonEvent.Armistice,
            Color(0xFFD9393E), Color(0xFF14254A), Color(0xFFE8ECF5), Color(0xFF1A2A4A), Color(0xFF4A1A22), Color(0xFF14203A),
            Color(0xFF05060A), Decors.Armistice, radius = 8.dp,
        )
        val Christmas = Winter.holiday(
            "Christmas", "Noël", "Neige et guirlandes", SeasonEvent.Christmas,
            Color(0xFFE0313F), Color(0xFF4A0E18), Color(0xFFF2D58A), Color(0xFF5A1420), Color(0xFF1E5A3A), Color(0xFF3A0E16),
            Color(0xFF060506), Decors.Christmas, radius = 14.dp,
        )

        val seasons: List<NflixSeasonTheme> = listOf(Winter, Spring, Summer, Autumn)

        /** Holiday themes in calendar order. */
        val holidays: List<NflixSeasonTheme> = listOf(
            NewYear, Epiphany, Candlemas, Valentine, Easter, LabourDay, VictoryDay, MothersDay, FathersDay,
            MusicDay, BastilleDay, BackToSchool, Halloween, AllSaints, Armistice, Christmas,
        )

        /** Everything Settings can force. */
        val selectable: List<NflixSeasonTheme> = seasons + holidays

        fun byKey(key: String): NflixSeasonTheme? = selectable.firstOrNull { it.key == key }

        fun forSeason(season: Season) = when (season) {
            Season.Winter -> Winter
            Season.Spring -> Spring
            Season.Summer -> Summer
            Season.Autumn -> Autumn
        }

        fun forEvent(event: SeasonEvent): NflixSeasonTheme = holidays.first { it.event == event }

        /** [mode] is [NflixSeason.AUTO], [NflixSeason.OFF] or a theme key to force. Holiday beats season. */
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

    /** Holidays override the season in automatic mode. */
    var events by mutableStateOf(true)
        private set

    /** Animated decorations (frozen only under the system "remove animations" setting). */
    var ambient by mutableStateOf(true)
        private set

    /** Drawn decorations (backdrop, corners, silhouette); off keeps only the colours. */
    var decorations by mutableStateOf(true)
        private set

    var today by mutableStateOf(LocalDate.now())
        private set

    val theme: NflixSeasonTheme by derivedStateOf { NflixSeasonTheme.resolve(mode, today, events) }

    /** What automatic mode would show today. */
    val automatic: NflixSeasonTheme get() = NflixSeasonTheme.resolve(AUTO, today, events)

    val summary: String
        get() = when (mode) {
            OFF -> "Désactivé"
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
        // New key: older builds could leave a stale "off" from the previous ambience switch.
        ambient = prefs.getBoolean("season_animations", true)
        decorations = prefs.getBoolean("season_decor", true)
    }

    fun refreshDate() {
        LocalDate.now().takeIf { it != today }?.let { today = it }
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun updateMode(value: String) { mode = value; prefs()?.edit { putString("season_mode", value) } }
    fun updateEvents(value: Boolean) { events = value; prefs()?.edit { putBoolean("season_events", value) } }
    fun updateAmbient(value: Boolean) { ambient = value; prefs()?.edit { putBoolean("season_animations", value) } }
    fun updateDecorations(value: Boolean) { decorations = value; prefs()?.edit { putBoolean("season_decor", value) } }
}

// ---- Motion policy ----------------------------------------------------------------------------

/** False only when the user turned system animations off (accessibility "remove animations"). */
@Composable
fun rememberAmbientMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Battery saver: decorations keep moving, but lighter (fewer particles, 30 fps). */
@Composable
fun rememberPowerSaver(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saver by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { saver = power?.isPowerSaveMode == true }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saver
}

/** Low-RAM devices and battery saver get fewer particles at 30 fps. */
@Composable
fun rememberLowEndDevice(): Boolean {
    val context = LocalContext.current
    val lowRam = remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am != null && (am.isLowRamDevice || am.memoryClass < 96)
    }
    return lowRam || rememberPowerSaver()
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

/** Kept for the home hero call sites: the static colour grade, motion lives in [SeasonAmbience]. */
@Composable
fun SeasonalParticles(modifier: Modifier = Modifier, intensity: Float = 1f) = SeasonHeroGrade(modifier, intensity.coerceIn(0f, 1f))

/** Badge line under the home logo: themed glyph, letter-spaced kicker, hairline, label. */
@Composable
fun SeasonalGreeting(visible: Boolean) {
    val theme = NflixSeason.theme
    AnimatedVisibility(visible = visible && theme.isSeasonal, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        SeasonBadge(theme, Modifier.padding(start = 18.dp, top = 2.dp, bottom = 4.dp))
    }
}

@Composable
fun SeasonBadge(theme: NflixSeasonTheme, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SeasonGlyphIcon(theme.decor.badgeGlyph, theme, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(theme.decor.kicker, color = theme.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(1.dp).height(14.dp).drawWithCache { onDrawBehind { drawRect(theme.accentSoft.copy(alpha = 0.45f)) } })
        Spacer(Modifier.width(12.dp))
        Text(theme.decor.badge, color = Nflix.Text.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}
