package org.jellyfin.mobile.nflix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// ---- Decor model ------------------------------------------------------------------------------

/** Small original vector drawings used for particles, icons and corner decorations. */
enum class Glyph {
    Pumpkin, Bat, Star, Spark, Snowflake, Heart, Egg, Flower, Petal, Lily, Note, Leaf, Poppy,
    Crown, Crepe, Tie, Gift, Confetti, Sun, Book, Candle, Bauble, Cockade, Dot, Bubble,
}

/** How a particle family moves. */
enum class Motion { Fall, Rise, Fly, Confetti, Twinkle, Drift }

/** Main motif of the header sky. */
enum class Feature { None, Moon, MoonCastle, Stars, Fireworks, Sun, Glow }

/** Decoration hanging from the top corners. */
enum class Corners { None, Branches, Holly, Lights, Bunting, Blossoms, Frost, Ribbon }

/** Silhouette drawn just above the bottom navigation bar. */
enum class Ground { None, Graveyard, Firs, City, Meadow, Dunes, Hills, Plain }

@Immutable
data class Particles(
    val glyph: Glyph,
    val motion: Motion,
    val count: Int,
    val colors: List<Color>,
    val size: Float = 1f,
)

/** Everything drawn (not coloured) by a theme. */
@Immutable
data class SeasonDecor(
    /** Letter-spaced caps before the divider of the badge line. */
    val kicker: String = "SAISONNIER",
    /** Text after the divider: "Spécial Halloween". */
    val badge: String = "",
    val badgeGlyph: Glyph = Glyph.Spark,
    /** Header sky, top to bottom; it fades out into the hero. */
    val sky: List<Color> = emptyList(),
    val feature: Feature = Feature.None,
    val featureColors: List<Color> = listOf(Color.White),
    val corners: Corners = Corners.None,
    val cornerColors: List<Color> = listOf(Color.White),
    /** Large then small glyph at each bottom corner of the hero. */
    val heroGlyphs: List<Glyph> = emptyList(),
    val heroCandles: Boolean = false,
    val cobwebs: Boolean = false,
    val sectionGlyph: Glyph = Glyph.Spark,
    val playGlyph: Glyph? = null,
    val drip: Boolean = false,
    val ground: Ground = Ground.None,
    val groundGlyph: Glyph? = null,
    val particles: List<Particles> = emptyList(),
    /** Commemorations: slow, quiet, no confetti or fireworks. */
    val sober: Boolean = false,
) {
    companion object { val None = SeasonDecor() }
}

/** Decorations of every theme. All drawings are original vector shapes. */
object Decors {
    private val Orange = Color(0xFFF7781C)
    private val Amber = Color(0xFFFFB25C)
    private val Gold = Color(0xFFE6C15A)
    private val Snow = Color(0xFFF4F8FF)
    private val Blue = Color(0xFF2E5BD8)
    private val Red = Color(0xFFEF4135)
    private val Pink = Color(0xFFFF8FB4)
    private val Leafy = Color(0xFF7FC27A)

