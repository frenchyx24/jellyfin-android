package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class FamilyTab(val label: String, val icon: ImageVector) {
    Profiles("Profils", Icons.Rounded.Groups),
    Requests("Demandes", Icons.Rounded.MarkEmailUnread),
    Friends("Amis", Icons.Rounded.People),
    Messages("Messages", Icons.Rounded.Forum),
}

/** "Famille": profiles, requests, friends and messages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen() {
    val deps = LocalDeps.current
    var tab by remember { mutableStateOf(FamilyTab.Profiles) }
    var me by remember { mutableStateOf<FamilyMe?>(FamilyBadges.me) }
    // The server knows when this session comes from a profile switch, even if local state was lost.
    val switched = deps.switcher.isSwitched || me?.isSwitched == true
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val refresh: () -> Unit = { reload++ }
    LaunchedEffect(reload) {
        try {
            me = deps.family.me().also { if (!deps.switcher.isSwitched && !it.isSwitched) deps.switcher.store.lastKnownHead = it.isHead }
            error = null
        } catch (e: FamilyException) {
            error = e.message
        } finally {
            refreshing = false
        }
    }

    val accent = parseColor(me?.family?.avatarColor)
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        // Soft glow in the family colour behind the header.
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(accent.copy(alpha = .38f), accent.copy(alpha = .10f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            FamilyHeader(me, refresh)
            if (switched) SwitchBackBanner()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = .06f)).padding(4.dp),
            ) {
                FamilyTab.entries.forEach { entry ->
                    val count = when (entry) {
                        FamilyTab.Requests -> (me?.pendingRequestCount ?: 0)
                        FamilyTab.Friends -> (me?.pendingFriendCount ?: 0)
                        FamilyTab.Messages -> (me?.unreadMessageCount ?: 0)
                        else -> 0
                    }
                    TabChip(entry, entry == tab, FamilyFormat.badgeText(count), Modifier.weight(1f)) { tab = entry }
                }
            }
            val current = me
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (androidx.compose.animation.slideInHorizontally { it / 5 * dir } + androidx.compose.animation.fadeIn(tween(220)))
                            .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
                    },
                    label = "family-tab",
                ) { shown ->
                    when {
                        current == null && error != null -> ErrorBox(error!!, refresh)
                        current == null -> ProfilesSkeleton()
                        else -> when (shown) {
                            FamilyTab.Profiles -> ProfilesTab(current, switched, refresh) { tab = it }
                            FamilyTab.Requests -> RequestsTab(current, switched, refresh)
                            FamilyTab.Friends -> FriendsTab(refresh, reload, canInvite = current.isHead && !switched)
                            FamilyTab.Messages -> MessagesTab(reload)
                        }
                    }
                }
            }
        }
        BackButton()
    }
}

/** Big header: family name, member avatars stacked, counts. */
@Composable
private fun FamilyHeader(me: FamilyMe?, refresh: () -> Unit) {
    val family = me?.family
    Row(Modifier.statusBarsPadding().padding(start = 64.dp, end = 10.dp, top = 12.dp, bottom = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (family == null) "FAMILLE" else "MA FAMILLE", color = Color.White.copy(alpha = .6f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(family?.name ?: "Nflix Famille", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (family != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box {
                        family.members.take(5).forEachIndexed { i, m ->
                            Box(Modifier.padding(start = (i * 20).dp).border(2.dp, Nflix.Night, RoundedCornerShape(10.dp))) { MemberAvatar(m, 28) }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    val n = family.members.size
                    Text("$n profil${if (n > 1) "s" else ""}" + (if (me.isHead) " · vous êtes responsable" else ""), color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                }
            }
        }
        IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Actualiser", tint = Color.White) }
    }
}

@Composable
private fun TabChip(entry: FamilyTab, selected: Boolean, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(if (selected) Color.White else Color.Transparent, tween(200), label = "tab-bg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) Color(0xFF111111) else Color.White.copy(alpha = .75f), tween(200), label = "tab-fg")
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(entry.icon, null, tint = fg, modifier = Modifier.size(20.dp))
                if (badge != null) {
                    Text(badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(start = 14.dp).clip(RoundedCornerShape(50)).background(Nflix.Red).padding(horizontal = 4.dp))
                }
            }
            Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun ErrorBox(message: String, retry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(Icons.Rounded.Refresh, "Impossible de charger la famille", message) {
                OutlinedButton(onClick = retry) { Text("Réessayer") }
            }
        }
    }
}

/** Icon, title and hint shown when a list is empty. */
@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp).appear(0), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Nflix.Muted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Nflix.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (text != null) Text(text, color = Nflix.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

/** Shimmer-free placeholder grid while the family loads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilesSkeleton() {
    val pulse = remember { Animatable(0.35f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(0.7f, tween(700))
            pulse.animateTo(0.35f, tween(700))
        }
    }
    FlowRow(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)).background(Nflix.SurfaceHigh.copy(alpha = pulse.value)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(64.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Nflix.SurfaceHigh.copy(alpha = pulse.value)))
            }
        }
    }
}

/** Fade + rise when the item first appears, staggered by [index]. */
@Composable
private fun Modifier.appear(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 45L).coerceAtMost(400L))
        progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 260f))
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 36f
        val sc = 0.94f + 0.06f * progress.value
        scaleX = sc
        scaleY = sc
    }
}

