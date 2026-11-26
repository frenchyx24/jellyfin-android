package org.jellyfin.mobile.nflix.ui.screens

import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.*
// @@IMPORTS@@
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale

private val HintGold = Color(0xFFFFC35A)
private val DoneGreen = Color(0xFF4CD964)

// ---------------------------------------------------------------------------------------------
// Secret badge hints
// ---------------------------------------------------------------------------------------------

/** Hints of hidden badges, by badge id; refreshed by the Succès tab. */
internal object SecretHintStore {
    val hints = mutableStateMapOf<String, SecretHint>()
    fun replace(list: List<SecretHint>) {
        hints.clear()
        list.forEach { hints[it.id] = it }
    }
}

/** Hint line with 1–3 lit dots for a locked hidden badge; nothing otherwise. */
@Composable
internal fun SecretHintLine(badge: AchievementBadge, large: Boolean = false) {
    if (badge.unlocked) return
    val hint = SecretHintStore.hints[badge.id] ?: return
    Column(Modifier.padding(top = if (large) 12.dp else 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hint.hint, style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic, color = Nflix.Muted, textAlign = TextAlign.Center,
            maxLines = if (large) 4 else 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        HintDots(hint.level, if (large) 8.dp else 5.dp)
    }
}

@Composable
private fun HintDots(level: Int, dot: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(dot * 0.6f), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i -> Box(Modifier.size(dot).clip(CircleShape).background(if (i < level) HintGold else Color.White.copy(alpha = 0.15f))) }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------------------------

@Composable
internal fun rememberSeasonCountdown(iso: String?): String? {
    val target = remember(iso) { iso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } } ?: return null
    val now by produceState(OffsetDateTime.now(), target) {
        while (true) {
            value = OffsetDateTime.now()
            delay(30_000)
        }
    }
    return seasonCountdownLabel(Duration.between(now, target))
}

internal fun seasonCountdownLabel(left: Duration): String {
    if (left.isNegative || left.isZero) return "Terminé"
    val days = left.toDays()
    val hours = left.toHours() % 24
    val minutes = left.toMinutes() % 60
    return when {
        days > 0 -> "$days j $hours h"
        hours > 0 -> "$hours h ${"%02d".format(minutes)}"
        else -> "${maxOf(1L, minutes)} min"
    }
}

private fun endsLabel(left: String) = if (left == "Terminé") "Saison terminée" else "Fin dans $left"

@Composable
private fun SeasonProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val reduced = rememberAchievementReducedMotion()
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), if (reduced) snap() else tween(900), label = "season-bar")
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
        if (shown > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))),
            )
        }
    }
}

