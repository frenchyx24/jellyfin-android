package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.AchievementPublicConfig
import org.jellyfin.mobile.nflix.data.ActionResult
import org.jellyfin.mobile.nflix.data.LeaderboardRow
import org.jellyfin.mobile.nflix.data.Quest
import org.jellyfin.mobile.nflix.data.ShopItem
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.value
import java.util.Locale

/** Sections of the achievements hub; [admin] ones only for administrators. */
enum class HubTab(val label: String, val admin: Boolean = false) {
    Quests("Quêtes"), Leaderboard("Classement"), Activity("Activité"), Shop("Boutique"), Admin("Administration", admin = true)
}

/** Tabs a member can open, given the server's public switches. */
fun visibleHubTabs(config: AchievementPublicConfig, isAdmin: Boolean): List<HubTab> = HubTab.entries.filter { tab ->
    when (tab) {
        HubTab.Quests -> config.quests
        HubTab.Leaderboard -> config.leaderboard && !config.privacy
        HubTab.Activity -> config.activityFeed && !config.privacy
        HubTab.Shop -> true
        HubTab.Admin -> isAdmin
    }
}

/** Jellyfin administrator, and in the Mira admin group when signed in through Mira. */
fun achievementAdmin(jellyfinAdmin: Boolean, method: String?, miraGroups: Set<String>): Boolean =
    jellyfinAdmin && (method != "mira" || miraGroups.any { it.equals("authentik Admins", ignoreCase = true) })

@Composable
fun AchievementHubScreen(start: String) {
    val deps = LocalDeps.current
    val hub = remember(deps.api) { AchievementHub(deps.api) }
    val config = rememberLoad("ab-config") { hub.publicConfig() }.value ?: AchievementPublicConfig()
    val jellyfinAdmin = rememberLoad("is-admin") { deps.admin.isAdmin(it) }.value == true
    val isAdmin = achievementAdmin(jellyfinAdmin, deps.session.lastMethod, deps.session.miraGroups)
    val tabs = visibleHubTabs(config, isAdmin)
    var current by rememberSaveable { mutableStateOf(start) }
    val index = tabs.indexOfFirst { it.name == current }.coerceAtLeast(0)
    val tab = tabs.getOrNull(index) ?: HubTab.Shop
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(start = 60.dp, top = 10.dp)) {
                Text("Succès", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (config.welcome.isNotBlank()) Text(config.welcome, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2)
            }
            ScrollableTabRow(
                selectedTabIndex = index, edgePadding = 12.dp, containerColor = Color.Transparent, contentColor = Nflix.Text, divider = {},
                indicator = { positions -> if (index < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[index]), color = Nflix.Red) },
            ) {
                tabs.forEach { t -> Tab(selected = t == tab, onClick = { current = t.name }, text = { Text(t.label) }) }
            }
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "hub-tab", modifier = Modifier.weight(1f)) { shown ->
                when (shown) {
                    HubTab.Quests -> QuestsTab(hub)
                    HubTab.Leaderboard -> LeaderboardTab(hub, config)
                    HubTab.Activity -> ActivityTab(hub)
                    HubTab.Shop -> ShopTab(hub, config)
                    HubTab.Admin -> AchievementAdminTab(hub)
                }
            }
        }
        BackButton()
    }
}

/** Runs an action, then shows its message in a small banner. */
@Composable
internal fun rememberActionRunner(onDone: () -> Unit): Triple<Boolean, String?, (suspend () -> ActionResult) -> Unit> {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val run: (suspend () -> ActionResult) -> Unit = { block ->
        if (!busy) {
            busy = true
            message = null
            scope.launch {
                try {
                    val result = block()
                    message = result.message ?: if (result.success) "C'est fait." else "Action refusée."
                    if (result.success) onDone()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = achievementError(error)
                } finally {
                    busy = false
                }
            }
        }
    }
    return Triple(busy, message, run)
}

