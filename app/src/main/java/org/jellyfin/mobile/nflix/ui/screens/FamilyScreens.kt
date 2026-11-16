package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.family.ActivityInfo
import org.jellyfin.mobile.nflix.family.ConversationInfo
import org.jellyfin.mobile.nflix.family.FamilyBadges
import org.jellyfin.mobile.nflix.family.FamilyException
import org.jellyfin.mobile.nflix.family.FamilyFormat
import org.jellyfin.mobile.nflix.family.FamilyMe
import org.jellyfin.mobile.nflix.family.FamilyMemberInfo
import org.jellyfin.mobile.nflix.family.FamilyUser
import org.jellyfin.mobile.nflix.family.FriendsInfo
import org.jellyfin.mobile.nflix.family.MessageInfo
import org.jellyfin.mobile.nflix.family.ProfileSwitchLogic
import org.jellyfin.mobile.nflix.ui.AvatarImage
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

private val ProfileColors = listOf("#E50914", "#F5A623", "#46D39A", "#6CB6FF", "#B388FF", "#FF6F91", "#00BFA5", "#8D6E63")

private fun parseColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#E50914")) }.getOrDefault(Nflix.BrandRed)

/** Runs a family call and turns its error into a toast. Returns true on success. */
private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: FamilyException) {
    Toasts.show(e.message ?: "Erreur", ToastKind.Warning)
    false
}

private enum class FamilyTab(val label: String) { Profiles("Profils"), Requests("Demandes"), Friends("Amis"), Messages("Messages") }

/** "Famille": profiles, requests, friends and messages. */
@Composable
fun FamilyScreen() {
    val deps = LocalDeps.current
    val switched = deps.switcher.isSwitched
    var tab by remember { mutableStateOf(FamilyTab.Profiles) }
    var me by remember { mutableStateOf<FamilyMe?>(FamilyBadges.me) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { reload++ }
    LaunchedEffect(reload) {
        try {
            me = deps.family.me().also { if (!switched) deps.switcher.store.lastKnownHead = it.isHead }
            error = null
        } catch (e: FamilyException) {
            error = e.message
        }
    }

    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.statusBarsPadding().padding(start = 64.dp, end = 12.dp, top = 14.dp, bottom = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Famille", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Actualiser") }
            }
            if (switched) SwitchBackBanner()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FamilyTab.entries.forEach { entry ->
                    val count = when (entry) {
                        FamilyTab.Requests -> (me?.pendingRequestCount ?: 0)
                        FamilyTab.Friends -> (me?.pendingFriendCount ?: 0)
                        FamilyTab.Messages -> (me?.unreadMessageCount ?: 0)
                        else -> 0
                    }
                    TabChip(entry.label, entry == tab, FamilyFormat.badgeText(count), Modifier.weight(1f)) { tab = entry }
                }
            }
            val current = me
            when {
                current == null && error != null -> ErrorBox(error!!, refresh)
                current == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) }
                else -> when (tab) {
                    FamilyTab.Profiles -> ProfilesTab(current, switched, refresh)
                    FamilyTab.Requests -> RequestsTab(current, switched, refresh)
                    FamilyTab.Friends -> FriendsTab(refresh)
                    FamilyTab.Messages -> MessagesTab()
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (selected) Nflix.Red else Nflix.SurfaceHigh).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            if (badge != null) {
                Spacer(Modifier.width(4.dp))
                Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Nflix.Red else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Color.White else Nflix.Red).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Nflix.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = retry) { Text("Réessayer") }
    }
}

/** Visible while a head uses another profile. */
@Composable
fun SwitchBackBanner(modifier: Modifier = Modifier) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val state = deps.switcher.state ?: return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Nflix.SurfaceHigh).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Profil « ${state.activeName} »", fontWeight = FontWeight.SemiBold, color = Nflix.Text, fontSize = 14.sp)
            Text("Profil principal : ${state.head.userName}", color = Nflix.Muted, fontSize = 12.sp)
        }
        Button(
            onClick = { scope.launch { attempt { deps.switcher.switchBack() } } },
            enabled = !deps.switcher.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
        ) { Text("Revenir au profil principal", fontSize = 12.sp) }
    }
}

