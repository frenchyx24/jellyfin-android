package org.jellyfin.mobile.nflix.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.jellyfin.mobile.nflix.data.AchievementBadge
import org.jellyfin.mobile.nflix.data.BadgeRecommendation
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.SeasonGlyphBurst
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Browser state: filter chips, rarity, sort, search, collapsed categories (survives rotation)
// ---------------------------------------------------------------------------------------------

internal val BadgeFilters = listOf("Tous", "Débloqués", "À faire", "Presque", "Récents")
internal val BadgeSorts = listOf("Catégorie", "Rareté", "Progression", "Date")
private val BadgeRarities = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic")
private const val SEP = "\u001F"

@Stable
internal class BadgeBrowserState(
    filter: String = "Tous",
    rarity: String? = null,
    sort: String = "Catégorie",
    query: String = "",
    collapsed: Set<String> = emptySet(),
) {
    var filter by mutableStateOf(filter)
    var rarity by mutableStateOf(rarity)
    var sort by mutableStateOf(sort)
    var query by mutableStateOf(query)
    var collapsed by mutableStateOf(collapsed)

    fun toggle(category: String) {
        collapsed = if (category in collapsed) collapsed - category else collapsed + category
    }

    /** Badges matching every active filter, in the chosen order. */
    fun visible(badges: List<AchievementBadge>, now: OffsetDateTime = OffsetDateTime.now()): List<AchievementBadge> {
        val needle = query.trim()
        return badges.filter { badge ->
            (rarity == null || badge.rarity == rarity) &&
                (needle.isEmpty() || badge.title.contains(needle, ignoreCase = true)) &&
                matchesBadgeFilter(badge, filter, now)
        }.sortedWith(badgeComparator(sort))
    }

    companion object {
        val Saver = listSaver<BadgeBrowserState, String>(
            save = { listOf(it.filter, it.rarity.orEmpty(), it.sort, it.query, it.collapsed.joinToString(SEP)) },
            restore = {
                BadgeBrowserState(it[0], it[1].ifEmpty { null }, it[2], it[3], it[4].split(SEP).filter { value -> value.isNotEmpty() }.toSet())
            },
        )
    }
}

@Composable
internal fun rememberBadgeBrowserState(): BadgeBrowserState = rememberSaveable(saver = BadgeBrowserState.Saver) { BadgeBrowserState() }

private fun unlockedTime(badge: AchievementBadge): OffsetDateTime? = badge.unlockedAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

internal fun matchesBadgeFilter(badge: AchievementBadge, filter: String, now: OffsetDateTime): Boolean = when (filter) {
    "Débloqués" -> badge.unlocked
    "À faire" -> !badge.unlocked
    "Presque" -> !badge.unlocked && badge.targetValue > 0 && badge.progress >= 0.75f
    "Récents" -> badge.unlocked && unlockedTime(badge)?.isAfter(now.minusDays(30)) == true
    else -> true
}

private fun badgeComparator(sort: String): Comparator<AchievementBadge> = when (sort) {
    "Rareté" -> compareByDescending<AchievementBadge> { rarityRank(it.rarity) }.thenByDescending { it.unlocked }.thenBy { it.title }
    "Progression" -> compareByDescending<AchievementBadge> { it.progress }.thenBy { it.title }
    "Date" -> compareByDescending<AchievementBadge> { it.unlocked }
        .thenByDescending { unlockedTime(it)?.toEpochSecond() ?: Long.MIN_VALUE }
        .thenByDescending { it.progress }.thenBy { it.title }
    else -> compareBy<AchievementBadge> { categoryName(it.category) }
        .thenByDescending { it.unlocked }.thenByDescending { it.progress }.thenBy { it.title }
}

private fun filterLabel(filter: String): String = if (filter == "Presque") "Presque (≥75 %)" else filter

// ---------------------------------------------------------------------------------------------
// Category grid: one flat LazyColumn section list, rows of three tiles (no nested vertical scroll)
// ---------------------------------------------------------------------------------------------

private const val COLUMNS = 3

