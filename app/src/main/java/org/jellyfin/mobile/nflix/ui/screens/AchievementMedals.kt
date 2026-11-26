package org.jellyfin.mobile.nflix.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.jellyfin.mobile.nflix.data.AchievementBadge
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.BadgeShowcaseCache
import org.jellyfin.mobile.nflix.ui.LocalDeps
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Illustration drawn inside a medallion, chosen from the badge category family. */
internal enum class MedalMotif { Film, Tv, Flame, Moon, Sun, Sofa, Mask, Globe, Note, Hourglass, Calendar, Secret, Festive, Star }

internal fun medalMotif(category: String, icon: String? = null): MedalMotif {
    val fromCategory = motifFor(category)
    if (fromCategory != MedalMotif.Star || icon.isNullOrBlank()) return fromCategory
    return motifFor(icon)
}

private fun motifFor(raw: String): MedalMotif {
    val c = raw.lowercase(Locale.ROOT)
    fun has(vararg keys: String) = keys.any { it in c }
    return when {
        has("hidden", "secret") -> MedalMotif.Secret
        has("holiday", "seasonal", "fête", "fete", "christmas", "noël", "noel", "halloween", "festive", "celebration") -> MedalMotif.Festive
        has("binge", "marathon", "fire", "flame") -> MedalMotif.Flame
        has("night", "nuit", "late", "midnight", "moon") -> MedalMotif.Moon
        has("morning", "matin", "early", "breakfast", "sunny", "sunrise") -> MedalMotif.Sun
        has("weekend", "week-end", "sofa", "couch", "chair") -> MedalMotif.Sofa
        has("streak", "daily", "consecutive", "jours", "calendar", "today") -> MedalMotif.Calendar
        has("genre", "mask", "theater", "theatre") -> MedalMotif.Mask
        has("world", "monde", "language", "langue", "international", "country", "pays", "foreign", "globe", "public", "translate") -> MedalMotif.Globe
        has("music", "musique", "discograph", "album", "audio", "song", "artist", "note") -> MedalMotif.Note
        has("time", "temps", "endurance", "hour", "heure", "duration", "durée", "hourglass", "timer", "schedule") -> MedalMotif.Hourglass
        has("series", "série", "serie", "tv", "episode", "épisode", "show", "season") -> MedalMotif.Tv
        has("film", "movie", "cinema", "cinéma", "theaters") -> MedalMotif.Film
        else -> MedalMotif.Star
    }
}

private fun polar(cx: Float, cy: Float, r: Float, degrees: Float): Pair<Float, Float> {
    val a = degrees * PI.toFloat() / 180f
    return cx + r * cos(a) to cy + r * sin(a)
}