/** Confirmation dialog for destructive or important actions. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, danger: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = Nflix.Muted) },
        confirmButton = {
            Button(onClick = { onDismiss(); onConfirm() }, colors = ButtonDefaults.buttonColors(containerColor = if (danger) Nflix.Red else Nflix.SurfaceHigh)) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** A confirmation waiting for the user. */
private data class Ask(val title: String, val text: String, val label: String, val danger: Boolean, val action: suspend () -> Unit)

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
private fun ProfilesTab(me: FamilyMe, switched: Boolean, refresh: () -> Unit, openTab: (FamilyTab) -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val family = me.family
    var editing by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    var pinFor by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    var inviteOpen by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val canSwitch = ProfileSwitchLogic.canSwitch(me.isHead, deps.switcher.state) && !switched

    val incoming = if (switched) emptyList() else me.requests.filter { it.state == "pending" && it.direction == "incoming" }
    LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (incoming.isNotEmpty()) item { InboxCard(incoming, refresh) }
        if (family == null) {
            item { NoFamily(refresh) }
            return@LazyColumn
        }
        item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        if (canSwitch) "Touchez un profil pour l’ouvrir" else "Les profils de la famille",
                        color = Nflix.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        family.members.forEachIndexed { index, member ->
                            val isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId)
                            ProfileTile(
                                member = member,
                                isMe = isMe,
                                index = index,
                                onClick = {
                                    when {
                                        isMe || !canSwitch -> editing = if ((me.isHead && !switched) || isMe) member else null
                                        member.hasPin -> { pinError = null; pinFor = member }
                                        else -> scope.launch { attempt { deps.switcher.switchTo(member, null) } }
                                    }
                                },
                                onEdit = if ((me.isHead && !switched) || isMe) ({ editing = member }) else null,
                            )
                        }
                        if (me.isHead && !switched) AddProfileTile(family.members.size) { inviteOpen = true }
                    }
                }
            }
        }
        if (me.isHead && !switched) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(Icons.Rounded.PersonAdd, "Inviter", "un proche", Nflix.Red, Modifier.weight(1f)) { inviteOpen = true }
                    ActionCard(Icons.Rounded.Forum, "Discuter", "en famille", Nflix.Info, Modifier.weight(1f)) { openTab(FamilyTab.Messages) }
                }
            }
            item { Box(Modifier.padding(horizontal = 14.dp)) { GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { PickerSetting() } } } }
            item { ActivitySection() }
            item { FamilySocialSection(me, switched) }
        } else if (!switched) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(Icons.Rounded.Forum, "Discuter", "en famille", Nflix.Info, Modifier.weight(1f)) { openTab(FamilyTab.Messages) }
                    ActionCard(Icons.Rounded.People, "Amis", "ajouter, écrire", Nflix.Success, Modifier.weight(1f)) { openTab(FamilyTab.Friends) }
                }
            }
            item { FamilySocialSection(me, switched) }
        }
        if (!switched) {
            item {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Zone sensible")
                    TextButton(onClick = { ask = Ask("Quitter la famille ?", "Vous ne verrez plus les profils ni la conversation de « ${family.name} ».", "Quitter", true) { if (attempt { deps.family.leave() }) refresh() } }) {
                        Icon(Icons.Rounded.Logout, null, tint = Nflix.Muted); Spacer(Modifier.width(8.dp)); Text("Quitter la famille", color = Nflix.Muted)
                    }
                    if (me.isHead) {
                        TextButton(onClick = { ask = Ask("Supprimer la famille ?", "« ${family.name} », ses profils et sa conversation de groupe seront supprimés. Cette action est définitive.", "Supprimer", true) { if (attempt { deps.family.deleteFamily() }) refresh() } }) {
                            Icon(Icons.Rounded.Block, null, tint = Nflix.Red); Spacer(Modifier.width(8.dp)); Text("Supprimer la famille", color = Nflix.Red)
                        }
                    }
                }
            }
        }
    }

    editing?.let { member ->
        ProfileEditDialog(
            member, isHead = me.isHead && !switched, isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId),
            onDismiss = { editing = null },
            onMemberAction = { action ->
                editing = null
                ask = when (action) {
                    "promote" -> Ask("Nommer ${member.displayName} responsable ?", "Un responsable gère la famille et peut ouvrir tous les profils.", "Nommer", false) { if (attempt { deps.family.memberAction(member.userId, action) }) refresh() }
                    "demote" -> Ask("Retirer le rôle de responsable ?", "${member.displayName} redeviendra simple membre.", "Retirer le rôle", true) { if (attempt { deps.family.memberAction(member.userId, action) }) refresh() }
                    else -> Ask("Retirer ${member.displayName} de la famille ?", "Les sessions ouvertes depuis ce profil seront fermées.", "Retirer", true) { if (attempt { deps.family.memberAction(member.userId, action) }) refresh() }
                }
            },
        ) {
            editing = null
            refresh()
        }
    }
    pinFor?.let { member ->
        PinDialog(member, error = pinError, busy = deps.switcher.busy, onDismiss = { pinFor = null }) { pin ->
            scope.launch {
                pinError = null
                try {
                    deps.switcher.switchTo(member, pin)
                    pinFor = null
                } catch (e: FamilyException) {
                    pinError = e.message ?: "Code PIN incorrect."
                }
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
    ask?.let { a -> ConfirmDialog(a.title, a.text, a.label, a.danger, onDismiss = { ask = null }) { scope.launch { a.action() } } }
}

@Composable
private fun NoFamily(refresh: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var joinOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).appear(0), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Nflix.Red, Nflix.RedDeep))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Regardez Nflix en famille", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Un profil par personne, un code PIN si besoin, et une discussion pour toute la maison.", color = Nflix.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        GlassCard(Modifier.fillMaxWidth().appear(1)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Groups, Nflix.Red); Spacer(Modifier.width(12.dp))
                    Column { Text("Créer ma famille", fontWeight = FontWeight.SemiBold, color = Nflix.Text); Text("Vous en serez le responsable", color = Nflix.Muted, fontSize = 12.sp) }
                }
                OutlinedTextField(name, { name = it.take(40) }, placeholder = { Text("Ex. : Famille Martin") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Button(
                    onClick = { scope.launch { if (attempt { deps.family.createFamily(name.trim()) }) { Toasts.show("Famille créée", ToastKind.Success); refresh() } } },
                    enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
                ) { Text("Créer", fontWeight = FontWeight.Bold) }
            }
        }
        GlassCard(Modifier.fillMaxWidth().appear(2), onClick = { joinOpen = true }) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.PersonSearch, Nflix.Info); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rejoindre une famille", fontWeight = FontWeight.SemiBold, color = Nflix.Text)
                    Text("Cherchez le responsable, il recevra votre demande", color = Nflix.Muted, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Nflix.Muted)
            }
        }
        Text("Une invitation reçue apparaît tout en haut de cet écran.", color = Nflix.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        JoinByLinkCard(refresh)
    }
    if (joinOpen) {
        UserSearchDialog("Rejoindre une famille", "Demander", onDismiss = { joinOpen = false }) { user ->
            scope.launch {
                if (attempt { deps.family.askToJoin(user.name) }) {
                    Toasts.show("Demande envoyée à ${user.name}", ToastKind.Success)
                    joinOpen = false
                    refresh()
                }
            }
        }
    }
}

