package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.Item

/** Network image with crossfade and a soft placeholder. */
@Composable
fun NImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    description: String? = null,
    placeholderIcon: ImageVector? = Icons.Rounded.Movie,
    background: Color = Nflix.SurfaceHigh,
) {
    val context = LocalPlatformContext.current
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        if (url == null) {
            if (placeholderIcon != null) Icon(placeholderIcon, null, tint = Nflix.Muted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
        } else {
            val request = remember(context, url) { ImageRequest.Builder(context).data(url).crossfade(220).build() }
            AsyncImage(model = request, contentDescription = description, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/** User photo: HTTPS URL or inline data URI (decoded once), drawn over [fallback]. */
@Composable
fun AvatarImage(source: String?, modifier: Modifier = Modifier, fallback: @Composable () -> Unit = {}) {
    val context = LocalPlatformContext.current
    val model: Any? = remember(source) {
        when {
            source == null -> null
            source.startsWith("data:") -> org.jellyfin.mobile.nflix.MiraProfile.inlineBytes(source)
            else -> source
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        fallback()
        if (model != null) {
            val request = remember(context, model) { ImageRequest.Builder(context).data(model).crossfade(220).build() }
            AsyncImage(model = request, contentDescription = "Photo de profil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Press feedback: cards sink slightly while touched. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
}

fun Modifier.bottomScrim(start: Float = 0.35f, color: Color = Nflix.Night): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.7f), color), startY = size.height * start, endY = size.height)
    onDrawWithContent {
        drawContent()
        drawRect(brush)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Nflix.Red))
    }
}

@Composable
fun Badge(text: String, color: Color = Nflix.Red, modifier: Modifier = Modifier) {
    Surface(color = color, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Portrait poster card used across every list. */
@Composable
fun PosterCard(
    item: Item,
    imageUrl: String?,
    origin: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    aspect: Float = 2f / 3f,
    played: Boolean = item.userData?.played == true,
    favorite: Boolean = item.userData?.favorite == true,
    subtitle: String? = item.year?.toString(),
    typeLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width).pressable(onClick, onLongClick ?: { ItemMenu.show(item) })) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(8.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(8.dp))) {
            NImage(imageUrl, Modifier.fillMaxSize().sharedPoster(origin), description = item.name)
            if (typeLabel != null) Badge(typeLabel, Color.Black.copy(alpha = 0.78f), Modifier.align(Alignment.TopStart).padding(6.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (favorite) Icon(Icons.Rounded.Favorite, null, tint = Nflix.Red, modifier = Modifier.size(16.dp))
                if (played) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                else item.userData?.unplayed?.takeIf { it > 0 && item.type == "Series" }?.let { Badge(it.toString()) }
            }
            ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Nflix.Text)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 16:9 card for continue watching and episodes. */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    progress: Float,
    origin: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    onLongClick: (() -> Unit)? = null,
    typeLabel: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Column(modifier.width(width).pressable(onClick, onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(8.dp))) {
            NImage(imageUrl, Modifier.fillMaxSize().sharedPoster(origin), description = title)
            if (typeLabel != null) Badge(typeLabel, Color.Black.copy(alpha = 0.72f), Modifier.align(Alignment.TopStart).padding(6.dp))
            overlay()
            ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HorusStatusBadge(status: HorusStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        HorusStatus.Available -> "Disponible" to Nflix.Success
        HorusStatus.Partial -> "Partiel" to Nflix.Info
        HorusStatus.Pending -> "En attente" to Nflix.Warning
        HorusStatus.Processing -> "En cours" to Nflix.Info
        HorusStatus.Blocked -> "Bloqué" to Color.Gray
        else -> return
    }
    Badge(label, color.copy(alpha = 0.92f), modifier)
}

@Composable
fun HorusCard(media: HorusMedia, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 120.dp) {
    val square = media.type == org.jellyfin.mobile.nflix.data.HorusType.Music
    Column(modifier.width(width).pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(8.dp))) {
            NImage(media.poster, Modifier.fillMaxSize().sharedPoster("horus-${media.type.api}-${media.id}"), description = media.title)
            HorusStatusBadge(media.status, Modifier.align(Alignment.TopStart).padding(6.dp))
            Badge(media.type.label, Color.Black.copy(alpha = 0.55f), Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(media.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(media.subtitle, media.year).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Nflix.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (onAction != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action ?: "Tout voir", style = MaterialTheme.typography.labelMedium, color = Nflix.Text)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = Nflix.Red, modifier = Modifier.size(10.dp))
            }
        }
    }
}

/** Horizontal row with a header; items are keyed for stable recomposition. */
fun <T> LazyListScope.rowSection(
    key: String,
    title: String,
    items: List<T>,
    itemKey: (T) -> String,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val unique = items.distinctBy(itemKey)
    item(key = "header-$key", contentType = "header") { SectionHeader(title, onAction = onSeeAll) }
    item(key = "row-$key", contentType = "row") {
        val state = rememberLazyListState()
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(unique, key = itemKey, contentType = { "card-$key" }) { content(it) }
        }
    }
}

/** Shimmering skeleton while content loads. */
@Composable
fun Skeleton(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(
        modifier.clip(shape).drawWithCache {
            val brush = Brush.linearGradient(
                listOf(Nflix.SurfaceHigh, Color(0xFF26262F), Nflix.SurfaceHigh),
                start = Offset(size.width * x - size.width, 0f),
                end = Offset(size.width * x, size.height),
            )
            onDrawBehind { drawRect(brush) }
        },
    )
}

@Composable
fun SkeletonRow(count: Int = 4, width: Dp = 120.dp, aspect: Float = 2f / 3f) {
    Column {
        Skeleton(Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp).width(160.dp).height(18.dp), RoundedCornerShape(6.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(count) { Skeleton(Modifier.width(width).aspectRatio(aspect)) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Nflix.Muted, modifier = Modifier.size(42.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted)
    }
}

/** Fade + rise entrance for list content. */
@Composable
fun Modifier.appear(delayMs: Int = 0): Modifier {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * 36f }
}

fun formatRuntime(minutes: Int?): String? = minutes?.let { if (it >= 60) "${it / 60} h ${(it % 60).toString().padStart(2, '0')}" else "$it min" }

fun formatTicks(ticks: Long): String {
    val seconds = ticks / 10_000_000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
