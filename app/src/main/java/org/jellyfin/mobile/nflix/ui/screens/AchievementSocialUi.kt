package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.Duel
import org.jellyfin.mobile.nflix.data.ProgressAlert
import org.jellyfin.mobile.nflix.data.UnlockItem
import org.jellyfin.mobile.nflix.data.WeeklyRow
import org.jellyfin.mobile.nflix.family.FamilyMemberInfo
import org.jellyfin.mobile.nflix.family.MiraAvatars
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.ProfilePicture
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

/** Small square avatar with the Mira/Jellyfin photo or the colour initial. */
@Composable
private fun MemberAvatar(userId: String?, name: String, size: Int = 36, colourKey: String? = null) {
    val color = rarityPalette(profileAvatarColor(colourKey ?: userId)).last()
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color, Nflix.SurfaceHigh))), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
        ProfilePicture(userId, null, Modifier.size(size.dp).clip(CircleShape))
    }
}

internal fun profileAvatarColor(key: String?): Color {
    val palette = listOf(Color(0xFFE50914), Color(0xFFF5A623), Color(0xFF46D39A), Color(0xFF6CB6FF), Color(0xFFB388FF), Color(0xFFFF6F91), Color(0xFF00BFA5))
    val index = (key.orEmpty().hashCode() and 0x7fffffff) % palette.size
    return palette[index]
}

private fun rarityColorOf(rarity: String?): Color = when (rarity?.lowercase()) {
    "uncommon" -> Color(0xFF6CB6FF)
    "rare" -> Color(0xFF46D39A)
    "epic" -> Color(0xFFB388FF)
    "legendary" -> Color(0xFFFFC35A)
    "mythic" -> Color(0xFFFF6F91)
    else -> Nflix.Muted
}

private fun timeAgoFr(iso: String?): String {
    if (iso == null) return ""
    val then = runCatching { java.time.OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
    val minutes = java.time.Duration.between(then.toInstant(), java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (24 * 60)} j"
    }
}

/**
 * Social tab of the Succès screen (plugin 3.2): progress alerts, the family weekly board, duels
 * between friends and the unlock feed with emoji reactions.
 */
