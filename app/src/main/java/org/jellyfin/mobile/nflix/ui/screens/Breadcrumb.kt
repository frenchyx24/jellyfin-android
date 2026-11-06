package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.VideoCatalog
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

/** One step of the trail: where it leads, and whether it is the page being shown. */
private data class Crumb(
    val key: String,
    val label: String,
    val image: String? = null,
    val icon: ImageVector? = null,
    val current: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * Breadcrumb of an episode or season page: « Séries › Supracell › Saison 1 › É3 ».
 * Every parent is one tap away; the current step opens a picker of its siblings
 * (other seasons, other episodes of the season). Glass over the artwork, solid once scrolled.
 */
@Composable
fun TitleBreadcrumb(item: Item, accent: Color, solid: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val motion = rememberMotionEnabled()
    val seriesId = item.seriesId
    val series = rememberLoad("crumb-series", seriesId, initial = seriesId?.let(deps.jellyfin::cachedItem)) {
        seriesId?.let { deps.jellyfin.item(it) }
    }.value
    val anime = series?.let(VideoCatalog::isAnime) == true
    var picker by remember(item.id) { mutableStateOf(false) }
    // Pages of this show below the target: they leave the history when going up.
    val below = setOfNotNull(item.id, item.seasonId).map { Route.Item(it).key }.toSet()

    val crumbs = buildList {
        add(Crumb("root", if (anime) "Animés" else "Séries", icon = Icons.Rounded.Movie, onClick = { navigator.selectTab(Route.Library) }))
        if (seriesId != null) {
            add(Crumb("series", series?.name ?: item.seriesName.orEmpty(), image = series?.let { deps.jellyfin.primary(it, 90) }, onClick = { navigator.up(Route.Item(seriesId), below) }))
        }
        when (item.type) {
            "Season" -> add(Crumb("season", item.name, current = true, onClick = { picker = !picker }))
            "Episode" -> {
                val seasonId = item.seasonId
                add(
                    Crumb(
                        "season",
                        item.seasonName ?: item.parentIndexNumber?.let { "Saison $it" } ?: "Saison",
                        onClick = seasonId?.let { id -> { navigator.up(Route.Item(id), setOf(Route.Item(item.id).key)) } },
                    ),
                )
                add(Crumb("episode", listOfNotNull(item.indexNumber?.let { "É$it" }, item.name.takeIf { it.isNotBlank() }).joinToString(" · "), current = true, onClick = { picker = !picker }))
            }
        }
    }

    val barColor by animateColorAsState(if (solid || picker) Nflix.Night.copy(alpha = 0.97f) else Color.Transparent, tween(280), label = "crumb-bar")
    Column(Modifier.fillMaxWidth().background(barColor).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { navigator.back() },
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = if (solid) 0f else 0.4f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Spacer(Modifier.width(6.dp))
            Trail(crumbs, accent, motion, picker, Modifier.weight(1f))
        }
        AnimatedVisibility(picker, enter = expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut(tween(160))) {
            SiblingPicker(item, accent) { picker = false }
        }
    }
}

@Composable
private fun Trail(crumbs: List<Crumb>, accent: Color, motion: Boolean, pickerOpen: Boolean, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    // The current step is always the one in view.
    LaunchedEffect(crumbs.size, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    // The "thread": a thin line that draws itself under the trail, from the root to the current step.
    val thread = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(crumbs.last().key, motion) { if (motion) thread.animateTo(1f, tween(900, 150, FastOutSlowInEasing)) else thread.snapTo(1f) }
    Box(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), accent.copy(alpha = 0.55f))), RoundedCornerShape(22.dp)),
    ) {
        Row(
            Modifier.horizontalScroll(scroll).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) Separator(index, motion, accent)
                CrumbChip(crumb, index, accent, motion, pickerOpen)
            }
        }
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp)) {
            val end = size.width * thread.value
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.9f)), endX = end.coerceAtLeast(1f)),
                Offset(0f, size.height / 2), Offset(end, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round,
            )
        }
    }
}

/** Fade + slide + spring for each step, one after the other. */
@Composable
private fun rememberEntrance(index: Int, motion: Boolean): Float {
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        kotlinx.coroutines.delay(70L * index)
        progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    return progress.value
}

@Composable
private fun Separator(index: Int, motion: Boolean, accent: Color) {
    val p = rememberEntrance(index, motion)
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
        tint = accent.copy(alpha = 0.85f),
        modifier = Modifier.size(18.dp).graphicsLayer { alpha = p.coerceIn(0f, 1f); rotationZ = (1f - p) * -90f; scaleX = 0.5f + 0.5f * p; scaleY = 0.5f + 0.5f * p },
    )
}