/** Pending invitations and join requests to answer, right on the main screen. */
@Composable
private fun InboxCard(incoming: List<org.jellyfin.mobile.nflix.family.JoinRequestInfo>, refresh: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    Box(Modifier.padding(horizontal = 14.dp).appear(0)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Nflix.Red.copy(alpha = .30f), Nflix.Red.copy(alpha = .10f))))
                .border(1.dp, Nflix.Red.copy(alpha = .45f), RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MarkEmailUnread, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("À traiter (${incoming.size})", color = Color.White, fontWeight = FontWeight.Bold)
            }
            incoming.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserBubble(r.userId, if (r.kind == "invite") r.createdByName else r.userName, false, 40)
                    Text(FamilyFormat.requestTitle(r), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { if (attempt { deps.family.resolveRequest(r.id, "accept") }) { Toasts.show(if (r.kind == "invite") "Bienvenue dans « ${r.familyName} » !" else "${r.userName} a rejoint la famille", ToastKind.Success); refresh() } } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                    ) { Text("Accepter", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { scope.launch { if (attempt { deps.family.resolveRequest(r.id, "decline") }) refresh() } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text("Refuser", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            IconTile(icon, tint)
            Spacer(Modifier.height(10.dp))
            Text(title, color = Nflix.Text, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Nflix.Muted, fontSize = 12.sp)
        }
    }
}

/** "+" tile at the end of the profiles grid (heads only). */
@Composable
private fun AddProfileTile(index: Int, onClick: () -> Unit) {
    Column(Modifier.width(108.dp).appear(index).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.padding(3.dp).size(96.dp).clip(RoundedCornerShape(22.dp)).border(2.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PersonAdd, "Inviter", tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(8.dp))
        Text("Inviter", color = Nflix.Muted, fontSize = 14.sp)
    }
}

@Composable
private fun MemberAvatar(member: FamilyMemberInfo, size: Int) {
    val deps = LocalDeps.current
    val image = if (member.useUserImage && member.hasImage) deps.api.userImageUrl(member.userId, null) else null
    val base = parseColor(member.avatarColor)
    val dark = Color(base.red * 0.6f, base.green * 0.6f, base.blue * 0.6f, 1f)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp)).background(Brush.linearGradient(listOf(base, dark))), contentAlignment = Alignment.Center) {
        AvatarImage(image, Modifier.size(size.dp)) {
            GeneratedAvatarArt(member.userId, FamilyFormat.initials(member.displayName), size)
        }
    }
}