/** Draws [motif] in the square box at [origin] of side [side]; [hole] colours cut-outs (eyes, inner flame). */
internal fun DrawScope.drawMedalMotif(motif: MedalMotif, ink: Color, hole: Color, origin: Offset = Offset.Zero, side: Float = size.minDimension) {
    val s = side
    fun p(x: Float, y: Float) = Offset(origin.x + x * s, origin.y + y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun Path.mv(x: Float, y: Float) = moveTo(origin.x + x * s, origin.y + y * s)
    fun Path.ln(x: Float, y: Float) = lineTo(origin.x + x * s, origin.y + y * s)
    fun Path.cu(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(origin.x + x1 * s, origin.y + y1 * s, origin.x + x2 * s, origin.y + y2 * s, origin.x + x3 * s, origin.y + y3 * s)
    fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path = Path().apply {
        for (i in 0 until points * 2) {
            val (x, y) = polar(cx, cy, if (i % 2 == 0) outer else inner, -90f + i * 180f / points)
            if (i == 0) mv(x, y) else ln(x, y)
        }
        close()
    }
    val sw = s * 0.08f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (motif) {
        MedalMotif.Film -> {
            drawCircle(ink, s * 0.42f, p(.5f, .5f), style = stroke)
            for (i in 0 until 6) {
                val (x, y) = polar(.5f, .5f, .22f, -90f + i * 60f)
                drawCircle(ink, s * 0.085f, p(x, y))
            }
            drawCircle(ink, s * 0.05f, p(.5f, .5f))
        }
        MedalMotif.Tv -> {
            drawRoundRect(ink, p(.1f, .28f), sz(.8f, .52f), CornerRadius(.08f * s), style = stroke)
            drawLine(ink, p(.36f, .08f), p(.5f, .26f), sw, StrokeCap.Round)
            drawLine(ink, p(.64f, .08f), p(.5f, .26f), sw, StrokeCap.Round)
            drawLine(ink, p(.32f, .93f), p(.68f, .93f), sw, StrokeCap.Round)
            drawPath(Path().apply { mv(.43f, .42f); ln(.43f, .66f); ln(.62f, .54f); close() }, ink)
        }
        MedalMotif.Flame -> {
            drawPath(Path().apply {
                mv(.5f, .96f); cu(.22f, .96f, .12f, .74f, .2f, .56f); cu(.26f, .42f, .38f, .36f, .42f, .16f)
                cu(.6f, .28f, .66f, .42f, .62f, .5f); cu(.7f, .46f, .74f, .4f, .74f, .32f)
                cu(.86f, .48f, .9f, .66f, .82f, .8f); cu(.74f, .92f, .62f, .96f, .5f, .96f); close()
            }, ink)
            drawPath(Path().apply {
                mv(.5f, .9f); cu(.38f, .9f, .32f, .8f, .36f, .7f); cu(.4f, .62f, .48f, .58f, .5f, .5f)
                cu(.58f, .6f, .66f, .68f, .64f, .76f); cu(.62f, .86f, .56f, .9f, .5f, .9f); close()
            }, hole)
        }
        MedalMotif.Moon -> {
            val disc = Path().apply { addOval(Rect(p(.12f, .12f), sz(.76f, .76f))) }
            val bite = Path().apply { addOval(Rect(p(.36f, .02f), sz(.68f, .68f))) }
            drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, ink)
            drawPath(star(.78f, .74f, .1f, .04f, 4), ink)
        }
        MedalMotif.Sun -> {
            drawCircle(ink, s * 0.2f, p(.5f, .5f))
            for (i in 0 until 8) {
                val (x1, y1) = polar(.5f, .5f, .31f, i * 45f)
                val (x2, y2) = polar(.5f, .5f, .44f, i * 45f)
                drawLine(ink, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
            }
        }
        MedalMotif.Sofa -> {
            drawRoundRect(ink, p(.2f, .24f), sz(.6f, .34f), CornerRadius(.08f * s))
            drawRoundRect(ink, p(.14f, .52f), sz(.72f, .2f), CornerRadius(.06f * s))
            drawRoundRect(ink, p(.04f, .42f), sz(.16f, .34f), CornerRadius(.07f * s))
            drawRoundRect(ink, p(.8f, .42f), sz(.16f, .34f), CornerRadius(.07f * s))
            drawLine(hole, p(.24f, .53f), p(.76f, .53f), s * 0.03f, StrokeCap.Round)
            drawLine(ink, p(.2f, .74f), p(.2f, .86f), sw, StrokeCap.Round)
            drawLine(ink, p(.8f, .74f), p(.8f, .86f), sw, StrokeCap.Round)
        }
        MedalMotif.Mask -> {
            drawPath(Path().apply {
                mv(.14f, .14f); cu(.38f, .24f, .62f, .24f, .86f, .14f); cu(.9f, .5f, .78f, .86f, .5f, .92f)
                cu(.22f, .86f, .1f, .5f, .14f, .14f); close()
            }, ink)
            drawOval(hole, p(.27f, .36f), sz(.16f, .1f))
            drawOval(hole, p(.57f, .36f), sz(.16f, .1f))
            drawArc(hole, 20f, 140f, false, p(.32f, .46f), sz(.36f, .26f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
        }
        MedalMotif.Globe -> {
            val thin = Stroke(sw * 0.75f, cap = StrokeCap.Round)
            drawCircle(ink, s * 0.4f, p(.5f, .5f), style = stroke)
            drawOval(ink, p(.32f, .1f), sz(.36f, .8f), style = thin)
            drawLine(ink, p(.1f, .5f), p(.9f, .5f), sw * 0.75f)
            drawLine(ink, p(.18f, .3f), p(.82f, .3f), sw * 0.65f)
            drawLine(ink, p(.18f, .7f), p(.82f, .7f), sw * 0.65f)
        }
        MedalMotif.Note -> {
            drawOval(ink, p(.1f, .68f), sz(.28f, .2f))
            drawOval(ink, p(.54f, .6f), sz(.28f, .2f))
            drawLine(ink, p(.35f, .76f), p(.35f, .2f), sw)
            drawLine(ink, p(.79f, .68f), p(.79f, .12f), sw)
            drawPath(Path().apply { mv(.31f, .14f); ln(.83f, .04f); ln(.83f, .2f); ln(.31f, .3f); close() }, ink)
        }
        MedalMotif.Hourglass -> {
            drawLine(ink, p(.2f, .08f), p(.8f, .08f), sw, StrokeCap.Round)
            drawLine(ink, p(.2f, .92f), p(.8f, .92f), sw, StrokeCap.Round)
            drawPath(Path().apply {
                mv(.26f, .1f); ln(.74f, .1f); cu(.74f, .34f, .56f, .42f, .56f, .5f); cu(.56f, .58f, .74f, .66f, .74f, .9f)
                ln(.26f, .9f); cu(.26f, .66f, .44f, .58f, .44f, .5f); cu(.44f, .42f, .26f, .34f, .26f, .1f); close()
            }, ink, style = Stroke(sw * 0.8f, join = StrokeJoin.Round))
            drawPath(Path().apply { mv(.34f, .26f); ln(.66f, .26f); ln(.5f, .44f); close() }, ink)
            drawPath(Path().apply { mv(.32f, .86f); ln(.68f, .86f); ln(.5f, .68f); close() }, ink)
        }
        MedalMotif.Calendar -> {
            drawRoundRect(ink, p(.12f, .18f), sz(.76f, .7f), CornerRadius(.08f * s), style = stroke)
            drawRoundRect(ink, p(.12f, .18f), sz(.76f, .18f), CornerRadius(.08f * s))
            drawLine(ink, p(.32f, .08f), p(.32f, .24f), sw, StrokeCap.Round)
            drawLine(ink, p(.68f, .08f), p(.68f, .24f), sw, StrokeCap.Round)
            for (r in 0..1) for (c in 0..2) drawCircle(ink, s * 0.045f, p(.3f + .2f * c, .52f + .18f * r))
        }
        MedalMotif.Secret -> {
            drawArc(ink, 200f, 250f, false, p(.3f, .1f), sz(.4f, .38f), style = stroke)
            drawLine(ink, p(.5f, .48f), p(.5f, .62f), sw, StrokeCap.Round)
            drawCircle(ink, s * 0.06f, p(.5f, .82f))
        }
        MedalMotif.Festive -> {
            drawPath(star(.44f, .56f, .42f, .12f, 4), ink)
            drawPath(star(.8f, .2f, .16f, .05f, 4), ink)
            drawCircle(ink, s * 0.04f, p(.84f, .64f))
        }
        MedalMotif.Star -> drawPath(star(.5f, .54f, .46f, .19f, 5), ink)
    }
}

/** Complete medallion: inner gradient, rarity-coloured rim, studs and the category illustration. */
internal fun DrawScope.drawIllustratedMedal(badge: AchievementBadge, locked: Boolean, center: Offset = this.center, radius: Float = size.minDimension / 2f) {
    val color = rarityColor(badge.rarity)
    if (locked) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF161616)), center, radius), radius, center)
    } else {
        drawCircle(
            Brush.radialGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.55f)), center - Offset(radius * 0.3f, radius * 0.3f), radius * 1.5f),
            radius, center,
        )
    }
    val rim = radius * 0.14f
    drawCircle(
        if (locked) SolidColor(Color.White.copy(alpha = 0.12f))
        else Brush.sweepGradient(listOf(Color.White, color, lerp(color, Color.Black, 0.3f), Color.White.copy(alpha = 0.9f), color, Color.White), center),
        radius - rim / 2f, center, style = Stroke(rim),
    )
    drawCircle(Color.White.copy(alpha = if (locked) 0.06f else 0.35f), radius * 0.78f, center, style = Stroke(radius * 0.03f))
    if (!locked && radius > 20f) {
        for (i in 0 until 24) {
            val (x, y) = polar(center.x, center.y, radius - rim / 2f, i * 15f)
            drawCircle(Color.White.copy(alpha = 0.55f), radius * 0.018f, Offset(x, y))
        }
    }
    val motif = if (locked && badge.category == "Hidden") MedalMotif.Secret else medalMotif(badge.category, badge.icon)
    val side = radius * 1.0f
    drawMedalMotif(
        motif,
        ink = if (locked) Color(0xFF8C8C8C).copy(alpha = 0.45f) else Color.White,
        hole = if (locked) Color(0xFF1E1E1E) else lerp(color, Color.Black, 0.45f),
        origin = center - Offset(side / 2f, side / 2f), side = side,
    )
}

