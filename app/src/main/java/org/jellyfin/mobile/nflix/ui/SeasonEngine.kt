package org.jellyfin.mobile.nflix.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.lerp
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

// Pure theme logic (no Android): effect intensity, birthday, countdown, 12-month agenda and the
// crossfade between two themes. Unit-tested in SeasonEngineTest.

// ---- Effect intensity -------------------------------------------------------------------------

/** "Intensité des effets": scales particle counts and motion amplitude. Off stays the Animations switch. */
enum class SeasonIntensity(val key: String, val label: String, val particles: Float, val motion: Float) {
    Discreet("discreet", "Discrète", 0.5f, 0.6f),
    Normal("normal", "Normale", 1f, 1f),
    Intense("intense", "Intense", 1.6f, 1.35f);

    /**
     * Particles drawn for a family declared with [base] motes. Low-end devices and battery saver
     * halve the result; a family never drops below [min] so the theme stays recognisable.
     */
    fun count(base: Int, lowEnd: Boolean, min: Int = 2): Int {
        val scaled = base * particles * (if (lowEnd) 0.5f else 1f)
        return scaled.roundToInt().coerceAtLeast(min).coerceAtMost(MAX_PER_FAMILY)
    }

    /** Motion amplitude factor (sway, bob, swing); low-end devices keep the same amplitude. */
    fun amplitude(): Float = motion

    companion object {
        const val MAX_PER_FAMILY = 40
        fun of(key: String?): SeasonIntensity = entries.firstOrNull { it.key == key } ?: Normal
    }
}

// ---- Birthday ---------------------------------------------------------------------------------

/** The user's birthday, day and month only, stored locally as "MM-dd". */
object SeasonBirthday {
    fun parse(value: String?): MonthDay? = value?.takeIf { it.isNotBlank() }?.let { runCatching { MonthDay.parse("--$it") }.getOrNull() }

    fun format(day: MonthDay): String = "%02d-%02d".format(day.monthValue, day.dayOfMonth)

    /** The birthday in [year]; 29 February is celebrated on 28 February in common years. */
    fun inYear(day: MonthDay, year: Int): LocalDate =
        if (day.monthValue == 2 && day.dayOfMonth == 29 && !Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28) else day.atYear(year)

    fun isToday(day: MonthDay?, date: LocalDate): Boolean = day != null && inYear(day, date.year) == date
}

// ---- Countdown --------------------------------------------------------------------------------

data class HolidayCountdown(val event: SeasonEvent, val days: Int) {
    /** "J-12 avant Noël". */
    val label: String get() = "J-$days avant ${before(event)}"

    companion object {
        const val HORIZON_DAYS = 30

        /** Commemorations get no countdown: it would read as a celebration. */
        private val excluded = setOf(SeasonEvent.VictoryDay, SeasonEvent.Armistice, SeasonEvent.AllSaints)

        /** The nearest celebration whose main day is 1..[horizon] days after [date], or null. */
        fun next(date: LocalDate, horizon: Int = HORIZON_DAYS): HolidayCountdown? =
            SeasonEvent.entries.filter { it !in excluded }.mapNotNull { event ->
                // NewYear's day of year Y is 1 Jan Y+1, so year - 1 covers the coming 1 January.
                listOf(date.year - 1, date.year, date.year + 1)
                    .map { FrenchCalendar.day(event, it) }
                    .map { ChronoUnit.DAYS.between(date, it).toInt() }
                    .filter { it in 1..horizon }
                    .minOrNull()
                    ?.let { HolidayCountdown(event, it) }
            }.minWithOrNull(compareBy<HolidayCountdown> { it.days }.thenBy { it.event.ordinal })

        /** French article and preposition form: "avant le Nouvel An", "avant la Saint-Valentin". */
        fun before(event: SeasonEvent): String = when (event) {
            SeasonEvent.NewYear -> "le Nouvel An"
            SeasonEvent.Epiphany -> "l'Épiphanie"
            SeasonEvent.Candlemas -> "la Chandeleur"
            SeasonEvent.Valentine -> "la Saint-Valentin"
            SeasonEvent.Easter -> "Pâques"
            SeasonEvent.LabourDay -> "la Fête du Travail"
            SeasonEvent.VictoryDay -> "le 8 Mai"
            SeasonEvent.MothersDay -> "la Fête des Mères"
            SeasonEvent.FathersDay -> "la Fête des Pères"
            SeasonEvent.MusicDay -> "la Fête de la Musique"
            SeasonEvent.BastilleDay -> "le 14 Juillet"
            SeasonEvent.BackToSchool -> "la rentrée"
            SeasonEvent.Halloween -> "Halloween"
            SeasonEvent.AllSaints -> "la Toussaint"
            SeasonEvent.Armistice -> "le 11 Novembre"
            SeasonEvent.Christmas -> "Noël"
        }
    }
}

