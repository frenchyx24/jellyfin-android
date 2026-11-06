package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.CancellationException
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
            (category == null || badge.category == category) && when (filter) {
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
                item("rank") {
                    if (data != null) RankHeader(data, loading) { revision++ }
                    else Spacer(Modifier.statusBarsPadding().height(64.dp))
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
                    item("showcase") {
                        SectionTitle("Votre vitrine", "${data.equipped.size} exposé(s)")
                        if (data.equipped.isEmpty()) {
                            Text(
                                "Touchez un succès débloqué pour l'exposer ici.",
                                color = Nflix.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(data.equipped, key = { "eq-${it.id}" }) { badge ->
                                    val full = data.badges.find { it.id == badge.id } ?: badge
                                    Column(
                                        Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).clickable { selected = full; actionError = null }.padding(vertical = 6.dp),
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
                    if (almost.isNotEmpty()) item("almost") {
                        SectionTitle("Presque débloqués", "Vos prochains objectifs")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(almost, key = { "al-${it.id}" }) { badge ->
                                Column(
                                    Modifier.width(220.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
                                        .clickable { selected = badge; actionError = null }.padding(14.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Medallion(badge, 40.dp, showLocked = false)
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
                    items(visible.chunked(2), key = { row -> "row-" + row.joinToString("-") { it.id } }) { row ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { badge ->
                                BadgeTile(badge, data.rarityStats[badge.id], data.equipped.any { it.id == badge.id }, Modifier.weight(1f)) {
                                    selected = badge; actionError = null
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

    selected?.let { badge ->
        val equipped = profile?.equipped?.any { it.id == badge.id } == true
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!saving) selected = null }, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Medallion(badge, 104.dp)
                Spacer(Modifier.height(14.dp))
                Text(badge.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                RarityPill(badge.rarity)
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
                    Text("${badge.currentValue} / ${badge.targetValue}", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
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
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RankHeader(data: AchievementProfile, loading: Boolean, onRefresh: () -> Unit) {
    val color = tierColor(data.rank.tier.color)
    val ring by animateFloatAsState(data.rank.progressToNext.coerceIn(0, 100) / 100f, tween(900), label = "rank-ring")
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
                    drawArc(color, -90f, 360f * ring, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Box(
                    Modifier.size(104.dp).clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Nflix.Surface))),
                    contentAlignment = Alignment.Center,
                ) { Icon(achievementIcon(data.rank.tier.icon), null, tint = color, modifier = Modifier.size(56.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(data.rank.tier.frenchName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                data.rank.nextTier?.let { "${data.rank.score} pts · encore ${(it.minScore - data.rank.score).coerceAtLeast(0)} pour ${it.frenchName}" }
                    ?: "${data.rank.score} pts · rang maximal",
                style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Débloqués", "${data.summary.unlocked}/${data.summary.total}", Modifier.weight(1f), String.format(Locale.FRANCE, "%.0f %%", data.summary.percentage))
                StatTile("Série", "${data.summary.currentWatchStreak} j", Modifier.weight(1f), "jours d'affilée")
                StatTile("Record", "${data.summary.bestWatchStreak} j", Modifier.weight(1f), "meilleure série")
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
private fun SectionTitle(title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Nflix.Text.copy(alpha = 0.85f), maxLines = 1)
        caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1) }
    }
}

/** Round badge emblem tinted by rarity; locked badges are greyed with a padlock. */
@Composable
private fun Medallion(badge: AchievementBadge, size: androidx.compose.ui.unit.Dp, showLocked: Boolean = true) {
    val color = rarityColor(badge.rarity)
    val locked = !badge.unlocked && showLocked
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (locked) Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF161616))) else Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.12f))))
            .border(2.dp, if (locked) Color.White.copy(alpha = 0.1f) else color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (locked) Icons.Rounded.Lock else achievementIcon(badge.icon), null,
            tint = if (locked) Nflix.Muted else Color.White, modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun BadgeProgress(badge: AchievementBadge) {
    val animated by animateFloatAsState(badge.progress, tween(700), label = "badge-progress")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(rarityColor(badge.rarity)))
    }
}

@Composable
private fun RarityPill(rarity: String) {
    val color = rarityColor(rarity)
    Text(
        rarityName(rarity).uppercase(Locale.FRANCE),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun BadgeTile(badge: AchievementBadge, membersPercent: Double?, equipped: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = rarityColor(badge.rarity)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
            .border(1.dp, if (badge.unlocked) color.copy(alpha = 0.35f) else Nflix.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Medallion(badge, 58.dp)
            if (equipped) Icon(Icons.Rounded.PushPin, "Dans votre vitrine", tint = Nflix.Red, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(8.dp))
        Text(badge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp))
        Text(badge.description, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(30.dp))
        Spacer(Modifier.height(8.dp))
        if (badge.unlocked) {
            Text(
                listOfNotNull(rarityName(badge.rarity), membersPercent?.let { String.format(Locale.FRANCE, "%.0f %% des membres", it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else if (badge.targetValue > 0) {
            BadgeProgress(badge)
            Spacer(Modifier.height(4.dp))
            Text("${badge.currentValue} / ${badge.targetValue}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        } else {
            Text(rarityName(badge.rarity), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        }
    }
}

private fun rarityColor(value: String): Color = when (value) {
    "Uncommon" -> Color(0xFF46D39A)
    "Rare" -> Color(0xFF4FA3FF)
    "Epic" -> Color(0xFFB57BFF)
    "Legendary" -> Color(0xFFFFB547)
    "Mythic" -> Color(0xFFFF4F7B)
    else -> Color(0xFF9E9EA8)
}

private fun tierColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Nflix.Red)
private fun achievementDate(value: String): String = runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)) }.getOrDefault(value)
private fun achievementError(error: Exception): String = when (error) {
    is AchievementPluginMissingException -> error.message.orEmpty()
    is NflixHttpException -> when (error.code) {
        401, 403 -> "Votre session ne permet pas d’accéder aux succès. Reconnectez-vous."
        400 -> "Ce badge ne peut pas être équipé. Vérifiez les règles et la limite de votre vitrine."
        429 -> "Trop de demandes. Patientez puis réessayez."
        else -> "Le serveur ne peut pas charger les succès. Réessayez."
    }
    else -> "Impossible de charger les succès. Vérifiez votre connexion puis réessayez."
}
private fun rarityName(value: String): String = when (value) {
    "Common" -> "Commun"
    "Uncommon" -> "Peu commun"
    "Rare" -> "Rare"
    "Epic" -> "Épique"
    "Legendary" -> "Légendaire"
    "Mythic" -> "Mythique"
    else -> value
}
private fun categoryName(value: String): String = when (value) {
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
private fun achievementIcon(value: String?): ImageVector = when (value) {
    "movie", "theaters" -> Icons.Rounded.Movie
    "music_note", "headphones" -> Icons.Rounded.MusicNote
    "menu_book", "auto_stories" -> Icons.Rounded.MenuBook
    "local_fire_department", "bolt" -> Icons.Rounded.LocalFireDepartment
    "star", "auto_awesome", "workspace_premium" -> Icons.Rounded.Star
    "visibility", "tv" -> Icons.Rounded.Tv
    "person", "group" -> Icons.Rounded.Person
    else -> Icons.Rounded.EmojiEvents
}