@Composable
private fun CrumbChip(crumb: Crumb, index: Int, accent: Color, motion: Boolean, pickerOpen: Boolean) {
    val p = rememberEntrance(index, motion)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.55f, 700f), label = "crumb-press")
    val shape = RoundedCornerShape(16.dp)
    // Current step: a light that runs along its border.
    val sweep = if (crumb.current && motion) {
        rememberInfiniteTransition(label = "crumb-sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else null
    val background by animateColorAsState(if (crumb.current) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f), tween(300), label = "crumb-bg")
    Row(
        Modifier
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f)
                translationX = (1f - p) * -48f
                val s = (0.85f + 0.15f * p) * press
                scaleX = s; scaleY = s
            }
            .widthIn(max = if (crumb.current) 230.dp else 160.dp)
            .clip(shape)
            .background(background)
            .then(
                if (crumb.current) {
                    // Drawn, not composed: the running light reads the animation in the draw phase only.
                    Modifier.drawWithContent {
                        drawContent()
                        val at = sweep?.value ?: 0.5f
                        val dim = accent.copy(alpha = 0.3f)
                        val brush = Brush.linearGradient(
                            0f to dim,
                            (at - 0.15f).coerceIn(0f, 1f) to dim,
                            at to Color.White.copy(alpha = if (sweep != null) 0.95f else 0.5f),
                            (at + 0.15f).coerceIn(0f, 1f) to dim,
                            1f to dim,
                            start = Offset.Zero, end = Offset(size.width, 0f),
                        )
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            brush, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(stroke),
                        )
                    }
                } else Modifier,
            )
            .then(
                crumb.onClick?.let { action ->
                    Modifier.clickable(interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = action)
                } ?: Modifier,
            )
            .semantics { contentDescription = if (crumb.current) "${crumb.label}, page actuelle" else "Aller à ${crumb.label}" }
            .padding(start = if (crumb.image != null || crumb.icon != null) 4.dp else 12.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            crumb.image != null -> {
                NImage(crumb.image, Modifier.size(24.dp).clip(CircleShape).border(1.dp, accent.copy(alpha = 0.7f), CircleShape), placeholderIcon = null)
                Spacer(Modifier.width(6.dp))
            }
            crumb.icon != null -> {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(crumb.icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            crumb.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (crumb.current) FontWeight.Bold else FontWeight.Medium,
            color = if (crumb.current) Color.White else Color.White.copy(alpha = 0.78f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (crumb.current) {
            val turn by animateFloatAsState(if (pickerOpen) 180f else 0f, spring(0.6f, 400f), label = "crumb-turn")
            Icon(Icons.Rounded.KeyboardArrowDown, if (pickerOpen) "Fermer" else "Changer", tint = Color.White, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
        }
    }
}

/** Other seasons of the show, or other episodes of the season, to jump sideways. */
@Composable
private fun SiblingPicker(item: Item, accent: Color, onPicked: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val seriesId = item.seriesId ?: return
    val episodeMode = item.type == "Episode"
    val siblings = rememberLoad("crumb-siblings", item.id) {
        if (episodeMode) item.seasonId?.let { deps.jellyfin.episodes(seriesId, it) }.orEmpty() else deps.jellyfin.seasons(seriesId)
    }.value
    val motion = rememberMotionEnabled()
    val listState = rememberLazyListState()
    LaunchedEffect(siblings) {
        val index = siblings?.indexOfFirst { it.id == item.id } ?: -1
        if (index > 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            if (episodeMode) "Épisodes de la saison" else "Saisons",
            style = MaterialTheme.typography.labelMedium, color = Nflix.Muted,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp),
        )
        if (siblings == null) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { org.jellyfin.mobile.nflix.ui.Skeleton(Modifier.width(if (episodeMode) 150.dp else 86.dp).height(if (episodeMode) 84.dp else 129.dp)) }
            }
            return@Column
        }
        LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(siblings, key = { _, it -> it.id }) { index, sibling ->
                val selected = sibling.id == item.id
                val p = rememberEntrance(index.coerceAtMost(8), motion)
                val played = sibling.userData?.played == true
                Column(
                    Modifier.width(if (episodeMode) 150.dp else 86.dp)
                        .graphicsLayer { alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * 30f }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onPicked()
                            if (!selected) navigator.replace(Route.Item(sibling.id))
                        },
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(if (episodeMode) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(12.dp))
                            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier),
                    ) {
                        NImage(if (episodeMode) deps.jellyfin.thumb(sibling, 320) ?: deps.jellyfin.primary(sibling, 320) else deps.jellyfin.primary(sibling, 200), Modifier.fillMaxSize())
                        if (played) Icon(Icons.Rounded.CheckCircle, "Vu", tint = Nflix.Success, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
                        if (episodeMode && sibling.progress > 0f && !played) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                                Box(Modifier.fillMaxWidth(sibling.progress).fillMaxHeight().background(accent))
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (episodeMode) listOfNotNull(sibling.indexNumber?.let { "É$it" }, sibling.name).joinToString(" · ") else sibling.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accent else Nflix.Text,
                        maxLines = if (episodeMode) 2 else 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}