@Composable
internal fun Banner(message: String?) {
    if (message == null) return
    Surface(color = Nflix.SurfaceHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

internal fun LazyListScope.loadingState(load: Load<*>, onRetry: () -> Unit) {
    when (load) {
        is Load.Loading -> item("loading") { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp)) } } }
        is Load.Failed -> item("failed") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(load.message, color = Nflix.Muted, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("Réessayer", color = Nflix.Red) }
            }
        }
        else -> Unit
    }
}

@Composable
private fun QuestsTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("quests", reloader = reloader) { hub.quests() }
    val board = load.value
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        if (board != null) {
            item("daily") { QuestHeader("Quêtes du jour", board.dailyRerolls, busy) { run { hub.reroll(weekly = false) } } }
            if (board.daily.isEmpty()) item("d-empty") { EmptyLine("Aucune quête aujourd'hui.") }
            itemsIndexed(board.daily, key = { _, q -> "d-${q.id}" }) { i, q -> QuestCard(q, Modifier.achievementAppear(i)) }
            item("weekly") { QuestHeader("Quêtes de la semaine", board.weeklyRerolls, busy) { run { hub.reroll(weekly = true) } } }
            if (board.weekly.isEmpty()) item("w-empty") { EmptyLine("Aucune quête cette semaine.") }
            itemsIndexed(board.weekly, key = { _, q -> "w-${q.id}" }) { i, q -> QuestCard(q, Modifier.achievementAppear(i)) }
            item("note") { EmptyLine("Chaque quête terminée crédite votre banque de score, à dépenser dans la boutique.") }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun QuestHeader(title: String, rerolls: Int, busy: Boolean, onReroll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onReroll, enabled = rerolls > 0 && !busy) {
            Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp), tint = if (rerolls > 0) Nflix.Red else Nflix.Muted)
            Spacer(Modifier.width(6.dp))
            Text(if (rerolls > 0) "Relancer" else "Relance utilisée", color = if (rerolls > 0) Nflix.Red else Nflix.Muted)
        }
    }
}

@Composable
private fun QuestCard(quest: Quest, modifier: Modifier = Modifier) {
    val reduced = rememberAchievementReducedMotion()
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).then(if (quest.completed) Modifier.rarityGlow(Nflix.Success, 2, animated = false) else Modifier).clip(CircleShape)
                .background(if (quest.completed) Nflix.Success.copy(alpha = 0.2f) else Nflix.Red.copy(alpha = 0.16f))
                .achievementShine(enabled = quest.completed && !reduced, seed = quest.id.hashCode()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (quest.completed) Icons.Rounded.CheckCircle else achievementIcon(quest.icon), null, tint = if (quest.completed) Nflix.Success else Nflix.Red)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(quest.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (quest.description.isNotBlank()) Text(quest.description, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            AchievementProgressBar(quest.progress, if (quest.completed) Nflix.Success else Nflix.Red)
            Text("${quest.current} / ${quest.target}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Text("+${quest.reward}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Nflix.Warning)
    }
}

@Composable
private fun EmptyLine(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

@Composable
private fun LeaderboardTab(hub: AchievementHub, config: AchievementPublicConfig) {
    val deps = LocalDeps.current
    var prestige by rememberSaveable { mutableStateOf(false) }
    var compareWith by remember { mutableStateOf<String?>(null) }
    val reloader = rememberReloader()
    val load = rememberLoad("leaderboard", prestige, reloader = reloader) { if (prestige) hub.prestigeLeaderboard() else hub.leaderboard() }
    val stats = rememberLoad("ab-stats") { hub.serverStats() }.value
    val me = deps.session.userId?.replace("-", "")
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        stats?.let { s ->
            item("stats") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AchievementStatTile("Membres", s.users, Modifier.weight(1f))
                    AchievementStatTile("Succès", s.unlocked, Modifier.weight(1f), "débloqués au total")
                    AchievementStatTile("Titres vus", s.itemsWatched, Modifier.weight(1f))
                }
            }
        }
        item("mode") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubChip("Score", !prestige) { prestige = false }
                if (config.prestige) HubChip("Prestige", prestige) { prestige = true }
            }
        }
        load.value?.let { rows ->
            if (rows.isEmpty()) item("empty") { EmptyLine("Personne au classement pour l'instant.") }
            itemsIndexed(rows, key = { i, r -> "lb-$i-${r.userId}" }) { i, row ->
                LeaderRow(i + 1, row, row.userId.replace("-", "").equals(me, true), prestige, Modifier.achievementAppear(i)) {
                    if (config.compare && !row.userId.replace("-", "").equals(me, true)) compareWith = row.userId
                }
            }
        }
        loadingState(load) { reloader.reload() }
        if (config.compare) item("compare-pick") { ComparePicker(hub) { compareWith = it } }
    }
    compareWith?.let { other -> CompareDialog(hub, other) { compareWith = null } }
}