// ------------------------------------------------------------------ profiles

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilesTab(me: FamilyMe, switched: Boolean, refresh: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val family = me.family
    var editing by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    var pinFor by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    var inviteOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    val canSwitch = ProfileSwitchLogic.canSwitch(me.isHead, deps.switcher.state) && !switched

    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
        if (family == null) {
            item { NoFamily(refresh) }
            return@LazyColumn
        }
        item {
            Text(family.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            Text(
                if (me.isHead) "Touchez un profil pour l’ouvrir. Crayon : modifier le profil." else "Vous êtes membre de cette famille.",
                color = Nflix.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        item {
            FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                family.members.forEach { member ->
                    val isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId)
                    ProfileTile(
                        member = member,
                        isMe = isMe,
                        onClick = {
                            when {
                                isMe -> Unit
                                !canSwitch -> Unit
                                member.hasPin -> pinFor = member
                                else -> scope.launch { attempt { deps.switcher.switchTo(member, null) } }
                            }
                        },
                        onEdit = if ((me.isHead && !switched) || isMe) ({ editing = member }) else null,
                    )
                }
            }
        }
        if (me.isHead && !switched) {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { inviteOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red), modifier = Modifier.fillMaxWidth()) { Text("Inviter quelqu’un") }
                    PickerSetting()
                }
            }
            item { ActivitySection() }
        }
        if (!switched) {
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "Quitter la famille « ${family.name} » ?" to { if (attempt { deps.family.leave() }) refresh() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Quitter la famille")
                    }
                    if (me.isHead) {
                        OutlinedButton(onClick = { confirm = "Supprimer la famille « ${family.name} » ? Les messages de groupe seront effacés." to { if (attempt { deps.family.deleteFamily() }) refresh() } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Supprimer la famille", color = Nflix.Red)
                        }
                    }
                }
            }
        }
    }

    editing?.let { member ->
        ProfileEditDialog(member, isHead = me.isHead && !switched, isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId), onDismiss = { editing = null }) {
            editing = null
            refresh()
        }
    }
    pinFor?.let { member ->
        PinDialog("Code PIN de ${member.displayName}", onDismiss = { pinFor = null }) { pin ->
            scope.launch {
                if (attempt { deps.switcher.switchTo(member, pin) }) pinFor = null
            }
        }
    }
    if (inviteOpen) {
        UserSearchDialog("Inviter dans la famille", "Inviter", onDismiss = { inviteOpen = false }) { user ->
            scope.launch {
                if (attempt { deps.family.invite(user.id) }) {
                    Toasts.show("Invitation envoyée à ${user.name}", ToastKind.Success)
                    inviteOpen = false
                    refresh()
                }
            }
        }
    }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; scope.launch { action() } }) { Text("Confirmer", color = Nflix.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun NoFamily(refresh: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vous ne faites partie d’aucune famille.", color = Nflix.Text, fontWeight = FontWeight.SemiBold)
        Text("Créez la vôtre (vous en serez responsable) ou demandez à rejoindre celle d’un proche. Les invitations reçues sont dans « Demandes ».", color = Nflix.Muted, fontSize = 13.sp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Créer une famille", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nom de la famille") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { scope.launch { if (attempt { deps.family.createFamily(name.trim()) }) refresh() } },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
                ) { Text("Créer") }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rejoindre une famille", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(head, { head = it.take(64) }, label = { Text("Nom d’utilisateur du responsable") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        scope.launch {
                            if (attempt { deps.family.askToJoin(head.trim()) }) {
                                Toasts.show("Demande envoyée", ToastKind.Success)
                                head = ""
                                refresh()
                            }
                        }
                    },
                    enabled = head.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
                ) { Text("Demander à rejoindre") }
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: FamilyMemberInfo, size: Int) {
    val deps = LocalDeps.current
    val image = if (member.useUserImage && member.hasImage) deps.api.userImageUrl(member.userId, null) else null
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 6).dp)).background(parseColor(member.avatarColor)), contentAlignment = Alignment.Center) {
        AvatarImage(image, Modifier.size(size.dp)) {
            Text(FamilyFormat.initials(member.displayName), fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ProfileTile(member: FamilyMemberInfo, isMe: Boolean, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(if (isMe) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)).padding(2.dp) else Modifier) { MemberAvatar(member, 88) }
            if (member.hasPin) Icon(Icons.Rounded.Lock, "PIN", tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp))
            if (onEdit != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f)).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Edit, "Modifier", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(member.displayName, color = Nflix.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (isMe) "Vous" else FamilyFormat.roleLabel(member), color = Nflix.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PickerSetting() {
    val deps = LocalDeps.current
    var enabled by remember { mutableStateOf(deps.switcher.store.pickerEnabled) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("« Qui regarde ? » au démarrage", color = Nflix.Text, fontSize = 14.sp)
            Text("Choisir le profil à chaque ouverture de Nflix", color = Nflix.Muted, fontSize = 12.sp)
        }
        Switch(enabled, { enabled = it; deps.switcher.store.pickerEnabled = it })
    }
}

