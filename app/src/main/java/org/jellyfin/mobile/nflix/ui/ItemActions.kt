package org.jellyfin.mobile.nflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Report
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.Item

/** What a long press was made on. [playlistId] is set when the item is an entry of that playlist. */
sealed interface MenuTarget {
    data class Media(val item: Item, val playlistId: String? = null) : MenuTarget
    data object Player : MenuTarget
}

/** Global long-press menu: any card calls [ItemMenu.show]; [ItemMenuHost] in the root draws it. */
object ItemMenu {
    var target by mutableStateOf<MenuTarget?>(null)
        private set

    /** Bumped after an action that changes lists (playlists, continue watching) so screens reload. */
    var revision by mutableIntStateOf(0)
        private set

    var message by mutableStateOf<String?>(null)

    /** Item being reported to Mira (problem or improvement), shown by [ItemMenuHost]. */
    var reporting by mutableStateOf<Pair<Item, String>?>(null)
        private set

    fun report(item: Item, kind: String = "incident") { target = null; reporting = item to kind }
    fun endReport() { reporting = null }

    fun show(item: Item, playlistId: String? = null) { target = MenuTarget.Media(item, playlistId) }
    fun showPlayer() { target = MenuTarget.Player }
    fun dismiss() { target = null }
    fun changed(text: String) { revision++; message = text }
}

