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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SyncAlt
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
    // Shown only to Jellyfin administrators; the server checks the right again on every admin call.
    val isAdmin = rememberLoad("is-admin") { deps.admin.isAdmin(it) }.value == true
    val miraInstalled = remember { runCatching { context.packageManager.getPackageInfo("fr.miracloud.connect", 0); true }.getOrDefault(false) }
    val plan = remember(deps.session.miraGroups) { MiraPlan.fromGroups(deps.session.miraGroups) }
    val serverHost = remember { BuildConfig.NFLIX_SERVER_URL.substringAfter("://").substringBefore('/').ifBlank { "Nflix" } }
    var seasonalSheet by remember { mutableStateOf(false) }
    if (seasonalSheet) org.jellyfin.mobile.nflix.ui.SeasonalThemesSheet(onDismiss = { seasonalSheet = false })
    val entrance = rememberProfileEntrance()

    val spaceEntries = buildList {
        if (isAdmin) add(ProfileEntry("admin", Icons.Rounded.AdminPanelSettings, "Administration", "Distributeurs, demandes à valider, activité, bibliothèques", ProfileAccents.Admin) { navigator.open(Route.Admin) })
        add(ProfileEntry("badges", Icons.Rounded.EmojiEvents, "Mes succès", "Badges, rang, progression et vitrine", ProfileAccents.Badges) { navigator.open(Route.Badges) })
        add(ProfileEntry("downloads", Icons.Rounded.Download, "Téléchargements", "Vos films et épisodes hors ligne", ProfileAccents.Downloads) { deps.openDownloads() })
        add(ProfileEntry("horus", Icons.Rounded.Inbox, "Mes demandes Horus", "Suivi de vos demandes", ProfileAccents.Horus) { navigator.open(Route.HorusRequests) })
        add(ProfileEntry("sync", Icons.Rounded.SyncAlt, "Comptes liés", "Deezer et Spotify : playlists importées et envoyées", ProfileAccents.Mira) { navigator.open(Route.SyncAccounts) })
        add(ProfileEntry("releases", Icons.Rounded.CalendarMonth, "Prochaines sorties", "Calendrier des épisodes et films à venir", ProfileAccents.Releases) { navigator.open(Route.Releases) })
    }
    val newsEntries = listOf(
        ProfileEntry("alerts", Icons.Rounded.NotificationsActive, "Notifications", "Demandes Horus, séries suivies, succès", ProfileAccents.Alerts) { navigator.open(Route.AlertSettings) },
        ProfileEntry("notices", Icons.Rounded.Campaign, "Annonces", "Les nouvelles de Nflix", ProfileAccents.Notices) { navigator.open(Route.Notices) },
        ProfileEntry("support", Icons.Rounded.Report, "Mes signalements", "Problèmes et idées envoyés à Mira", ProfileAccents.Alerts) { navigator.open(Route.Support) },
    )
    val event = org.jellyfin.mobile.nflix.ui.SeasonalThemes.active
    val seasonalSubtitle = when {
        !org.jellyfin.mobile.nflix.ui.SeasonalThemes.enabled && event == null -> "Désactivés"
        event != null -> "${event.emoji} ${event.label} en ce moment"
        else -> "Noël, Halloween, 14 Juillet, Nouvel An…"
    }
    val lookEntries = listOf(
        ProfileEntry("seasonal", Icons.Rounded.Celebration, "Thèmes saisonniers", seasonalSubtitle, Nflix.Red) { seasonalSheet = true },
        ProfileEntry("playback", Icons.Rounded.Settings, "Lecture et lecteur", "Qualité, sous-titres, lecteur vidéo", ProfileAccents.Playback) { deps.openSettings() },
    )
    val appEntries = buildList {
        add(ProfileEntry("updates", Icons.Rounded.SystemUpdate, "Mises à jour", "Version ${BuildConfig.VERSION_NAME}", ProfileAccents.Updates) { scope.launch { deps.updater.check(force = true) } })
        if (miraInstalled) {
            add(
                ProfileEntry("mira", Icons.Rounded.Key, "Mira Connect", "Gérer votre compte Mira", ProfileAccents.Mira) {
                    context.packageManager.getLaunchIntentForPackage("fr.miracloud.connect")?.let { context.startActivity(it) }
                },
            )
        }
        add(ProfileEntry("web", Icons.Rounded.Language, "Version web", "Administration et pages des plugins", ProfileAccents.Web) { deps.onOpenWeb() })
        add(ProfileEntry("tutorial", Icons.Rounded.School, "Didacticiel", "Revoir la présentation de Nflix", ProfileAccents.Tutorial) { org.jellyfin.mobile.nflix.ui.tutorial.Tutorial.open() })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                ProfileHeader(
                    displayName = deps.session.miraName?.takeIf { it.isNotBlank() } ?: me?.name ?: deps.session.userName,
                    email = deps.session.miraEmail?.takeIf { it.isNotBlank() },
                    avatar = org.jellyfin.mobile.nflix.MiraProfile.safeAvatar(deps.session.miraAvatar),
                    plan = plan,
                    viaMira = deps.session.lastMethod == "mira",
                    serverHost = serverHost,
                    version = BuildConfig.VERSION_NAME,
                    modifier = Modifier.staggerIn(entrance, 0),
                )
            }
            item(key = "section-space") { ProfileSection("Mon espace", spaceEntries, Modifier.staggerIn(entrance, 1)) }
            item(key = "section-news") { ProfileSection("Notifications et actualités", newsEntries, Modifier.staggerIn(entrance, 2)) }
            item(key = "section-look") { ProfileSection("Apparence et lecture", lookEntries, Modifier.staggerIn(entrance, 3)) }
            item(key = "section-app") { ProfileSection("Application", appEntries, Modifier.staggerIn(entrance, 4)) }
            item(key = "sign-out") {
                ProfileDangerRow(Icons.AutoMirrored.Rounded.Logout, "Se déconnecter", Modifier.padding(top = 8.dp).staggerIn(entrance, 5)) { deps.onSignOut() }
            }
            item(key = "footer") {
                Text(
                    "Nflix ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Nflix.Muted.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp).staggerIn(entrance, 6),
                )
            }
        }
        BackButton()
    }
}