@Composable
internal fun HubChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Nflix.Red else Nflix.SurfaceHigh, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun LeaderRow(position: Int, row: LeaderboardRow, mine: Boolean, prestige: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val medal = when (position) { 1 -> Color(0xFFFFC94D); 2 -> Color(0xFFC8CDD6); 3 -> Color(0xFFD08A4E); else -> Nflix.Muted }
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (mine) Nflix.Red.copy(alpha = 0.18f) else Nflix.Surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position <= 3) {
            Box(Modifier.width(44.dp)) {
                Box(
                    Modifier.size(34.dp).rarityGlow(medal, 3, animated = false).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(medal.copy(alpha = 0.55f), medal.copy(alpha = 0.15f))))
                        .border(1.5.dp, medal, CircleShape)
                        .achievementShine(enabled = !rememberAchievementReducedMotion(), seed = position * 1400),
                    contentAlignment = Alignment.Center,
                ) { Text("$position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White) }
            }
        } else {
            Text("#$position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = medal, modifier = Modifier.width(44.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.name + if (mine) " (vous)" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (prestige) "Prestige ${row.prestige}" else listOfNotNull(row.customTitle, "${row.unlocked}/${row.total} succès", row.bestStreak.takeIf { it > 0 }?.let { "record $it j" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1,
            )
        }
        Text("${row.score} pts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComparePicker(hub: AchievementHub, onPick: (String) -> Unit) {
    val members = rememberLoad("ab-directory") { hub.directory() }.value.orEmpty()
    if (members.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CompareArrows, null, tint = Nflix.Red)
            Spacer(Modifier.width(8.dp))
            Text("Se comparer à un membre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(members, key = { it.id }) { member -> HubChip(member.name, false) { onPick(member.id) } }
        }
    }
}

@Composable
private fun CompareDialog(hub: AchievementHub, other: String, onDismiss: () -> Unit) {
    val load = rememberLoad("compare", other) { hub.compare(other) }
    val profile = rememberLoad("public-profile", other) { hub.publicProfile(other) }.value
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Nflix.Red) } },
        containerColor = Nflix.SurfaceHigh,
        title = { Text("Comparaison") },
        text = {
            when (val state = load) {
                is Load.Ready -> {
                    val (comparison, error) = state.value
                    if (comparison == null) Text(error ?: "Comparaison impossible.")
                    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(comparison.a.name, "${comparison.a.score} pts", Modifier.weight(1f), "${comparison.a.unlocked}/${comparison.a.total}")
                            StatTile(comparison.b.name, "${comparison.b.score} pts", Modifier.weight(1f), "${comparison.b.unlocked}/${comparison.b.total}")
                        }
                        Text("En commun : ${comparison.both} · Vous seul : ${comparison.onlyA} · ${comparison.b.name} seul : ${comparison.onlyB}", style = MaterialTheme.typography.bodyMedium)
                        profile?.customTitle?.let { Text("Titre : $it", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted) }
                    }
                }
                is Load.Failed -> Text(state.message)
                else -> CircularProgressIndicator(color = Nflix.Red)
            }
        },
    )
}

