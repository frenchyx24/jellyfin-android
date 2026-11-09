package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.ui.Nflix

/**
 * Seek bar of the full player: the fill glides between position updates, the bar thickens and
 * the thumb grows while dragging, and a soft glow breathes on the thumb while the music plays.
 */
@Composable
internal fun AnimatedSeekBar(fraction: Float, playing: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val smooth by animateFloatAsState(fraction.coerceIn(0f, 1f), if (dragging) spring(stiffness = Spring.StiffnessHigh) else tween(260, easing = LinearEasing), label = "seek-fill")
    val shown = if (dragging) dragValue else smooth
    val thickness by animateDpAsState(if (dragging) 10.dp else 5.dp, spring(dampingRatio = 0.55f), label = "seek-thick")
    val thumb by animateDpAsState(if (dragging) 11.dp else if (playing) 7.dp else 6.dp, spring(dampingRatio = 0.5f), label = "seek-thumb")
    val pulse = rememberInfiniteTransition(label = "seek-glow")
    val glow by pulse.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "seek-glow-a")
    Canvas(
        modifier.fillMaxWidth().height(28.dp)
            .semantics { contentDescription = "Progression" }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragging = true; dragValue = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { onSeek(dragValue); dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ -> change.consume(); dragValue = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        width = size.width
        val h = thickness.toPx()
        val top = (size.height - h) / 2
        val radius = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), Size(size.width, h), radius)
        val filled = size.width * shown
        if (filled > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Nflix.Red.copy(alpha = 0.75f), Nflix.Red, Color(0xFFFF5A6E)), endX = filled.coerceAtLeast(1f)),
                Offset(0f, top), Size(filled, h), radius,
            )
        }
        val center = Offset(filled.coerceIn(0f, size.width), size.height / 2)
        if (playing || dragging) drawCircle(Nflix.Red.copy(alpha = if (dragging) 0.35f else glow * 0.5f), thumb.toPx() * 2.1f, center)
        drawCircle(Color.White, thumb.toPx(), center)
    }
}

/** Icon button that squashes on press and springs back, with an optional sideways nudge on tap. */
@Composable
internal fun BouncyIconButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 48.dp, iconSize: Dp = 26.dp, tint: Color = Nflix.Text, nudge: Float = 0f, spinOnChange: Any? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "btn-scale")
    val color by animateColorAsState(tint, tween(250), label = "btn-tint")
    val shift = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(spinOnChange) {
        if (first) { first = false; return@LaunchedEffect }
        spin.snapTo(0f); spin.animateTo(360f, tween(450, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
                if (nudge != 0f) scope.launch { shift.snapTo(0f); shift.animateTo(nudge, tween(90)); shift.animateTo(0f, spring(dampingRatio = 0.4f)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = color, modifier = Modifier.size(iconSize).graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = shift.value; rotationZ = spin.value
        })
    }
}

/** Big play/pause disc: icon morphs with a twist, the disc pops on toggle and ripples while playing. */
@Composable
internal fun AnimatedPlayButton(playing: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(playing) { pop.snapTo(0.86f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "play-press")
    val ripple = rememberInfiniteTransition(label = "play-ripple")
    val wave by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "play-wave")
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (playing) {
            Canvas(Modifier.size(96.dp)) {
                drawCircle(Color.White.copy(alpha = 0.22f * (1f - wave)), radius = size.minDimension / 2 * (0.8f + 0.2f * wave))
            }
        }
        Box(
            Modifier.size(76.dp).graphicsLayer { val s = pop.value * pressScale; scaleX = s; scaleY = s }
                .clip(CircleShape).background(Color.White)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                playing,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.4f) + fadeIn(tween(150))) togetherWith
                        (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)))
                },
                label = "play-icon",
            ) { isPlaying ->
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Lecture", tint = Color.Black, modifier = Modifier.size(40.dp))
            }
        }
    }
}