    val Winter = SeasonDecor(
        badge = "Hiver givré", badgeGlyph = Glyph.Snowflake,
        sky = listOf(Color(0xFF0B1A2E), Color(0xFF0A1424)), feature = Feature.Stars, featureColors = listOf(Snow),
        corners = Corners.Frost, cornerColors = listOf(Color(0xFFCFE6FF)),
        heroGlyphs = listOf(Glyph.Snowflake, Glyph.Snowflake), sectionGlyph = Glyph.Snowflake, playGlyph = Glyph.Snowflake,
        ground = Ground.Firs,
        particles = listOf(Particles(Glyph.Snowflake, Motion.Fall, 16, listOf(Snow, Color(0xFFBFDBFF)), 0.7f)),
    )
    val Spring = SeasonDecor(
        badge = "Printemps en fleurs", badgeGlyph = Glyph.Flower,
        sky = listOf(Color(0xFF2A1A2A), Color(0xFF14201A)), feature = Feature.Glow, featureColors = listOf(Color(0xFFFFC2D6)),
        corners = Corners.Blossoms, cornerColors = listOf(Pink, Color(0xFFFFE1EC), Color.White),
        heroGlyphs = listOf(Glyph.Flower, Glyph.Petal), sectionGlyph = Glyph.Flower, playGlyph = Glyph.Flower,
        ground = Ground.Meadow, groundGlyph = Glyph.Flower,
        particles = listOf(Particles(Glyph.Petal, Motion.Fall, 14, listOf(Pink, Color(0xFFFFE1EC), Color.White), 0.8f)),
    )
    val Summer = SeasonDecor(
        badge = "Été doré", badgeGlyph = Glyph.Sun,
        sky = listOf(Color(0xFF3A1E0A), Color(0xFF1A0F06)), feature = Feature.Sun, featureColors = listOf(Color(0xFFFFC46B), Color(0xFFFF8A3A)),
        heroGlyphs = listOf(Glyph.Sun), sectionGlyph = Glyph.Sun, playGlyph = Glyph.Sun,
        ground = Ground.Dunes,
        particles = listOf(Particles(Glyph.Dot, Motion.Drift, 12, listOf(Color(0xFFFFE2A8), Color.White), 0.5f)),
    )
    val Autumn = SeasonDecor(
        badge = "Automne doré", badgeGlyph = Glyph.Leaf,
        sky = listOf(Color(0xFF2A140A), Color(0xFF140B07)), feature = Feature.Moon, featureColors = listOf(Color(0xFFF2D2A8)),
        corners = Corners.Branches, cornerColors = listOf(Color(0xFFC8622E), Color(0xFFD9A55B), Color(0xFF8A2A1A)),
        heroGlyphs = listOf(Glyph.Leaf, Glyph.Leaf), sectionGlyph = Glyph.Leaf, playGlyph = Glyph.Leaf,
        ground = Ground.Hills, groundGlyph = Glyph.Leaf,
        particles = listOf(Particles(Glyph.Leaf, Motion.Fall, 10, listOf(Color(0xFFC8622E), Color(0xFFD9A55B), Color(0xFF8A2A1A)))),
    )
    val NewYear = SeasonDecor(
        badge = "Bonne année", badgeGlyph = Glyph.Spark,
        sky = listOf(Color(0xFF0A0A18), Color(0xFF07070C)), feature = Feature.Fireworks, featureColors = listOf(Gold, Color(0xFFFF6B9A), Color(0xFF7FD3FF), Color.White),
        corners = Corners.Lights, cornerColors = listOf(Gold, Color.White),
        heroGlyphs = listOf(Glyph.Star, Glyph.Spark), sectionGlyph = Glyph.Spark, playGlyph = Glyph.Star,
        ground = Ground.City,
        particles = listOf(Particles(Glyph.Confetti, Motion.Confetti, 18, listOf(Gold, Color(0xFFFF6B9A), Color(0xFF7FD3FF), Color.White), 0.55f)),
    )
    val Epiphany = SeasonDecor(
        badge = "Tirez les rois", badgeGlyph = Glyph.Crown,
        sky = listOf(Color(0xFF14102A), Color(0xFF0A0812)), feature = Feature.Stars, featureColors = listOf(Color(0xFFFFE7A3)),
        heroGlyphs = listOf(Glyph.Crown, Glyph.Star), sectionGlyph = Glyph.Crown, playGlyph = Glyph.Crown,
        ground = Ground.Plain, groundGlyph = Glyph.Star,
        particles = listOf(Particles(Glyph.Spark, Motion.Twinkle, 14, listOf(Gold, Color(0xFFFFF3C4)), 0.8f)),
    )
    val Candlemas = SeasonDecor(
        badge = "C'est la Chandeleur", badgeGlyph = Glyph.Crepe,
        sky = listOf(Color(0xFF2A1A0A), Color(0xFF120C06)), feature = Feature.Glow, featureColors = listOf(Color(0xFFFFC46B)),
        heroGlyphs = listOf(Glyph.Crepe, Glyph.Candle), heroCandles = true, sectionGlyph = Glyph.Crepe, playGlyph = Glyph.Crepe,
        ground = Ground.Plain, groundGlyph = Glyph.Candle,
        particles = listOf(Particles(Glyph.Crepe, Motion.Confetti, 6, listOf(Color(0xFFE8B464)), 1.2f)),
    )
    val Valentine = SeasonDecor(
        badge = "Saint-Valentin", badgeGlyph = Glyph.Heart,
        sky = listOf(Color(0xFF3A0A22), Color(0xFF14060E)), feature = Feature.Glow, featureColors = listOf(Color(0xFFFF6B9A)),
        corners = Corners.Bunting, cornerColors = listOf(Color(0xFFF0457A), Color(0xFFFFB3C9), Color.White),
        heroGlyphs = listOf(Glyph.Heart, Glyph.Heart), sectionGlyph = Glyph.Heart, playGlyph = Glyph.Heart,
        ground = Ground.Meadow, groundGlyph = Glyph.Heart,
        particles = listOf(Particles(Glyph.Heart, Motion.Rise, 12, listOf(Color(0xFFF0457A), Color(0xFFFFB3C9), Color(0xFFFF6B9A)), 0.8f)),
    )
    val Easter = SeasonDecor(
        badge = "Joyeuses Pâques", badgeGlyph = Glyph.Egg,
        sky = listOf(Color(0xFF2A1A3A), Color(0xFF121A16)), feature = Feature.Glow, featureColors = listOf(Color(0xFFFFE08A)),
        corners = Corners.Blossoms, cornerColors = listOf(Pink, Color(0xFFFFE08A), Color.White),
        heroGlyphs = listOf(Glyph.Egg, Glyph.Flower), sectionGlyph = Glyph.Egg, playGlyph = Glyph.Egg,
        ground = Ground.Meadow, groundGlyph = Glyph.Egg,
        particles = listOf(Particles(Glyph.Petal, Motion.Fall, 12, listOf(Pink, Color(0xFFFFE08A), Color(0xFFB8E6FF)), 0.8f)),
    )
    val LabourDay = SeasonDecor(
        badge = "Un brin de muguet", badgeGlyph = Glyph.Lily,
        sky = listOf(Color(0xFF0E2A1A), Color(0xFF07120C)), feature = Feature.Glow, featureColors = listOf(Color(0xFFE8FFE8)),
        heroGlyphs = listOf(Glyph.Lily, Glyph.Lily), sectionGlyph = Glyph.Lily, playGlyph = Glyph.Lily,
        ground = Ground.Meadow, groundGlyph = Glyph.Lily,
        particles = listOf(Particles(Glyph.Dot, Motion.Drift, 10, listOf(Color.White, Color(0xFFE8FFE8)), 0.5f)),
    )
    val VictoryDay = SeasonDecor(
        kicker = "COMMÉMORATION", badge = "Victoire du 8 mai 1945", badgeGlyph = Glyph.Cockade,
        sky = listOf(Color(0xFF0E1630), Color(0xFF080A12)), feature = Feature.Stars, featureColors = listOf(Color(0xFFE8ECF5)),
        corners = Corners.Ribbon, cornerColors = listOf(Blue, Color.White, Red),
        sectionGlyph = Glyph.Cockade, ground = Ground.Plain, groundGlyph = Glyph.Poppy,
        particles = listOf(Particles(Glyph.Petal, Motion.Fall, 4, listOf(Color(0xFFC62828)), 0.6f)),
        sober = true,
    )
    val MothersDay = SeasonDecor(
        badge = "Bonne fête maman", badgeGlyph = Glyph.Flower,
        sky = listOf(Color(0xFF3A1A2E), Color(0xFF160A12)), feature = Feature.Glow, featureColors = listOf(Color(0xFFFFD1E2)),
        corners = Corners.Blossoms, cornerColors = listOf(Color(0xFFF07AA8), Color(0xFFFFD1E2), Color.White),
        heroGlyphs = listOf(Glyph.Flower, Glyph.Heart), sectionGlyph = Glyph.Heart, playGlyph = Glyph.Flower,
        ground = Ground.Meadow, groundGlyph = Glyph.Flower,
        particles = listOf(
            Particles(Glyph.Heart, Motion.Rise, 7, listOf(Color(0xFFF07AA8), Color(0xFFFFD1E2)), 0.7f),
            Particles(Glyph.Petal, Motion.Fall, 7, listOf(Pink, Color.White), 0.7f),
        ),
    )
    val FathersDay = SeasonDecor(
        badge = "Bonne fête papa", badgeGlyph = Glyph.Tie,
        sky = listOf(Color(0xFF0E1E30), Color(0xFF070C12)), feature = Feature.Glow, featureColors = listOf(Color(0xFFB8E0FF)),
        corners = Corners.Bunting, cornerColors = listOf(Color(0xFF4FA3D9), Color(0xFFF2B33D), Color.White),
        heroGlyphs = listOf(Glyph.Tie, Glyph.Heart), sectionGlyph = Glyph.Tie, playGlyph = Glyph.Tie,
        ground = Ground.Hills,
        particles = listOf(
            Particles(Glyph.Heart, Motion.Rise, 6, listOf(Color(0xFF4FA3D9), Color(0xFFB8E0FF)), 0.7f),
            Particles(Glyph.Tie, Motion.Fall, 5, listOf(Color(0xFF4FA3D9), Color(0xFFF2B33D)), 0.8f),
        ),
    )
    val MusicDay = SeasonDecor(
        badge = "Fête de la Musique", badgeGlyph = Glyph.Note,
        sky = listOf(Color(0xFF1E0E3A), Color(0xFF0A0614)), feature = Feature.Glow, featureColors = listOf(Color(0xFFB36BFF)),
        corners = Corners.Lights, cornerColors = listOf(Color(0xFFFFD36B), Color(0xFFB36BFF), Color(0xFF6BE0FF)),
        heroGlyphs = listOf(Glyph.Note, Glyph.Note), sectionGlyph = Glyph.Note, playGlyph = Glyph.Note,
        ground = Ground.City,
        particles = listOf(Particles(Glyph.Note, Motion.Rise, 12, listOf(Color(0xFFFFD36B), Color(0xFFB36BFF), Color(0xFF6BE0FF)), 0.85f)),
    )
    val BastilleDay = SeasonDecor(
        kicker = "FÊTE NATIONALE", badge = "14 Juillet", badgeGlyph = Glyph.Cockade,
        sky = listOf(Color(0xFF0A1636), Color(0xFF060A16)), feature = Feature.Fireworks, featureColors = listOf(Color(0xFF5B8CFF), Color.White, Red),
        corners = Corners.Ribbon, cornerColors = listOf(Blue, Color.White, Red),
        heroGlyphs = listOf(Glyph.Cockade, Glyph.Star), sectionGlyph = Glyph.Cockade, playGlyph = Glyph.Cockade,
        ground = Ground.City,
        particles = listOf(Particles(Glyph.Spark, Motion.Twinkle, 12, listOf(Color(0xFF8FB0FF), Color.White, Color(0xFFFF8A80)), 0.7f)),
    )
    val BackToSchool = SeasonDecor(
        badge = "C'est la rentrée", badgeGlyph = Glyph.Book,
        sky = listOf(Color(0xFF14202E), Color(0xFF0A0C12)), feature = Feature.Glow, featureColors = listOf(Color(0xFF9FD3FF)),
        corners = Corners.Bunting, cornerColors = listOf(Color(0xFFF2B33D), Color(0xFF9FD3FF), Color(0xFFEF6B5A)),
        heroGlyphs = listOf(Glyph.Book, Glyph.Star), sectionGlyph = Glyph.Book, playGlyph = Glyph.Book,
        ground = Ground.Plain, groundGlyph = Glyph.Leaf,
        particles = listOf(Particles(Glyph.Leaf, Motion.Fall, 6, listOf(Color(0xFFF2B33D), Color(0xFFC8622E)), 0.8f)),
    )
    val Halloween = SeasonDecor(
        badge = "Spécial Halloween", badgeGlyph = Glyph.Pumpkin,
        sky = listOf(Color(0xFF2A0F3A), Color(0xFF14081E)), feature = Feature.MoonCastle, featureColors = listOf(Color(0xFFF2853A), Color(0xFFFFC27A)),
        corners = Corners.Branches, cornerColors = listOf(Orange, Amber, Color(0xFFC8461A)),
        heroGlyphs = listOf(Glyph.Pumpkin, Glyph.Pumpkin), heroCandles = true, cobwebs = true,
        sectionGlyph = Glyph.Pumpkin, playGlyph = Glyph.Pumpkin, drip = true,
        ground = Ground.Graveyard, groundGlyph = Glyph.Pumpkin,
        particles = listOf(
            Particles(Glyph.Bat, Motion.Fly, 5, listOf(Color(0xFF0E0612)), 1.3f),
            Particles(Glyph.Leaf, Motion.Fall, 7, listOf(Orange, Amber, Color(0xFF8A2A1A)), 0.9f),
        ),
    )
    val AllSaints = SeasonDecor(
        badge = "Toussaint", badgeGlyph = Glyph.Leaf,
        sky = listOf(Color(0xFF1E1A22), Color(0xFF0C0A0C)), feature = Feature.Moon, featureColors = listOf(Color(0xFFE0D8C8)),
        corners = Corners.Branches, cornerColors = listOf(Color(0xFFB8743A), Color(0xFFE0C08A), Color(0xFF6A3A1A)),
        heroGlyphs = listOf(Glyph.Leaf), sectionGlyph = Glyph.Leaf, ground = Ground.Hills, groundGlyph = Glyph.Leaf,
        particles = listOf(Particles(Glyph.Leaf, Motion.Fall, 9, listOf(Color(0xFFB8743A), Color(0xFFE0C08A), Color(0xFF6A3A1A)))),
        sober = true,
    )
    val Armistice = SeasonDecor(
        kicker = "COMMÉMORATION", badge = "Armistice du 11 novembre", badgeGlyph = Glyph.Poppy,
        sky = listOf(Color(0xFF0E1630), Color(0xFF080A12)), feature = Feature.None,
        corners = Corners.Ribbon, cornerColors = listOf(Blue, Color.White, Red),
        sectionGlyph = Glyph.Poppy, ground = Ground.Plain, groundGlyph = Glyph.Poppy,
        particles = listOf(Particles(Glyph.Petal, Motion.Fall, 4, listOf(Color(0xFFC62828)), 0.6f)),
        sober = true,
    )
    val Christmas = SeasonDecor(
        badge = "Joyeux Noël", badgeGlyph = Glyph.Bauble,
        sky = listOf(Color(0xFF0E1A2E), Color(0xFF0A0A12)), feature = Feature.Stars, featureColors = listOf(Snow, Gold),
        corners = Corners.Lights, cornerColors = listOf(Color(0xFFFF4A4A), Gold, Color(0xFF4AE08A), Color(0xFF6BB8FF)),
        heroGlyphs = listOf(Glyph.Gift, Glyph.Bauble), sectionGlyph = Glyph.Star, playGlyph = Glyph.Gift,
        ground = Ground.Firs, groundGlyph = Glyph.Star,
        particles = listOf(Particles(Glyph.Snowflake, Motion.Fall, 22, listOf(Snow, Color.White, Color(0xFFDDEBFF)), 0.75f)),
    )
}