internal fun LazyListScope.achievementBadgeBrowser(
    state: BadgeBrowserState,
    all: List<AchievementBadge>,
    visible: List<AchievementBadge>,
    summary: String,
    rarityStats: Map<String, Double>,
    equippedIds: Set<String>,
    freshIds: Set<String>,
    onOpen: (AchievementBadge) -> Unit,
) {
    item("filters") {
        SectionTitle("Tous les succès", summary)
        BadgeBrowserControls(state, all)
    }
    if (visible.isEmpty()) {
        item("empty") { Text("Aucun succès ne correspond à ces filtres.", color = Nflix.Muted, modifier = Modifier.padding(20.dp)) }
        return
    }
    val row: @Composable (Int, List<AchievementBadge>) -> Unit = { index, badges ->
        Row(Modifier.achievementAppear(index).padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            badges.forEach { badge ->
                BrowserBadgeTile(badge, rarityStats[badge.id], badge.id in equippedIds, badge.id in freshIds, Modifier.weight(1f)) { onOpen(badge) }
            }
            repeat(COLUMNS - badges.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (state.sort == "Catégorie") {
        val totals = all.groupBy { it.category }
        visible.groupBy { it.category }.forEach { (category, badges) ->
            val inCategory = totals[category].orEmpty()
            val collapsed = category in state.collapsed
            item("cat-h-$category") {
                CategoryHeader(categoryName(category), inCategory.count { it.unlocked }, inCategory.size, badges.size, collapsed) { state.toggle(category) }
            }
            if (!collapsed) {
                itemsIndexed(badges.chunked(COLUMNS), key = { _, chunk -> "bg-" + chunk.first().id }) { index, chunk -> row(index, chunk) }
            }
        }
    } else {
        item("flat-h") {
            Text(
                "${visible.size} succès · tri par ${state.sort.lowercase(Locale.FRANCE)}",
                style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        itemsIndexed(visible.chunked(COLUMNS), key = { _, chunk -> "bg-" + chunk.first().id }) { index, chunk -> row(index, chunk) }
    }
}

@Composable
private fun BadgeBrowserControls(state: BadgeBrowserState, all: List<AchievementBadge>) {
    val now = remember { OffsetDateTime.now() }
    OutlinedTextField(
        state.query, { state.query = it.take(60) }, singleLine = true,
        label = { Text("Rechercher un succès") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BadgeFilters, key = { "f-$it" }) { filter ->
            val count = all.count { matchesBadgeFilter(it, filter, now) }
            NChip("${filterLabel(filter)} · $count", state.filter == filter) { state.filter = filter }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var rarityOpen by remember { mutableStateOf(false) }
        Box {
            NChip("Rareté : ${state.rarity?.let { rarityName(it) } ?: "toutes"}", state.rarity != null, Icons.Rounded.FilterList) { rarityOpen = true }
            DropdownMenu(expanded = rarityOpen, onDismissRequest = { rarityOpen = false }, containerColor = Nflix.SurfaceHigh) {
                DropdownMenuItem(text = { Text("Toutes les raretés") }, onClick = { state.rarity = null; rarityOpen = false })
                BadgeRarities.forEach { rarity ->
                    DropdownMenuItem(
                        text = { Text(rarityName(rarity), color = rarityColor(rarity), fontWeight = if (state.rarity == rarity) FontWeight.Bold else null) },
                        onClick = { state.rarity = rarity; rarityOpen = false },
                    )
                }
            }
        }
        var sortOpen by remember { mutableStateOf(false) }
        Box {
            NChip("Tri : ${state.sort.lowercase(Locale.FRANCE)}", state.sort != "Catégorie", Icons.AutoMirrored.Rounded.Sort) { sortOpen = true }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }, containerColor = Nflix.SurfaceHigh) {
                BadgeSorts.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort, fontWeight = if (state.sort == sort) FontWeight.Bold else null) },
                        onClick = { state.sort = sort; sortOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(label: String, unlocked: Int, total: Int, shown: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val progress = if (total > 0) unlocked.toFloat() / total else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$unlocked / $total", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
            if (collapsed && shown > 0) Text(" · $shown masqué(s)", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            Icon(if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (collapsed) "Déplier" else "Replier", tint = Nflix.Muted)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Nflix.Surface)) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(if (progress >= 1f) Nflix.Success else Nflix.Red))
        }
    }
}

@Composable
private fun BrowserBadgeTile(badge: AchievementBadge, members: Double?, equipped: Boolean, fresh: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = rarityColor(badge.rarity)
    val shape = RoundedCornerShape(16.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Nflix.Surface)
                .then(if (equipped) Modifier.border(1.5.dp, color, shape) else Modifier)
                .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HoloMedal(badge, 54.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                badge.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            if (badge.unlocked) {
                Text(members?.let { String.format(Locale.FRANCE, "%.0f %% l'ont", it) } ?: rarityName(badge.rarity), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            } else if (badge.targetValue > 0) {
                AchievementProgressBar(badge.progress, color, Modifier.fillMaxWidth().padding(horizontal = 6.dp), height = 4.dp)
                Spacer(Modifier.height(2.dp))
                Text("${(badge.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            } else {
                Text("Verrouillé", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        }
        if (fresh) NewBadgeTag(Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Holographic medal: a sheen sweeps across legendary/mythic medals following the device tilt
// ---------------------------------------------------------------------------------------------

/** Device tilt in [-1, 1] on both axes, smoothed and throttled to ~20 Hz; null without a sensor. */
@Composable
internal fun rememberDeviceTilt(): State<Offset?> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf<Offset?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.timestamp - last < 50_000_000L) return
                last = event.timestamp
                val raw = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    Offset(-event.values[0] / 6f, event.values[2] / 9.81f - 0.5f)
                } else {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    Offset(orientation[2] / 0.6f, (orientation[1] + 0.7f) / 0.6f)
                }
                val target = Offset(raw.x.coerceIn(-1f, 1f), raw.y.coerceIn(-1f, 1f))
                val previous = tilt.value
                tilt.value = if (previous == null) target else previous + (target - previous) * 0.3f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}

/** Holographic sheen over a round medal; tilt-driven, slow shimmer without sensor, off with reduced motion. */
@Composable
internal fun Modifier.holoSheen(enabled: Boolean, tint: Color): Modifier {
    if (!enabled || rememberAchievementReducedMotion()) return this
    val tilt = rememberDeviceTilt()
    val time = rememberInfiniteTransition(label = "holo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5600, easing = LinearEasing), RepeatMode.Restart), label = "holo-t",
    )
    return drawWithContent {
        drawContent()
        val t = tilt.value
        val phase = t?.let { (it.x + 1f) / 2f } ?: time.value
        val slant = t?.y ?: 0.2f
        val w = size.width
        val h = size.height
        val center = -0.6f * w + phase * 2.2f * w
        val brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.35f to tint.copy(alpha = 0.18f),
            0.46f to Color(0xFF8FF3FF).copy(alpha = 0.38f),
            0.52f to Color.White.copy(alpha = 0.55f),
            0.58f to Color(0xFFFF9BEA).copy(alpha = 0.34f),
            0.7f to tint.copy(alpha = 0.12f),
            1f to Color.Transparent,
            start = Offset(center - 0.5f * w, -0.2f * h * slant),
            end = Offset(center + 0.5f * w, h * (1f + 0.4f * slant)),
        )
        drawCircle(brush, radius = size.minDimension / 2f * 0.94f, blendMode = BlendMode.Screen)
    }
}

/** [Medallion] with the holographic sheen on unlocked legendary and mythic badges. */
@Composable
internal fun HoloMedal(badge: AchievementBadge, size: Dp, showLocked: Boolean = true) {
    val holo = (badge.unlocked || !showLocked) && rarityRank(badge.rarity) >= 4
    Box(Modifier.size(size).holoSheen(holo, rarityColor(badge.rarity)), contentAlignment = Alignment.Center) {
        Medallion(badge, size, showLocked)
    }
}

// ---------------------------------------------------------------------------------------------
// Full-screen badge sheet, swipe between the badges of the current list
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AchievementBadgeSheet(
    badges: List<AchievementBadge>,
    startId: String,
    rarityStats: Map<String, Double>,
    recommendations: List<BadgeRecommendation>,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPageChange: () -> Unit = {},
    actions: @Composable ColumnScope.(AchievementBadge) -> Unit,
) {
    if (badges.isEmpty()) return
    val start = badges.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { badges.size }
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect { if (!first) onPageChange(); first = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { badges[it].id }) { page ->
                BadgePage(badges[page], rarityStats[badges[page].id], recommendations.firstOrNull { it.badgeId == badges[page].id }, onOpenItem, actions)
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }
                Spacer(Modifier.weight(1f))
                if (badges.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${badges.size}", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgePage(
    badge: AchievementBadge,
    members: Double?,
    recommendation: BadgeRecommendation?,
    onOpenItem: (String) -> Unit,
    actions: @Composable ColumnScope.(AchievementBadge) -> Unit,
) {
    val reduced = rememberAchievementReducedMotion()
    val color = rarityColor(badge.rarity)
    val pop = remember(badge.id) { Animatable(if (reduced) 1f else 0.35f) }
    val spin = remember(badge.id) { Animatable(if (reduced) 0f else -40f) }
    LaunchedEffect(badge.id) {
        if (!reduced) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(badge.id) {
        if (!reduced) spin.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val glow = if (reduced) null else rememberInfiniteTransition(label = "glow").animateFloat(
        0.65f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow-a",
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = glow?.value ?: 1f }.background(
                    Brush.radialGradient(listOf(color.copy(alpha = if (badge.unlocked) 0.55f else 0.22f), color.copy(alpha = 0.10f), Color.Transparent)),
                ),
            )
            Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = spin.value }, contentAlignment = Alignment.Center) {
                if (!badge.unlocked && badge.targetValue > 0) {
                    AchievementProgressRing(badge.progress, color, 184.dp, stroke = 8.dp) { HoloMedal(badge, 140.dp) }
                } else {
                    HoloMedal(badge, 160.dp)
                }
            }
        }
        Text(badge.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        RarityPill(badge.rarity, badge.rarityLabel)
        Spacer(Modifier.height(12.dp))
        Text(badge.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Nflix.Text.copy(alpha = 0.9f))
        Spacer(Modifier.height(18.dp))
        if (badge.unlocked) {
            Text(
                badge.unlockedAt?.let { "Obtenu le ${achievementDate(it)}" } ?: "Obtenu",
                color = Nflix.Success, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
        } else if (badge.targetValue > 0) {
            AchievementProgressBar(badge.progress, color, Modifier.fillMaxWidth(), height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${badge.currentValue} / ${badge.targetValue} · ${(badge.progress * 100).toInt()} %",
                style = MaterialTheme.typography.labelLarge, color = Nflix.Muted,
            )
        } else {
            Text("Pas encore obtenu", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
        }
        members?.let {
            Spacer(Modifier.height(6.dp))
            Text(String.format(Locale.FRANCE, "%.1f %% des membres l'ont", it), style = MaterialTheme.typography.labelLarge, color = color)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Catégorie", categoryName(badge.category), Modifier.weight(1f))
            StatTile("Rareté", badge.rarityLabel ?: rarityName(badge.rarity), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(16.dp)) {
            Text("Comment l'obtenir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(badge.description, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.85f))
            if (!badge.unlocked && badge.targetValue > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Objectif : ${badge.targetValue} · encore ${(badge.targetValue - badge.currentValue).coerceAtLeast(0)}",
                    style = MaterialTheme.typography.labelMedium, color = Nflix.Muted,
                )
            }
        }
        val items = recommendation?.items.orEmpty()
        if (!badge.unlocked && items.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Titres conseillés", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { "rec-${it.id}" }) { item ->
                    Column(
                        Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(Nflix.Surface)
                            .clickable { onOpenItem(item.id) }.padding(12.dp),
                    ) {
                        Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(
                            item.year.takeIf { it > 0 }?.toString(),
                            item.runTimeMinutes.takeIf { it > 0 }?.let { "$it min" },
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        actions(badge)
    }
}

// ---------------------------------------------------------------------------------------------
// Unlock celebration: full-screen overlay, rarity confetti, medal pop, haptics, share
// ---------------------------------------------------------------------------------------------

internal fun rarityPalette(color: Color): List<Color> =
    listOf(color, lerp(color, Color.White, 0.45f), lerp(color, Color.Black, 0.25f), Color.White, lerp(color, Color(0xFFFFE08A), 0.5f))

internal fun shareBadge(context: Context, badge: AchievementBadge) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "J'ai débloqué « ${badge.title} » sur Nflix !")
    runCatching { context.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun AchievementUnlockOverlay(badges: List<AchievementBadge>, onOpen: (AchievementBadge) -> Unit, onDismiss: () -> Unit) {
    val hero = badges.firstOrNull() ?: return
    val reduced = rememberAchievementReducedMotion()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val color = rarityColor(hero.rarity)
    val fade = remember { Animatable(0f) }
    val pop = remember { Animatable(if (reduced) 1f else 0.1f) }
    val spin = remember { Animatable(if (reduced) 0f else -120f) }
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(hero.id) { fade.animateTo(1f, tween(if (reduced) 350 else 220)) }
    LaunchedEffect(hero.id) {
        // Short three-beat pattern: strong, light, light.
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        if (!reduced) {
            delay(140)
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            delay(110)
            runCatching {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
    LaunchedEffect(hero.id) { if (!reduced) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(hero.id) { if (!reduced) spin.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)) }
    LaunchedEffect(hero.id) { if (!reduced) reveal.animateTo(1f, tween(480, delayMillis = 320)) }
    val rays = if (reduced) null else rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "rays-r",
    )
    val confetti = remember(color) { rarityPalette(color) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.92f))))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            if (rays != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val origin = Offset(size.width / 2f, size.height * 0.34f)
                    val length = size.maxDimension
                    rotate(rays.value, origin) {
                        for (index in 0 until 12) {
                            val a = (index * 30.0) * PI / 180.0
                            val b = a + 9.0 * PI / 180.0
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(origin.x, origin.y)
                                lineTo(origin.x + (cos(a) * length).toFloat(), origin.y + (sin(a) * length).toFloat())
                                lineTo(origin.x + (cos(b) * length).toFloat(), origin.y + (sin(b) * length).toFloat())
                                close()
                            }
                            drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), origin, length * 0.6f))
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.12f), Color.Transparent))))
                    Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationY = spin.value }) {
                        HoloMedal(hero, 140.dp, showLocked = false)
                    }
                }
                Column(
                    Modifier.graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 28.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (badges.size > 1) "${badges.size} nouveaux succès !" else "Succès débloqué !",
                        style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(hero.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    RarityPill(hero.rarity, hero.rarityLabel)
                    Spacer(Modifier.height(10.dp))
                    Text(hero.description, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val others = badges.drop(1).take(5)
                    AnimatedVisibility(others.isNotEmpty()) {
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            others.forEach { Medallion(it, 40.dp, showLocked = false) }
                            if (badges.size > 6) Text("+${badges.size - 6}", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { onOpen(hero) },
                        colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Voir le succès", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { shareBadge(context, hero) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp), tint = Nflix.Text); Spacer(Modifier.width(8.dp))
                        Text("Partager", color = Nflix.Text)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Continuer", color = Nflix.Text) }
                }
            }
            if (!reduced) {
                AchievementConfetti(trigger = 1, colors = confetti, modifier = Modifier.fillMaxSize(), originY = 0.34f, count = 90)
                SeasonGlyphBurst(trigger = 1, modifier = Modifier.fillMaxSize(), originY = 0.34f)
            }
        }
    }
}