// ---- 12-month agenda ----------------------------------------------------------------------------

/** One line of the annual calendar preview. [theme] key, inclusive dates. */
data class AgendaEntry(val key: String, val label: String, val start: LocalDate, val end: LocalDate, val holiday: Boolean)

/** The theme of an agenda [key] (birthday included), falling back to the brand look. */
fun NflixSeasonTheme.Companion.themeFor(key: String): NflixSeasonTheme = byKey(key) ?: Brand

object SeasonAgenda {
    /**
     * Every theme shown in the 12 months starting at [from]: holidays (windows that end on or
     * after [from] and start before [from] + 1 year), season changes and the birthday, by start.
     */
    fun upcoming(from: LocalDate, birthday: MonthDay? = null): List<AgendaEntry> {
        val until = from.plusYears(1)
        fun inRange(start: LocalDate, end: LocalDate) = !end.isBefore(from) && start.isBefore(until)
        val out = mutableListOf<AgendaEntry>()
        for (year in from.year - 1..from.year + 1) {
            SeasonEvent.entries.forEach { event ->
                val w = FrenchCalendar.window(event, year)
                if (inRange(w.start, w.end)) {
                    val theme = NflixSeasonTheme.forEvent(event)
                    out += AgendaEntry(theme.key, theme.label, w.start, w.end, holiday = true)
                }
            }
            seasonStarts(year).forEach { (season, start, end) ->
                if (inRange(start, end)) {
                    val theme = NflixSeasonTheme.forSeason(season)
                    out += AgendaEntry(theme.key, theme.label, start, end, holiday = false)
                }
            }
            birthday?.let {
                val day = SeasonBirthday.inYear(it, year)
                if (inRange(day, day)) out += AgendaEntry(NflixSeasonTheme.Birthday.key, NflixSeasonTheme.Birthday.label, day, day, holiday = true)
            }
        }
        return out.distinct().sortedWith(compareBy<AgendaEntry> { it.start }.thenBy { !it.holiday })
    }

    /** Astronomical seasons of [year] as used by [Season.on]; winter runs into the next year. */
    private fun seasonStarts(year: Int): List<Triple<Season, LocalDate, LocalDate>> = listOf(
        Triple(Season.Spring, LocalDate.of(year, 3, 20), LocalDate.of(year, 6, 20)),
        Triple(Season.Summer, LocalDate.of(year, 6, 21), LocalDate.of(year, 9, 22)),
        Triple(Season.Autumn, LocalDate.of(year, 9, 23), LocalDate.of(year, 12, 20)),
        Triple(Season.Winter, LocalDate.of(year, 12, 21), LocalDate.of(year + 1, 3, 19)),
    )
}

// ---- Theme transition -----------------------------------------------------------------------------

/**
 * Crossfade between the theme on screen and a new one (manual change, date change, birthday).
 * [palette] is the colour mix every screen reads; decorations draw [from] at 1 - [progress] and
 * [to] at [progress]. The driver composable advances [progress]; without a driver the change is
 * immediate.
 */