@Composable
private fun CollectionRing(progress: Float, color: Color, diameter: Dp, stroke: Dp = 3.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arc, style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Daily challenge
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DailyChallengeSection(hub: AchievementHub, revision: Int) {
    val challenge by produceState<DailyChallenge?>(null, hub, revision) { value = runCatching { hub.dailyChallenge() }.getOrNull() }
    challenge?.let { DailyChallengeCard(it) }
}

@Composable
private fun DailyChallengeCard(challenge: DailyChallenge) {
    val accent = if (challenge.completed) DoneGreen else HintGold
    val left = rememberSeasonCountdown(challenge.expiresAt)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), Nflix.Surface)))
            .border(1.dp, accent.copy(alpha = 0.45f), shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DÉFI DU JOUR", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            if (left != null && !challenge.completed) {
                Text(if (left == "Terminé") "Expiré" else "Expire dans $left", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (challenge.completed) Icons.Rounded.Check else achievementIcon(challenge.icon), null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (challenge.description.isNotBlank()) {
                    Text(challenge.description, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "+${challenge.rewardXp} XP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (challenge.completed) {
            Text("✓ Défi relevé ! Revenez demain pour un nouveau défi.", style = MaterialTheme.typography.bodySmall, color = DoneGreen, fontWeight = FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeasonProgressBar(challenge.progress.toFloat() / challenge.target, accent, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("${challenge.progress.coerceAtMost(challenge.target)}/${challenge.target}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Season pass
// ---------------------------------------------------------------------------------------------

private enum class SeasonKind { Autumn, Winter, Spring, Summer }

private val wordEte = Regex("(^|[-_ ])(ete|été)($|[-_ ])")

private fun seasonKind(key: String): SeasonKind? {
    val k = key.lowercase(Locale.ROOT)
    return when {
        "automne" in k || "autumn" in k -> SeasonKind.Autumn
        "hiver" in k || "winter" in k -> SeasonKind.Winter
        "printemps" in k || "spring" in k -> SeasonKind.Spring
        wordEte.containsMatchIn(k) || "summer" in k -> SeasonKind.Summer
        else -> null
    }
}

private fun seasonPalette(kind: SeasonKind): List<Color> = when (kind) {
    SeasonKind.Autumn -> listOf(Color(0xFFB5481B), Color(0xFFFFA24C), Color(0xFF7A3B12))
    SeasonKind.Winter -> listOf(Color(0xFF4FA3E0), Color(0xFFE8F6FF), Color(0xFF2D6FA8))
    SeasonKind.Spring -> listOf(Color(0xFF6CC56C), Color(0xFFFFB3D1), Color(0xFF3E8E4C))
    SeasonKind.Summer -> listOf(Color(0xFFFFB627), Color(0xFF4FD1C5), Color(0xFFFF7A3D))
}

internal fun seasonAccent(id: String): Color = seasonKind(id)?.let { seasonPalette(it)[1] } ?: Nflix.Red

@Composable
internal fun SeasonPassSection(hub: AchievementHub, revision: Int, onOpen: () -> Unit) {
    val pass by produceState<SeasonPass?>(null, hub, revision) { value = runCatching { hub.seasonPass() }.getOrNull() }
    pass?.let { SeasonPassCard(it, onOpen) }
}

@Composable
private fun SeasonPassCard(pass: SeasonPass, onOpen: () -> Unit) {
    val color = seasonAccent(pass.id)
    val left = rememberSeasonCountdown(pass.endsAt)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.22f), Nflix.Surface, Nflix.Surface)))
            .border(1.dp, color.copy(alpha = 0.5f), shape).clickable(onClick = onOpen).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PASSE DE SAISON", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(pass.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Palier ${pass.currentTier.coerceAtMost(pass.maxTier)}/${pass.maxTier}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (left != null) Text(endsLabel(left), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        SeasonProgressBar(pass.tierProgress, color, Modifier.fillMaxWidth(), 8.dp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (pass.complete) "Passe complété ✓" else "${pass.xpIntoTier}/${pass.xpPerTier} XP vers le palier ${pass.currentTier + 1}",
                style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.weight(1f),
            )
            Text("Récompenses ›", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Full screen « Passe de saison »: the 30-tier track with its rewards. */
@Composable
fun SeasonPassScreen() {
    val deps = LocalDeps.current
    val hub = remember(deps.api) { AchievementHub(deps.api) }
    var pass by remember { mutableStateOf<SeasonPass?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(hub) {
        pass = runCatching { hub.seasonPass() }.getOrNull()
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Nflix.Night).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 60.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Passe de saison", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        val current = pass
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) }
            current == null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Aucun passe de saison en cours.", color = Nflix.Muted, textAlign = TextAlign.Center)
            }
            else -> SeasonPassContent(current)
        }
    }
}