@Composable
private fun ProfileTile(member: FamilyMemberInfo, isMe: Boolean, index: Int, onClick: () -> Unit, onEdit: (() -> Unit)?, size: Int = 96, light: Boolean = false) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "tile-press")
    Column(
        Modifier.width((size + 12).dp).appear(index).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(if (isMe) Modifier.border(2.dp, Color.White, RoundedCornerShape((size * 0.25f).dp)).padding(3.dp) else Modifier.padding(3.dp)) { MemberAvatar(member, size) }
            if (member.isKid) {
                Text("ENFANT", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), letterSpacing = 0.6.sp,
                    modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00)))).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            if (member.hasPin) {
                Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(Color(0xFF111111)).border(2.dp, Nflix.Night, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, "Protégé par un code PIN", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
            if (onEdit != null) {
                Box(Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = .65f)).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Edit, "Modifier", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(member.displayName, color = if (light) Color.White else Nflix.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (member.isHead) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC35A), modifier = Modifier.size(12.dp).padding(end = 2.dp))
            Text(if (isMe) "Vous" else FamilyFormat.roleLabel(member), color = if (member.isHead) Color(0xFFFFC35A) else Nflix.Muted, fontSize = 11.sp)
        }
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
private fun ProfileEditDialog(member: FamilyMemberInfo, isHead: Boolean, isMe: Boolean, onDismiss: () -> Unit, onMemberAction: (String) -> Unit, onDone: () -> Unit) {
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
                val canPin = isMe || (isHead && !member.isHead)
                if (canPin) OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(8); clearPin = false },
                    label = { Text(if (member.hasPin) "Nouveau code PIN (4 à 8 chiffres)" else "Code PIN (facultatif)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (canPin && member.hasPin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Retirer le code PIN", Modifier.weight(1f))
                        Switch(clearPin, { clearPin = it; if (it) pin = "" })
                    }
                }
                if (isHead && !isMe) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.isHead) {
                            OutlinedButton(onClick = { onMemberAction("demote") }) { Text("Retirer responsable") }
                        } else {
                            if (!member.isKid) OutlinedButton(onClick = { onMemberAction("promote") }) { Text("Nommer responsable") }
                            OutlinedButton(onClick = { onMemberAction("remove") }) { Text("Retirer", color = Nflix.Red) }
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
            GeneratedAvatarArt(userId, FamilyFormat.initials(name), size)
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
        if (me.requests.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.MarkEmailRead, "Aucune demande", if (me.family == null) "Les invitations à rejoindre une famille apparaîtront ici." else "Les demandes pour rejoindre votre famille apparaîtront ici.")
            }
        }
        itemsIndexed(pending + past, key = { _, r -> r.id }) { index, request ->
            GlassCard(Modifier.fillMaxWidth().appear(index)) {
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
private fun FriendsTab(refreshMe: () -> Unit, outerReload: Int, canInvite: Boolean) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FriendsInfo?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    LaunchedEffect(reload, outerReload) { data = runCatching { deps.family.friends() }.getOrNull() ?: data ?: FriendsInfo() }
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
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) } }
            return@LazyColumn
        }
        if (current.incoming.isNotEmpty()) {
            item { SectionLabel("Demandes reçues") }
            items(current.incoming, key = { "in-" + it.id }) { f ->
                FriendRow(f.userId, f.userName, f.hasImage) {
                    TextButton(onClick = { act(f.id, "accept") }) { Text("Accepter") }
                    TextButton(onClick = { act(f.id, "decline") }) { Text("Refuser", color = Nflix.Muted) }
                    IconButton(onClick = { ask = Ask("Bloquer ${f.userName} ?", "Cette personne ne pourra plus vous écrire, vous inviter ni vous envoyer de demande.", "Bloquer", true) { act(f.userId, "block") } }) {
                        Icon(Icons.Rounded.Block, "Bloquer", tint = Nflix.Muted)
                    }
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
        if (current.friends.isEmpty()) item { EmptyState(Icons.Rounded.PersonSearch, "Pas encore d’amis", "Cherchez un proche par son nom d’utilisateur pour lui envoyer une demande.") }
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
                    text = {
                        Column {
                            if (canInvite) {
                                TextButton(onClick = {
                                    menu = false
                                    scope.launch { if (attempt { deps.family.invite(f.userId) }) Toasts.show("Invitation envoyée à ${f.userName}", ToastKind.Success) }
                                }) { Icon(Icons.Rounded.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text("Inviter dans la famille") }
                            }
                            TextButton(onClick = { menu = false; ask = Ask("Retirer ${f.userName} de vos amis ?", "Vous ne pourrez plus vous écrire.", "Retirer", true) { act(f.id, "remove") } }) { Text("Retirer des amis") }
                            TextButton(onClick = { menu = false; ask = Ask("Bloquer ${f.userName} ?", "Cette personne ne pourra plus vous écrire, vous inviter ni vous envoyer de demande. Votre amitié sera supprimée.", "Bloquer", true) { act(f.id, "block") } }) {
                                Text("Bloquer", color = Nflix.Red)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { menu = false }) { Text("Fermer") } },
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
    ask?.let { a -> ConfirmDialog(a.title, a.text, a.label, a.danger, onDismiss = { ask = null }) { scope.launch { a.action() } } }
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
private fun MessagesTab(outerReload: Int) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var list by remember { mutableStateOf<List<ConversationInfo>?>(null) }
    LaunchedEffect(outerReload) {
        while (true) {
            list = runCatching { deps.family.conversations() }.getOrNull() ?: list ?: emptyList()
            delay(15_000)
        }
    }
    val current = list
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            current == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) } }
            current.isEmpty() -> item { EmptyState(Icons.Rounded.Forum, "Aucune conversation", "Rejoignez une famille ou ajoutez des amis pour discuter.") }
            else -> itemsIndexed(current, key = { _, c -> c.id }) { index, c ->
                Row(
                    Modifier.fillMaxWidth().appear(index).clip(RoundedCornerShape(18.dp)).background(Nflix.Surface).clickable { navigator.open(Route.FamilyChat(c.id, c.title)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (c.kind == "family") {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nflix.Red, Nflix.RedDeep))), contentAlignment = Alignment.Center) {
                            Text(FamilyFormat.initials(c.title), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        UserBubble(c.peerUserId ?: c.id, c.title, true, 44)
                    }
                    Spacer(Modifier.width(12.dp))
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<MessageInfo>() }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var hasOlder by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var firstUnread by remember { mutableStateOf<Long?>(null) }
    var initialised by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 5.8 social
    val polls = remember { mutableStateListOf<org.jellyfin.mobile.nflix.family.PollInfo>() }
    var typingUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var replyTo by remember { mutableStateOf<MessageInfo?>(null) }
    var showPollForm by remember { mutableStateOf(false) }

    suspend fun pull() {
        val latest = runCatching { deps.family.messages(conversationId) }.getOrNull() ?: return
        val known = messages.map { it.id }.toHashSet()
        val fresh = latest.filter { it.id !in known }
        if (messages.isEmpty() && latest.size < 50) hasOlder = false
        if (!initialised) {
            firstUnread = FamilyFormat.firstUnreadId(latest)
            initialised = true
        }
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
            if (text.isNotBlank()) runCatching { typingUsers = deps.family.typing(conversationId) } else typingUsers = emptyList()
            runCatching { polls.clear(); polls.addAll(deps.family.polls(conversationId)) }
            delay(5_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(Nflix.Surface).statusBarsPadding().padding(start = 64.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nflix.Red, Nflix.RedDeep))), contentAlignment = Alignment.Center) {
                    Text(FamilyFormat.initials(title), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (typingUsers.isNotEmpty()) "${typingUsers.joinToString(", ")} écrit…" else "Messages actualisés toutes les 5 s", color = Nflix.Muted, fontSize = 11.sp)
                }
            }
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
                if (initialised && messages.isEmpty()) {
                    item(key = "empty") { EmptyState(Icons.Rounded.ChatBubbleOutline, "Aucun message", "Dites bonjour !") }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                    val previous = messages.getOrNull(index - 1)
                    if (previous == null || FamilyFormat.dayKey(previous.sentUtc) != FamilyFormat.dayKey(m.sentUtc)) DaySeparator(FamilyFormat.dayLabel(m.sentUtc))
                    if (m.id == firstUnread) UnreadSeparator()
                    Bubble(m, grouped = FamilyFormat.continuesGroup(previous, m) && m.id != firstUnread, conversationId = conversationId, onReply = { replyTo = it }, onChanged = { poke++ }, react = { emoji -> scope.launch { runCatching { deps.family.react(conversationId, m.id, emoji) } } })
                }
            }
            // 5.8: active polls of this conversation, above the input.
            if (polls.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(Nflix.Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    polls.take(3).forEach { poll -> PollCard(poll, conversationId, deps, scope) { poke++ } }
                    if (polls.size > 3) Text("+ ${polls.size - 3} sondage(s)", fontSize = 11.sp, color = Nflix.Muted)
                }
            }
            if (replyTo != null) {
                Row(Modifier.fillMaxWidth().background(Nflix.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Répondre à ${replyTo!!.senderName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Nflix.Info)
                        Text(replyTo!!.text, fontSize = 12.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { replyTo = null }, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, "Annuler la réponse", tint = Nflix.Muted) }
                }
            }
            if (showPollForm) {
                var q by remember { mutableStateOf("") }
                var opts by remember { mutableStateOf(listOf("", "")) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPollForm = false },
                    title = { Text("On regarde quoi ce soir ?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedTextField(q, { q = it.take(200) }, label = { Text("Question") }, singleLine = true)
                            opts.forEachIndexed { i, o ->
                                androidx.compose.material3.OutlinedTextField(o, { v -> opts = opts.toMutableList().also { it[i] = v.take(60) } }, label = { Text("Choix ${i + 1}") }, singleLine = true)
                            }
                            if (opts.size < 8) TextButton(onClick = { opts = opts + "" }) { Text("+ Ajouter un choix") }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                val clean = opts.map { it.trim() }.filter { it.isNotEmpty() }
                                if (q.isNotBlank() && clean.size >= 2 && attempt { deps.family.createPoll(conversationId, q.trim(), clean, false) }) {
                                    showPollForm = false; poke++
                                }
                            }
                        }) { Text("Créer") }
                    },
                    dismissButton = { TextButton(onClick = { showPollForm = false }) { Text("Annuler") } },
                )
            }
            Row(
                Modifier.fillMaxWidth().background(Nflix.Surface).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) scope.launch {
                        sending = true
                        val bytes = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { org.jellyfin.mobile.nflix.data.readCapped(it, 3 * 1024 * 1024) } } }.getOrNull()
                        if (bytes == null || bytes.size > 3 * 1024 * 1024) {
                            org.jellyfin.mobile.nflix.ui.Toasts.show("Image trop lourde (3 Mo max).", org.jellyfin.mobile.nflix.ui.ToastKind.Warning)
                        } else if (attempt { deps.social15.sendImage(conversationId, bytes) }) {
                            firstUnread = null; poke++
                        }
                        sending = false
                    }
                }
                IconButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Image, "Envoyer une photo ou un GIF", tint = Color.White)
                }
                IconButton(onClick = { showPollForm = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Poll, "Créer un sondage « on regarde quoi ce soir »", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Nflix.Red),
                    maxLines = 5,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Nflix.SurfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Text("Écrire un message…", color = Nflix.Muted, fontSize = 15.sp); inner() } },
                )
                Spacer(Modifier.width(8.dp))
                val canSend = text.isNotBlank() && !sending
                IconButton(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(if (canSend) Nflix.Red else Nflix.SurfaceHigh),
                    enabled = canSend,
                    onClick = {
                        val toSend = text.trim()
                        sending = true
                        scope.launch {
                            if (attempt { deps.family.send(conversationId, toSend, replyTo?.id ?: 0) }) {
                                text = ""
                                replyTo = null
                                firstUnread = null
                                poke++
                            }
                            sending = false
                        }
                    },
                ) {
                    if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = Color.White)
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label.replaceFirstChar { it.titlecase(java.util.Locale.FRANCE) }, fontSize = 11.sp, color = Nflix.Muted,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Nflix.SurfaceHigh).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun UnreadSeparator() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Nflix.Red.copy(alpha = .6f)))
        Text("NOUVEAUX MESSAGES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Nflix.Red, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Nflix.Red.copy(alpha = .6f)))
    }
}