@Stable
class SeasonTransition(initial: NflixSeasonTheme) {
    var from: NflixSeasonTheme? by mutableStateOf(null)
        private set
    var to: NflixSeasonTheme by mutableStateOf(initial)
        private set
    private var raw by mutableFloatStateOf(1f)

    /** [raw] quantised to [STEPS]: palette readers recompose once per step, not every frame. */
    private var step by mutableIntStateOf(STEPS)

    /** 0 → 1 while a crossfade runs; 1 at rest. */
    val progress: Float get() = raw

    val running: Boolean get() = from != null

    /**
     * Starts a crossfade to [target]. Mid-transition, the theme that dominates the screen becomes
     * the new starting point. Returns false when [target] is already shown or targeted.
     */
    fun start(target: NflixSeasonTheme): Boolean {
        if (target.key == to.key) return false
        val current = from
        from = if (current != null && raw < 0.5f) current else to
        to = target
        raw = 0f
        step = 0
        if (from?.key == target.key) { from = null; raw = 1f; step = STEPS }
        return true
    }

    fun advance(value: Float) {
        raw = value.coerceIn(0f, 1f)
        val s = ceil(raw * STEPS).toInt()
        if (s != step) step = s
        if (raw >= 1f) from = null
    }

    /** No animation: show [target] at once (system animations off, first frame). */
    fun snap(target: NflixSeasonTheme) {
        from = null
        to = target
        raw = 1f
        step = STEPS
    }

    /** Opacity of a decor layer drawn for [theme]. */
    fun alphaOf(theme: NflixSeasonTheme): Float = when {
        from == null -> if (theme.key == to.key) 1f else 0f
        theme.key == to.key -> raw
        theme.key == from?.key -> 1f - raw
        else -> 0f
    }

    /** Colours mixed in [STEPS] steps: few recompositions, no visible banding over 700 ms. */
    val palette: NflixSeasonTheme
        get() {
            val start = from ?: return to
            return mixThemes(start, to, step / STEPS.toFloat())
        }

    companion object {
        const val STEPS = 12
        const val DURATION_MS = 700
    }
}

/** [a] → [b] at [f]: colours and radii interpolated, identity and decor switch at half-way. */
fun mixThemes(a: NflixSeasonTheme, b: NflixSeasonTheme, f: Float): NflixSeasonTheme {
    if (f <= 0f) return a
    if (f >= 1f) return b
    val base = if (f < 0.5f) a else b
    return base.copy(
        // The target identity is kept so keyed state (particles, previews) settles once.
        key = b.key,
        accent = lerp(a.accent, b.accent, f), accentDeep = lerp(a.accentDeep, b.accentDeep, f), accentSoft = lerp(a.accentSoft, b.accentSoft, f),
        night = lerp(a.night, b.night, f), surface = lerp(a.surface, b.surface, f), surfaceHigh = lerp(a.surfaceHigh, b.surfaceHigh, f),
        outline = lerp(a.outline, b.outline, f), muted = lerp(a.muted, b.muted, f),
        glow = lerp(a.glow, b.glow, f), glowAlt = lerp(a.glowAlt, b.glowAlt, f),
        cardRadius = lerp(a.cardRadius, b.cardRadius, f), chipRadius = lerp(a.chipRadius, b.chipRadius, f),
        barSurface = lerp(a.barSurface, b.barSurface, f), heroGrade = lerp(a.heroGrade, b.heroGrade, f),
        logoSheen = b.logoSheen.indices.map { lerp(a.logoSheen.getOrElse(it) { a.logoSheen.last() }, b.logoSheen[it], f) },
        skeletonBase = lerp(a.skeletonBase, b.skeletonBase, f), skeletonHighlight = lerp(a.skeletonHighlight, b.skeletonHighlight, f),
        decor = b.decor,
    )
}
