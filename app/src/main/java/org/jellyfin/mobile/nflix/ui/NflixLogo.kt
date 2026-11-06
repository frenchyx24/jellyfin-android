package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.R

/**
 * Home logo: large on the hero, shrinks once the user scrolls. Pops in on launch and a light sweep
 * (tinted by the festive theme) crosses it every few seconds. The active festive emoji sits on top.
 */
@Composable
fun AnimatedNflixLogo(url: String?, compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val height by animateDpAsState(if (compact) 34.dp else 56.dp, spring(dampingRatio = 0.75f, stiffness = 300f), label = "logo-height")
    val width by animateDpAsState(if (compact) 104.dp else 172.dp, spring(dampingRatio = 0.75f, stiffness = 300f), label = "logo-width")
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f)) }

    val loop = rememberInfiniteTransition(label = "logo-loop")
    val sweep by loop.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5200
                -0.6f at 0 using LinearEasing
                -0.6f at 2600 using FastOutSlowInEasing
                1.6f at 3700
                1.6f at 5200
            },
        ),
        label = "logo-sweep",
    )
    val bob by loop.animateFloat(-3f, 3f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "emoji-bob")
    val event = SeasonalThemes.active

    Box(modifier.width(width + 18.dp).height(height + 14.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    val e = entrance.value
                    alpha = e.coerceIn(0f, 1f)
                    scaleX = 0.7f + 0.3f * e
                    scaleY = 0.7f + 0.3f * e
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Light sweep drawn only where the logo has pixels.
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), (event?.accent ?: Color.White).copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.25f, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        ) {
            val request = remember(context, url) { ImageRequest.Builder(context).data(url).crossfade(240).build() }
            val fallback = painterResource(R.drawable.nflix_logo)
            AsyncImage(
                model = request,
                contentDescription = "Nflix",
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.width(width).height(height),
            )
        }
        if (event != null) {
            Text(
                event.emoji,
                fontSize = if (compact) 16.sp else 26.sp,
                modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { translationY = bob; rotationZ = bob * 3f; alpha = entrance.value.coerceIn(0f, 1f) },
            )
        }
    }
}

/** Short festive message under the logo while a seasonal theme is active. */
@Composable
fun SeasonalGreeting(visible: Boolean) {
    val event = SeasonalThemes.active
    AnimatedVisibility(visible = visible && event != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val current = event ?: return@AnimatedVisibility
        Row(
            Modifier.padding(start = 18.dp, top = 2.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(current.accent.copy(alpha = 0.32f), current.deep.copy(alpha = 0.25f))))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.emoji)
            Spacer(Modifier.width(6.dp))
            Text(current.greeting, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