@Composable
private fun ActivitySection() {
    val deps = LocalDeps.current
    var open by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<ActivityInfo>?>(null) }
    LaunchedEffect(open) { if (open) list = runCatching { deps.family.activity() }.getOrDefault(emptyList()) }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        TextButton(onClick = { open = !open }) { Text(if (open) "Masquer le journal des changements de profil" else "Journal des changements de profil") }
        if (open) {
            val items = list
            when {
                items == null -> CircularProgressIndicator(color = Nflix.Red, modifier = Modifier.size(24.dp))
                items.isEmpty() -> Text("Aucun changement de profil.", color = Nflix.Muted, fontSize = 13.sp)
                else -> items.take(50).forEach { a ->
                    Text(
                        "${FamilyFormat.messageTime(a.utc)} · ${a.actorName} → ${a.targetName} · ${a.device}${if (a.success) "" else " · refusé (${a.detail})"}",
                        color = if (a.success) Nflix.Text else Nflix.Warning, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditDialog(member: FamilyMemberInfo, isHead: Boolean, isMe: Boolean, onDismiss: () -> Unit, onDone: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(member.displayName) }
    var color by remember { mutableStateOf(member.avatarColor ?: ProfileColors.first()) }
    var kid by remember { mutableStateOf(member.isKid) }
    var pin by remember { mutableStateOf("") }
    var clearPin by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil de ${member.userName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nom affiché") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileColors.forEach { hex ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(parseColor(hex))
                                .border(if (hex.equals(color, true)) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { color = hex },
                        )
                    }
                }
                if (isHead && !member.isHead) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Profil enfant", Modifier.weight(1f))
                        Switch(kid, { kid = it })
                    }
                }
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(8); clearPin = false },
                    label = { Text(if (member.hasPin) "Nouveau code PIN (4 à 8 chiffres)" else "Code PIN (facultatif)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (member.hasPin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Retirer le code PIN", Modifier.weight(1f))
                        Switch(clearPin, { clearPin = it; if (it) pin = "" })
                    }
                }
                if (isHead && !isMe) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.isHead) {
                            OutlinedButton(onClick = { scope.launch { if (attempt { deps.family.memberAction(member.userId, "demote") }) onDone() } }) { Text("Retirer responsable") }
                        } else {
                            OutlinedButton(onClick = { scope.launch { if (attempt { deps.family.memberAction(member.userId, "promote") }) onDone() } }) { Text("Nommer responsable") }
                            OutlinedButton(onClick = { scope.launch { if (attempt { deps.family.memberAction(member.userId, "remove") }) onDone() } }) { Text("Retirer", color = Nflix.Red) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val ok = attempt {
                        deps.family.updateProfile(
                            member.userId,
                            displayName = name.trim().takeIf { it != member.displayName },
                            color = color.takeIf { !it.equals(member.avatarColor, true) },
                            isKid = kid.takeIf { isHead && !member.isHead && it != member.isKid },
                            pin = pin.takeIf { it.isNotEmpty() },
                            clearPin = clearPin,
                        )
                    }
                    if (ok) onDone()
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Numeric PIN pad (4 to 8 digits). */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("•".repeat(pin.length).ifEmpty { " " }, fontSize = 30.sp, letterSpacing = 6.sp, color = Nflix.Text)
                Spacer(Modifier.height(10.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { key ->
                            Box(
                                Modifier.size(58.dp).clip(CircleShape).background(if (key.isEmpty()) Color.Transparent else Nflix.SurfaceHigh)
                                    .clickable(enabled = key.isNotEmpty()) {
                                        pin = if (key == "<") pin.dropLast(1) else (pin + key).take(8)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "<") Icon(Icons.AutoMirrored.Rounded.Backspace, "Effacer", tint = Color.White)
                                else Text(key, fontSize = 22.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pin) }, enabled = pin.length >= 4) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun UserSearchDialog(title: String, actionLabel: String, onDismiss: () -> Unit, onPick: (FamilyUser) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { UserSearch(actionLabel, onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun UserSearch(actionLabel: String, onPick: (FamilyUser) -> Unit) {
    val deps = LocalDeps.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FamilyUser>>(emptyList()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        results = runCatching { deps.family.search(q) }.getOrDefault(emptyList())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(query, { query = it.take(64) }, label = { Text("Nom d’utilisateur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (query.trim().length >= 2 && results.isEmpty()) Text("Aucun résultat.", color = Nflix.Muted, fontSize = 13.sp)
        results.forEach { user ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                UserBubble(user.id, user.name, user.hasImage)
                Text(user.name, Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onPick(user) }) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun UserBubble(userId: String, name: String, hasImage: Boolean, size: Int = 36) {
    val deps = LocalDeps.current
    Box(Modifier.size(size.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
        AvatarImage(if (hasImage) deps.api.userImageUrl(userId, null) else null, Modifier.size(size.dp)) {
            Text(FamilyFormat.initials(name), fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ------------------------------------------------------------------ requests

@Composable
private fun RequestsTab(me: FamilyMe, switched: Boolean, refresh: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val pending = me.requests.filter { it.state == "pending" }
    val past = me.requests.filter { it.state != "pending" }.take(20)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (me.requests.isEmpty()) item { Text("Aucune demande ni invitation.", color = Nflix.Muted, modifier = Modifier.padding(16.dp)) }
        items(pending + past, key = { it.id }) { request ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(FamilyFormat.requestTitle(request), color = Nflix.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${FamilyFormat.stateLabel(request.state)} · ${FamilyFormat.messageTime(request.createdUtc)}", color = Nflix.Muted, fontSize = 12.sp)
                    val actions = if (switched) emptyList() else FamilyFormat.requestActions(request)
                    if (actions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            actions.forEach { action ->
                                val primary = action == "accept"
                                val onClick: () -> Unit = { scope.launch { if (attempt { deps.family.resolveRequest(request.id, action) }) refresh() } }
                                if (primary) Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red)) { Text(FamilyFormat.actionLabel(action)) }
                                else OutlinedButton(onClick = onClick) { Text(FamilyFormat.actionLabel(action)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ friends

@Composable
private fun FriendsTab(refreshMe: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FriendsInfo?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { data = runCatching { deps.family.friends() }.getOrNull() ?: data ?: FriendsInfo() }
    val act: (String, String) -> Unit = { id, action ->
        scope.launch {
            if (attempt { deps.family.friendAction(id, action) }) {
                reload++
                refreshMe()
            }
        }
    }
    val current = data
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Button(onClick = { searchOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red), modifier = Modifier.fillMaxWidth()) { Text("Ajouter un ami") }
        }
        if (current == null) {
            item { CircularProgressIndicator(color = Nflix.Red) }
            return@LazyColumn
        }
        if (current.incoming.isNotEmpty()) {
            item { SectionLabel("Demandes reçues") }
            items(current.incoming, key = { "in-" + it.id }) { f ->
                FriendRow(f.userId, f.userName, f.hasImage) {
                    TextButton(onClick = { act(f.id, "accept") }) { Text("Accepter") }
                    TextButton(onClick = { act(f.id, "decline") }) { Text("Refuser", color = Nflix.Muted) }
                }
            }
        }
        if (current.outgoing.isNotEmpty()) {
            item { SectionLabel("Demandes envoyées") }
            items(current.outgoing, key = { "out-" + it.id }) { f ->
                FriendRow(f.userId, f.userName, f.hasImage) { TextButton(onClick = { act(f.id, "decline") }) { Text("Annuler") } }
            }
        }
        item { SectionLabel("Amis (${current.friends.size})") }
        if (current.friends.isEmpty()) item { Text("Pas encore d’amis. Ajoutez-en avec leur nom d’utilisateur.", color = Nflix.Muted, fontSize = 13.sp) }
        items(current.friends, key = { "f-" + it.id }) { f ->
            var menu by remember { mutableStateOf(false) }
            FriendRow(f.userId, f.userName, f.hasImage) {
                TextButton(onClick = {
                    scope.launch {
                        attempt {
                            val c = deps.family.openConversation(f.userId)
                            navigator.open(Route.FamilyChat(c.id, c.title))
                        }
                    }
                }) { Text("Écrire") }
                TextButton(onClick = { menu = true }) { Text("…") }
            }
            if (menu) {
                AlertDialog(
                    onDismissRequest = { menu = false },
                    title = { Text(f.userName) },
                    confirmButton = { TextButton(onClick = { menu = false; act(f.id, "remove") }) { Text("Retirer des amis") } },
                    dismissButton = { TextButton(onClick = { menu = false; act(f.id, "block") }) { Text("Bloquer", color = Nflix.Red) } },
                )
            }
        }
        if (current.blocked.isNotEmpty()) {
            item { SectionLabel("Bloqués") }
            items(current.blocked, key = { "b-" + it.id }) { u ->
                FriendRow(u.id, u.name, u.hasImage) { TextButton(onClick = { act(u.id, "unblock") }) { Text("Débloquer") } }
            }
        }
    }
    if (searchOpen) {
        UserSearchDialog("Ajouter un ami", "Ajouter", onDismiss = { searchOpen = false }) { user ->
            scope.launch {
                if (attempt { deps.family.addFriend(user.id) }) {
                    Toasts.show("Demande envoyée à ${user.name}", ToastKind.Success)
                    searchOpen = false
                    reload++
                }
            }
        }
    }
}

@Composable
private fun FriendRow(userId: String, name: String, hasImage: Boolean, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        UserBubble(userId, name, hasImage)
        Text(name, Modifier.weight(1f).padding(start = 10.dp), color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

// ------------------------------------------------------------------ messages

@Composable
private fun MessagesTab() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var list by remember { mutableStateOf<List<ConversationInfo>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            list = runCatching { deps.family.conversations() }.getOrNull() ?: list ?: emptyList()
            delay(15_000)
        }
    }
    val current = list
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            current == null -> item { CircularProgressIndicator(color = Nflix.Red) }
            current.isEmpty() -> item { Text("Aucune conversation. Rejoignez une famille ou ajoutez des amis pour discuter.", color = Nflix.Muted, fontSize = 13.sp) }
            else -> items(current, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).clickable { navigator.open(Route.FamilyChat(c.id, c.title)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text((if (c.kind == "family") "Famille · " else "") + c.title, color = Nflix.Text, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        c.lastMessage?.let { m ->
                            Text((if (m.mine) "Vous : " else "${m.senderName} : ") + m.text, color = Nflix.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    c.lastMessage?.let { Text(FamilyFormat.messageTime(it.sentUtc), color = Nflix.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)) }
                    FamilyFormat.badgeText(c.unread)?.let {
                        Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Nflix.Red).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

/** One conversation. New messages are fetched every 5 s while the screen is open. */
@Composable
fun FamilyChatScreen(conversationId: String, title: String) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<MessageInfo>() }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var hasOlder by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    suspend fun pull() {
        val latest = runCatching { deps.family.messages(conversationId) }.getOrNull() ?: return
        val known = messages.map { it.id }.toHashSet()
        val fresh = latest.filter { it.id !in known }
        if (messages.isEmpty() && latest.size < 50) hasOlder = false
        if (fresh.isNotEmpty()) {
            messages.addAll(fresh)
            messages.sortBy { it.id }
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0) + 1)
            runCatching { deps.family.markRead(conversationId) }
        }
    }

    LaunchedEffect(conversationId, poke) {
        while (true) {
            pull()
            delay(5_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.statusBarsPadding().padding(start = 64.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "older") {
                    if (hasOlder && messages.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                val older = runCatching { deps.family.messages(conversationId, before = messages.first().id) }.getOrNull().orEmpty()
                                if (older.size < 50) hasOlder = false
                                messages.addAll(0, older.filter { o -> messages.none { it.id == o.id } })
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Messages précédents") }
                    }
                }
                items(messages, key = { it.id }) { m -> Bubble(m) }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(2000) }, placeholder = { Text("Message") }, modifier = Modifier.weight(1f), maxLines = 4)
                IconButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        val toSend = text.trim()
                        sending = true
                        scope.launch {
                            if (attempt { deps.family.send(conversationId, toSend) }) {
                                text = ""
                                poke++
                            }
                            sending = false
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = Nflix.Red) }
            }
        }
        BackButton()
    }
}

@Composable
private fun Bubble(m: MessageInfo) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (m.mine) Nflix.Red.copy(alpha = .85f) else Nflix.SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!m.mine) Text(m.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Nflix.Info)
            Text(m.text, color = Color.White, fontSize = 14.sp)
            Text(FamilyFormat.messageTime(m.sentUtc), fontSize = 10.sp, color = Color.White.copy(alpha = .7f), modifier = Modifier.align(Alignment.End))
        }
    }
}

// ------------------------------------------------------------------ "Qui regarde ?"

/** Full-screen profile picker for family heads ("Qui regarde ?"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoIsWatchingScreen(me: FamilyMe, onDone: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var pinFor by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    val members = me.family?.members.orEmpty()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .97f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("Qui regarde ?", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(28.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                members.forEach { member ->
                    val isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId)
                    ProfileTile(member, isMe = false, onClick = {
                        when {
                            isMe -> onDone()
                            member.hasPin -> pinFor = member
                            else -> scope.launch { attempt { deps.switcher.switchTo(member, null) } }
                        }
                    }, onEdit = null)
                }
            }
            Spacer(Modifier.height(28.dp))
            if (deps.switcher.busy) CircularProgressIndicator(color = Nflix.Red)
            TextButton(onClick = onDone) { Text("Continuer avec mon profil", color = Nflix.Muted) }
            TextButton(onClick = { deps.switcher.store.pickerEnabled = false; onDone() }) { Text("Ne plus demander au démarrage", color = Nflix.Muted, fontSize = 12.sp) }
        }
    }
    pinFor?.let { member ->
        PinDialog("Code PIN de ${member.displayName}", onDismiss = { pinFor = null }) { pin ->
            scope.launch { if (attempt { deps.switcher.switchTo(member, pin) }) pinFor = null }
        }
    }
}
