package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.family.FamilyMe
import org.jellyfin.mobile.nflix.family.FamilyMemberInfo
import org.jellyfin.mobile.nflix.family.ParentalRulesInfo
import org.jellyfin.mobile.nflix.family.PartyInfo
import org.jellyfin.mobile.nflix.family.PartySession
import org.jellyfin.mobile.nflix.family.UsageRowInfo
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

/** Family 1.4 block on the profiles tab: live parties, presence, weekly report and parental control (heads). */
@Composable
fun FamilySocialSection(me: FamilyMe, switched: Boolean) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var parties by remember { mutableStateOf<List<PartyInfo>>(emptyList()) }
    var online by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var report by remember { mutableStateOf<List<UsageRowInfo>>(emptyList()) }
    var parentalFor by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    LaunchedEffect(me.userId) {
        parties = deps.social.parties()
        online = runCatching { deps.social.heartbeat() }.getOrDefault(emptyMap())
        if (me.isHead && !switched) report = runCatching { deps.social.weeklyReport() }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val members = me.family?.members.orEmpty()
        val onlineNames = members.filter { online[it.userId.replace("-", "")] == true || online[it.userId] == true }.map { it.displayName }
        if (onlineNames.isNotEmpty()) Text("En ligne : " + onlineNames.joinToString(", "))
        if (parties.isNotEmpty()) {
            Text("Soirées en cours", fontWeight = FontWeight.SemiBold)
            parties.forEach { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Soirée de ${p.hostName} · ${p.members.size} participant(s)")
                    TextButton(onClick = {
                        PartySession.partyId = p.id
                        PartySession.myUserId = me.userId
                        scope.launch {
                            runCatching { deps.social.party(p.id) }.onSuccess { joined ->
                                deps.playVideos(scope, listOf(joined.itemId), 0, joined.positionTicks)
                            }.onFailure { Toasts.show(it.message ?: "Impossible de rejoindre.", ToastKind.Warning) }
                        }
                    }) { Text("Rejoindre") }
                }
            }
        }
        if (me.isHead && !switched && report.isNotEmpty()) {
            Text("Cette semaine (classement)", fontWeight = FontWeight.SemiBold)
            report.forEachIndexed { i, r ->
                Text("${i + 1}. ${r.name} — ${formatMinutes(r.weekMinutes)} (aujourd'hui ${formatMinutes(r.todayMinutes)}, ${r.activeDays} j actifs)")
            }
        }
        if (me.isHead && !switched) {
            members.filter { it.isKid }.forEach { kid ->
                TextButton(onClick = { parentalFor = kid }) { Text("Contrôle parental · ${kid.displayName}") }
            }
        }
    }
    FamilyMoreSection(me, switched)
    parentalFor?.let { kid -> ParentalDialog(kid) { parentalFor = null } }
}

fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"

@Composable
private fun ParentalDialog(kid: FamilyMemberInfo, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf<String?>("12") }
    var from by remember { mutableFloatStateOf(8f) }
    var to by remember { mutableFloatStateOf(21f) }
    var limit by remember { mutableFloatStateOf(120f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contrôle parental · ${kid.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Classification maximale")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("TP", "10", "12", "16").forEach { r -> FilterChip(selected = rating == r, onClick = { rating = r }, label = { Text(r) }) }
                }
                Text("Horaires : ${from.toInt()} h – ${to.toInt()} h")
                Slider(value = from, onValueChange = { from = it.coerceAtMost(to - 1) }, valueRange = 0f..23f, steps = 22)
                Slider(value = to, onValueChange = { to = it.coerceAtLeast(from + 1) }, valueRange = 1f..24f, steps = 22)
                Text("Limite par jour : " + if (limit.toInt() == 0) "aucune" else formatMinutes(limit.toInt()))
                Slider(value = limit, onValueChange = { limit = it }, valueRange = 0f..480f, steps = 15)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        deps.social.setParental(kid.userId, ParentalRulesInfo(rating, from.toInt(), to.toInt(), limit.toInt()))
                    }.onSuccess { Toasts.show("Règles enregistrées.", ToastKind.Success); onDismiss() }
                        .onFailure { Toasts.show(it.message ?: "Échec.", ToastKind.Warning) }
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Starts a watch party on [item] and plays it as host. */
fun startParty(deps: org.jellyfin.mobile.nflix.ui.AppDeps, scope: kotlinx.coroutines.CoroutineScope, item: Item) {
    scope.launch {
        runCatching { deps.social.createParty(item.id) }.onSuccess { p ->
            PartySession.partyId = p.id
            PartySession.myUserId = deps.session.userId.orEmpty()
            Toasts.show("Soirée lancée : la famille peut vous rejoindre.", ToastKind.Success)
            deps.play(scope, item)
        }.onFailure { Toasts.show(it.message ?: "Impossible de lancer la soirée.", ToastKind.Warning) }
    }
}