/** Category illustration alone, drawn over an existing medallion background. */
@Composable
internal fun MedalMotifArt(badge: AchievementBadge, locked: Boolean, modifier: Modifier = Modifier) {
    val motif = if (locked && badge.category == "Hidden") MedalMotif.Secret else medalMotif(badge.category, badge.icon)
    val color = rarityColor(badge.rarity)
    Canvas(modifier) {
        drawMedalMotif(motif, if (locked) Color(0xFF8C8C8C).copy(alpha = 0.45f) else Color.White, if (locked) Color(0xFF1E1E1E) else lerp(color, Color.Black, 0.35f))
    }
}

@Composable
internal fun MiniMedal(badge: AchievementBadge, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIllustratedMedal(badge, locked = false) }
}

/** Up to three showcased badges of [userId] as tiny medallions; fetched lazily, cached, errors ignored. */
@Composable
internal fun ShowcaseMedals(userId: String?, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    if (userId.isNullOrBlank()) return
    val deps = LocalDeps.current
    val hub = remember(deps.api) { AchievementHub(deps.api) }
    val version = BadgeShowcaseCache.version
    val badges by produceState(BadgeShowcaseCache.peek(userId).orEmpty(), userId, version) { value = hub.showcase(userId) }
    if (badges.isEmpty()) return
    Row(modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        badges.take(3).forEach { MiniMedal(it, size) }
    }
}