// ---- Clock ------------------------------------------------------------------------------------

/**
 * Seconds since the decorations started, ticking every frame (every other frame on low-end
 * devices). Frozen when [active] is false, when the user turned motion off, under the system
 * "remove animations" setting or battery saver. The frame clock itself stops while the app is
 * in the background, so nothing runs off-screen.
 */
@Composable
fun rememberSeasonClock(active: Boolean = true): State<Float> {
    val motion = active && NflixSeason.ambient && rememberAmbientMotionAllowed()
    val lowEnd = rememberLowEndDevice()
    val clock = remember { mutableFloatStateOf(14f) }
    LaunchedEffect(motion, lowEnd) {
        if (!motion) return@LaunchedEffect
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
    val tone = r.nextInt(8)

    private companion object { val DEPTHS = floatArrayOf(0.55f, 0.85f, 1.2f) }
}

private fun motes(count: Int, salt: Int) = List(count) { SeasonMote(it, salt) }

private fun wrap(v: Float) = v - floor(v)

/** Deterministic pseudo-random in [0, 1). */
private fun hash(a: Int, b: Int): Float = wrap(sin(a * 12.9898f + b * 78.233f) * 43758.547f)

// ---- Glyphs -----------------------------------------------------------------------------------

private val LeafPath = Path().apply {
    moveTo(0f, -1f)
    cubicTo(0.62f, -0.55f, 0.5f, 0.45f, 0f, 1f)
    cubicTo(-0.5f, 0.45f, -0.62f, -0.55f, 0f, -1f)
    close()
}

private val HeartPath = Path().apply {
    moveTo(0f, 0.9f)
    cubicTo(-1.25f, 0.05f, -0.85f, -1.1f, 0f, -0.42f)
    cubicTo(0.85f, -1.1f, 1.25f, 0.05f, 0f, 0.9f)
    close()
}

private val SparkPath = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0.12f, -0.12f, 1f, 0f)
    quadraticTo(0.12f, 0.12f, 0f, 1f)
    quadraticTo(-0.12f, 0.12f, -1f, 0f)
    quadraticTo(-0.12f, -0.12f, 0f, -1f)
    close()
}

private val StarPath = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 1f else 0.42f
        val a = -PI / 2 + i * PI / 5
        val x = (cos(a) * r).toFloat()
        val y = (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val BatPath = Path().apply {
    moveTo(0f, -0.28f)
    lineTo(0.12f, -0.4f)
    lineTo(0.18f, -0.22f)
    cubicTo(0.45f, -0.55f, 0.8f, -0.5f, 1f, -0.2f)
    quadraticTo(0.82f, -0.08f, 0.72f, 0.1f)
    quadraticTo(0.58f, -0.04f, 0.44f, 0.12f)
    quadraticTo(0.3f, 0.02f, 0.16f, 0.22f)
    lineTo(0f, 0.32f)
    lineTo(-0.16f, 0.22f)
    quadraticTo(-0.3f, 0.02f, -0.44f, 0.12f)
    quadraticTo(-0.58f, -0.04f, -0.72f, 0.1f)
    quadraticTo(-0.82f, -0.08f, -1f, -0.2f)
    cubicTo(-0.8f, -0.5f, -0.45f, -0.55f, -0.18f, -0.22f)
    lineTo(-0.12f, -0.4f)
    close()
}

private val PumpkinFace = Path().apply {
    // Eyes
    moveTo(-0.48f, -0.02f); lineTo(-0.14f, -0.02f); lineTo(-0.3f, -0.36f); close()
    moveTo(0.48f, -0.02f); lineTo(0.14f, -0.02f); lineTo(0.3f, -0.36f); close()
    // Nose
    moveTo(-0.08f, 0.14f); lineTo(0.08f, 0.14f); lineTo(0f, 0.02f); close()
    // Grin
    moveTo(-0.58f, 0.22f)
    lineTo(-0.42f, 0.32f); lineTo(-0.3f, 0.24f); lineTo(-0.15f, 0.34f); lineTo(0f, 0.26f)
    lineTo(0.15f, 0.34f); lineTo(0.3f, 0.24f); lineTo(0.42f, 0.32f); lineTo(0.58f, 0.22f)
    quadraticTo(0f, 0.8f, -0.58f, 0.22f)
    close()
}

private val FlamePath = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0.32f, -0.45f, 0f, -0.1f)
    quadraticTo(-0.32f, -0.45f, 0f, -1f)
    close()
}

private val CrownPath = Path().apply {
    moveTo(-0.9f, 0.55f); lineTo(-0.9f, -0.35f); lineTo(-0.45f, 0.05f); lineTo(0f, -0.65f)
    lineTo(0.45f, 0.05f); lineTo(0.9f, -0.35f); lineTo(0.9f, 0.55f); close()
}

private val TiePath = Path().apply {
    moveTo(-0.26f, -1f); lineTo(0.26f, -1f); lineTo(0.15f, -0.66f); lineTo(-0.15f, -0.66f); close()
    moveTo(-0.15f, -0.62f); lineTo(0.15f, -0.62f); lineTo(0.36f, 0.6f); lineTo(0f, 1f); lineTo(-0.36f, 0.6f); close()
}

private val BookPath = Path().apply {
    moveTo(0f, -0.55f); lineTo(-1f, -0.8f); lineTo(-1f, 0.65f); lineTo(0f, 0.9f); close()
    moveTo(0f, -0.55f); lineTo(1f, -0.8f); lineTo(1f, 0.65f); lineTo(0f, 0.9f); close()
}

private val NoteFlag = Path().apply {
    moveTo(-0.02f, -0.95f)
    quadraticTo(0.62f, -0.62f, 0.45f, -0.05f)
    quadraticTo(0.4f, -0.45f, -0.02f, -0.58f)
    close()
}

private val Ink = Color(0xFF0E0612)

/** Draws [glyph] centred on [c], [r] px radius. [tint] colours monochrome glyphs. */
fun DrawScope.drawGlyph(
    glyph: Glyph, c: Offset, r: Float, tint: Color,
    alpha: Float = 1f, rotation: Float = 0f, sx: Float = 1f, sy: Float = 1f, t: Float = 0f,
) {
    if (alpha <= 0.01f || r <= 0f) return
    withTransform({
        translate(c.x, c.y)
        if (rotation != 0f) rotate(rotation, Offset.Zero)
        scale(r * sx, r * sy, Offset.Zero)
    }) { unitGlyph(glyph, tint, alpha.coerceIn(0f, 1f), t) }
}

