package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rowSection
import org.jellyfin.mobile.nflix.ui.value

enum class MiraPlan(val label: String, val color: Color) {
    NONE("", Color.Transparent),
    STANDARD("Standard", Nflix.Info),
    PRO("Pro", Nflix.Red),
    DIEU("Dieu", Nflix.Warning);

    companion object {
        fun fromGroups(groups: Set<String>): MiraPlan {
            val normalized = groups.map { it.lowercase().filterNot(Char::isWhitespace) }.toSet()
            return when {
                "plan_dieu" in normalized -> DIEU
                "plan_pro" in normalized -> PRO
                "plan_standard" in normalized -> STANDARD
                else -> NONE
            }
        }
    }
}

@Composable
fun NoticesScreen() {
    val deps = LocalDeps.current
    val notices by deps.plugins.notices.collectAsState()
    LaunchedEffect(Unit) { deps.plugins.refreshNotices() }
    LaunchedEffect(notices) { deps.plugins.markSeen(notices) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item { Text("Annonces", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 64.dp, top = 14.dp, bottom = 12.dp)) }
            if (notices.isEmpty()) item { EmptyState(Icons.Rounded.Campaign, "Aucune annonce", null) }
            items(notices, key = { it.id }) { notice ->
                val color = when (notice.level) { "urgent" -> Nflix.Red; "warning" -> Nflix.Warning; else -> Nflix.Info }
                Surface(color = Nflix.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().appear()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(notice.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (notice.version.isNotBlank()) Text(notice.version, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                        }
                        Text(notice.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                        Spacer(Modifier.height(8.dp))
                        Text(notice.message, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.9f))
                        if (notice.actionUrl.isNotBlank()) TextButton(onClick = { deps.openUrl(notice.actionUrl) }) { Text(notice.actionLabel.ifBlank { "Ouvrir" }, color = Nflix.Red) }
                    }
                }
            }
        }
        BackButton()
    }
}

@Composable
fun ProfileScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = rememberLoad("me") { deps.jellyfin.me() }.value
    val miraInstalled = remember { runCatching { context.packageManager.getPackageInfo("fr.miracloud.connect", 0); true }.getOrDefault(false) }
    val plan = remember(deps.session.miraGroups) { MiraPlan.fromGroups(deps.session.miraGroups) }
    var seasonalSheet by remember { mutableStateOf(false) }
    if (seasonalSheet) org.jellyfin.mobile.nflix.ui.SeasonalThemesSheet(onDismiss = { seasonalSheet = false })
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 56.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val miraAvatar = org.jellyfin.mobile.nflix.MiraProfile.safeAvatar(deps.session.miraAvatar)
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).background(Nflix.SurfaceHigh).border(2.dp, plan.color.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Same order as Authentik's avatar setting: attributes.avatar, then initials.
                        val displayName = deps.session.miraName?.takeIf { it.isNotBlank() } ?: me?.name ?: deps.session.userName
                        org.jellyfin.mobile.nflix.ui.AvatarImage(miraAvatar, Modifier.size(104.dp).clip(CircleShape)) {
                            Text(
                                org.jellyfin.mobile.nflix.MiraProfile.initials(displayName),
                                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = plan.color,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(deps.session.miraName?.takeIf { it.isNotBlank() } ?: me?.name ?: deps.session.userName ?: "", style = MaterialTheme.typography.headlineSmall)
                    deps.session.miraEmail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (plan != MiraPlan.NONE) {
                        Surface(color = plan.color.copy(alpha = 0.16f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, plan.color.copy(alpha = 0.5f))) {
                            Text(
                                "Abonnement ${plan.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = plan.color,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(if (deps.session.lastMethod == "mira") "Connecté avec Mira Connect" else "Connecté avec vos identifiants", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    Spacer(Modifier.height(20.dp))
                }
            }
            item {
                // Shown only to Jellyfin administrators; the server checks the right again on every admin call.
                val isAdmin = rememberLoad("is-admin") { deps.admin.isAdmin(it) }.value == true
                if (isAdmin) ProfileRow(Icons.Rounded.AdminPanelSettings, "Administration", "Distributeurs, demandes à valider, activité, bibliothèques") { navigator.open(Route.Admin) }
            }
            item { ProfileRow(Icons.Rounded.EmojiEvents, "Mes succès", "Badges, rang, progression et vitrine") { navigator.open(Route.Badges) } }
            item { ProfileRow(Icons.Rounded.Download, "Téléchargements", "Vos films et épisodes hors ligne") { deps.openDownloads() } }
            item { ProfileRow(Icons.Rounded.Inbox, "Mes demandes Horus", "Suivi de vos demandes") { navigator.open(Route.HorusRequests) } }
            item { ProfileRow(Icons.Rounded.Campaign, "Annonces", "Les nouvelles de Nflix") { navigator.open(Route.Notices) } }
            item {
                val event = org.jellyfin.mobile.nflix.ui.SeasonalThemes.active
                ProfileRow(
                    androidx.compose.material.icons.Icons.Rounded.Celebration,
                    "Thèmes saisonniers",
                    when {
                        !org.jellyfin.mobile.nflix.ui.SeasonalThemes.enabled && event == null -> "Désactivés"
                        event != null -> "${event.emoji} ${event.label} en ce moment"
                        else -> "Noël, Halloween, 14 Juillet, Nouvel An…"
                    },
                ) { seasonalSheet = true }
            }
            item { ProfileRow(Icons.Rounded.Settings, "Lecture et lecteur", "Qualité, sous-titres, lecteur vidéo") { deps.openSettings() } }
            item {
                ProfileRow(Icons.Rounded.SystemUpdate, "Mises à jour", "Version ${BuildConfig.VERSION_NAME}") {
                    scope.launch { deps.updater.check(force = true) }
                }
            }
            if (miraInstalled) {
                item {
                    ProfileRow(Icons.Rounded.Key, "Mira Connect", "Gérer votre compte Mira") {
                        context.packageManager.getLaunchIntentForPackage("fr.miracloud.connect")?.let { context.startActivity(it) }
                    }
                }
            }
            item { ProfileRow(Icons.Rounded.Language, "Version web", "Administration et pages des plugins") { deps.onOpenWeb() } }
            item { ProfileRow(Icons.Rounded.School, "Didacticiel", "Revoir la présentation de Nflix") { org.jellyfin.mobile.nflix.ui.tutorial.Tutorial.open() } }
            item { ProfileRow(Icons.AutoMirrored.Rounded.Logout, "Se déconnecter", null, destructive = true) { deps.onSignOut() } }
        }
        BackButton()
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, subtitle: String?, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) Nflix.Red else Nflix.Text)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) Nflix.Red else Nflix.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