private data class MenuAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val run: suspend () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemMenuHost() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val userData by deps.jellyfin.userDataChanges.collectAsState()
    var playlistPicker by remember { mutableStateOf<List<Item>?>(null) }
    var rename by remember { mutableStateOf<Item?>(null) }
    var confirmDelete by remember { mutableStateOf<Item?>(null) }

    ItemMenu.message?.let { text ->
        LaunchedEffect(text) { Toasts.show(text, ToastKind.Success); ItemMenu.message = null }
    }

    val target = ItemMenu.target ?: run {
        ItemMenu.reporting?.let { (item, kind) -> ReportDialog(item, kind) { ItemMenu.endReport() } }
        playlistPicker?.let { items -> PlaylistPicker(items) { playlistPicker = null } }
        rename?.let { playlist -> RenameDialog(playlist) { rename = null } }
        confirmDelete?.let { playlist ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Supprimer « ${playlist.name} » ?") },
                text = { Text("La playlist sera supprimée pour de bon. Les titres restent dans la bibliothèque.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { deps.jellyfin.deletePlaylist(playlist.id) }
                                .onSuccess { ItemMenu.changed("Playlist supprimée"); if (navigatorShowing(navigator, playlist.id)) navigator.back() }
                                .onFailure { ItemMenu.message = "Suppression refusée par le serveur." }
                            confirmDelete = null
                        }
                    }) { Text("Supprimer", color = Nflix.Red) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } },
                containerColor = Nflix.SurfaceHigh,
            )
        }
        return
    }

    val actions: List<MenuAction>
    val title: String
    val subtitle: String?
    val image: String?
    when (target) {
        MenuTarget.Player -> {
            val state = deps.music.state.value
            title = state.current?.title ?: "Lecteur"
            subtitle = state.current?.artist
            image = state.current?.artwork
            actions = buildList {
                add(MenuAction(Icons.Rounded.PlaylistPlay, "Ouvrir le lecteur") { navigator.open(Route.NowPlaying) })
                state.current?.albumId?.let { id -> add(MenuAction(Icons.Rounded.Album, "Aller à l'album") { navigator.open(Route.Album(id)) }) }
                add(MenuAction(Icons.Rounded.Shuffle, if (state.shuffle) "Désactiver l'aléatoire" else "Activer l'aléatoire") { deps.music.toggleShuffle() })
                add(MenuAction(Icons.Rounded.Stop, "Arrêter la musique", destructive = true) { deps.music.stop() })
            }
        }
        is MenuTarget.Media -> {
            val item = target.item.let { base -> userData[base.id]?.let { base.copy(userData = it) } ?: base }
            title = item.seriesName ?: item.name
            subtitle = listOfNotNull(if (item.seriesName != null) item.name else null, item.albumArtist, item.year?.toString()).joinToString(" · ").ifBlank { null }
            image = deps.jellyfin.primary(item, 200)
            val favorite = item.userData?.favorite == true
            val played = item.userData?.played == true
            val resumable = (item.userData?.positionTicks ?: 0) > 0 && !played
            val music = item.type in setOf("Audio", "MusicAlbum", "MusicArtist")
            actions = buildList {
                when (item.type) {
                    "Audio" -> {
                        add(MenuAction(Icons.Rounded.PlayArrow, "Lire") { deps.music.play(listOf(item)) })
                        add(MenuAction(Icons.Rounded.PlaylistPlay, "Lire ensuite") { deps.music.enqueue(listOf(item), next = true); ItemMenu.message = "Lu après le titre en cours" })
                        add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Ajouter à la file d'attente") { deps.music.enqueue(listOf(item)); ItemMenu.message = "Ajouté à la file d'attente" })
                    }
                    "MusicAlbum", "MusicArtist", "Playlist" -> {
                        add(MenuAction(Icons.Rounded.PlayArrow, "Lire") { deps.play(scope, item, navigator = navigator) })
                        if (item.type == "MusicAlbum") {
                            add(MenuAction(Icons.Rounded.Shuffle, "Lecture aléatoire") { deps.music.play(deps.jellyfin.albumTracks(item.id), shuffle = true) })
                            add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Ajouter à la file d'attente") { deps.music.enqueue(deps.jellyfin.albumTracks(item.id)); ItemMenu.message = "Album ajouté à la file" })
                        }
                    }
                    "Book" -> add(MenuAction(Icons.Rounded.PlayArrow, "Lire") { navigator.open(Route.Reader(item.id)) })
                    else -> {
                        add(MenuAction(Icons.Rounded.PlayArrow, if (resumable) "Reprendre" else "Lecture") { deps.play(scope, item, navigator = navigator) })
                        if (resumable) add(MenuAction(Icons.Rounded.Replay, "Reprendre du début") { deps.play(scope, item, fromStart = true, navigator = navigator) })
                    }
                }
                if (item.type != "Playlist") {
                    add(
                        MenuAction(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            when {
                                music && favorite -> "Retirer des titres likés"
                                music -> "Aimer"
                                favorite -> "Retirer de Ma liste"
                                else -> "Ajouter à Ma liste"
                            },
                        ) { deps.jellyfin.setFavorite(item, !favorite); ItemMenu.message = if (favorite) "Retiré" else "Ajouté" },
                    )
                }
                if (item.type in setOf("Audio", "MusicAlbum")) {
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist…") {
                        playlistPicker = if (item.type == "MusicAlbum") deps.jellyfin.albumTracks(item.id) else listOf(item)
                    })
                }
                if (item.type != "Playlist") {
                    add(MenuAction(Icons.Rounded.Report, "Signaler un problème") { ItemMenu.report(item, "incident") })
                    add(MenuAction(Icons.Rounded.Lightbulb, "Proposer une amélioration") { ItemMenu.report(item, "improvement") })
                }
                if (target.playlistId != null && item.playlistItemId != null) {
                    add(MenuAction(Icons.Rounded.RemoveCircleOutline, "Retirer de cette playlist", destructive = true) {
                        deps.jellyfin.removeFromPlaylist(target.playlistId, listOf(item.playlistItemId))
                        ItemMenu.changed("Retiré de la playlist")
                    })
                }
                if (item.isPlayableVideo || item.type in setOf("Series", "Season", "Book")) {
                    add(MenuAction(if (played) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (played) "Marquer comme non vu" else "Marquer comme vu") {
                        deps.jellyfin.setPlayed(item, !played); ItemMenu.changed(if (played) "Marqué comme non vu" else "Marqué comme vu")
                    })
                }
                if (resumable && (item.isPlayableVideo || item.type == "AudioBook")) {
                    add(MenuAction(Icons.Rounded.RemoveCircleOutline, "Retirer de « Reprendre »") {
                        deps.jellyfin.removeFromResume(item); ItemMenu.changed("Retiré de « Reprendre »")
                    })
                }
                if (item.isPlayableVideo || item.type in setOf("Season", "Series", "MusicAlbum", "MusicArtist", "Playlist")) {
                    add(MenuAction(Icons.Rounded.Download, "Télécharger") { deps.downloadComplete(item) })
                }
                if (item.type == "Playlist") {
                    add(MenuAction(Icons.Rounded.Edit, "Renommer") { rename = item })
                    add(MenuAction(Icons.Rounded.Delete, "Supprimer la playlist", destructive = true) { confirmDelete = item })
                }
                item.seriesId?.takeIf { item.type in setOf("Episode", "Season") }?.let { id -> add(MenuAction(Icons.Rounded.Tv, "Voir la série") { navigator.open(Route.Item(id)) }) }
                item.albumId?.takeIf { item.type == "Audio" }?.let { id -> add(MenuAction(Icons.Rounded.Album, "Aller à l'album") { navigator.open(Route.Album(id)) }) }
                (item.albumArtists.firstOrNull() ?: item.artistItems.firstOrNull())?.takeIf { music }?.let { artist ->
                    add(MenuAction(Icons.Rounded.Person, "Aller à l'artiste") { navigator.open(Route.Artist(artist.id)) })
                }
                if (item.type !in setOf("Audio", "MusicAlbum", "MusicArtist", "Playlist")) add(MenuAction(Icons.Rounded.Info, "Voir la fiche") { navigator.open(Route.Item(item.id)) })
            }
        }
    }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { ItemMenu.dismiss() }, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                NImage(image, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Nflix.Outline)
            actions.forEach { action ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        ItemMenu.dismiss()
                        scope.launch { runCatching { action.run() }.onFailure { ItemMenu.message = "Action impossible : ${it.message ?: "erreur serveur"}" } }
                    }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(action.icon, null, tint = if (action.destructive) Nflix.Red else Nflix.Text)
                    Spacer(Modifier.width(18.dp))
                    Text(action.label, color = if (action.destructive) Nflix.Red else Nflix.Text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private val reportCategories = mapOf(
    "Movie" to listOf("Vidéo", "Son", "Sous-titres", "Mauvaise version", "Infos / image", "Autre"),
    "Episode" to listOf("Vidéo", "Son", "Sous-titres", "Épisode manquant", "Mauvais épisode", "Infos / image", "Autre"),
    "Season" to listOf("Épisodes manquants", "Mauvais ordre", "Qualité", "Sous-titres", "Infos / image", "Autre"),
    "Series" to listOf("Saison manquante", "Épisodes manquants", "Qualité", "Infos / image", "Autre"),
    "Audio" to listOf("Ne se lit pas", "Son", "Paroles", "Infos / pochette", "Autre"),
    "MusicAlbum" to listOf("Titres manquants", "Ordre des titres", "Son", "Infos / pochette", "Autre"),
    "Book" to listOf("Ne s'ouvre pas", "Pages manquantes", "Mauvais livre", "Infos / couverture", "Autre"),
)

private fun reportLabel(item: Item): String = when (item.type) {
    "Movie" -> "Film"; "Series" -> "Série"; "Season" -> "Saison"; "Episode" -> "Épisode"
    "Audio" -> "Musique"; "MusicAlbum" -> "Album"; "MusicArtist" -> "Artiste"; "Book" -> "Livre"; "AudioBook" -> "Livre audio"
    else -> item.type
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportDialog(item: Item, initialKind: String, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val repo = remember(deps.api, context) { org.jellyfin.mobile.nflix.data.SupportRepository(deps.api, context) }
    var kind by remember { mutableStateOf(initialKind) }
    var category by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val categories = reportCategories[item.type] ?: listOf("Lecture", "Qualité", "Infos / image", "Autre")
    val where = listOfNotNull(
        item.seriesName,
        item.parentIndexNumber?.let { "S$it" }.takeIf { item.type == "Episode" },
        item.indexNumber?.let { if (item.type == "Episode") "É$it" else null },
        item.name,
    ).joinToString(" · ")
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (kind == "incident") "Signaler un problème" else "Proposer une amélioration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${reportLabel(item)} · $where", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == "incident", { kind = "incident"; category = null }, { Text("Problème") })
                    FilterChip(kind == "improvement", { kind = "improvement"; category = null }, { Text("Amélioration") })
                }
                if (kind == "incident") FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { label -> FilterChip(category == label, { category = if (category == label) null else label }, { Text(label) }) }
                }
                OutlinedTextField(
                    value = message, onValueChange = { if (it.length <= 4000) message = it },
                    placeholder = { Text(if (kind == "incident") "Que se passe-t-il ? (minute, appareil…)" else "Votre idée") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Text("Visible par les admins dans Mira Connect › Tickets.", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        },
        confirmButton = {
            TextButton(enabled = (message.isNotBlank() || category != null) && !sending, onClick = {
                sending = true
                val text = message.trim().ifBlank { category.orEmpty() }
                val ctx = listOfNotNull(category?.let { "Catégorie : $it" }, where.takeIf { it.isNotBlank() }?.let { "Élément : $it" }).joinToString("\n").ifBlank { null }
                scope.launch {
                    try {
                        when (repo.report(item, kind, text, ctx)) {
                            in 200..299 -> ItemMenu.changed("Signalement envoyé à Mira")
                            0 -> ItemMenu.message = "Connectez Mira Connect pour signaler"
                            401, 403 -> ItemMenu.message = "Session Mira expirée : ouvrez Mira Connect"
                            429 -> ItemMenu.message = "Limite du jour atteinte (20 signalements)"
                            else -> ItemMenu.message = "Envoi impossible. Réessayez plus tard."
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ItemMenu.message = "Envoi impossible. Réessayez plus tard."
                    } finally {
                        sending = false
                        onDismiss()
                    }
                }
            }) { Text(if (sending) "Envoi…" else "Envoyer", color = Nflix.Red) }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Annuler") } },
        containerColor = Nflix.SurfaceHigh,
    )
}

private fun navigatorShowing(navigator: Navigator, id: String) = navigator.current.let { it is Route.Playlist && it.id == id }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPicker(items: List<Item>, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val playlists = rememberLoad("audio-playlists", ItemMenu.revision) { deps.jellyfin.audioPlaylists(it) }.value
    var creating by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                if (items.size == 1) "Ajouter « ${items.first().name} »" else "Ajouter ${items.size} titres",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Nflix.Red), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Nouvelle playlist", fontWeight = FontWeight.Bold)
                    Text("Privée : visible par vous seul", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(playlists.orEmpty(), key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                runCatching { deps.jellyfin.addToPlaylist(playlist.id, items.map { it.id }) }
                                    .onSuccess { ItemMenu.changed("Ajouté à « ${playlist.name} »") }
                                    .onFailure { ItemMenu.message = "Ajout refusé par le serveur." }
                                onDismiss()
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NImage(deps.jellyfin.primary(playlist, 120), Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic)
                        Spacer(Modifier.width(14.dp))
                        Text(playlist.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        playlist.childCount?.let { Text("$it", color = Nflix.Muted, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    if (creating) NameDialog("Nouvelle playlist", "", onDismiss = { creating = false }) { name ->
        scope.launch {
            runCatching { deps.jellyfin.createPlaylist(name, items.map { it.id }) }
                .onSuccess { ItemMenu.changed("Playlist « $name » créée") }
                .onFailure { ItemMenu.message = "Création refusée par le serveur." }
            creating = false
            onDismiss()
        }
    }
}

@Composable
private fun RenameDialog(playlist: Item, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    NameDialog("Renommer la playlist", playlist.name, onDismiss) { name ->
        scope.launch {
            runCatching { deps.jellyfin.renamePlaylist(playlist.id, name) }
                .onSuccess { ItemMenu.changed("Playlist renommée") }
                .onFailure { ItemMenu.message = "Renommage refusé par le serveur." }
            onDismiss()
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(80) }, singleLine = true, placeholder = { Text("Nom de la playlist") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Valider", color = Nflix.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Nflix.SurfaceHigh,
    )
}
