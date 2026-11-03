package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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

private data class SearchResults(val local: List<Item>, val horus: List<HorusMedia>)

private val groups = listOf(
    "Movie" to "Films", "Series" to "Séries", "Episode" to "Épisodes", "MusicAlbum" to "Albums", "MusicArtist" to "Artistes",
    "Audio" to "Titres", "Book" to "Livres", "AudioBook" to "Livres audio", "BoxSet" to "Collections", "Playlist" to "Playlists", "Person" to "Personnes",
)

@Composable
fun SearchScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = rememberLoad("search", query) {
        if (query.length < 2) return@rememberLoad SearchResults(emptyList(), emptyList())
        delay(300)
        coroutineScope {
            val local = async { runCatching { deps.jellyfin.search(query, 60) }.getOrDefault(emptyList()) }
            val horus = async { runCatching { deps.horus.searchEverything(query) }.getOrDefault(emptyList()) }
            SearchResults(local.await(), horus.await().filter { it.jellyfinId == null })
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Films, séries, musique, livres…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, unfocusedBorderColor = Nflix.Outline, focusedContainerColor = Nflix.Surface, unfocusedContainerColor = Nflix.Surface),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        val data = results.value
        when {
            query.length < 2 -> EmptyState(Icons.Rounded.Search, "Que voulez-vous regarder ?", "La recherche couvre Nflix et Horus.")
            results is Load.Loading && data == null -> Column { repeat(2) { SkeletonRow() } }
            data != null -> LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                groups.forEach { (type, label) ->
                    val items = data.local.filter { it.type == type }
                    rowSection("s-$type", label, items, { it.id }) { item ->
                        val origin = "search/${item.id}"
                        val square = type in setOf("MusicAlbum", "MusicArtist", "Audio", "Person")
                        PosterCard(
                            item, deps.jellyfin.primary(item, 300), origin,
                            {
                                navigator.open(
                                    when (type) {
                                        "MusicAlbum" -> Route.Album(item.id)
                                        "MusicArtist" -> Route.Artist(item.id)
                                        "Playlist" -> Route.Playlist(item.id)
                                        "Person" -> Route.Person(item.id, item.name)
                                        "Audio" -> Route.Album(item.albumId ?: item.id)
                                        else -> Route.Item(item.id, origin)
                                    },
                                )
                            },
                            aspect = if (square) 1f else 2f / 3f,
                            subtitle = item.seriesName ?: item.albumArtist ?: item.year?.toString(),
                        )
                    }
                }
                rowSection("s-horus", "Pas encore sur Nflix · demander sur Horus", data.horus, { "h-${it.type.api}-${it.id}" }) { media ->
                    HorusCard(media, { navigator.open(Route.HorusDetail(media.type, media.id)) })
                }
                if (data.local.isEmpty() && data.horus.isEmpty()) item { EmptyState(Icons.Rounded.Search, "Aucun résultat", "Vérifiez l'orthographe.") }
            }
        }
    }
}

@Composable
fun NoticesScreen() {
    val deps = LocalDeps.current
    val notices by deps.plugins.notices.collectAsState()
    LaunchedEffect(Unit) { deps.plugins.refreshNotices() }
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
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 56.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val userId = me?.id ?: deps.session.userId
                    Box(Modifier.size(104.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                        if (userId != null) NImage(deps.api.userImageUrl(userId, me?.imageTag), Modifier.size(104.dp), placeholderIcon = Icons.Rounded.Person)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(me?.name ?: deps.session.userName ?: "", style = MaterialTheme.typography.headlineSmall)
                    Text(if (deps.session.lastMethod == "mira") "Connecté avec Mira Connect" else "Connecté avec vos identifiants", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    Spacer(Modifier.height(20.dp))
                }
            }
            item { ProfileRow(Icons.Rounded.Download, "Téléchargements", "Vos films et épisodes hors ligne") { deps.openDownloads() } }
            item { ProfileRow(Icons.Rounded.Inbox, "Mes demandes Horus", "Suivi de vos demandes") { navigator.open(Route.HorusRequests) } }
            item { ProfileRow(Icons.Rounded.Campaign, "Annonces", "Les nouvelles de Nflix") { navigator.open(Route.Notices) } }
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