private fun DrawScope.unitGlyph(glyph: Glyph, tint: Color, a: Float, t: Float) {
    fun Color.a(f: Float = 1f) = copy(alpha = (alpha * a * f).coerceIn(0f, 1f))
    when (glyph) {
        Glyph.Pumpkin -> {
            val flicker = 0.78f + 0.22f * sin(t * 11f) * sin(t * 7.3f + 1f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A2E).a(0.45f * flicker), Color.Transparent), Offset.Zero, 1.9f), 1.9f, Offset.Zero)
            drawOval(Color(0xFFB8500E).a(), Offset(-1f, -0.72f), Size(1.15f, 1.5f))
            drawOval(Color(0xFFB8500E).a(), Offset(-0.15f, -0.72f), Size(1.15f, 1.5f))
            drawOval(Color(0xFFE8731C).a(), Offset(-0.56f, -0.8f), Size(1.12f, 1.6f))
            drawOval(Color(0xFFF49A3A).a(0.5f), Offset(-0.3f, -0.7f), Size(0.3f, 1.2f))
            drawRoundRect(Color(0xFF4A6A2A).a(), Offset(-0.1f, -1.08f), Size(0.2f, 0.4f), CornerRadius(0.06f))
            drawPath(PumpkinFace, Color(0xFFFFD45C).a(flicker))
        }
        Glyph.Bat -> drawPath(BatPath, tint.a())
        Glyph.Star -> {
            drawCircle(Brush.radialGradient(listOf(tint.a(0.35f), Color.Transparent), Offset.Zero, 1.6f), 1.6f, Offset.Zero)
            drawPath(StarPath, tint.a())
        }
        Glyph.Spark -> {
            drawCircle(Brush.radialGradient(listOf(tint.a(0.3f), Color.Transparent), Offset.Zero, 1.2f), 1.2f, Offset.Zero)
            drawPath(SparkPath, tint.a())
        }
        Glyph.Snowflake -> for (i in 0 until 6) {
            val ang = i * PI.toFloat() / 3f
            val d = Offset(cos(ang), sin(ang))
            drawLine(tint.a(), Offset.Zero, d * 1f, 0.13f, StrokeCap.Round)
            val m = d * 0.55f
            for (s in listOf(-1f, 1f)) {
                val b = ang + s * PI.toFloat() / 4f
                drawLine(tint.a(), m, m + Offset(cos(b), sin(b)) * 0.32f, 0.11f, StrokeCap.Round)
            }
        }
        Glyph.Heart -> drawPath(HeartPath, tint.a())
        Glyph.Egg -> {
            drawOval(tint.a(), Offset(-0.75f, -1f), Size(1.5f, 2f))
            val band = Path().apply {
                moveTo(-0.72f, 0.05f)
                var x = -0.72f
                var up = true
                while (x < 0.72f) { x += 0.18f; lineTo(x.coerceAtMost(0.72f), if (up) -0.12f else 0.05f); up = !up }
            }
            drawPath(band, Color.White.a(0.85f), style = Stroke(0.14f))
            drawCircle(Color.White.a(0.7f), 0.1f, Offset(-0.25f, -0.5f))
            drawCircle(Color.White.a(0.7f), 0.1f, Offset(0.25f, -0.45f))
            drawCircle(Color.White.a(0.7f), 0.1f, Offset(0f, 0.55f))
        }
        Glyph.Flower -> {
            for (i in 0 until 5) {
                val ang = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 5f
                drawCircle(tint.a(), 0.42f, Offset(cos(ang), sin(ang)) * 0.52f)
            }
            drawCircle(Color(0xFFFFD45C).a(), 0.3f, Offset.Zero)
        }
        Glyph.Petal -> drawOval(tint.a(), Offset(-0.45f, -1f), Size(0.9f, 2f))
        Glyph.Lily -> {
            val green = Color(0xFF4FA866)
            drawOval(green.a(0.9f), Offset(0.25f, -0.35f), Size(0.5f, 1.35f))
            val stem = Path().apply { moveTo(0.15f, 1f); quadraticTo(0.3f, -0.55f, -0.65f, -0.85f) }
            drawPath(stem, green.a(), style = Stroke(0.1f, cap = StrokeCap.Round))
            listOf(Offset(-0.48f, -0.52f), Offset(-0.12f, -0.34f), Offset(0.14f, -0.02f)).forEachIndexed { i, p ->
                val br = 0.21f - i * 0.01f
                drawCircle(Color.White.a(), br, p)
                drawCircle(Color(0xFFE8F5E8).a(), br * 0.9f, p + Offset(0f, br * 0.4f))
            }
        }
        Glyph.Note -> {
            withTransform({ rotate(-20f, Offset(-0.38f, 0.62f)) }) { drawOval(tint.a(), Offset(-0.78f, 0.38f), Size(0.78f, 0.52f)) }
            drawLine(tint.a(), Offset(-0.04f, 0.6f), Offset(-0.04f, -0.95f), 0.13f)
            drawPath(NoteFlag, tint.a())
        }
        Glyph.Leaf -> {
            drawPath(LeafPath, tint.a())
            drawLine(Color.Black.a(0.35f), Offset(0f, -0.8f), Offset(0f, 0.9f), 0.06f)
        }
        Glyph.Poppy -> {
            val red = Color(0xFFD32F2F)
            val deep = Color(0xFFB71C1C)
            drawCircle(deep.a(), 0.55f, Offset(-0.38f, 0.1f))
            drawCircle(deep.a(), 0.55f, Offset(0.38f, 0.1f))
            drawCircle(red.a(), 0.55f, Offset(0f, -0.35f))
            drawCircle(red.a(), 0.55f, Offset(0f, 0.38f))
            drawCircle(Color(0xFF1A1010).a(), 0.22f, Offset.Zero)
        }
        Glyph.Crown -> {
            drawPath(CrownPath, Color(0xFFE6C15A).a())
            drawRect(Color(0xFFB8902A).a(), Offset(-0.9f, 0.32f), Size(1.8f, 0.23f))
            drawCircle(Color(0xFFD32F2F).a(), 0.13f, Offset(0f, -0.65f))
            drawCircle(Color(0xFF2E5BD8).a(), 0.11f, Offset(-0.9f, -0.35f))
            drawCircle(Color(0xFF2E5BD8).a(), 0.11f, Offset(0.9f, -0.35f))
        }
        Glyph.Crepe -> {
            drawCircle(Color(0xFFC88A3A).a(), 1f, Offset.Zero)
            drawCircle(Color(0xFFE8B464).a(), 0.88f, Offset.Zero)
            listOf(Offset(-0.4f, -0.3f), Offset(0.3f, -0.5f), Offset(0.45f, 0.25f), Offset(-0.2f, 0.45f), Offset(0.05f, 0f)).forEach {
                drawCircle(Color(0xFFB8762E).a(0.7f), 0.12f, it)
            }
        }
        Glyph.Tie -> {
            drawPath(TiePath, tint.a())
            drawLine(Color.White.a(0.5f), Offset(-0.1f, -0.3f), Offset(0.2f, -0.1f), 0.08f)
            drawLine(Color.White.a(0.5f), Offset(-0.2f, 0.2f), Offset(0.27f, 0.45f), 0.08f)
        }
        Glyph.Gift -> {
            drawRect(tint.a(), Offset(-0.8f, -0.35f), Size(1.6f, 1.3f))
            drawRect(tint.a(0.8f), Offset(-0.9f, -0.6f), Size(1.8f, 0.32f))
            drawRect(Color(0xFFE6C15A).a(), Offset(-0.13f, -0.6f), Size(0.26f, 1.55f))
            drawCircle(Color(0xFFE6C15A).a(), 0.24f, Offset(-0.26f, -0.8f), style = Stroke(0.12f))
            drawCircle(Color(0xFFE6C15A).a(), 0.24f, Offset(0.26f, -0.8f), style = Stroke(0.12f))
        }
        Glyph.Confetti -> drawRect(tint.a(), Offset(-0.45f, -1f), Size(0.9f, 2f))
        Glyph.Sun -> {
            drawCircle(Brush.radialGradient(listOf(tint.a(0.4f), Color.Transparent), Offset.Zero, 1.3f), 1.3f, Offset.Zero)
            for (i in 0 until 8) {
                val ang = i * PI.toFloat() / 4f + t * 0.2f
                val d = Offset(cos(ang), sin(ang))
                drawLine(tint.a(), d * 0.72f, d * 1f, 0.14f, StrokeCap.Round)
            }
            drawCircle(tint.a(), 0.52f, Offset.Zero)
        }
        Glyph.Book -> {
            drawPath(BookPath, Color(0xFFF4F1E8).a())
            drawLine(tint.a(), Offset(0f, -0.55f), Offset(0f, 0.9f), 0.1f)
            for (i in 0 until 3) {
                val y = -0.35f + i * 0.3f
                drawLine(tint.a(0.6f), Offset(-0.8f, y - 0.05f), Offset(-0.2f, y + 0.1f), 0.06f)
                drawLine(tint.a(0.6f), Offset(0.2f, y + 0.1f), Offset(0.8f, y - 0.05f), 0.06f)
            }
        }
        Glyph.Candle -> {
            val flicker = 0.85f + 0.15f * sin(t * 13f) * sin(t * 5.1f + 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC46B).a(0.5f * flicker), Color.Transparent), Offset(0f, -0.5f), 1.1f), 1.1f, Offset(0f, -0.5f))
            drawRoundRect(Color(0xFFF2E6CF).a(), Offset(-0.24f, -0.2f), Size(0.48f, 1.2f), CornerRadius(0.06f))
            drawRect(Color(0xFFD8C8A8).a(0.6f), Offset(0.08f, -0.2f), Size(0.16f, 1.2f))
            withTransform({ scale(flicker, 0.8f + 0.25f * flicker, Offset(0f, -0.12f)) }) {
                drawPath(FlamePath, Color(0xFFFFB347).a())
            }
        }
        Glyph.Bauble -> {
            drawRect(Color(0xFFE6C15A).a(), Offset(-0.2f, -0.9f), Size(0.4f, 0.25f))
            drawCircle(tint.a(), 0.78f, Offset(0f, 0.15f))
            drawCircle(Color.White.a(0.45f), 0.2f, Offset(-0.28f, -0.12f))
            drawLine(Color(0xFFE6C15A).a(0.7f), Offset(-0.72f, 0.2f), Offset(0.72f, 0.2f), 0.1f)
        }
        Glyph.Cockade -> {
            drawCircle(Color(0xFFEF4135).a(), 1f, Offset.Zero)
            drawCircle(Color.White.a(), 0.66f, Offset.Zero)
            drawCircle(Color(0xFF0055A4).a(), 0.34f, Offset.Zero)
        }
        Glyph.Dot -> {
            drawCircle(Brush.radialGradient(listOf(tint.a(0.6f), Color.Transparent), Offset.Zero, 1f), 1f, Offset.Zero)
            drawCircle(tint.a(), 0.25f, Offset.Zero)
        }
        Glyph.Bubble -> drawCircle(tint.a(), 0.9f, Offset.Zero, style = Stroke(0.12f))
    }
}

