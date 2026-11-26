package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.AchievementBadge
import org.jellyfin.mobile.nflix.data.AchievementPluginMissingException
import org.jellyfin.mobile.nflix.data.AchievementProfile
import org.jellyfin.mobile.nflix.data.AchievementRepository
import org.jellyfin.mobile.nflix.data.NflixHttpException
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.rememberAmbientMotionAllowed
import org.jellyfin.mobile.nflix.data.NflixAchievements
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val repository = remember(deps.api) { AchievementRepository(deps.api) }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<AchievementProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("Tous") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AchievementBadge?>(null) }
    var saving by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val hub = remember(deps.api) { org.jellyfin.mobile.nflix.data.AchievementHub(deps.api) }
    val publicConfig = org.jellyfin.mobile.nflix.ui.rememberLoad("ab-config") { hub.publicConfig() }.let { (it as? org.jellyfin.mobile.nflix.ui.Load.Ready)?.value }
        ?: org.jellyfin.mobile.nflix.data.AchievementPublicConfig()
    var bank by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.AchievementBank?>(null) }
    LaunchedEffect(hub, revision) { bank = runCatching { hub.bank() }.getOrNull() }
    // Level, XP, streak, next/recent and seasonal data; null on older plugins (classic header then).
    var extra by remember { mutableStateOf<NflixAchievements?>(null) }
    LaunchedEffect(hub, revision) {
        extra = try {
            hub.nflix()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            extra
        }
    }
    val context = LocalContext.current
    val userKey = deps.session.userId.orEmpty()
    // Badges unlocked since the last visit: celebrated once, then tagged "Nouveau" for this session.
    var celebrate by remember { mutableStateOf<List<AchievementBadge>>(emptyList()) }
    var freshIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(profile) {
        val data = profile ?: return@LaunchedEffect
        val unlockedIds = data.badges.filter { it.unlocked }.map { it.id }.toSet()
        val previous = withContext(Dispatchers.IO) {
            runCatching { AchievementSeenStore.read(context, userKey) }.getOrNull().also {
                runCatching { AchievementSeenStore.write(context, userKey, unlockedIds) }
            }
        }
        val fresh = newlyUnlocked(previous, data.badges)
        if (fresh.isNotEmpty()) {
            freshIds = freshIds + fresh.map { it.id }
            celebrate = fresh
        }
    }

    LaunchedEffect(repository, revision) {
        loading = true
        error = null
        try {
            profile = repository.profile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = achievementError(failure)
        } finally {
            loading = false
        }
    }

    val visibleFor: (AchievementProfile) -> List<AchievementBadge> = { data ->
        data.badges.filter { badge ->
            (category == null || badge.category == category) &&
                (query.isBlank() || badge.title.contains(query.trim(), ignoreCase = true) || badge.description.contains(query.trim(), ignoreCase = true)) && when (filter) {
                "Débloqués" -> badge.unlocked
                "En cours" -> !badge.unlocked && badge.currentValue > 0
                "Verrouillés" -> !badge.unlocked
                else -> true
            }
        }.sortedWith(compareByDescending<AchievementBadge> { it.unlocked }.thenByDescending { it.progress }.thenBy { it.title })
    }

    Box(Modifier.fillMaxSize()) {
        if (loading && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                val data = profile
                val nflix = extra
                item("rank") {
                    if (data != null && nflix != null) NflixHero(data, nflix, loading) { revision++ }
                    else if (data != null) RankHeader(data, loading) { revision++ }
                    else Spacer(Modifier.statusBarsPadding().height(64.dp))
                }
                if (data != null && nflix != null) {
                    item("streak") { StreakAndSeason(nflix) }
                    val next = nflix.next.map { b -> data.badges.find { it.id == b.id }?.let { it.copy(rarityLabel = it.rarityLabel ?: b.rarityLabel) } ?: b }
                    if (next.isNotEmpty()) item("next") {
                        SectionTitle("Prochains succès", "Vos objectifs les plus proches")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(next, key = { _, it -> "nx-${it.id}" }) { index, badge ->
                                NextBadgeCard(badge, Modifier.achievementAppear(index)) { selected = badge; actionError = null }
                            }
                        }
                    }
                    val recent = nflix.recent.map { b -> data.badges.find { it.id == b.id }?.let { it.copy(rarityLabel = it.rarityLabel ?: b.rarityLabel) } ?: b }
                    if (recent.isNotEmpty()) item("recent") {
                        SectionTitle("Récemment débloqués", "${recent.size} dernier(s)")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(recent, key = { _, it -> "rc-${it.id}" }) { index, badge ->
                                RecentBadgeCard(badge, fresh = badge.id in freshIds, modifier = Modifier.achievementAppear(index)) { selected = badge; actionError = null }
                            }
                        }
                    }
                }
                error?.let { message ->
                    item("error") {
                        Surface(color = Nflix.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(message)
                                TextButton(onClick = { revision++ }, enabled = !loading) { Text("Réessayer", color = Nflix.Red) }
                            }
                        }
                    }
                }
                if (data != null) {
                    item("hub") { HubShortcuts(publicConfig, bank) { navigator.open(org.jellyfin.mobile.nflix.ui.Route.AchievementHub(it)) } }
                    item("showcase") {
                        SectionTitle("Votre vitrine", "${data.equipped.size} exposé(s)")
                        if (data.equipped.isEmpty()) {
                            Text(
                                "Touchez un succès débloqué pour l'exposer ici.",
                                color = Nflix.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(data.equipped, key = { _, it -> "eq-${it.id}" }) { index, badge ->
                                    val full = data.badges.find { it.id == badge.id } ?: badge
                                    Column(
                                        Modifier.width(96.dp).achievementAppear(index).clip(RoundedCornerShape(14.dp)).clickable { selected = full; actionError = null }.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Medallion(full, 64.dp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(full.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    val almost = data.badges.filter { !it.unlocked && it.targetValue > 0 && it.currentValue > 0 }.sortedByDescending { it.progress }.take(10)
                    if (almost.isNotEmpty() && nflix?.next.isNullOrEmpty()) item("almost") {
                        SectionTitle("Presque débloqués", "Vos prochains objectifs")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(almost, key = { _, it -> "al-${it.id}" }) { index, badge ->
                                Column(
                                    Modifier.width(220.dp).achievementAppear(index).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
                                        .clickable { selected = badge; actionError = null }.padding(14.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Medallion(badge, 40.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(badge.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    BadgeProgress(badge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${badge.currentValue} / ${badge.targetValue} · encore ${(badge.targetValue - badge.currentValue).coerceAtLeast(0)}",
                                        style = MaterialTheme.typography.labelSmall, color = Nflix.Muted,
                                    )
                                }
                            }
                        }
                    }
                    item("filters") {
                        SectionTitle("Tous les succès", "${data.summary.unlocked} sur ${data.summary.total}")
                        androidx.compose.material3.OutlinedTextField(
                            query, { query = it.take(60) }, singleLine = true,
                            label = { Text("Rechercher un succès") },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                        )
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val counts = mapOf(
                                "Tous" to data.badges.size,
                                "Débloqués" to data.badges.count { it.unlocked },
                                "En cours" to data.badges.count { !it.unlocked && it.currentValue > 0 },
                                "Verrouillés" to data.badges.count { !it.unlocked },
                            )
                            items(counts.keys.toList(), key = { it }) { label -> NChip("$label · ${counts[label]}", filter == label) { filter = label } }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { NChip("Toutes catégories", category == null) { category = null } }
                            items(data.badges.map { it.category }.distinct().sortedBy { categoryName(it) }, key = { "cat-$it" }) { value ->
                                NChip(categoryName(value), category == value) { category = value }
                            }
                        }
                    }
                    val visible = visibleFor(data)
                    if (visible.isEmpty()) item("empty") {
                        Text("Aucun succès ne correspond à ces filtres.", color = Nflix.Muted, modifier = Modifier.padding(20.dp))
                    }
                    itemsIndexed(visible.chunked(2), key = { _, row -> "row-" + row.joinToString("-") { it.id } }) { index, row ->
                        Row(Modifier.achievementAppear(index).padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { badge ->
                                key(badge.id) {
                                    BadgeTile(badge, data.rarityStats[badge.id], data.equipped.any { it.id == badge.id }, Modifier.weight(1f), fresh = badge.id in freshIds) {
                                        selected = badge; actionError = null
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = { navigator.back() },
            modifier = Modifier.statusBarsPadding().padding(8.dp).size(42.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
    }

    if (celebrate.isNotEmpty()) {
        AchievementCelebration(
            celebrate,
            onOpen = { badge ->
                celebrate = emptyList()
                selected = profile?.badges?.find { it.id == badge.id } ?: badge
                actionError = null
            },
            onDismiss = { celebrate = emptyList() },
        )
    }

    selected?.let { badge ->
        val equipped = profile?.equipped?.any { it.id == badge.id } == true
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!saving) selected = null }, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeDetailHero(badge)
                Spacer(Modifier.height(6.dp))
                Text(badge.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                RarityPill(badge.rarity, badge.rarityLabel)
                Spacer(Modifier.height(12.dp))
                Text(badge.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Nflix.Text.copy(alpha = 0.9f))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Catégorie", categoryName(badge.category), Modifier.weight(1f))
                    StatTile(
                        "Membres",
                        profile?.rarityStats?.get(badge.id)?.let { String.format(Locale.FRANCE, "%.1f %%", it) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (badge.unlocked) {
                    Text("Débloqué${badge.unlockedAt?.let { " le ${achievementDate(it)}" }.orEmpty()}", color = Nflix.Success, style = MaterialTheme.typography.labelLarge)
                } else if (badge.targetValue > 0) {
                    BadgeProgress(badge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${badge.currentValue} / ${badge.targetValue} · encore ${(badge.targetValue - badge.currentValue).coerceAtLeast(0)}",
                        style = MaterialTheme.typography.labelLarge, color = Nflix.Muted,
                    )
                } else {
                    Text("Pas encore débloqué", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
                }
                actionError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(18.dp))
                if (badge.unlocked) {
                    Button(
                        enabled = !saving && !loading,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    repository.setEquipped(badge, !equipped)
                                    revision++
                                    selected = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    actionError = achievementError(failure)
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (equipped) Nflix.Surface else Nflix.Red, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (saving) "Enregistrement…" else if (equipped) "Retirer de la vitrine" else "Exposer dans la vitrine", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (badge.unlocked) {
                    val pinned = bank?.pinned?.contains(badge.id) == true
                    val titled = bank?.titleBadgeId == badge.id
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = !saving, onClick = {
                            saving = true
                            scope.launch {
                                val result = safeAction { hub.pin(badge.id, !pinned) }
                                if (result.success) { revision++ } else actionError = result.message
                                saving = false
                            }
                        }) { Text(if (pinned) "Désépingler" else "Épingler", color = Nflix.Red) }
                        TextButton(enabled = !saving, onClick = {
                            saving = true
                            scope.launch {
                                val result = safeAction { hub.setTitle(if (titled) null else badge.id) }
                                if (result.success) { revision++ } else actionError = result.message
                                saving = false
                            }
                        }) { Text(if (titled) "Retirer le titre" else "Utiliser comme titre", color = Nflix.Red) }
                    }
                } else {
                    val price = org.jellyfin.mobile.nflix.data.AchievementHub.badgePrice(badge.rarity)
                    var confirmBuy by remember(badge.id) { mutableStateOf(false) }
                    if (confirmBuy) AlertDialog(
                        onDismissRequest = { confirmBuy = false }, containerColor = Nflix.SurfaceHigh,
                        title = { Text("Débloquer « ${badge.title} » ?") },
                        text = { Text("$price points seront retirés de votre banque (${bank?.scoreBank ?: 0} disponibles).") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmBuy = false
                                saving = true
                                scope.launch {
                                    val result = safeAction { hub.buyBadge(badge.id) }
                                    if (result.success) { revision++; selected = null } else actionError = result.message ?: "Achat impossible."
                                    saving = false
                                }
                            }) { Text("Débloquer", color = Nflix.Red) }
                        },
                        dismissButton = { TextButton(onClick = { confirmBuy = false }) { Text("Annuler") } },
                    )
                    Button(
                        enabled = !saving && !loading && (bank?.scoreBank ?: 0) >= price,
                        onClick = { confirmBuy = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Nflix.Surface, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Débloquer pour $price pts (banque : ${bank?.scoreBank ?: 0})") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HubShortcuts(config: org.jellyfin.mobile.nflix.data.AchievementPublicConfig, bank: org.jellyfin.mobile.nflix.data.AchievementBank?, open: (String) -> Unit) {
    val entries = buildList {
        if (config.quests) add(Triple("Quests", "Quêtes", Icons.Rounded.TaskAlt))
        if (config.leaderboard && !config.privacy) add(Triple("Leaderboard", "Classement", Icons.Rounded.Leaderboard))
        if (config.activityFeed && !config.privacy) add(Triple("Activity", "Activité", Icons.Rounded.History))
        add(Triple("Shop", "Boutique", Icons.Rounded.Storefront))
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries, key = { it.first }) { (tab, label, icon) ->
            Column(
                Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).clickable { open(tab) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = Nflix.Red, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                if (tab == "Shop" && bank != null) Text("${bank.scoreBank} pts", style = MaterialTheme.typography.labelSmall, color = Nflix.Warning)
            }
        }
    }
}

@Composable
private fun RankHeader(data: AchievementProfile, loading: Boolean, onRefresh: () -> Unit) {
    val color = tierColor(data.rank.tier.color)
    val ring = rememberAchievementProgress(data.rank.progressToNext.coerceIn(0, 100) / 100f, delayMs = 250, durationMs = 1200)
    val reduced = rememberAchievementReducedMotion()
    val score = achievementCountUp(data.rank.score, durationMs = 1300)
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Nflix.RedDeep.copy(alpha = 0.18f), Nflix.Night))),
    ) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Succès", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Nflix.Red, strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "Actualiser")
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color, -90f, 360f * ring.value, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Box(
                    Modifier.size(104.dp).rarityGlow(color, 3, animated = false).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Nflix.Surface)))
                        .achievementShine(enabled = !reduced, seed = 977, periodMs = 5200),
                    contentAlignment = Alignment.Center,
                ) { Icon(achievementIcon(data.rank.tier.icon), null, tint = color, modifier = Modifier.size(56.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(data.rank.tier.frenchName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                data.rank.nextTier?.let { "$score pts · encore ${(it.minScore - data.rank.score).coerceAtLeast(0)} pour ${it.frenchName}" }
                    ?: "$score pts · rang maximal",
                style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AchievementStatTile("Débloqués", data.summary.unlocked, Modifier.weight(1f), String.format(Locale.FRANCE, "%.0f %%", data.summary.percentage)) { "$it/${data.summary.total}" }
                AchievementStatTile("Série", data.summary.currentWatchStreak, Modifier.weight(1f), "jours d'affilée") { "$it j" }
                AchievementStatTile("Record", data.summary.bestWatchStreak, Modifier.weight(1f), "meilleure série") { "$it j" }
            }
            Spacer(Modifier.height(12.dp))
            // Unlocked badges per rarity, rarest last.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic").forEach { rarity ->
                    val total = data.badges.count { it.rarity == rarity }
                    if (total > 0) {
                        val got = data.badges.count { it.rarity == rarity && it.unlocked }
                        Row(
                            Modifier.clip(CircleShape).background(rarityColor(rarity).copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(rarityColor(rarity)))
                            Spacer(Modifier.width(4.dp))
                            Text("$got/$total", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
    }
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.85f), maxLines = 1)
        caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1) }
    }
}

/**
 * Round badge emblem tinted by rarity. Unlocked badges glow by rarity and get a periodic light
 * sweep ([animated]); locked ones are desaturated with a padlock.
 */
@Composable
internal fun Medallion(badge: AchievementBadge, size: androidx.compose.ui.unit.Dp, showLocked: Boolean = true) {
    val color = rarityColor(badge.rarity)
    val locked = !badge.unlocked && showLocked
    val motion = !rememberAchievementReducedMotion()
    val lit = badge.unlocked
    Box(
        Modifier.size(size).then(if (lit) Modifier.rarityGlow(color, rarityRank(badge.rarity), motion) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .background(
                    if (locked) Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF161616)))
                    else Brush.radialGradient(listOf(color.copy(alpha = if (lit) 0.62f else 0.45f), color.copy(alpha = 0.12f))),
                )
                .achievementShine(enabled = lit && motion, seed = badge.id.hashCode())
                .border(
                    2.dp,
                    if (locked) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f)))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), color, color.copy(alpha = 0.55f), color)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (locked && badge.category == "Hidden") Icons.Rounded.Lock else achievementIcon(badge.icon), null,
                tint = if (locked) Nflix.Muted.copy(alpha = 0.45f) else Color.White, modifier = Modifier.size(size * 0.48f),
            )
        }
        if (locked && badge.category != "Hidden") {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(Nflix.SurfaceHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Lock, "Verrouillé", tint = Nflix.Muted, modifier = Modifier.size(size * 0.2f)) }
        }
    }
}

