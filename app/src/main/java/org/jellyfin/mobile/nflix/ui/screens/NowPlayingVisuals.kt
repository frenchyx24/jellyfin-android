package org.jellyfin.mobile.nflix.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import kotlin.math.sin

/** False when the user turned system animations off; every looping effect below then stays still. */
@Composable
internal fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/** Blurred artwork drifting slowly behind the player, with a red glow that breathes while music plays. */
@Composable
internal fun AmbientBackground(artwork: String?, playing: Boolean) {
    val motion = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val pulse by transition.animateFloat(0.18f, 0.38f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(if (playing && motion) pulse else 0.14f, tween(600), label = "glow")
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        NImage(
            artwork,
            Modifier.fillMaxSize().graphicsLayer {
                val d = if (motion) drift else 0.5f
                scaleX = 1.35f + 0.1f * d
                scaleY = 1.35f + 0.1f * d
                rotationZ = (d - 0.5f) * 10f
                translationX = (d - 0.5f) * 60f
                alpha = 0.6f
            }.blur(70.dp),
            placeholderIcon = null,
        )
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Nflix.Red.copy(alpha = glow), Color.Transparent), radius = 900f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Nflix.Night.copy(alpha = 0.15f), Nflix.Night.copy(alpha = 0.55f), Nflix.Night.copy(alpha = 0.95f)))))
    }
}

/** Cover art: a spinning record (vinyl mode) or a sleeve that breathes with playback. */
@Composable
internal fun AnimatedArtwork(artwork: String?, playing: Boolean, vinyl: Boolean, modifier: Modifier = Modifier) {
    val motion = animationsEnabled()
    if (!vinyl) {
        val scale by animateFloatAsState(if (playing) 1f else 0.86f, spring(dampingRatio = 0.6f), label = "art-scale")
        val transition = rememberInfiniteTransition(label = "breath")
        val breath by transition.animateFloat(1f, 1.02f, infiniteRepeatable(tween(1_800), RepeatMode.Reverse), label = "breath-scale")
        val total = scale * if (playing && motion) breath else 1f
        NImage(
            artwork,
            modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = total; scaleY = total; shadowElevation = 30f; shape = RoundedCornerShape(22.dp); clip = true },
            placeholderIcon = Icons.Rounded.MusicNote,
        )
        return
    }
    // Keeps its angle when paused, resumes from there: a real turntable does not snap back.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing, motion) {
        if (!playing || !motion) return@LaunchedEffect
        while (true) {
            angle.animateTo(angle.value + 360f, tween(6_000, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value }.clip(CircleShape).background(Color(0xFF0E0E0E)), contentAlignment = Alignment.Center) {
            // Grooves.
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                var r = radius * 0.98f
                while (r > radius * 0.42f) {
                    drawCircle(Color.White.copy(alpha = 0.05f), r, style = Stroke(1.2f))
                    r -= radius * 0.035f
                }
                drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent)), radius)
            }
            NImage(artwork, Modifier.fillMaxSize(0.42f).clip(CircleShape), placeholderIcon = Icons.Rounded.MusicNote)
            Box(Modifier.fillMaxSize(0.05f).clip(CircleShape).background(Nflix.Night))
        }
    }
}

/** Decorative equaliser that dances while music plays and settles when paused (not a level meter). */
@Composable
internal fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 5) {
    val motion = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "eq-phase")
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(bars) { index ->
            val target = if (playing && motion) 0.3f + 0.7f * ((sin(phase * (1f + index * 0.37f) + index) + 1f) / 2f) else 0.25f
            val height by animateFloatAsState(target, tween(120), label = "eq-$index")
            Box(Modifier.width(4.dp).fillMaxHeight(height).clip(RoundedCornerShape(2.dp)).background(Nflix.Red))
        }
    }
}