@Composable
private fun SeasonPassContent(pass: SeasonPass) {
    val color = seasonAccent(pass.id)
    val left = rememberSeasonCountdown(pass.endsAt)
    val byTier = remember(pass) { pass.tiers.associateBy { it.tier } }
    val reached = pass.currentTier.coerceIn(0, pass.maxTier)
    val rewards = pass.tiers.count { it.reward != null }
    val unlocked = pass.tiers.count { it.reward != null && (it.unlocked || it.tier <= reached) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Nflix.Surface)))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pass.name.uppercase(Locale.FRANCE), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("$reached", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text("palier${if (reached > 1) "s" else ""} sur ${pass.maxTier}", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            Spacer(Modifier.height(14.dp))
            SeasonProgressBar(pass.tierProgress, color, Modifier.fillMaxWidth(), 10.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (pass.complete) "Tous les paliers sont atteints ✓" else "${pass.xpIntoTier}/${pass.xpPerTier} XP vers le palier ${reached + 1}",
                style = MaterialTheme.typography.labelMedium, color = Nflix.Muted,
            )
            Text("${pass.seasonXp} XP de saison", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            if (left != null) {
                Spacer(Modifier.height(10.dp))
                Text(endsLabel(left), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        Text("Récompenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        if (rewards > 0) {
            Text("$unlocked/$rewards débloquées", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp))
        }
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (reached - 2).coerceAtLeast(0))
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            items((1..pass.maxTier).toList(), key = { it }) { tier ->
                val entry = byTier[tier]
                TierNode(
                    tier = tier, reward = entry?.reward,
                    unlocked = tier <= reached || entry?.unlocked == true,
                    current = tier == reached.coerceAtLeast(1),
                    nextLit = tier + 1 <= reached,
                    first = tier == 1, last = tier == pass.maxTier, color = color,
                )
            }
        }
        Text(
            "Chaque succès, quête et défi du jour rapporte de l'XP de saison. Les cadres et titres gagnés se trouvent dans « Personnaliser mon profil ».",
            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun TierNode(tier: Int, reward: SeasonReward?, unlocked: Boolean, current: Boolean, nextLit: Boolean, first: Boolean, last: Boolean, color: Color) {
    val reduced = rememberAchievementReducedMotion()
    val pulse = if (current && !reduced) {
        rememberInfiniteTransition(label = "tier").animateFloat(1f, 1.09f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "tier-pulse")
    } else null
    val dim = Color.White.copy(alpha = 0.12f)
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val y = this.size.height / 2
                val w = 4.dp.toPx()
                if (!first) drawLine(if (unlocked) color else dim, Offset(0f, y), Offset(this.size.width / 2, y), strokeWidth = w)
                if (!last) drawLine(if (nextLit) color else dim, Offset(this.size.width / 2, y), Offset(this.size.width, y), strokeWidth = w)
            }
            Box(
                Modifier.graphicsLayer { val s = pulse?.value ?: 1f; scaleX = s; scaleY = s }
                    .size(if (current) 44.dp else 34.dp)
                    .then(if (current) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clip(CircleShape).background(if (unlocked) color else Nflix.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text("$tier", fontWeight = FontWeight.Bold, fontSize = if (current) 16.sp else 13.sp, color = if (unlocked) Color.Black else Nflix.Muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (reward != null) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(84.dp).clip(shape).background(if (unlocked) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                    .border(1.dp, if (unlocked) color.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), shape)
                    .alpha(if (unlocked) 1f else 0.6f).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (reward.type == "frame") FrameSwatch(reward.id, 28.dp)
                else Icon(Icons.Rounded.Star, null, tint = if (unlocked) color else Nflix.Muted, modifier = Modifier.size(24.dp))
                Text(if (reward.type == "frame") "Cadre" else "Titre", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(reward.label, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = if (unlocked) Nflix.Text else Nflix.Muted)
                if (!unlocked) Icon(Icons.Rounded.Lock, "Verrouillé", tint = Nflix.Muted, modifier = Modifier.padding(top = 2.dp).size(12.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collections
// ---------------------------------------------------------------------------------------------

@Composable
internal fun CollectionsSection(hub: AchievementHub, revision: Int, onOpen: (String) -> Unit) {
    val list by produceState(emptyList<CollectionProgress>(), hub, revision) {
        value = runCatching { hub.collections() }.getOrDefault(value).sortedWith(compareBy({ it.completed }, { -it.progress }))
    }
    if (list.isEmpty()) return
    Column(Modifier.padding(top = 12.dp)) {
        SectionTitle("Collections", "${list.count { it.completed }}/${list.size} complétées")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { c -> CollectionCard(c) { onOpen(c.id) } }
        }
    }
}

@Composable
private fun CollectionCard(collection: CollectionProgress, onClick: () -> Unit) {
    val deps = LocalDeps.current
    val color = if (collection.completed) DoneGreen else Nflix.Red
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(124.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Nflix.SurfaceHigh)
                .then(if (collection.completed) Modifier.border(2.dp, DoneGreen, shape) else Modifier),
        ) {
            coil3.compose.AsyncImage(
                model = deps.api.imageUrl(collection.posterItemId ?: collection.id, maxWidth = 300),
                contentDescription = collection.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f))) {
                CollectionRing(collection.progress, color, 34.dp) {
                    if (collection.completed) Icon(Icons.Rounded.Check, "Complétée", tint = DoneGreen, modifier = Modifier.size(16.dp))
                    else Text("${(collection.progress * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(collection.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${collection.watched}/${collection.total} vus", style = MaterialTheme.typography.labelSmall, color = if (collection.completed) DoneGreen else Nflix.Muted)
    }
}

// ---------------------------------------------------------------------------------------------
// Profile frames and titles
// ---------------------------------------------------------------------------------------------

private enum class FrameKind { Bronze, Silver, Gold, Platinum, Diamond, Autumn, Winter, Spring, Summer }

private class FrameStyle(val colors: List<Color>, val kind: FrameKind)

private val wordGold = Regex("(^|[-_ ])(or|gold)($|[-_ ])")

private fun cosmeticFrameStyle(id: String): FrameStyle {
    val k = id.lowercase(Locale.ROOT)
    seasonKind(k)?.let { season ->
        val kind = when (season) {
            SeasonKind.Autumn -> FrameKind.Autumn
            SeasonKind.Winter -> FrameKind.Winter
            SeasonKind.Spring -> FrameKind.Spring
            SeasonKind.Summer -> FrameKind.Summer
        }
        return FrameStyle(seasonPalette(season), kind)
    }
    val kind = when {
        "diamant" in k || "diamond" in k -> FrameKind.Diamond
        "platine" in k || "platinum" in k -> FrameKind.Platinum
        wordGold.containsMatchIn(k) -> FrameKind.Gold
        "argent" in k || "silver" in k -> FrameKind.Silver
        "bronze" in k -> FrameKind.Bronze
        else -> Regex("\\d+").findAll(k).lastOrNull()?.value?.toIntOrNull()?.let { n ->
            when {
                n < 10 -> FrameKind.Bronze
                n < 20 -> FrameKind.Silver
                n < 35 -> FrameKind.Gold
                n < 50 -> FrameKind.Platinum
                else -> FrameKind.Diamond
            }
        } ?: FrameKind.Gold
    }
    val colors = when (kind) {
        FrameKind.Bronze -> listOf(Color(0xFF8C5A2B), Color(0xFFE3A869), Color(0xFF6B3E1C))
        FrameKind.Silver -> listOf(Color(0xFF9EA7B3), Color(0xFFF2F5F8), Color(0xFF6C7580))
        FrameKind.Gold -> listOf(Color(0xFFB8860B), Color(0xFFFFE08A), Color(0xFFC9971E))
        FrameKind.Platinum -> listOf(Color(0xFFB9D3DF), Color(0xFFFFFFFF), Color(0xFF8FB3C4))
        else -> listOf(Color(0xFF6FE7FF), Color(0xFFE6FBFF), Color(0xFFB98CFF))
    }
    return FrameStyle(colors, kind)
}

/** Original ring drawn around the avatar: metal tiers by keyword, seasonal ones in the season colours. */
private fun DrawScope.drawCosmeticFrame(style: FrameStyle, spin: Float) {
    val d = size.minDimension
    val c = center
    val r = d * 0.415f
    val w = d * 0.06f
    val o = d * 0.024f
    val deep = style.colors.first()
    val light = style.colors.getOrElse(1) { Color.White }
    val top = Offset(c.x, c.y - r)
    fun around(count: Int, offset: Float = 0f, block: DrawScope.() -> Unit) = repeat(count) { i -> rotate(offset + 360f * i / count, c) { block() } }
    rotate(spin, c) { drawCircle(Brush.sweepGradient(style.colors + style.colors.first(), c), r, c, style = Stroke(w)) }
    drawCircle(Color.White.copy(alpha = 0.35f), r + w / 2, c, style = Stroke(d * 0.006f))
    drawCircle(Color.Black.copy(alpha = 0.35f), r - w / 2, c, style = Stroke(d * 0.006f))
    when (style.kind) {
        FrameKind.Bronze -> around(4) {
            drawCircle(light, o * 1.1f, top)
            drawCircle(deep, o * 0.55f, top)
        }
        FrameKind.Silver -> around(8, 22.5f) {
            drawLine(light, Offset(c.x, c.y - r - w * 0.7f), Offset(c.x, c.y - r + w * 0.7f), strokeWidth = d * 0.012f, cap = StrokeCap.Round)
        }
        FrameKind.Gold -> {
            around(12, 15f) { drawCircle(light, o * 0.7f, top) }
            drawCircle(Color(0xFFE0474C), o * 1.3f, top)
            drawCircle(light, o * 1.3f, top, style = Stroke(d * 0.008f))
        }
        FrameKind.Platinum -> {
            drawCircle(light.copy(alpha = 0.7f), r + w * 1.1f, c, style = Stroke(d * 0.01f))
            around(6, 30f) { drawCircle(Color.White, o * 0.8f, Offset(c.x, c.y - r - w * 1.1f)) }
        }
        FrameKind.Diamond -> around(8) {
            val gem = Path().apply {
                moveTo(c.x, c.y - r - o * 2.1f)
                lineTo(c.x + o * 1.3f, c.y - r)
                lineTo(c.x, c.y - r + o * 2.1f)
                lineTo(c.x - o * 1.3f, c.y - r)
                close()
            }
            drawPath(gem, Brush.linearGradient(listOf(Color.White, style.colors.last()), Offset(c.x - o, c.y - r - o), Offset(c.x + o, c.y - r + o)))
            drawPath(gem, Color.White.copy(alpha = 0.8f), style = Stroke(d * 0.005f))
        }
        FrameKind.Autumn -> around(7, 10f) {
            drawOval(light, Offset(c.x - o * 0.8f, c.y - r - o * 1.6f), Size(o * 1.6f, o * 3.2f))
            drawLine(deep, Offset(c.x, c.y - r - o * 1.4f), Offset(c.x, c.y - r + o * 1.4f), strokeWidth = d * 0.005f)
        }
        FrameKind.Winter -> around(6, 30f) {
            repeat(3) { k ->
                rotate(60f * k, top) { drawLine(Color.White, Offset(top.x, top.y - o * 1.5f), Offset(top.x, top.y + o * 1.5f), strokeWidth = d * 0.007f, cap = StrokeCap.Round) }
            }
        }
        FrameKind.Spring -> around(6, 30f) {
            repeat(5) { k -> rotate(72f * k, top) { drawCircle(light, o * 0.65f, Offset(top.x, top.y - o * 0.85f)) } }
            drawCircle(Color(0xFFFFE27A), o * 0.55f, top)
        }
        FrameKind.Summer -> around(10) {
            val ray = Path().apply {
                moveTo(c.x - o, c.y - r - w / 2)
                lineTo(c.x, c.y - r - w / 2 - o * 2.2f)
                lineTo(c.x + o, c.y - r - w / 2)
                close()
            }
            drawPath(ray, light)
        }
    }
}

@Composable
private fun rememberFrameSpin(): State<Float> {
    if (rememberAchievementReducedMotion()) return remember { mutableStateOf(0f) }
    return rememberInfiniteTransition(label = "frame").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "frame-spin")
}

/** [content] (an avatar of [avatarSize]) inside the frame [frameId]; plain content without a frame. */
@Composable
internal fun CosmeticFramedAvatar(frameId: String?, avatarSize: Dp, content: @Composable () -> Unit) {
    if (frameId.isNullOrBlank()) {
        content()
        return
    }
    val style = remember(frameId) { cosmeticFrameStyle(frameId) }
    val spin = rememberFrameSpin()
    Box(Modifier.size(avatarSize * 1.3f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawCosmeticFrame(style, spin.value) }
        content()
    }
}

@Composable
private fun FrameSwatch(frameId: String, diameter: Dp) {
    val style = remember(frameId) { cosmeticFrameStyle(frameId) }
    Canvas(Modifier.size(diameter)) {
        drawCircle(Nflix.SurfaceHigh, this.size.minDimension * 0.385f)
        drawCosmeticFrame(style, 0f)
    }
}

@Composable
private fun rememberPublicCosmetics(userId: String?): PublicCosmetics? {
    if (userId.isNullOrBlank()) return null
    val deps = LocalDeps.current
    val hub = remember(deps.api) { AchievementHub(deps.api) }
    val version = ProfileCosmeticsCache.version
    val state by produceState(ProfileCosmeticsCache.peek(userId), userId, version) {
        value = runCatching { hub.publicCosmetics(userId) }.getOrNull() ?: value
    }
    return state
}

/** Equipped frame of [userId] around [content]; lazy and cached like ShowcaseMedals. */
@Composable
internal fun ProfileFrame(userId: String?, avatarSize: Dp, content: @Composable () -> Unit) {
    CosmeticFramedAvatar(rememberPublicCosmetics(userId)?.frame, avatarSize, content)
}

/** Equipped title of [userId], under the name. */
@Composable
internal fun ProfileTitleLine(userId: String?) {
    val title = rememberPublicCosmetics(userId)?.titleLabel ?: return
    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = HintGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Small frame swatch and title for member tiles. */
@Composable
internal fun ProfileCosmeticsBadge(userId: String?) {
    val cosmetics = rememberPublicCosmetics(userId) ?: return
    if (cosmetics.frame == null && cosmetics.titleLabel == null) return
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        cosmetics.frame?.let { FrameSwatch(it, 14.dp) }
        cosmetics.titleLabel?.let { Text(it, fontSize = 11.sp, color = HintGold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Hero strip: my framed avatar, my title and the « Personnaliser mon profil » sheet. */
@Composable
internal fun ProfileCosmeticsStrip(hub: AchievementHub, userId: String?, userName: String?, revision: Int) {
    val scope = rememberCoroutineScope()
    val version = ProfileCosmeticsCache.version
    var cosmetics by remember { mutableStateOf<ProfileCosmetics?>(null) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hub, revision, version) { runCatching { hub.profileCosmetics() }.onSuccess { cosmetics = it } }
    val current = cosmetics ?: return
    val initials = remember(userName) { org.jellyfin.mobile.nflix.MiraProfile.initials(userName) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(shape).background(Nflix.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape).clickable { message = null; open = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CosmeticFramedAvatar(current.equippedFrame, 48.dp) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                org.jellyfin.mobile.nflix.ui.ProfilePicture(userId, null, Modifier.size(48.dp).clip(CircleShape)) {
                    Text(initials, fontWeight = FontWeight.Bold, color = Nflix.Text)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(userName ?: "Mon profil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val title = current.equippedTitleLabel
            Text(title ?: "Aucun titre équipé", style = MaterialTheme.typography.bodySmall, color = if (title != null) HintGold else Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Nflix.Red.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Edit, null, tint = Nflix.Red, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Personnaliser", style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.SemiBold)
        }
    }
    if (open) {
        CustomizeProfileSheet(current, busy, message, onDismiss = { open = false }) { frame, title ->
            if (!busy) scope.launch {
                busy = true
                message = null
                val result = safeAction { hub.equipCosmetics(frame, title) }
                if (result.success) cosmetics = runCatching { hub.profileCosmetics() }.getOrNull() ?: current.copy(equippedFrame = frame, equippedTitle = title)
                else message = result.message ?: "Action refusée par le serveur."
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeProfileSheet(
    state: ProfileCosmetics,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onEquip: (frame: String?, title: String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nflix.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Personnaliser mon profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Nflix.Red, strokeWidth = 2.dp)
            }
            Text("Cadres et titres gagnés avec les succès et le passe de saison.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = Nflix.Red, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Text("Cadres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item("none") { FrameOption(null, "Aucun", null, true, state.equippedFrame == null, busy) { onEquip(null, state.equippedTitle) } }
                items(state.frames, key = { it.id }) { f ->
                    val selected = state.equippedFrame == f.id
                    FrameOption(f.id, f.label, f.source, f.unlocked, selected, busy) { onEquip(if (selected) null else f.id, state.equippedTitle) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Titres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            TitleOption("Aucun titre", null, true, state.equippedTitle == null, busy) { onEquip(state.equippedFrame, null) }
            state.titles.forEach { t ->
                val selected = state.equippedTitle == t.id
                TitleOption(t.label, t.source, t.unlocked, selected, busy) { onEquip(state.equippedFrame, if (selected) null else t.id) }
            }
        }
    }
}

@Composable
private fun FrameOption(id: String?, label: String, source: String?, unlocked: Boolean, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(100.dp).clip(shape).background(if (selected) HintGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(if (selected) 2.dp else 1.dp, if (selected) HintGold else Color.White.copy(alpha = 0.08f), shape)
            .clickable(enabled = unlocked && !busy, onClick = onClick).alpha(if (unlocked) 1f else 0.4f).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (id != null) FrameSwatch(id, 56.dp)
        else Box(Modifier.size(56.dp).padding(9.dp).border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!unlocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Nflix.Muted, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(2.dp))
                Text(source ?: "Verrouillé", fontSize = 10.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else if (selected) {
            Text("Équipé", fontSize = 10.sp, color = HintGold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TitleOption(label: String, source: String?, unlocked: Boolean, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .background(if (selected) HintGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) HintGold else Color.Transparent, shape)
            .clickable(enabled = unlocked && !busy, onClick = onClick).alpha(if (unlocked) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (selected) HintGold else Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!unlocked && source != null) Text(source, fontSize = 11.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            selected -> Icon(Icons.Rounded.Check, "Équipé", tint = HintGold, modifier = Modifier.size(18.dp))
            !unlocked -> Icon(Icons.Rounded.Lock, "Verrouillé", tint = Nflix.Muted, modifier = Modifier.size(16.dp))
        }
    }
}