/** Large medallion of the detail sheet: pops in, rotating halo when unlocked, progress ring otherwise. */
@Composable
private fun BadgeDetailHero(badge: AchievementBadge) {
    val reduced = rememberAchievementReducedMotion()
    val color = rarityColor(badge.rarity)
    val pop = remember(badge.id) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0.55f) }
    LaunchedEffect(badge.id) {
        if (!reduced) {
            pop.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                ),
            )
        }
    }
    val scaled = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
    if (badge.unlocked) {
        AchievementHalo(color, 190.dp, animated = !reduced, modifier = scaled) { Medallion(badge, 112.dp) }
    } else {
        Box(Modifier.size(190.dp).then(scaled), contentAlignment = Alignment.Center) {
            if (badge.targetValue > 0) {
                AchievementProgressRing(badge.progress, color, 150.dp, stroke = 7.dp) { Medallion(badge, 112.dp) }
            } else {
                Medallion(badge, 112.dp)
            }
            if (badge.targetValue > 0) {
                Text(
                    "${(badge.progress * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color,
                    modifier = Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(Nflix.Surface).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BadgeProgress(badge: AchievementBadge) {
    AchievementProgressBar(badge.progress, rarityColor(badge.rarity))
}

@Composable
internal fun RarityPill(rarity: String, label: String? = null) {
    val color = rarityColor(rarity)
    Text(
        (label ?: rarityName(rarity)).uppercase(Locale.FRANCE),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun BadgeTile(badge: AchievementBadge, membersPercent: Double?, equipped: Boolean, modifier: Modifier, fresh: Boolean = false, onClick: () -> Unit) {
    val color = rarityColor(badge.rarity)
    val shape = RoundedCornerShape(18.dp)
    val label = badge.rarityLabel ?: rarityName(badge.rarity)
    Column(
        modifier.clip(shape)
            .background(
                if (badge.unlocked) Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Nflix.Surface, Nflix.Surface))
                else Brush.verticalGradient(listOf(Color(0xFF1A1A1C), Color(0xFF141416))),
            )
            .border(
                if (fresh) 2.dp else 1.dp,
                if (badge.unlocked) Brush.linearGradient(listOf(color, color.copy(alpha = 0.25f), color.copy(alpha = 0.7f)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))),
                shape,
            )
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Medallion(badge, 58.dp)
            if (equipped) Icon(Icons.Rounded.PushPin, "Dans votre vitrine", tint = Nflix.Red, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
            if (fresh) NewBadgeTag(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            badge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = if (badge.unlocked) Nflix.Text else Nflix.Text.copy(alpha = 0.6f),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp),
        )
        Text(badge.description, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted.copy(alpha = if (badge.unlocked) 1f else 0.7f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(30.dp))
        Spacer(Modifier.height(8.dp))
        if (badge.unlocked) {
            Text(
                listOfNotNull(label, membersPercent?.let { String.format(Locale.FRANCE, "%.0f %% des membres", it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else if (badge.targetValue > 0) {
            AchievementProgressBar(badge.progress, color.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("${badge.currentValue} / ${badge.targetValue}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.weight(1f))
                Text("${(badge.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        } else {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

/** Hero of the Nflix view: animated XP ring with the level, title, XP, tier and unlocked count. */
@Composable
private fun NflixHero(data: AchievementProfile, extra: NflixAchievements, loading: Boolean, onRefresh: () -> Unit) {
    val level = extra.level
    val tier = tierColor(data.rank.tier.color)
    val motion = rememberMotionEnabled()
    val ambient = rememberAmbientMotionAllowed() && motion
    val ring = rememberAchievementProgress(level.progress, delayMs = 250, durationMs = 1400)
    val shownLevel = achievementCountUp(level.level, durationMs = 900)
    val xp = achievementCountUp(level.xpIntoLevel, durationMs = 1300)
    val pulse = if (ambient) {
        rememberInfiniteTransition(label = "hero").animateFloat(
            0.35f, 0.75f,
            infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
        ).value
    } else 0.5f
    val accent = Nflix.Red
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), Nflix.RedDeep.copy(alpha = 0.16f), Nflix.Night))),
    ) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Succès", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "Actualiser")
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF221012), Nflix.Surface, Color(0xFF120C0D))))
                    .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Color.White.copy(alpha = 0.06f), tier.copy(alpha = 0.5f))), RoundedCornerShape(28.dp))
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2 + 6.dp.toPx()
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawCircle(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.22f * pulse * 2f), Color.Transparent)),
                            radius = size.minDimension / 2,
                        )
                        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        val sweep = 360f * ring.value
                        if (sweep > 0.5f) {
                            drawArc(
                                Brush.sweepGradient(listOf(Nflix.RedDeep, accent, Color(0xFFFF8A65), accent)),
                                -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                            val angle = Math.toRadians((sweep - 90f).toDouble())
                            val r = arcSize.width / 2
                            val c = androidx.compose.ui.geometry.Offset(size.width / 2 + (r * kotlin.math.cos(angle)).toFloat(), size.height / 2 + (r * kotlin.math.sin(angle)).toFloat())
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = stroke * 0.32f, center = c)
                            drawCircle(accent.copy(alpha = 0.35f * pulse * 2f), radius = stroke * 0.9f, center = c)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NIVEAU", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("$shownLevel", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(level.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(
                    "$xp / ${level.xpForNextLevel} XP",
                    style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold,
                )
                Text(
                    "${(level.xpForNextLevel - level.xpIntoLevel).coerceAtLeast(0)} XP avant le niveau ${level.level + 1} · ${level.xp} XP au total",
                    style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroPill(
                        achievementIcon(data.rank.tier.icon), tier, "Rang",
                        extra.tierLabel ?: data.rank.tier.frenchName,
                        data.rank.nextTier?.let { "${data.rank.score} pts · ${data.rank.progressToNext.coerceIn(0, 100)} %" } ?: "${data.rank.score} pts · max",
                        Modifier.weight(1f),
                    )
                    val total = extra.total.takeIf { it > 0 } ?: data.summary.total
                    val unlocked = if (extra.total > 0) extra.unlocked else data.summary.unlocked
                    HeroPill(
                        Icons.Rounded.EmojiEvents, Nflix.Warning, "Débloqués", "$unlocked / $total",
                        if (total > 0) "${unlocked * 100 / total} %" else "—",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic").forEach { rarity ->
                        val total = data.badges.count { it.rarity == rarity }
                        if (total > 0) {
                            val got = data.badges.count { it.rarity == rarity && it.unlocked }
                            Row(
                                Modifier.clip(CircleShape).background(rarityColor(rarity).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(rarityColor(rarity)))
                                Spacer(Modifier.width(4.dp))
                                Text("$got/$total", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, color: Color, label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Flame streak chip and, during a seasonal event, its banner. */
@Composable
private fun StreakAndSeason(extra: NflixAchievements) {
    val ambient = rememberAmbientMotionAllowed() && rememberMotionEnabled()
    val flame = if (ambient && extra.streak.current > 0) {
        rememberInfiniteTransition(label = "flame").animateFloat(
            0.9f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameScale",
        ).value
    } else 1f
    val fire = Color(0xFFFF7A2F)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(fire.copy(alpha = if (extra.streak.current > 0) 0.22f else 0.08f), Nflix.Surface)))
                .border(1.dp, fire.copy(alpha = if (extra.streak.current > 0) 0.45f else 0.15f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.LocalFireDepartment, null,
                tint = if (extra.streak.current > 0) fire else Nflix.Muted,
                modifier = Modifier.size(30.dp).graphicsLayer { scaleX = flame; scaleY = flame },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "🔥 ${extra.streak.current} ${if (extra.streak.current > 1) "jours" else "jour"}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (extra.streak.current > 0) "Série en cours, continuez demain !" else "Regardez un titre pour lancer une série",
                    style = MaterialTheme.typography.labelSmall, color = Nflix.Muted,
                )
            }
            Text(
                "record ${extra.streak.best}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fire,
                modifier = Modifier.clip(CircleShape).background(fire.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        val season = extra.seasonal
        if (season.active && !season.name.isNullOrBlank()) {
            val (icon, tint) = when (season.name.lowercase(Locale.FRANCE)) {
                "halloween" -> Icons.Rounded.NightsStay to Color(0xFFFF8C1A)
                "noël", "noel" -> Icons.Rounded.AcUnit to Color(0xFF7FD3FF)
                "été", "ete" -> Icons.Rounded.WbSunny to Color(0xFFFFC94D)
                else -> Icons.Rounded.Celebration to Nflix.Red
            }
            val until = season.endsAt?.let { seasonEnd(it) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.30f), Nflix.RedDeep.copy(alpha = 0.25f), Nflix.Surface)))
                    .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Saison ${season.name}${until?.let { " — jusqu'au $it" }.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                    Text("Des succès saisonniers sont à débloquer en ce moment.", style = MaterialTheme.typography.labelSmall, color = Nflix.Text.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** EndsAt is the first instant after the event: show the previous day. */
private fun seasonEnd(value: String): String? = runCatching {
    OffsetDateTime.parse(value).minusDays(1).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE))
}.getOrNull()

@Composable
private fun NextBadgeCard(badge: AchievementBadge, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = rarityColor(badge.rarity)
    val percent = (badge.progress * 100).toInt()
    Column(
        modifier.width(210.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.12f), Nflix.Surface)))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medallion(badge, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(badge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(badge.rarityLabel ?: rarityName(badge.rarity), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AchievementProgressBar(badge.progress, color, Modifier.weight(1f), height = 7.dp)
            Spacer(Modifier.width(8.dp))
            Text("$percent %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
        if (badge.targetValue > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${badge.currentValue} / ${badge.targetValue} · encore ${(badge.targetValue - badge.currentValue).coerceAtLeast(0)}",
                style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentBadgeCard(badge: AchievementBadge, fresh: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = rarityColor(badge.rarity)
    Column(
        modifier.width(118.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Nflix.Surface), radius = 260f))
            .border(1.dp, Brush.linearGradient(listOf(color, color.copy(alpha = 0.2f))), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Medallion(badge, 64.dp)
            if (fresh) NewBadgeTag(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(badge.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(badge.rarityLabel ?: rarityName(badge.rarity), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        badge.unlockedAt?.let { at ->
            Text(
                runCatching { OffsetDateTime.parse(at).format(DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)) }.getOrDefault(""),
                style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1,
            )
        }
    }
}

internal fun rarityColor(value: String): Color = when (value) {
    "Uncommon" -> Color(0xFF46D39A)
    "Rare" -> Color(0xFF4FA3FF)
    "Epic" -> Color(0xFFB57BFF)
    "Legendary" -> Color(0xFFFFB547)
    "Mythic" -> Color(0xFFFF4F7B)
    else -> Color(0xFF9E9EA8)
}

internal fun tierColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Nflix.Red)
internal fun achievementDate(value: String): String = runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)) }.getOrDefault(value)
internal fun achievementError(error: Exception): String = when (error) {
    is AchievementPluginMissingException -> error.message.orEmpty()
    is NflixHttpException -> when (error.code) {
        401, 403 -> "Votre session ne permet pas d’accéder aux succès. Reconnectez-vous."
        400 -> "Ce badge ne peut pas être équipé. Vérifiez les règles et la limite de votre vitrine."
        429 -> "Trop de demandes. Patientez puis réessayez."
        else -> "Le serveur ne peut pas charger les succès. Réessayez."
    }
    else -> "Impossible de charger les succès. Vérifiez votre connexion puis réessayez."
}
internal fun rarityName(value: String): String = when (value) {
    "Common" -> "Commun"
    "Uncommon" -> "Peu commun"
    "Rare" -> "Rare"
    "Epic" -> "Épique"
    "Legendary" -> "Légendaire"
    "Mythic" -> "Mythique"
    else -> value
}
internal fun categoryName(value: String): String = when (value) {
    "Getting Started" -> "Premiers pas"
    "Afternoon Watching" -> "Visionnage l’après-midi"
    "Morning Watching" -> "Visionnage le matin"
    "Night Watching" -> "Visionnage nocturne"
    "Weekend Watching" -> "Visionnage le week-end"
    "Best Streaks" -> "Records de régularité"
    "Binge" -> "Marathons"
    "Combos" -> "Combinaisons"
    "Decades" -> "Décennies"
    "Discography" -> "Discographie"
    "Endurance" -> "Endurance"
    "Episode Marathons" -> "Marathons d’épisodes"
    "Film Marathons" -> "Marathons de films"
    "Eras" -> "Époques"
    "Exploration" -> "Exploration"
    "Games" -> "Jeux"
    "Genre Specialist" -> "Spécialiste de genre"
    "Genres" -> "Genres"
    "Hidden" -> "Secrets"
    "Holidays" -> "Fêtes"
    "Languages" -> "Langues"
    "Library Completion" -> "Bibliothèque complétée"
    "Loyalty" -> "Fidélité"
    "Meta" -> "Collection de succès"
    "People" -> "Personnalités"
    "Pilot vs Completer" -> "Du pilote au final"
    "Prestige" -> "Prestige"
    "Prime Time" -> "Première partie de soirée"
    "Rewatch" -> "Revoir ses favoris"
    "Runtime" -> "Durée"
    "Score Economy" -> "Points"
    "Studio Specialist" -> "Spécialiste de studio"
    "Total Time" -> "Temps total"
    "Weekdays" -> "Jours de semaine"
    "World" -> "Cinéma du monde"
    "Challenge" -> "Défis"
    "Custom" -> "Personnalisés"
    "General" -> "Général"
    "Movies", "Movie" -> "Films"
    "Series", "TV", "Episodes" -> "Séries"
    "Music" -> "Musique"
    "Books", "Reading" -> "Lecture"
    "Streak", "Streaks" -> "Régularité"
    "Time", "WatchTime" -> "Temps de visionnage"
    "Social" -> "Communauté"
    "Special" -> "Spécial"
    else -> value
}
internal fun achievementIcon(value: String?): ImageVector = when (value) {
    "movie", "theaters" -> Icons.Rounded.Movie
    "music_note", "headphones" -> Icons.Rounded.MusicNote
    "menu_book", "auto_stories" -> Icons.Rounded.MenuBook
    "local_fire_department", "bolt" -> Icons.Rounded.LocalFireDepartment
    "star", "auto_awesome", "workspace_premium" -> Icons.Rounded.Star
    "visibility", "tv" -> Icons.Rounded.Tv
    "person", "group" -> Icons.Rounded.Person
    else -> Icons.Rounded.EmojiEvents
}


/** Runs a hub action; cancellation propagates, any other failure becomes a refused result. */
internal suspend fun safeAction(block: suspend () -> org.jellyfin.mobile.nflix.data.ActionResult): org.jellyfin.mobile.nflix.data.ActionResult = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: Exception) {
    org.jellyfin.mobile.nflix.data.ActionResult(false, achievementError(error))
}