// ---- Particles --------------------------------------------------------------------------------

private fun DrawScope.drawParticles(p: Particles, motes: List<SeasonMote>, t: Float, alpha: Float, sober: Boolean) {
    val w = size.width
    val h = size.height
    val d = density
    val slow = if (sober) 0.5f else 1f
    motes.forEach { m ->
        val color = p.colors[m.tone % p.colors.size]
        val r = 6f * d * p.size * m.size * m.depth
        val baseA = (0.35f + 0.4f * ((m.depth - 0.55f) / 0.65f)) * alpha
        when (p.motion) {
            Motion.Fall -> {
                val y = (wrap(m.phase + t * slow * m.speed * 0.010f * m.depth) * 1.15f - 0.08f) * h
                val x = m.x * w + sin(t * 0.6f * slow + m.sway) * 18f * d * m.depth
                val flip = cos(t * 0.9f * m.power + m.sway)
                drawGlyph(p.glyph, Offset(x, y), r, color, baseA, rotation = m.sway * 57f + t * slow * m.spin * 0.4f, sx = 0.35f + 0.65f * abs(flip), t = t)
            }
            Motion.Rise -> {
                val rise = wrap(m.phase + t * slow * m.speed * 0.011f)
                val y = (1.06f - rise * 1.14f) * h
                val x = m.x * w + sin(t * 0.9f + m.sway) * 14f * d
                val fade = (rise * 6f).coerceAtMost(1f) * ((1f - rise) * 3f).coerceAtMost(1f)
                drawGlyph(p.glyph, Offset(x, y), r, color, baseA * fade, rotation = sin(t * 1.1f + m.sway) * 14f, t = t)
            }
            Motion.Fly -> {
                val x = (wrap(m.x + t * m.speed * 0.03f) * 1.3f - 0.15f) * w
                val y = m.phase * h * 0.6f + sin(t * 1.3f + m.sway) * 12f * d
                val flap = abs(sin(t * 8f * m.power + m.sway))
                drawGlyph(p.glyph, Offset(x, y), r, color, 0.9f * alpha, rotation = sin(t * 1.3f + m.sway) * 8f, sy = 0.35f + 0.65f * flap, t = t)
            }
            Motion.Confetti -> {
                val y = (wrap(m.phase + t * m.speed * 0.016f * m.depth) * 1.15f - 0.08f) * h
                val x = m.x * w + sin(t * 1.4f + m.sway) * 10f * d
                drawGlyph(p.glyph, Offset(x, y), r * 0.6f, color, baseA, rotation = t * m.spin * 3f, sx = cos(t * 3f * m.power + m.sway), t = t)
            }
            Motion.Twinkle -> {
                val flash = sin(t * 1.1f * m.power + m.sway * 6f).coerceAtLeast(0f).pow(3)
                drawGlyph(p.glyph, Offset(m.x * w, m.phase * h * 0.8f), r * (0.6f + 0.4f * flash), color, baseA * flash, rotation = t * 10f, t = t)
            }
            Motion.Drift -> {
                val x = wrap(m.x + t * m.speed * 0.006f) * w
                val y = m.phase * h * 0.7f + sin(t * 0.5f + m.sway) * 10f * d
                val glint = 0.5f + 0.5f * sin(t * 1.5f * m.power + m.sway)
                drawGlyph(p.glyph, Offset(x, y), r * 0.8f, color, baseA * glint, t = t)
            }
        }
    }
}

/**
 * Season particles over a screen (falling leaves, snow, rising hearts, notes, confetti…).
 * Draw phase only, never takes touches. Bats stay in the home header.
 */
@Composable
fun SeasonAmbience(
    modifier: Modifier = Modifier,
    theme: NflixSeasonTheme = NflixSeason.theme,
    strength: Float = 1f,
    animated: Boolean = true,
    topGlow: Boolean = true,
) {
    if (!theme.isSeasonal || !NflixSeason.decorations) return
    val lowEnd = rememberLowEndDevice()
    val clock = rememberSeasonClock(animated)
    val families = remember(theme.key, strength, lowEnd) {
        theme.decor.particles.filter { it.motion != Motion.Fly }.mapIndexed { i, p ->
            val n = (p.count * strength * if (lowEnd) 0.5f else 1f).toInt().coerceAtLeast(2)
            p to motes(n, theme.key.hashCode() + i * 31)
        }
    }
    Canvas(modifier) {
        val t = clock.value
        if (topGlow) {
            val breath = 0.85f + 0.15f * sin(t * 0.35f)
            drawRect(
                Brush.radialGradient(listOf(theme.glow.copy(alpha = 0.22f * breath), Color.Transparent), Offset(size.width * 0.25f, 0f), size.width * 0.75f),
                size = Size(size.width, 180.dp.toPx().coerceAtMost(size.height)),
            )
        }
        families.forEach { (p, m) -> drawParticles(p, m, t, 0.75f, theme.decor.sober) }
    }
}

// ---- Header -----------------------------------------------------------------------------------

/**
 * Themed sky behind the home top bar: gradient, moon/castle, stars, sun or fireworks, corner
 * decorations and flying bats. It fades out at the bottom into the hero.
 */
@Composable
fun SeasonHeader(modifier: Modifier = Modifier, active: Boolean = true, theme: NflixSeasonTheme = NflixSeason.theme) {
    if (!theme.isSeasonal || !NflixSeason.decorations) return
    val clock = rememberSeasonClock(active)
    val flyers = remember(theme.key) { theme.decor.particles.filter { it.motion == Motion.Fly }.map { it to motes(it.count, theme.key.hashCode()) } }
    Canvas(modifier) {
        val t = clock.value
        drawHeaderScene(theme, t)
        flyers.forEach { (p, m) -> drawParticles(p, m, t, 1f, false) }
    }
}

private fun DrawScope.drawHeaderScene(theme: NflixSeasonTheme, t: Float) {
    val decor = theme.decor
    val w = size.width
    val h = size.height
    if (decor.sky.isNotEmpty()) {
        drawRect(Brush.verticalGradient(listOf(decor.sky.first(), decor.sky.last().copy(alpha = 0.85f), Color.Transparent), 0f, h))
    }
    when (decor.feature) {
        Feature.None -> Unit
        Feature.Moon, Feature.MoonCastle -> drawMoon(decor, t, castle = decor.feature == Feature.MoonCastle)
        Feature.Stars -> drawStars(decor, t)
        Feature.Fireworks -> { drawStars(decor.copy(featureColors = listOf(Color.White)), t); drawFireworks(decor.featureColors, t) }
        Feature.Sun -> drawSun(decor.featureColors, t)
        Feature.Glow -> {
            val breath = 0.8f + 0.2f * sin(t * 0.6f)
            drawCircle(
                Brush.radialGradient(listOf(decor.featureColors.first().copy(alpha = 0.28f * breath), Color.Transparent), Offset(w * 0.42f, h * 0.35f), w * 0.55f),
                w * 0.55f, Offset(w * 0.42f, h * 0.35f),
            )
        }
    }
    drawCorners(decor, t, w, h)
}