@Composable
internal fun AchievementSocialTab(hub: AchievementHub, members: List<FamilyMemberInfo>, onOpenBadge: (String) -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<List<WeeklyRow>?>(null) }
    var duels by remember { mutableStateOf<List<Duel>?>(null) }
    var feed by remember { mutableStateOf<List<UnlockItem>?>(null) }
    var alerts by remember { mutableStateOf<List<ProgressAlert>>(emptyList()) }
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val ids = remember(members) { members.map { it.userId }.toSet() + listOfNotNull(deps.session.userId) }

    LaunchedEffect(hub, ids, tick) {
        if (duels == null) duels = emptyList()
        board = runCatching { hub.weeklyBoard(ids) }.getOrDefault(board.orEmpty())
        duels = runCatching { hub.duels() }.getOrDefault(duels.orEmpty())
        feed = runCatching { hub.unlockFeed(ids) }.getOrDefault(feed.orEmpty())
        alerts = runCatching { hub.progressAlerts() }.getOrDefault(alerts)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val visibleAlerts = alerts.filter { it.kind + it.title !in dismissed }
        if (visibleAlerts.isNotEmpty()) {
            item(key = "alerts") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleAlerts.forEach { alert ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nflix.Surface)
                                .clickable { alert.badgeId?.let(onOpenBadge) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Flag, null, tint = if (alert.kind == "streak-risk") Nflix.Warning else Nflix.Red, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(alert.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (alert.body.isNotBlank()) Text(alert.body, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                            }
                            IconButton(onClick = { dismissed = dismissed + (alert.kind + alert.title) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, "Masquer", tint = Nflix.Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        item(key = "board-title") { SocialTitle("Classement de la semaine") }
        item(key = "board") {
            val rows = board
            if (rows == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red, modifier = Modifier.size(26.dp)) }
            } else if (rows.isEmpty()) {
                Text("Ajoute des proches à ta famille pour te comparer.", color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val podium = rows.sortedBy { it.rank }.take(3)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        podium.forEach { row ->
                            val mine = row.userId.replace("-", "") == deps.session.userId?.replace("-", "")
                            val member = members.firstOrNull { it.userId.replace("-", "") == row.userId.replace("-", "") }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (mine) Nflix.Red.copy(alpha = .18f) else Nflix.Surface).padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("${row.rank}", fontWeight = FontWeight.Bold, color = listOf(Color(0xFFFFC35A), Color(0xFFC9CBD0), Color(0xFFCD7F32))[podium.indexOf(row).coerceIn(0, 2)], fontSize = 18.sp)
                                Spacer(Modifier.height(4.dp))
                                MemberAvatar(row.userId, row.name, 46, member?.avatarColor)
                                Spacer(Modifier.height(6.dp))
                                Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${row.weekXp} XP", fontSize = 12.sp, color = Nflix.Muted)
                                if (row.title.isNotBlank()) Text(row.title, fontSize = 10.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    rows.sortedBy { it.rank }.forEach { row ->
                        val mine = row.userId.replace("-", "") == deps.session.userId?.replace("-", "")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (mine) Nflix.Red.copy(alpha = .12f) else Color.Transparent).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${row.rank}", color = Nflix.Muted, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                            Text(row.name + if (mine) " · toi" else "", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text("Niv. ${row.level}", color = Nflix.Muted, fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("${row.weekXp} XP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item(key = "duels-title") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Défis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = { creating = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    enabled = members.isNotEmpty(),
                ) { Text("Lancer un défi") }
            }
        }
        val list = duels.orEmpty()
        if (list.isEmpty()) {
            item(key = "duels-empty") { Text("Aucun défi en cours.", color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(list, key = { "d-" + it.id }) { duel ->
                DuelCard(
                    duel = duel, busy = busy == duel.id,
                    onAccept = { scope.launch { busy = duel.id; val r = hub.respondDuel(duel.id, true); busy = null; Toasts.show(r.message ?: if (r.success) "Défi accepté." else "Défi impossible."); tick++ } },
                    onDecline = { scope.launch { busy = duel.id; val r = hub.respondDuel(duel.id, false); busy = null; Toasts.show(r.message ?: "Défi refusé."); tick++ } },
                )
            }
        }

        item(key = "feed-title") { SocialTitle("Activité des proches") }
        val unlocks = feed.orEmpty()
        if (unlocks.isEmpty()) {
            item(key = "feed-empty") { Text("Aucun succès récent parmi tes proches.", color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(unlocks, key = { "f-" + it.key }) { item ->
                UnlockCard(item) { emoji ->
                    val wasMine = emoji in item.mine
                    feed = feed.orEmpty().map { u ->
                        if (u.key != item.key) u else {
                            val counts = u.reactions.toMutableMap()
                            val next = if (wasMine) u.mine - emoji else u.mine + emoji
                            counts[emoji] = ((counts[emoji] ?: 0) + if (wasMine) -1 else 1).coerceAtLeast(0)
                            u.copy(reactions = counts, mine = next)
                        }
                    }
                    scope.launch { hub.reactToUnlock(item.key, emoji) }
                }
            }
        }
    }

    if (creating) {
        CreateDuelSheet(
            members = members.filter { MiraAvatars.norm(it.userId) != MiraAvatars.norm(deps.session.userId) },
            onDismiss = { creating = false },
            onCreate = { opponent, kind, target, days ->
                scope.launch {
                    val r = hub.createDuel(opponent, kind, target, days)
                    Toasts.show(r.message ?: if (r.success) "Défi envoyé." else "Défi impossible.", if (r.success) ToastKind.Success else ToastKind.Warning)
                    if (r.success) { creating = false; tick++ }
                }
            },
        )
    }
}

@Composable
private fun SocialTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DuelCard(duel: Duel, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(duel.challengerId, duel.challengerName, 28)
            Text(" vs ", color = Nflix.Muted)
            MemberAvatar(duel.opponentId, duel.opponentName, 28)
            Spacer(Modifier.width(10.dp))
            Text(duel.label.ifBlank { "Défi" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                duel.statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = when (duel.status.lowercase()) { "won" -> Nflix.Success; "lost" -> Nflix.Red; "active" -> Nflix.Info; "pending" -> Nflix.Warning; else -> Nflix.Muted },
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        DuelBar("Toi", duel.myProgress, duel.target)
        DuelBar(if (duel.status == "active" || duel.status == "won" || duel.status == "lost") "Adversaire" else "", duel.theirProgress, duel.target)
        if (duel.pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Accepter") }
                OutlinedButton(onClick = onDecline, enabled = !busy) { Text("Refuser") }
            }
        }
    }
}

@Composable
private fun DuelBar(label: String, value: Long, target: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Nflix.Muted, fontSize = 11.sp, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))) {
            val ratio = if (target > 0) (value.toFloat() / target).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(ratio).fillMaxSize().clip(CircleShape).background(Nflix.Red))
        }
        Spacer(Modifier.width(8.dp))
        Text("$value/$target", fontSize = 11.sp)
    }
}

@Composable
private fun UnlockCard(item: UnlockItem, onReact: (String) -> Unit) {
    val accent = rarityColorOf(item.rarity)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
            .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(item.userId, item.name, 34)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${item.name} a débloqué « ${item.title} »", fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(timeAgoFr(item.unlockedAt), color = Nflix.Muted, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("👏", "🔥", "😮", "❤️").forEach { emoji ->
                val count = item.reactions[emoji] ?: 0
                val mine = emoji in item.mine
                Text(
                    "$emoji" + if (count > 0) " $count" else "",
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (mine) Nflix.Red.copy(alpha = .25f) else Color.White.copy(alpha = .08f))
                        .clickable { onReact(emoji) }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDuelSheet(members: List<FamilyMemberInfo>, onDismiss: () -> Unit, onCreate: (String, String, Int, Int) -> Unit) {
    var opponent by remember { mutableStateOf(members.firstOrNull()?.userId) }
    var kind by remember { mutableStateOf("movies") }
    var target by remember { mutableStateOf(5f) }
    var days by remember { mutableStateOf(7) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lancer un défi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Choisis un proche", color = Nflix.Muted, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members, key = { it.userId }) { member ->
                    FilterChip(
                        selected = opponent == member.userId, onClick = { opponent = member.userId },
                        label = { Text(member.displayName.ifBlank { member.userName }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Nflix.Red, selectedLabelColor = Color.White),
                    )
                }
            }
            Text("Quoi ?", color = Nflix.Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("movies" to "Films", "episodes" to "Épisodes", "hours" to "Heures").forEach { (key, label) ->
                    FilterChip(
                        selected = kind == key, onClick = { kind = key }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Nflix.Red, selectedLabelColor = Color.White),
                    )
                }
            }
            Text("Objectif : ${target.toInt()} " + when (kind) { "movies" -> "films"; "episodes" -> "épisodes"; else -> "heures" }, fontSize = 14.sp)
            Slider(value = target, onValueChange = { target = it }, valueRange = 1f..20f, steps = 18, colors = SliderDefaults.colors(thumbColor = Nflix.Red, activeTrackColor = Nflix.Red))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 14).forEach { d ->
                    FilterChip(
                        selected = days == d, onClick = { days = d }, label = { Text("$d jours") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Nflix.Red, selectedLabelColor = Color.White),
                    )
                }
            }
            Button(
                onClick = { opponent?.let { onCreate(it, kind, target.toInt(), days) } },
                enabled = opponent != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Envoyer le défi") }
        }
    }
}