@Composable
private fun ActivityTab(hub: AchievementHub) {
    var mine by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val reloader = rememberReloader()
    val load = rememberLoad("activity", mine, page, reloader = reloader) { hub.activity(page, mine) }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("mode") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubChip("Tout le monde", !mine) { mine = false; page = 1 }
                HubChip("Moi", mine) { mine = true; page = 1 }
            }
        }
        load.value?.let { data ->
            if (data.entries.isEmpty()) item("empty") { EmptyLine("Aucune activité récente.") }
            itemsIndexed(data.entries, key = { i, e -> "a-$page-$i" }) { i, entry ->
                Row(
                    Modifier.achievementAppear(i).padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).rarityGlow(rarityColor(entry.rarity ?: "Common"), rarityRank(entry.rarity ?: "Common"), animated = false)
                            .clip(CircleShape).background(rarityColor(entry.rarity ?: "Common").copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(achievementIcon(entry.icon), null, tint = rarityColor(entry.rarity ?: "Common"))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("${entry.userName} a débloqué « ${entry.title} »", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(listOfNotNull(entry.rarity?.let(::rarityName), entry.at?.let(::achievementDate)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                }
            }
            if (data.totalPages > 1) item("pages") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { page-- }, enabled = page > 1) { Text("Précédent") }
                    Text("$page / ${data.totalPages}", color = Nflix.Muted)
                    TextButton(onClick = { page++ }, enabled = page < data.totalPages) { Text("Suivant") }
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun ShopTab(hub: AchievementHub, config: AchievementPublicConfig) {
    val reloader = rememberReloader()
    val catalog = rememberLoad("shop", reloader = reloader) { hub.shop() }
    val cosmetics = rememberLoad("cosmetics", reloader = reloader) { hub.cosmetics() }.value
    val power = rememberLoad("powerups", reloader = reloader) { hub.powerUps() }.value
    val bank = rememberLoad("bank", reloader = reloader) { hub.bank() }.value
    var kind by rememberSaveable { mutableStateOf("PowerUp") }
    var confirm by remember { mutableStateOf<ShopItem?>(null) }
    var prestigeAsk by remember { mutableStateOf(false) }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("bank") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AchievementStatTile("Banque", bank?.scoreBank ?: cosmetics?.scoreBank ?: 0, Modifier.weight(1f), "à dépenser") { "$it pts" }
                AchievementStatTile("Score à vie", bank?.lifetimeScore ?: cosmetics?.lifetimeScore ?: 0, Modifier.weight(1f))
                AchievementStatTile("Prestige", bank?.prestigeLevel ?: 0, Modifier.weight(1f))
            }
        }
        item("msg") { Banner(message) }
        power?.inventory?.takeIf { it.isNotEmpty() }?.let { inventory ->
            item("inventory") {
                Text("Vos bonus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(inventory, key = { "inv-${it.type}" }) { p ->
                        Column(
                            Modifier.width(170.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
                                .achievementShine(enabled = p.active && !rememberAchievementReducedMotion(), seed = p.type.hashCode(), periodMs = 5200).padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bolt, null, tint = if (p.active) Nflix.Success else Nflix.Warning)
                                Spacer(Modifier.width(6.dp))
                                Text(p.name.ifBlank { p.type }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (p.active) "Actif" else "× ${p.count}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                            TextButton(onClick = { run { hub.usePowerUp(p.type) } }, enabled = p.count > 0 && !p.active && !busy) { Text("Utiliser", color = Nflix.Red) }
                        }
                    }
                }
            }
        }
        catalog.value?.let { shop ->
            val kinds = listOf("PowerUp") + shop.cosmetics.map { it.kind }.distinct()
            item("kinds") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(kinds, key = { "k-$it" }) { k -> HubChip(AchievementHub.cosmeticKindName(k), k == kind) { kind = k } }
                }
            }
            val list = if (kind == "PowerUp") shop.powerUps else shop.cosmetics.filter { it.kind == kind }
            itemsIndexed(list, key = { _, it -> "shop-${it.id}" }) { i, item ->
                val owned = cosmetics?.owned?.contains(item.id) == true
                val equipped = cosmetics?.equipped?.get(item.kind) == item.id
                ShopRow(item, owned, equipped, busy, Modifier.achievementAppear(i),
                    onBuy = { confirm = item },
                    onEquip = { run { if (equipped) hub.unequip(item.kind) else hub.equip(item.id) } })
            }
        }
        loadingState(catalog) { reloader.reload() }
        if (config.prestige) item("prestige") {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Nflix.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text("Prestige", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Repartez de zéro pour gagner un niveau de prestige et un bonus de score permanent.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                TextButton(onClick = { prestigeAsk = true }, enabled = !busy) { Text("Passer au prestige suivant", color = Nflix.Red) }
            }
        }
    }
    confirm?.let { item ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = Nflix.SurfaceHigh,
            title = { Text("Acheter « ${item.name} » ?") },
            text = { Text("${item.price} points seront retirés de votre banque.${if (item.bundle > 1) " Lot de ${item.bundle}." else ""}") },
            confirmButton = { TextButton(onClick = { confirm = null; run { hub.purchase(item.id) } }) { Text("Acheter", color = Nflix.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
    if (prestigeAsk) {
        AlertDialog(
            onDismissRequest = { prestigeAsk = false }, containerColor = Nflix.SurfaceHigh,
            title = { Text("Passer au prestige ?") },
            text = { Text("Vos succès repartent de zéro. Votre niveau de prestige augmente et multiplie vos futurs scores. Cette action est définitive.") },
            confirmButton = { TextButton(onClick = { prestigeAsk = false; run { hub.prestige() } }) { Text("Confirmer", color = Nflix.Red) } },
            dismissButton = { TextButton(onClick = { prestigeAsk = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ShopRow(item: ShopItem, owned: Boolean, equipped: Boolean, busy: Boolean, modifier: Modifier, onBuy: () -> Unit, onEquip: () -> Unit) {
    val swatch = item.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Nflix.Red
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(swatch.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(if (item.isPowerUp) Icons.Rounded.Bolt else Icons.Rounded.Storefront, null, tint = swatch)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2)
            item.milestone?.let { Text("Offert à $it points à vie", style = MaterialTheme.typography.labelSmall, color = Nflix.Warning) }
        }
        when {
            !item.isPowerUp && owned -> Button(
                onClick = onEquip, enabled = !busy, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (equipped) Nflix.SurfaceHigh else Color.White, contentColor = if (equipped) Color.White else Color.Black),
            ) { Text(if (equipped) "Retirer" else "Équiper") }
            item.price > 0 -> Button(onClick = onBuy, enabled = !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red)) { Text("${item.price}") }
            else -> Text("Gratuit", color = Nflix.Muted)
        }
    }
}

@Composable
private fun AchievementAdminTab(hub: AchievementHub) {
    var section by rememberSaveable { mutableStateOf("features") }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("features" to "Fonctions", "catalog" to "Catalogue", "quests" to "Quêtes", "challenges" to "Défis", "custom" to "Badges perso", "members" to "Membres", "webhook" to "Webhook", "audit" to "Journal"), key = { it.first }) { (id, label) ->
                HubChip(label, section == id) { section = id }
            }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                "features" -> AdminFeatures(hub)
                "catalog" -> AdminCatalog(hub)
                "quests" -> AdminQuestsSection(hub)
                "challenges" -> AdminChallenges(hub)
                "custom" -> AdminCustomBadges(hub)
                "members" -> AdminMembers(hub)
                "webhook" -> AdminWebhook(hub)
                else -> AdminAudit(hub)
            }
        }
    }
}

@Composable
private fun AdminSwitch(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
            .clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, null, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
    }
}