private fun DrawScope.drawMoon(decor: SeasonDecor, t: Float, castle: Boolean) {
    val w = size.width
    val h = size.height
    val r = minOf(w * 0.12f, h * 0.26f)
    val c = Offset(w * 0.4f, h * 0.42f)
    val base = decor.featureColors.first()
    val light = decor.featureColors.getOrElse(1) { Color.White }
    val pulse = 0.82f + 0.18f * sin(t * 0.9f)
    drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.5f * pulse), base.copy(alpha = 0.12f), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
    drawCircle(Brush.radialGradient(listOf(light, base), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, c)
    drawCircle(Color.Black.copy(alpha = 0.10f), r * 0.22f, c + Offset(r * 0.3f, -r * 0.2f))
    drawCircle(Color.Black.copy(alpha = 0.08f), r * 0.15f, c + Offset(-r * 0.35f, r * 0.35f))
    drawCircle(Color.Black.copy(alpha = 0.07f), r * 0.1f, c + Offset(r * 0.1f, r * 0.5f))
    if (!castle) return
    val u = r * 0.62f
    val by = c.y + r * 1.05f
    val ink = Color(0xFF0A0510)
    val sil = Path().apply {
        // Hill
        moveTo(c.x - u * 3.2f, by + u * 0.9f)
        quadraticTo(c.x, by - u * 0.55f, c.x + u * 3.2f, by + u * 0.9f)
        close()
        // Curtain wall
        addRect(androidx.compose.ui.geometry.Rect(c.x - u * 1.25f, by - u * 0.55f, c.x + u * 1.25f, by + u * 0.2f))
        // Towers: x, width, height, spire
        listOf(
            floatArrayOf(-1.05f, 0.26f, 0.85f, 0.4f), floatArrayOf(-0.58f, 0.3f, 1.35f, 0.55f), floatArrayOf(-0.12f, 0.36f, 1.85f, 0.75f),
            floatArrayOf(0.38f, 0.28f, 1.2f, 0.5f), floatArrayOf(0.85f, 0.22f, 0.8f, 0.35f),
        ).forEach { (x, tw, th, sp) ->
            val left = c.x + x * u
            val right = left + tw * u
            val top = by - th * u
            addRect(androidx.compose.ui.geometry.Rect(left, top, right, by + u * 0.2f))
            moveTo(left - tw * u * 0.12f, top)
            lineTo((left + right) / 2f, top - sp * u)
            lineTo(right + tw * u * 0.12f, top)
            close()
        }
    }
    drawPath(sil, ink)
    // Lit windows
    val glow = 0.7f + 0.3f * sin(t * 2.3f)
    listOf(Offset(-0.47f, -0.9f), Offset(0.02f, -1.35f), Offset(0.06f, -0.9f), Offset(0.5f, -0.75f)).forEach {
        drawRect(Color(0xFFFFC46B).copy(alpha = 0.8f * glow), Offset(c.x + it.x * u, by + it.y * u), Size(u * 0.07f, u * 0.12f))
    }
}

private fun DrawScope.drawStars(decor: SeasonDecor, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    for (i in 0 until 26) {
        val x = hash(i, 1) * w
        val y = hash(i, 2) * h * 0.75f
        val tw = 0.45f + 0.55f * sin(t * (0.8f + hash(i, 3) * 1.6f) + i)
        val color = decor.featureColors[i % decor.featureColors.size]
        val r = (0.6f + hash(i, 4) * 1.1f) * d
        drawCircle(color.copy(alpha = 0.75f * tw.coerceIn(0f, 1f)), r, Offset(x, y))
        if (i % 7 == 0) drawGlyph(Glyph.Spark, Offset(x, y), r * 4f, color, 0.6f * tw.coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawFireworks(colors: List<Color>, t: Float) {
    val w = size.width
    val h = size.height
    val d = density
    for (i in 0 until 4) {
        val period = 2.4f + i * 0.41f
        val local = (t + i * 0.83f) / period
        val cycle = floor(local).toInt()
        val p = local - cycle
        val cx = (0.12f + 0.76f * hash(cycle, i)) * w
        val cy = (0.18f + 0.38f * hash(i, cycle + 7)) * h
        val color = colors[(cycle + i) % colors.size]
        if (p < 0.22f) {
            val q = p / 0.22f
            val y = h + (cy - h) * q
            drawCircle(color.copy(alpha = 0.9f), 1.6f * d, Offset(cx, y))
            drawLine(color.copy(alpha = 0.3f), Offset(cx, y), Offset(cx, y + 18f * d), 1.2f * d)
        } else {
            val q = (p - 0.22f) / 0.78f
            val radius = 46f * d * (1f - (1f - q).pow(3))
            val fade = (1f - q).pow(1.4f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f * fade), Color.Transparent), Offset(cx, cy), radius * 1.4f + 1f), radius * 1.4f + 1f, Offset(cx, cy))
            for (k in 0 until 14) {
                val ang = k * 2f * PI.toFloat() / 14f + i
                val dir = Offset(cos(ang), sin(ang))
                val pos = Offset(cx, cy) + dir * radius + Offset(0f, q * q * 16f * d)
                val tail = Offset(cx, cy) + dir * (radius * 0.72f) + Offset(0f, q * q * 12f * d)
                drawLine(color.copy(alpha = 0.55f * fade), tail, pos, 1.3f * d, StrokeCap.Round)
                drawCircle(color.copy(alpha = fade), 1.5f * d, pos)
            }
        }
    }
}

private fun DrawScope.drawSun(colors: List<Color>, t: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.86f, h * 0.18f)
    val r = w * 0.1f
    val core = colors.first()
    val rim = colors.getOrElse(1) { core }
    drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.45f), rim.copy(alpha = 0.12f), Color.Transparent), c, w * 0.7f), w * 0.7f, c)
    withTransform({ rotate(t * 4f, c) }) {
        for (i in 0 until 14) {
            val ang = i * 2f * PI.toFloat() / 14f
            val a = Offset(cos(ang), sin(ang))
            val b = Offset(cos(ang + 0.07f), sin(ang + 0.07f))
            val ray = Path().apply {
                moveTo(c.x + a.x * r * 1.3f, c.y + a.y * r * 1.3f)
                lineTo(c.x + a.x * w * 0.75f, c.y + a.y * w * 0.75f)
                lineTo(c.x + b.x * w * 0.75f, c.y + b.y * w * 0.75f)
                close()
            }
            drawPath(ray, core.copy(alpha = 0.07f))
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, core), c, r), r, c)
    // Heat shimmer bands.
    val band = 10f * density
    for (i in 0 until 3) {
        val y = h * (0.55f + i * 0.12f) + sin(t * 0.7f + i * 1.7f) * 6f * density
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, core.copy(alpha = 0.05f), Color.Transparent), y - band, y + band),
            topLeft = Offset(0f, y - band), size = Size(w, band * 2f),
        )
    }
}

private fun DrawScope.mirrored(w: Float, block: DrawScope.() -> Unit) {
    block()
    withTransform({ scale(-1f, 1f, Offset(w / 2f, 0f)) }) { block() }
}

private fun DrawScope.drawCorners(decor: SeasonDecor, t: Float, w: Float, h: Float) {
    val d = density
    val colors = decor.cornerColors
    when (decor.corners) {
        Corners.None -> Unit
        Corners.Branches, Corners.Blossoms -> mirrored(w) {
            val bark = if (decor.corners == Corners.Branches) Color(0xFF1A0E0A) else Color(0xFF3A2418)
            val branches = Path().apply {
                moveTo(-8f * d, 34f * d); cubicTo(40f * d, 12f * d, 82f * d, 50f * d, 152f * d, 22f * d)
                moveTo(60f * d, 30f * d); quadraticTo(72f * d, 62f * d, 96f * d, 74f * d)
                moveTo(106f * d, 32f * d); quadraticTo(122f * d, 4f * d, 142f * d, -2f * d)
                moveTo(28f * d, 24f * d); quadraticTo(18f * d, 54f * d, 40f * d, 84f * d)
            }
            drawPath(branches, bark.copy(alpha = 0.92f), style = Stroke(4f * d, cap = StrokeCap.Round))
            val spots = listOf(
                Offset(152f, 22f), Offset(96f, 74f), Offset(142f, -2f), Offset(40f, 84f), Offset(82f, 36f),
                Offset(122f, 30f), Offset(66f, 58f), Offset(20f, 40f), Offset(132f, 12f), Offset(48f, 30f),
            )
            spots.forEachIndexed { i, s ->
                val sway = sin(t * 1.2f + i * 0.9f) * 9f
                val glyph = if (decor.corners == Corners.Branches) Glyph.Leaf else Glyph.Flower
                drawGlyph(glyph, s * d, (if (glyph == Glyph.Leaf) 9f else 6f) * d, colors[i % colors.size], 0.92f, rotation = i * 47f + sway, t = t)
            }
        }
        Corners.Holly -> mirrored(w) { drawHolly(Offset(26f * d, 30f * d), d, t) }
        Corners.Lights -> {
            val sag = 24f * d
            val y0 = 14f * d
            val wire = Path().apply { moveTo(0f, y0); quadraticTo(w / 2f, y0 + sag * 2f, w, y0) }
            drawPath(wire, Color(0xFF1A2A1A), style = Stroke(1.5f * d))
            val n = 16
            for (i in 1 until n) {
                val s = i / n.toFloat()
                val x = w * s
                val y = y0 + sag * 2f * 2f * s * (1f - s)
                val tw = 0.55f + 0.45f * sin(t * 3f + i * 1.7f)
                val col = colors[i % colors.size]
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.5f * tw), Color.Transparent), Offset(x, y + 4f * d), 12f * d), 12f * d, Offset(x, y + 4f * d))
                drawOval(col.copy(alpha = 0.6f + 0.4f * tw), Offset(x - 2.5f * d, y + 1f * d), Size(5f * d, 7f * d))
            }
            if (decor.groundGlyph == Glyph.Star) mirrored(w) { drawHolly(Offset(16f * d, 18f * d), d, t) }
        }
        Corners.Bunting -> {
            val sag = 18f * d
            val y0 = 10f * d
            val wire = Path().apply { moveTo(0f, y0); quadraticTo(w / 2f, y0 + sag * 2f, w, y0) }
            drawPath(wire, Color.White.copy(alpha = 0.4f), style = Stroke(1f * d))
            val n = 13
            for (i in 1 until n) {
                val s = i / n.toFloat()
                val x = w * s
                val y = y0 + sag * 2f * 2f * s * (1f - s)
                val tri = Path().apply { moveTo(-7f * d, 0f); lineTo(7f * d, 0f); lineTo(0f, 14f * d); close() }
                withTransform({ translate(x, y); rotate(sin(t * 1.6f + i) * 8f, Offset.Zero) }) {
                    drawPath(tri, colors[i % colors.size].copy(alpha = 0.9f))
                }
            }
        }
        Corners.Frost -> mirrored(w) {
            drawCircle(Brush.radialGradient(listOf(colors.first().copy(alpha = 0.22f), Color.Transparent), Offset.Zero, 130f * d), 130f * d, Offset.Zero)
            drawGlyph(Glyph.Snowflake, Offset(24f * d, 30f * d), 16f * d, Color.White, 0.45f, rotation = t * 3f)
            drawGlyph(Glyph.Snowflake, Offset(62f * d, 16f * d), 9f * d, Color.White, 0.35f, rotation = -t * 4f)
            drawGlyph(Glyph.Snowflake, Offset(14f * d, 70f * d), 7f * d, Color.White, 0.3f, rotation = t * 5f)
        }
        Corners.Ribbon -> {
            val speed = if (decor.sober) 0.5f else 1.3f
            val stripe = 5f * d
            colors.forEachIndexed { k, col ->
                val path = Path()
                var x = w * 0.48f
                var first = true
                while (x <= w + 4f * d) {
                    val s = (x - w * 0.48f) / (w * 0.52f)
                    val y = 6f * d + s * 58f * d + sin(s * 5f - t * speed) * 5f * d + k * stripe
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    x += 6f * d
                }
                drawPath(path, col.copy(alpha = 0.85f), style = Stroke(stripe + 0.6f * d))
            }
        }
    }
}