@Composable
private fun Bubble(m: MessageInfo, grouped: Boolean, conversationId: String = "", onReply: (MessageInfo) -> Unit = {}, onChanged: () -> Unit = {}, react: ((String) -> Unit) = {}) {
    val shape = if (m.mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Row(Modifier.fillMaxWidth().padding(top = if (grouped) 0.dp else 4.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!m.mine) {
            if (grouped) Spacer(Modifier.width(30.dp)) else UserBubble(m.senderId, m.senderName, true, 30)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier.widthIn(max = 290.dp)
                .pointerInput(m.id) {
                    detectTapGestures(onLongPress = {
                        onReply(m)
                    })
                }
                .clip(shape)
                .background(if (m.mine) Brush.linearGradient(listOf(Nflix.Red, Nflix.RedDeep)) else Brush.linearGradient(listOf(Nflix.SurfaceHigh, Nflix.SurfaceHigh)))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            if (!m.mine && !grouped) Text(m.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Nflix.Info)
            if (m.replyToId != 0L) Text("↩ réponse à un message", fontSize = 10.sp, color = Color.White.copy(alpha = .6f))
            if (m.imageId != null) ChatImage(m.imageId) else Text(m.text, color = Color.White, fontSize = 14.sp)
            if (m.reactions.isNotEmpty() || conversationId.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    m.reactions.forEach { (emoji, count) -> Text("$emoji $count", fontSize = 11.sp, color = Color.White.copy(alpha = .85f)) }
                    if (conversationId.isNotEmpty()) {
                        var picker by remember { mutableStateOf(false) }
                        Text("+", fontSize = 11.sp, color = Color.White.copy(alpha = .6f), modifier = Modifier.clickable { picker = true })
                        if (picker) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { picker = false },
                                title = { Text("Réagir") },
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE21", "\uD83C\uDF89", "\u2764\uFE0F", "\uD83D\uDE31").forEach { e ->
                                        Text(e, fontSize = 20.sp, modifier = Modifier.clickable {
                                            picker = false
                                            react(e)
                                            onChanged()
                                        })
                                    }
                                } },
                                confirmButton = { TextButton(onClick = { picker = false }) { Text("Fermer") } },
                            )
                        }
                    }
                }
            }
            val receipt = if (m.mine && m.readByNames.isNotEmpty()) " · Lu par " + m.readByNames.take(3).joinToString(", ") + (if (m.readByNames.size > 3) "…" else "") else ""
            Text(FamilyFormat.clock(m.sentUtc) + receipt, fontSize = 10.sp, color = Color.White.copy(alpha = .7f), modifier = Modifier.align(Alignment.End))
        }
    }
}