// ---------------------------------------------------------------------------------------------
// Share as image
// ---------------------------------------------------------------------------------------------

private fun sharePaint(sizePx: Float, color: Int, bold: Boolean, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = sizePx
    this.color = color
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    letterSpacing = spacing
}

/** Renders an original Nflix card for [badge] and shares it as PNG; falls back to text when anything fails. */
internal fun shareBadgeImage(context: Context, badge: AchievementBadge, userName: String? = null, levelLine: String? = null) {
    val text = "J'ai débloqué « ${badge.title} » sur Nflix !"
    val shared = runCatching {
        val w = 1080
        val h = 1350
        val image = ImageBitmap(w, h)
        val color = rarityColor(badge.rarity)
        val medalCenter = Offset(w / 2f, 560f)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0B0F), lerp(color, Color.Black, 0.72f), Color(0xFF050507))))
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), medalCenter, 520f), 520f, medalCenter)
            drawIllustratedMedal(badge, locked = false, center = medalCenter, radius = 260f)
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("NFLIX", w / 2f, 150f, sharePaint(84f, 0xFFE50914.toInt(), true, 0.2f))
            nc.drawText("SUCCÈS DÉBLOQUÉ", w / 2f, 215f, sharePaint(34f, 0xB3FFFFFF.toInt(), true, 0.15f))
            val titlePaint = sharePaint(72f, 0xFFFFFFFF.toInt(), true)
            nc.drawText(TextUtils.ellipsize(badge.title, titlePaint, w - 120f, TextUtils.TruncateAt.END).toString(), w / 2f, 940f, titlePaint)
            nc.drawText(rarityName(badge.rarity).uppercase(Locale.FRANCE), w / 2f, 1010f, sharePaint(40f, color.toArgb(), true, 0.1f))
            if (!userName.isNullOrBlank()) nc.drawText(userName, w / 2f, 1120f, sharePaint(46f, 0xFFFFFFFF.toInt(), true))
            if (!levelLine.isNullOrBlank()) nc.drawText(levelLine, w / 2f, 1180f, sharePaint(36f, 0xB3FFFFFF.toInt(), false))
            nc.drawText("Nflix · Succès", w / 2f, 1290f, sharePaint(30f, 0x80FFFFFF.toInt(), false, 0.1f))
        }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "badge-${badge.id.filter { it.isLetterOrDigit() }.take(40).ifEmpty { "nflix" }}.png")
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.isSuccess
    if (!shared) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