@Composable
private fun AdminFeatures(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-features", reloader = reloader) { hub.featureConfig() }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        load.value?.let { config ->
            items(FeatureConfig.FLAGS, key = { it.first }) { (key, label) ->
                val default = !key.startsWith("Force")
                AdminSwitch(label, config.flag(key, default), !busy) { value -> run { hub.saveFeatureConfig(config, mapOf(key to JsonPrimitive(value))) } }
            }
            item("max") {
                val max = config.number("MaxEquippedBadges").takeIf { it > 0 } ?: 5
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Badges en vitrine : $max", modifier = Modifier.weight(1f))
                    TextButton(onClick = { run { hub.saveFeatureConfig(config, mapOf("MaxEquippedBadges" to JsonPrimitive((max - 1).coerceAtLeast(1)))) } }, enabled = !busy && max > 1) { Text("−") }
                    TextButton(onClick = { run { hub.saveFeatureConfig(config, mapOf("MaxEquippedBadges" to JsonPrimitive((max + 1).coerceAtMost(10)))) } }, enabled = !busy && max < 10) { Text("+") }
                }
            }
            item("welcome") {
                var text by remember(config) { mutableStateOf(config.text("WelcomeMessage")) }
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(text, { text = it.take(500) }, label = { Text("Message d'accueil") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { run { hub.saveFeatureConfig(config, mapOf("WelcomeMessage" to JsonPrimitive(text.trim()))) } }, enabled = !busy) { Text("Enregistrer", color = Nflix.Red) }
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminCatalog(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-catalog", reloader = reloader) { hub.adminCatalog() }
    var query by rememberSaveable { mutableStateOf("") }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    var confirm by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    confirm?.let { (label, ids) ->
        ConfirmDialog("$label ?", "Cela modifie ${if (query.isBlank()) "tout le catalogue" else "tous les badges filtrés"}. Les choix précédents seront remplacés.", "Confirmer", { confirm = null }) {
            run { hub.setDisabledBadges(ids) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        item("search") { OutlinedTextField(query, { query = it }, label = { Text("Rechercher un badge") }, singleLine = true, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) }
        load.value?.let { all ->
            val list = all.filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }
            item("bulk") {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { confirm = "Tout activer" to all.filter { it.disabled && it !in list }.map { it.id } }, enabled = !busy && list.isNotEmpty()) { Text("Tout activer (filtre)") }
                    TextButton(onClick = { confirm = "Tout désactiver" to (all.filter { it.disabled } + list).map { it.id }.distinct() }, enabled = !busy && list.isNotEmpty()) { Text("Tout désactiver (filtre)") }
                }
            }
            itemsIndexed(list, key = { i, b -> "cat-$i-${b.id}" }) { _, badge ->
                AdminSwitch("${badge.title} · ${categoryName(badge.category)}", !badge.disabled, !busy) { on -> run { hub.toggleBadge(badge.id, !on) } }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminQuestsSection(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-admin-quests", reloader = reloader) { hub.adminQuests() }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("3") }
    var reward by remember { mutableStateOf("20") }
    var weekly by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        load.value?.let { data ->
            val disabled = data.quests.filter { it.disabled }.map { it.id }.toSet()
            itemsIndexed(data.quests, key = { i, q -> "q-$i-${q.builtIn}-${q.weekly}-${q.id}" }) { _, quest ->
                AdminSwitch("${if (quest.weekly) "Semaine" else "Jour"} · ${quest.title}${if (quest.builtIn) "" else " (perso)"}", !quest.disabled, !busy) { on ->
                    run { hub.saveQuests(data, if (on) disabled - quest.id else disabled + quest.id) }
                }
            }
            item("new") {
                Column(Modifier.padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nouvelle quête (titres regardés)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(target, { target = it.filter(Char::isDigit).take(4) }, label = { Text("Objectif") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(reward, { reward = it.filter(Char::isDigit).take(4) }, label = { Text("Récompense") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HubChip("Jour", !weekly) { weekly = false }; Spacer(Modifier.width(8.dp)); HubChip("Semaine", weekly) { weekly = true }
                    }
                    TextButton(enabled = !busy && title.isNotBlank() && (target.toIntOrNull() ?: 0) > 0, onClick = {
                        val quest = kotlinx.serialization.json.buildJsonObject {
                            put("Id", JsonPrimitive("nflix-" + System.currentTimeMillis().toString(36)))
                            put("Title", JsonPrimitive(title.trim())); put("Description", JsonPrimitive("Regarder ${target} titres"))
                            put("Metric", JsonPrimitive("TotalItemsWatched")); put("Target", JsonPrimitive(target.toInt()))
                            put("Reward", JsonPrimitive(reward.toIntOrNull() ?: 20)); put("Icon", JsonPrimitive("play_circle"))
                        }
                        val daily = data.quests.filter { !it.builtIn && !it.weekly }.map { it.raw }
                        val weeklyList = data.quests.filter { !it.builtIn && it.weekly }.map { it.raw }
                        run { hub.saveQuests(data, disabled, if (weekly) daily else daily + quest, if (weekly) weeklyList + quest else weeklyList) }
                        title = ""
                    }) { Text("Ajouter", color = Nflix.Red) }
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminChallenges(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-challenges", reloader = reloader) { hub.challenges() }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("10") }
    var days by remember { mutableStateOf("7") }
    var removing by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        load.value?.let { list ->
            if (list.isEmpty()) item("none") { EmptyLine("Aucun défi en cours.") }
            removing?.let { challenge ->
                item("confirm-remove") {
                    ConfirmDialog("Retirer ce défi ?", "Le défi disparaît pour tous les membres.", "Retirer", { removing = null }) { run { hub.saveChallenges(list - challenge) } }
                }
            }
            itemsIndexed(list, key = { i, c -> "ch-$i-" + (c["Id"]?.toString() ?: "") }) { _, challenge ->
                val name = (challenge["Title"] as? JsonPrimitive)?.content ?: "Défi"
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Nflix.Warning)
                    Text(name, Modifier.weight(1f).padding(horizontal = 10.dp))
                    TextButton(onClick = { removing = challenge }, enabled = !busy) { Text("Retirer", color = Nflix.Red) }
                }
            }
            item("new") {
                Column(Modifier.padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nouveau défi (titres regardés)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(target, { target = it.filter(Char::isDigit).take(4) }, label = { Text("Objectif") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) }, label = { Text("Durée (jours)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    TextButton(enabled = !busy && title.isNotBlank() && (target.toIntOrNull() ?: 0) > 0 && (days.toIntOrNull() ?: 0) > 0, onClick = {
                        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                        val challenge = kotlinx.serialization.json.buildJsonObject {
                            put("Id", JsonPrimitive("nflix-challenge-" + System.currentTimeMillis().toString(36)))
                            put("Key", JsonPrimitive("nflix-challenge")); put("Title", JsonPrimitive(title.trim()))
                            put("Description", JsonPrimitive("Regarder $target titres en $days jours")); put("Icon", JsonPrimitive("emoji_events"))
                            put("Category", JsonPrimitive("Challenge")); put("Rarity", JsonPrimitive("Rare"))
                            put("TargetValue", JsonPrimitive(target.toInt())); put("Metric", JsonPrimitive("TotalItemsWatched"))
                            put("IsChallenge", JsonPrimitive(true)); put("ChallengeStart", JsonPrimitive(now.toString()))
                            put("ChallengeEnd", JsonPrimitive(now.plusDays(days.toLong()).toString()))
                        }
                        run { hub.saveChallenges(list + challenge) }
                        title = ""
                    }) { Text("Lancer le défi", color = Nflix.Red) }
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminCustomBadges(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-custom", reloader = reloader) { hub.customBadges() }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("10") }
    var rarity by remember { mutableStateOf("Rare") }
    var metric by remember { mutableStateOf(AchievementHub.METRICS.first().first) }
    var deleting by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.CustomBadgeItem?>(null) }
    deleting?.let { badge ->
        ConfirmDialog("Supprimer « ${badge.name} » ?", "Le badge est retiré de tous les profils qui l'ont gagné. Cette action est définitive.", "Supprimer", { deleting = null }) {
            run { hub.deleteCustomBadge(badge.id) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        load.value?.let { list ->
            items(list, key = { "cb-${it.id}" }) { badge ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(badge.name, style = MaterialTheme.typography.titleSmall)
                        Text("${rarityName(badge.rarity)}${if (badge.enabled) "" else " · désactivé"}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                    TextButton(onClick = { deleting = badge }, enabled = !busy) { Text("Supprimer", color = Nflix.Red) }
                }
            }
        }
        item("new") {
            Column(Modifier.padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nouveau badge personnalisé", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(200) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(AchievementHub.METRICS, key = { it.first }) { (id, label) -> HubChip(label, metric == id) { metric = id } } }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(AchievementHub.RARITIES, key = { it }) { r -> HubChip(rarityName(r), rarity == r) { rarity = r } } }
                OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit).take(6) }, label = { Text("Seuil") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !busy && name.isNotBlank() && (threshold.toIntOrNull() ?: 0) > 0, onClick = {
                    run { hub.createCustomBadge(name, description, rarity, metric, threshold.toInt()) }
                    name = ""; description = ""
                }) { Text("Créer", color = Nflix.Red) }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminMembers(hub: AchievementHub) {
    val members = rememberLoad("ab-directory-admin") { hub.directory() }
    val (busy, message, run) = rememberActionRunner { }
    var amount by remember { mutableStateOf("50") }
    var reset by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.Member?>(null) }
    var granting by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.Member?>(null) }
    granting?.let { member ->
        val value = amount.toIntOrNull() ?: 0
        ConfirmDialog(if (value > 0) "Donner $value points à ${member.name} ?" else "Retirer ${-value} points à ${member.name} ?", "Le score est crédité dans sa banque.", "Confirmer", { granting = null }) {
            run { hub.grantScore(member.id, value) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        item("amount") {
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '-' }.take(7) }, label = { Text("Points à attribuer (négatif pour retirer)") }, singleLine = true, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        }
        members.value?.let { list ->
            items(list, key = { "m-${it.id}" }) { member ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups, null, tint = Nflix.Muted)
                    Text(member.name, Modifier.weight(1f).padding(horizontal = 10.dp))
                    TextButton(onClick = { granting = member }, enabled = !busy && (amount.toIntOrNull() ?: 0) != 0) { Text("Attribuer") }
                    TextButton(onClick = { reset = member }, enabled = !busy) { Text("Réinitialiser", color = Nflix.Red) }
                }
            }
        }
        loadingState(members) { }
    }
    reset?.let { member ->
        AlertDialog(
            onDismissRequest = { reset = null }, containerColor = Nflix.SurfaceHigh,
            title = { Text("Réinitialiser ${member.name} ?") },
            text = { Text("Tous ses succès, scores et quêtes seront effacés. Cette action est définitive.") },
            confirmButton = { TextButton(onClick = { reset = null; run { hub.resetUser(member.id) } }) { Text("Réinitialiser", color = Nflix.Red) } },
            dismissButton = { TextButton(onClick = { reset = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun AdminWebhook(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-webhook", reloader = reloader) { hub.webhook() }
    val (busy, message, run) = rememberActionRunner { reloader.reload() }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        item("msg") { Banner(message) }
        load.value?.let { config ->
            item("form") {
                var url by remember(config) { mutableStateOf("") }
                var template by remember(config) { mutableStateOf(config.template.orEmpty()) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminSwitch("Webhook activé", config.enabled, !busy) { on -> run { hub.saveWebhook(config, on, null, null) } }
                    Text("Adresse actuelle : ${config.maskedUrl ?: "aucune"}", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("Nouvelle adresse (laisser vide pour garder)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(template, { template = it.take(1000) }, label = { Text("Modèle du message (vide : inchangé)") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { run { hub.saveWebhook(config, config.enabled, url.ifBlank { null }, template.ifBlank { null }) } }, enabled = !busy) { Text("Enregistrer", color = Nflix.Red) }
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
private fun AdminAudit(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("ab-audit", reloader = reloader) { hub.auditLog() }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        load.value?.let { list ->
            if (list.isEmpty()) item("none") { EmptyLine("Journal vide.") }
            itemsIndexed(list, key = { i, _ -> "au-$i" }) { _, entry ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nflix.Surface).padding(12.dp)) {
                    Text("${entry.type} · ${entry.user}", style = MaterialTheme.typography.labelLarge)
                    Text(listOfNotNull(entry.at?.let(::achievementDate), entry.details.take(200).takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 3)
                }
            }
        }
        loadingState(load) { reloader.reload() }
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Nflix.SurfaceHigh,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = Nflix.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private typealias FeatureConfig = org.jellyfin.mobile.nflix.data.FeatureConfig

@Suppress("unused")
private fun percent(value: Double) = String.format(Locale.FRANCE, "%.0f %%", value)