/** 5.8: one poll card with vote bars; tapping an option votes (or un-votes). */
@Composable
private fun PollCard(poll: org.jellyfin.mobile.nflix.family.PollInfo, conversationId: String, deps: org.jellyfin.mobile.nflix.ui.AppDeps, scope: kotlinx.coroutines.CoroutineScope, poke: () -> Unit) {
    var state by remember(poll.id) { mutableStateOf(poll) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nflix.SurfaceHigh).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Poll, null, tint = Nflix.Info, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.question, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text("par ${state.createdByName}", fontSize = 10.sp, color = Nflix.Muted)
        }
        state.options.forEachIndexed { i, opt ->
            val votes = state.votes[i]?.size ?: 0
            val mine = i in state.myVotes
            val total = state.totalVotes.coerceAtLeast(1)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (mine) Nflix.Red.copy(alpha = .25f) else Nflix.Surface).clickable {
                scope.launch { runCatching { state = deps.family.vote(conversationId, state.id, i) }; poke() }
            }.padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(opt, fontSize = 13.sp, color = Color.White)
                    Text("$votes", fontSize = 11.sp, color = Nflix.Muted)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Nflix.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(votes.toFloat() / total).height(4.dp).background(if (mine) Nflix.Red else Nflix.Muted))
                }
            }
        }
        Text("${state.totalVotes} vote(s)${if (state.multiple) " · choix multiple" else ""} — appuyez pour voter", fontSize = 10.sp, color = Nflix.Muted)
    }
}