/** "Ton année Nflix": a few full-screen story slides built from the records and the level. */
@Composable
internal fun YearRecapScreen(hub: AchievementHub, onClose: () -> Unit) {
    var nflix by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.NflixAchievements?>(null) }
    var records by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.StatRecords?>(null) }
    LaunchedEffect(hub) {
        nflix = runCatching { hub.nflix() }.getOrNull()
        records = runCatching { hub.records() }.getOrNull()
    }
    val slides = listOfNotNull(
        "${records?.get("TotalHoursWatched") ?: 0}" to "heures passées sur Nflix",
        "${records?.get("MoviesWatched") ?: 0}" to "films regardés",
        "${records?.get("SeriesCompleted") ?: 0}" to "séries terminées",
        "${records?.get("MaxEpisodesInSingleDay") ?: 0}" to "épisodes en un seul jour (record)",
        "${nflix?.unlocked ?: 0}" to "succès débloqués",
        "${nflix?.level?.level ?: 0}" to (nflix?.level?.title ?: "ton niveau"),
    )
    var index by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A0507), Color(0xFF050505))))
            .clickable { if (index < slides.lastIndex) index++ else onClose() },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            slides.indices.forEach { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(if (i <= index) Nflix.Red else Color.White.copy(alpha = .2f)))
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp)) {
            Icon(Icons.Rounded.Close, "Fermer", tint = Color.White)
        }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ton année Nflix", color = Nflix.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Text(slides[index].first, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(slides[index].second, fontSize = 20.sp, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(if (index < slides.lastIndex) "Touche pour continuer" else "Tout a été dit — touche pour fermer", color = Nflix.Muted, fontSize = 12.sp)
        }
    }
}