private fun DrawScope.drawHolly(c: Offset, d: Float, t: Float) {
    val green = Color(0xFF1E6A3A)
    drawGlyph(Glyph.Leaf, c + Offset(-9f * d, 0f), 12f * d, green, 1f, rotation = -60f)
    drawGlyph(Glyph.Leaf, c + Offset(9f * d, 0f), 12f * d, green, 1f, rotation = 60f)
    drawGlyph(Glyph.Leaf, c + Offset(0f, -9f * d), 11f * d, Color(0xFF2A7A48), 1f, rotation = 0f)
    val glint = 0.85f + 0.15f * sin(t * 2f)
    listOf(Offset(-3f, 4f), Offset(3f, 4f), Offset(0f, -1f)).forEach { drawCircle(Color(0xFFD32F2F).copy(alpha = glint), 3.2f * d, c + it * d) }
}

// ---- Hero -------------------------------------------------------------------------------------

/**
 * Bottom corners of the hero: glyph clusters (pumpkins, gifts, eggs…), candles and cobwebs.
 * Small and drawn under the hero text and buttons.
 */
@Composable
fun SeasonHeroCorners(modifier: Modifier = Modifier, active: Boolean = true, theme: NflixSeasonTheme = NflixSeason.theme) {
    if (!theme.isSeasonal || !NflixSeason.decorations) return
    val decor = theme.decor
    if (decor.heroGlyphs.isEmpty() && !decor.heroCandles && !decor.cobwebs) return
    val clock = rememberSeasonClock(active)
    Canvas(modifier) { drawHeroCorners(theme, clock.value, size.width, size.height) }
}

private fun DrawScope.drawHeroCorners(theme: NflixSeasonTheme, t: Float, w: Float, h: Float) {
    val decor = theme.decor
    val d = density
    if (decor.cobwebs) mirrored(w) { drawCobweb(Offset(0f, h * 0.55f), 70f * d) }
    mirrored(w) {
        drawCircle(Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.22f), Color.Transparent), Offset(30f * d, h - 18f * d), 70f * d), 70f * d, Offset(30f * d, h - 18f * d))
        decor.heroGlyphs.getOrNull(0)?.let { drawGlyph(it, Offset(26f * d, h - 26f * d), 19f * d, theme.accent, 0.95f, t = t) }
        decor.heroGlyphs.getOrNull(1)?.let { drawGlyph(it, Offset(60f * d, h - 14f * d), 10f * d, theme.accentSoft, 0.9f, rotation = 12f, t = t + 1.3f) }
        if (decor.heroCandles) {
            drawGlyph(Glyph.Candle, Offset(8f * d, h - 14f * d), 8f * d, Color.White, 1f, t = t + 0.4f)
            drawGlyph(Glyph.Candle, Offset(80f * d, h - 9f * d), 6f * d, Color.White, 1f, t = t + 2.1f)
        }
    }
}

private fun DrawScope.drawCobweb(origin: Offset, r: Float) {
    val color = Color.White.copy(alpha = 0.16f)
    val stroke = 0.8f * density
    val spokes = 6
    val angles = List(spokes) { -PI.toFloat() / 2f + it * (PI.toFloat() / (spokes - 1)) }
    angles.forEach { a -> drawLine(color, origin, origin + Offset(cos(a), sin(a)) * r, stroke) }
    for (ring in 1..4) {
        val rr = r * ring / 4.5f
        val path = Path()
        angles.forEachIndexed { i, a ->
            val p = origin + Offset(cos(a), sin(a)) * rr
            if (i == 0) path.moveTo(p.x, p.y) else {
                val mid = (a + angles[i - 1]) / 2f
                val q = origin + Offset(cos(mid), sin(mid)) * (rr * 0.85f)
                path.quadraticTo(q.x, q.y, p.x, p.y)
            }
        }
        drawPath(path, color, style = Stroke(stroke))
    }
}

