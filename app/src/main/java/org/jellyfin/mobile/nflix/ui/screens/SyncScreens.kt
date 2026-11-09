package org.jellyfin.mobile.nflix.ui.screens

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.SyncConfig
import org.jellyfin.mobile.nflix.data.SyncPlaylist
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.ItemMenu
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix

/**
 * Linked streaming accounts (Deezer / Spotify). The OAuth flow runs in an in-app WebView that
 * intercepts the redirect URI, so the code never leaves the device. Import matches tracks against
 * the local library (artist+title, server-side); Deezer export creates the playlist on the account.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SyncAccountsScreen() {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<SyncConfig?>(null) }
    var accounts by remember { mutableStateOf<List<org.jellyfin.mobile.nflix.data.SyncAccountInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var oauth by remember { mutableStateOf<String?>(null) } // service being linked
    var playlists by remember { mutableStateOf<Map<String, List<SyncPlaylist>>>(emptyMap()) }
    var importing by remember { mutableStateOf<String?>(null) }
    var localPlaylists by remember { mutableStateOf<List<org.jellyfin.mobile.nflix.data.Item>?>(null) }

    fun refresh() {
        scope.launch {
            config = runCatching { deps.sync.config() }.getOrNull()
            accounts = runCatching { deps.sync.status() }.getOrDefault(emptyList())
            localPlaylists = runCatching { deps.jellyfin.audioPlaylists(force = true) }.getOrNull()
            for (service in accounts.map { it.service }) {
                playlists = playlists + (service to runCatching { deps.sync.playlists(service) }.getOrDefault(emptyList()))
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    // OAuth sheet: loads the provider's consent page, intercepts the redirect to grab the code.
    if (oauth != null && config != null && config!!.redirectUri.isNotBlank()) {
        val service = oauth!!
        // Anti-CSRF: the redirect is only accepted with the state this sheet generated.
        val state = remember(service) { java.util.UUID.randomUUID().toString().replace("-", "") }
        val url = if (service == "deezer") {
            "https://connect.deezer.com/oauth/auth.php?app_id=${config!!.deezerAppId}&redirect_uri=${java.net.URLEncoder.encode(config!!.redirectUri, "UTF-8")}&perms=basic_access,manage_library,offline_access&state=$state"
        } else {
            "https://accounts.spotify.com/authorize?client_id=${config!!.spotifyClientId}&response_type=code&redirect_uri=${java.net.URLEncoder.encode(config!!.redirectUri, "UTF-8")}&scope=playlist-read-private%20user-library-read&show_dialog=true&state=$state"
        }
        ModalBottomSheet(
            onDismissRequest = { oauth = null },
            modifier = Modifier.fillMaxWidth().height(560.dp),
            containerColor = Nflix.SurfaceHigh,
            contentColor = Nflix.Text,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connexion ${service.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Connecte-toi, puis reviens ici.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString().orEmpty()
                                    if (u.startsWith(config!!.redirectUri)) {
                                        val code = request!!.url.getQueryParameter("code")
                                        val returnedState = request.url.getQueryParameter("state")
                                        // Deezer may not echo "state": only Spotify must return it, a mismatch is always refused.
                                        if (returnedState != state && (service == "spotify" || returnedState != null)) {
                                            ItemMenu.message = "Liaison refusée (réponse inattendue)"
                                            oauth = null
                                        } else if (!code.isNullOrBlank()) {
                                            scope.launch {
                                                busy = true
                                                runCatching { deps.sync.link(service, code) }
                                                    .onSuccess { ItemMenu.message = "Compte ${service} lié" }
                                                    .onFailure { ItemMenu.message = it.message ?: "Liaison impossible" }
                                                busy = false
                                                oauth = null
                                                refresh()
                                            }
                                        }
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    onRelease = { web -> web.stopLoading(); web.destroy() },
                )
            }
        }
    }

    // Google device flow: show the code, open google.com/device, poll until linked.
    var googleCode by remember { mutableStateOf<org.jellyfin.mobile.nflix.data.GoogleDeviceCode?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(googleCode) {
        val code = googleCode ?: return@LaunchedEffect
        var wait = code.interval.coerceAtLeast(5) * 1000L
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(wait)
            when (runCatching { deps.sync.googlePoll() }.getOrDefault("pending")) {
                "linked" -> { ItemMenu.message = "Compte musique lié"; googleCode = null; refresh(); return@LaunchedEffect }
                "denied" -> { ItemMenu.message = "Liaison refusée"; googleCode = null; return@LaunchedEffect }
                "expired" -> { ItemMenu.message = "Code expiré, recommencez"; googleCode = null; return@LaunchedEffect }
                "slow_down" -> wait += 5_000
            }
        }
        googleCode = null
    }
    googleCode?.let { code ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { googleCode = null },
            containerColor = Nflix.SurfaceHigh,
            title = { Text("Lier la musique en ligne") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ouvrez ${code.verificationUrl.removePrefix("https://")} et saisissez ce code :", style = MaterialTheme.typography.bodyMedium)
                    Text(code.userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Nflix.Red)
                    Text("La liaison se termine toute seule ici.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                    clip?.setPrimaryClip(android.content.ClipData.newPlainText("code", code.userCode))
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(code.verificationUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Copier et ouvrir", color = Nflix.Red) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { googleCode = null }) { Text("Annuler") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ContentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton()
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Comptes liés", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Playlists importées et envoyées", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { oauth = "deezer" },
                    enabled = config?.deezerConfigured == true && config?.redirectUri?.isNotBlank() == true && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Text("Lier Deezer", Modifier.padding(start = 6.dp)) }
                OutlinedButton(
                    onClick = { oauth = "spotify" },
                    enabled = config?.spotifyConfigured == true && config?.redirectUri?.isNotBlank() == true && !busy,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (config?.spotifyConfigured == true) Nflix.Red else Nflix.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Nflix.Text),
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Text("Lier Spotify", Modifier.padding(start = 6.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { deps.sync.googleStart() }
                                .onSuccess { googleCode = it }
                                .onFailure { ItemMenu.message = if ((it as? org.jellyfin.mobile.nflix.data.NflixHttpException)?.code == 412) "Compte musique non configuré sur le serveur" else (serverMessage(it) ?: "Service de musique injoignable") }
                        }
                    },
                    enabled = config?.youtubeConfigured == true && !busy && accounts.none { it.service == "youtube" },
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (config?.youtubeConfigured == true) Nflix.Red else Nflix.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Nflix.Text),
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Text("Lier la musique en ligne", Modifier.padding(start = 6.dp)) }
            }
            if (config != null && !config!!.deezerConfigured && !config!!.spotifyConfigured && !config!!.youtubeConfigured) {
                Text(
                    "Aucun service configuré côté serveur : un administrateur doit renseigner les identifiants Deezer, Spotify ou Google dans les réglages de Nflix Butler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nflix.Warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        if (accounts.isNotEmpty()) {
            item { Text("Liés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Nflix.Text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
            items(accounts, key = { it.service }) { account ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Nflix.Surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SyncAlt, null, tint = Nflix.Success)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(serviceLabel(account.service), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(account.profileName.ifBlank { account.profileId }, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                    IconButton(onClick = { scope.launch { runCatching { deps.sync.unlink(account.service) }; refresh() } }) {
                        Icon(Icons.Rounded.LinkOff, "Délier", tint = Nflix.Muted)
                    }
                }
            }
        }
        // Import: playlists of each linked account.
        accounts.forEach { account ->
            val lists = playlists[account.service].orEmpty()
            if (lists.isNotEmpty()) {
                item { Text("Importer depuis ${serviceLabel(account.service)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Nflix.Text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
                items(lists, key = { "${account.service}-${it.id}" }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Nflix.Surface)
                            .clickable(enabled = importing == null) {
                                importing = playlist.id
                                scope.launch {
                                    runCatching { deps.sync.import(account.service, playlist.id) }
                                        .onSuccess { result ->
                                            val ids = result.matched.map { it.itemId }.distinct()
                                            if (ids.isEmpty()) {
                                                ItemMenu.message = "Aucun morceau trouvé localement sur ${result.total}"
                                            } else {
                                                val created = runCatching { deps.jellyfin.createPlaylist(playlist.title, ids) }.getOrNull()
                                                val missing = if (result.missing.isNotEmpty()) " · ${result.missing.size} à télécharger" else ""
                                                ItemMenu.message = if (created != null)
                                                    "« ${playlist.title} » ajoutée : ${ids.size}/${result.total} morceaux$missing"
                                                else
                                                    "Import terminé, playlist non créée (${ids.size}/${result.total})"
                                                localPlaylists = runCatching { deps.jellyfin.audioPlaylists(force = true) }.getOrNull()
                                            }
                                        }
                                        .onFailure { ItemMenu.message = it.message ?: "Import impossible" }
                                    importing = null
                                }
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, null, tint = Nflix.Red)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(playlist.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.tracks} morceaux", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                        }
                        if (importing == playlist.id) CircularProgressIndicator(Modifier.size(20.dp), color = Nflix.Red, strokeWidth = 2.dp)
                    }
                }
            }
        }
        // Export: local playlists pushed to Deezer.
        val deezerLinked = accounts.any { it.service == "deezer" }
        val locals = localPlaylists.orEmpty()
        if (deezerLinked && locals.isNotEmpty()) {
            item { Text("Envoyer sur Deezer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Nflix.Text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
            items(locals, key = { "local-${it.id}" }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Nflix.Surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                val tracks = deps.jellyfin.playlistItems(playlist.id)
                                deps.sync.exportDeezer(playlist.name, tracks.map { org.jellyfin.mobile.nflix.data.DeezerTrack(title = it.name, artist = it.artistItems.joinToString { a -> a.name }) })
                            }
                                .onSuccess { ItemMenu.message = "Playlist envoyée sur Deezer" }
                                .onFailure { ItemMenu.message = it.message ?: "Envoi impossible" }
                            busy = false
                        }
                    }, enabled = !busy) {
                        Icon(Icons.Rounded.Upload, null, Modifier.size(16.dp))
                        Text("Envoyer", Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        if (accounts.isEmpty() && config != null) {
            item { EmptyState(Icons.Rounded.SyncAlt, "Aucun compte lié", "Liez Deezer, Spotify ou votre compte musique pour importer vos playlists.") }
        }
    }
}

private fun serviceLabel(service: String): String = when (service) {
    "youtube" -> "Musique en ligne"
    "deezer" -> "Deezer"
    "spotify" -> "Spotify"
    else -> service.replaceFirstChar { it.uppercase() }
}

/** French message returned by the server for a failed linking request, when there is one. */
private fun serverMessage(error: Throwable): String? {
    val body = (error as? org.jellyfin.mobile.nflix.data.NflixHttpException)?.body ?: return null
    return runCatching {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(body)
        ((element as? kotlinx.serialization.json.JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