/** Primary "Lecture" button in the theme: accent gradient, glow, optional drips and glyph. */
@Composable
fun SeasonPlayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, theme: NflixSeasonTheme = NflixSeason.theme) {
    val decor = theme.decor
    val shape = RoundedCornerShape(12.dp)
    val clock = rememberSeasonClock(decor.drip)
    val content = if (theme.accent.luminance() > 0.3f) Color(0xFF1A0E05) else Color.White
    Box(
        modifier
            .height(48.dp)
            .seasonGlow(1f, shape, theme.accent)
            .drawBehind {
                if (!decor.drip) return@drawBehind
                val t = clock.value
                val dd = density
                listOf(0.1f to 0f, 0.18f to 1.7f, 0.74f to 3.1f).forEach { (fx, ph) ->
                    val len = (7f + 6f * (0.5f + 0.5f * sin(t * 0.6f + ph))) * dd
                    val x = size.width * fx
                    drawRoundRect(theme.accent, Offset(x - 2.5f * dd, size.height - 3f * dd), Size(5f * dd, len), CornerRadius(2.5f * dd))
                    drawCircle(theme.accent, 3.6f * dd, Offset(x, size.height - 3f * dd + len))
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(theme.accentSoft, theme.accent, theme.accent.copy(red = theme.accent.red * 0.8f, green = theme.accent.green * 0.8f, blue = theme.accent.blue * 0.8f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = content)
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = content, maxLines = 1)
            decor.playGlyph?.let {
                Spacer(Modifier.width(10.dp))
                SeasonGlyphIcon(it, theme, Modifier.size(22.dp))
            }
        }
    }
}

/** Soft glow around [shape] in [color]; [strength] 0..1 animates it in and out. */
fun Modifier.seasonGlow(strength: Float, shape: Shape, color: Color): Modifier = drawBehind {
    if (strength <= 0.01f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    for (i in 4 downTo 1) {
        drawOutline(outline, color.copy(alpha = 0.09f * strength * (5 - i)), style = Stroke(width = i * 3.dp.toPx()))
    }
}

/** Chip or nav border + glow when [selected]: themed, or nothing for the brand look. */
fun Modifier.seasonChip(selected: Boolean, shape: Shape, theme: NflixSeasonTheme): Modifier =
    if (!theme.isSeasonal || !selected) this
    else this.seasonGlow(1f, shape, theme.accent).border(1.5.dp, theme.accent, shape)

/** A glyph as a small static icon. */
@Composable
fun SeasonGlyphIcon(glyph: Glyph, theme: NflixSeasonTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tint = when (glyph) {
            Glyph.Bat -> Color.White
            Glyph.Snowflake, Glyph.Spark, Glyph.Star, Glyph.Sun, Glyph.Note, Glyph.Heart, Glyph.Leaf -> theme.accent
            Glyph.Flower, Glyph.Petal -> theme.accentSoft
            else -> theme.accent
        }
        drawGlyph(glyph, center, size.minDimension / 2f * 0.86f, tint, 1f, t = 3f)
    }
}

/** Leading glyph of section titles, or nothing for the brand look. */
@Composable
fun SeasonSectionIcon(theme: NflixSeasonTheme = NflixSeason.theme) {
    if (!theme.isSeasonal || !NflixSeason.decorations) return
    SeasonGlyphIcon(theme.decor.sectionGlyph, theme, Modifier.size(22.dp))
    Spacer(Modifier.width(10.dp))
}

// ---- Ground silhouette ------------------------------------------------------------------------

/** Silhouette above the bottom bar: graveyard, snowy firs, city, meadow, dunes… */
@Composable
fun SeasonGround(modifier: Modifier = Modifier, active: Boolean = true, theme: NflixSeasonTheme = NflixSeason.theme) {
    if (!theme.isSeasonal || !NflixSeason.decorations || theme.decor.ground == Ground.None) return
    val clock = rememberSeasonClock(active)
    Canvas(modifier) { drawGround(theme, clock.value, size.width, size.height) }
}

private fun DrawScope.drawGround(theme: NflixSeasonTheme, t: Float, w: Float, h: Float) {
    val decor = theme.decor
    val d = density
    val ink = theme.barSurface
    // Rim light so the silhouette reads against the content behind it.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.accent.copy(alpha = 0.18f)), h * 0.2f, h), Offset(0f, h * 0.2f), Size(w, h * 0.8f))
    fun hill(amp: Float, base: Float, waves: Float, phase: Float) = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, h * base - sin(x / w * waves * PI.toFloat() + phase) * amp * h)
            x += 8f * d
        }
        lineTo(w, h)
        close()
    }
    when (decor.ground) {
        Ground.None -> Unit
        Ground.Graveyard -> {
            drawPath(hill(0.12f, 0.72f, 3f, 0.4f), ink)
            listOf(0.06f, 0.2f, 0.33f, 0.66f, 0.8f, 0.93f).forEachIndexed { i, fx ->
                val x = w * fx
                val base = h * 0.74f
                if (i % 2 == 0) {
                    drawRect(ink, Offset(x - 1.5f * d, base - 22f * d), Size(3f * d, 22f * d))
                    drawRect(ink, Offset(x - 7f * d, base - 16f * d), Size(14f * d, 3f * d))
                } else {
                    drawRoundRect(ink, Offset(x - 7f * d, base - 15f * d), Size(14f * d, 17f * d), CornerRadius(7f * d, 7f * d))
                }
            }
            // Dead tree
            val tree = Path().apply {
                moveTo(w * 0.42f, h * 0.75f); lineTo(w * 0.43f, h * 0.25f)
                moveTo(w * 0.43f, h * 0.4f); lineTo(w * 0.40f, h * 0.18f)
                moveTo(w * 0.43f, h * 0.33f); lineTo(w * 0.46f, h * 0.12f)
            }
            drawPath(tree, ink, style = Stroke(2f * d, cap = StrokeCap.Round))
            decor.groundGlyph?.let { drawGlyph(it, Offset(w * 0.5f, h * 0.55f), 13f * d, theme.accent, 1f, t = t) }
        }
        Ground.Firs -> {
            drawPath(hill(0.08f, 0.78f, 2f, 1.2f), Color(0xFFE8F0FF).copy(alpha = 0.22f))
            listOf(0.04f, 0.13f, 0.24f, 0.71f, 0.82f, 0.94f).forEachIndexed { i, fx ->
                val x = w * fx
                val th = (26f + (i % 3) * 7f) * d
                val base = h * 0.82f
                val fir = Path().apply { moveTo(x, base - th); lineTo(x + th * 0.36f, base); lineTo(x - th * 0.36f, base); close() }
                drawPath(fir, Color(0xFF0E2A1C))
                val cap = Path().apply { moveTo(x, base - th); lineTo(x + th * 0.12f, base - th * 0.66f); lineTo(x - th * 0.12f, base - th * 0.66f); close() }
                drawPath(cap, Color.White.copy(alpha = 0.7f))
            }
            decor.groundGlyph?.let { drawGlyph(it, Offset(w * 0.5f, h * 0.5f), 9f * d, theme.accentSoft, 0.8f + 0.2f * sin(t * 2f)) }
        }
        Ground.City -> {
            val city = Path()
            var x = 0f
            var i = 0
            while (x < w) {
                val bw = (16f + hash(i, 9) * 22f) * d
                val bh = h * (0.3f + hash(i, 5) * 0.45f)
                city.addRect(androidx.compose.ui.geometry.Rect(x, h - bh, x + bw + 1f, h))
                x += bw
                i++
            }
            // Lattice tower silhouette.
            val tx = w * 0.72f
            city.moveTo(tx - 12f * d, h); city.lineTo(tx, h * 0.02f); city.lineTo(tx + 12f * d, h); city.close()
            drawPath(city, ink)
            for (k in 0 until 18) {
                val wx = hash(k, 11) * w
                val wy = h * (0.55f + hash(k, 12) * 0.35f)
                val on = sin(t * 0.7f + k * 2.1f) > -0.3f
                if (on) drawRect(theme.accentSoft.copy(alpha = 0.5f), Offset(wx, wy), Size(2f * d, 2.5f * d))
            }
        }
        Ground.Meadow -> {
            drawPath(hill(0.06f, 0.82f, 2.5f, 0.3f), Color(0xFF16301E))
            for (k in 0 until 40) {
                val bx = hash(k, 3) * w
                val bh = (8f + hash(k, 4) * 10f) * d
                val sway = sin(t * 1.4f + k * 0.6f) * 3f * d
                drawLine(Color(0xFF2E6A3E), Offset(bx, h), Offset(bx + sway, h - bh), 1.4f * d, StrokeCap.Round)
            }
            decor.groundGlyph?.let { g ->
                for (k in 0 until 7) {
                    val fx = (k + 0.5f) / 7f
                    val sway = sin(t * 1.3f + k) * 8f
                    drawGlyph(g, Offset(w * fx, h * 0.62f + (k % 2) * 5f * d), 7f * d, if (k % 2 == 0) theme.accent else theme.accentSoft, 0.95f, rotation = sway, t = t)
                }
            }
        }
        Ground.Dunes -> {
            drawPath(hill(0.1f, 0.75f, 1.6f, 0.8f), Color(0xFF3A2410).copy(alpha = 0.9f))
            drawPath(hill(0.07f, 0.88f, 2.2f, 2.4f), Color(0xFF241608))
            val shimmer = 0.5f + 0.5f * sin(t * 1.1f)
            drawLine(theme.accentSoft.copy(alpha = 0.25f * shimmer), Offset(0f, h * 0.6f), Offset(w, h * 0.6f), 1f * d)
        }
        Ground.Hills -> {
            drawPath(hill(0.1f, 0.76f, 2f, 0.9f), mixDark(theme.accentDeep))
            drawPath(hill(0.06f, 0.88f, 3f, 2f), ink)
            decor.groundGlyph?.let { g ->
                for (k in 0 until 9) drawGlyph(g, Offset(hash(k, 21) * w, h * (0.8f + hash(k, 22) * 0.12f)), 5f * d, if (k % 2 == 0) theme.accent else theme.accentSoft, 0.8f, rotation = k * 40f)
            }
        }
        Ground.Plain -> {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, ink), h * 0.5f, h), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
            drawLine(theme.accentSoft.copy(alpha = 0.25f), Offset(0f, h * 0.86f), Offset(w, h * 0.86f), 1f * d)
            decor.groundGlyph?.let { g ->
                val count = if (decor.sober) 5 else 7
                for (k in 0 until count) {
                    val fx = (k + 0.5f) / count
                    val sway = if (decor.sober) sin(t * 0.5f + k) * 3f else sin(t * 1.2f + k) * 8f
                    drawGlyph(g, Offset(w * fx, h * 0.72f), 6.5f * d, theme.accent, 0.9f, rotation = sway, t = t)
                }
            }
        }
    }
}

private fun mixDark(c: Color) = Color(c.red * 0.55f, c.green * 0.55f, c.blue * 0.55f, 1f)

// ---- Whole scene (Settings previews, screenshots) ---------------------------------------------

/** Miniature of a theme: header sky, corners, hero corners, silhouette and particles. */
@Composable
fun SeasonScene(theme: NflixSeasonTheme, modifier: Modifier = Modifier, animated: Boolean = true) {
    val clock = rememberSeasonClock(animated)
    val families = remember(theme.key) { theme.decor.particles.map { it to motes((it.count / 2).coerceAtLeast(2), theme.key.hashCode()) } }
    Box(modifier.background(Brush.verticalGradient(listOf(theme.heroGrade, theme.night)))) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val full = size
            drawHeaderScene(theme, t)
            families.forEach { (p, m) -> drawParticles(p, m, t, 0.9f, theme.decor.sober) }
            val gh = (full.height * 0.22f).coerceAtMost(40.dp.toPx())
            withTransform({ translate(0f, full.height - gh) }) { drawGround(theme, t, full.width, gh) }
            drawHeroCorners(theme, t, full.width, full.height - gh * 0.6f)
        }
    }
}
