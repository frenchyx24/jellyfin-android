package org.jellyfin.mobile.nflix.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.nflix.data.AdminSession
import org.jellyfin.mobile.nflix.data.CompanyLogo
import org.jellyfin.mobile.nflix.data.Distributor
import org.jellyfin.mobile.nflix.data.PendingRequest
import org.jellyfin.mobile.nflix.data.StudioMatch
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.ItemMenu
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value
import java.io.ByteArrayOutputStream

/** Shows [content] only to Jellyfin administrators; the server enforces the same rule on every call. */
@Composable
private fun AdminOnly(title: String, content: @Composable () -> Unit) {
    val deps = LocalDeps.current
    val admin = rememberLoad("is-admin") { deps.admin.isAdmin(it) }
    Box(Modifier.fillMaxSize()) {
        when {
            admin is Load.Failed -> EmptyState(Icons.Rounded.AdminPanelSettings, "Vérification impossible", admin.message, Modifier.align(Alignment.Center))
            admin.value == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Nflix.Red)
            admin.value != true -> EmptyState(Icons.Rounded.AdminPanelSettings, "Réservé aux administrateurs", "Votre compte n'a pas accès à $title.", Modifier.align(Alignment.Center))
            else -> content()
        }
        BackButton()
    }
}

@Composable
private fun AdminTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 64.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted) }
        }
        trailing()
    }
}

@Composable
fun AdminScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var confirmScan by remember { mutableStateOf(false) }
    AdminOnly("l'administration") {
        val distributors = rememberLoad("admin-distributors-count", ItemMenu.revision) { deps.admin.distributors(all = true, force = true) }.value
        val pending = rememberLoad("admin-pending-count", ItemMenu.revision) { deps.admin.pendingRequests() }
        val sessions = rememberLoad("admin-sessions-count") { deps.admin.sessions() }.value
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item { AdminTitle("Administration", "Outils réservés aux administrateurs Nflix") }
            item {
                AdminCard(Icons.Rounded.Business, "Distributeurs", distributors?.let { "${it.count { d -> d.enabled }} affiché(s) sur l'accueil · ${it.size} au total" } ?: "Chargement…", Nflix.Red) {
                    navigator.open(Route.AdminDistributors)
                }
            }
            item {
                AdminCard(
                    Icons.Rounded.Inbox, "Demandes à valider",
                    when (pending) {
                        is Load.Failed -> "Horus injoignable : ${pending.message}"
                        is Load.Ready -> if (pending.value.isEmpty()) "Aucune demande en attente" else "${pending.value.size} demande(s) en attente"
                        else -> "Chargement…"
                    },
                    Nflix.Warning,
                    badge = (pending as? Load.Ready)?.value?.size?.takeIf { it > 0 },
                ) { navigator.open(Route.AdminRequests) }
            }
            item {
                AdminCard(
                    Icons.Rounded.Sensors, "Activité en direct",
                    sessions?.let { list -> "${list.count { it.item != null }} en lecture · ${list.size} appareil(s) actif(s)" } ?: "Chargement…",
                    Nflix.Info,
                ) { navigator.open(Route.AdminActivity) }
            }
            item {
                AdminCard(Icons.Rounded.LibraryBooks, "Analyser les bibliothèques", "Recherche les nouveaux fichiers sur le serveur", Nflix.Success) { confirmScan = true }
            }
        }
        if (confirmScan) {
            AlertDialog(
                onDismissRequest = { confirmScan = false },
                title = { Text("Analyser toutes les bibliothèques ?") },
                text = { Text("Le serveur parcourt tous les dossiers. Cela peut le ralentir quelques minutes.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmScan = false
                        scope.launch {
                            runCatching { deps.admin.scanLibraries() }
                                .onSuccess { ItemMenu.message = "Analyse lancée sur le serveur" }
                                .onFailure { ItemMenu.message = "Analyse refusée : ${it.message}" }
                        }
                    }) { Text("Lancer", color = Nflix.Red) }
                },
                dismissButton = { TextButton(onClick = { confirmScan = false }) { Text("Annuler") } },
                containerColor = Nflix.SurfaceHigh,
            )
        }
    }
}

@Composable
private fun AdminCard(icon: ImageVector, title: String, subtitle: String, color: Color, badge: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
        }
        badge?.let {
            Text(
                "$it", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(Nflix.Red).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun AdminDistributorsScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Distributor>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Distributor?>(null) }
    LaunchedEffect(ItemMenu.revision) {
        runCatching { deps.admin.distributors(all = true, force = true) }.onSuccess { list = it; error = null }.onFailure { error = it.message }
    }
    fun save(next: List<Distributor>) {
        val previous = list
        list = next
        scope.launch {
            runCatching { deps.admin.saveDistributors(next) }
                .onSuccess { list = it }
                .onFailure { list = previous; ItemMenu.message = "Enregistrement refusé : ${it.message}" }
        }
    }
    AdminOnly("les distributeurs") {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item {
                AdminTitle("Distributeurs", "Une rangée par distributeur sur l'accueil, dans cet ordre") {
                    IconButton(onClick = { navigator.open(Route.AdminDistributorEdit(null)) }) { Icon(Icons.Rounded.Add, "Ajouter un distributeur", tint = Nflix.Red) }
                }
            }
            val current = list
            when {
                current == null && error != null -> item { EmptyState(Icons.Rounded.Business, "Distributeurs indisponibles", "Le plugin serveur Nflix Auth 1.3 est-il installé ? ($error)") }
                current == null -> items(3) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(84.dp)) }
                current.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.Rounded.Business, "Aucun distributeur", "Ajoutez Netflix, Disney+, Prime Video… pour créer leurs rangées.")
                        Button(onClick = { navigator.open(Route.AdminDistributorEdit(null)) }, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter un distributeur")
                        }
                    }
                }
                else -> itemsIndexed(current, key = { _, d -> d.id }) { index, d ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
                            .clickable { navigator.open(Route.AdminDistributorEdit(d.id)) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            IconButton(onClick = { save(current.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Monter")
                            }
                            IconButton(onClick = { save(current.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < current.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Descendre")
                            }
                        }
                        DistributorLogo(d, Modifier.width(96.dp).padding(horizontal = 6.dp), height = 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (d.studios.isEmpty()) "Aucun studio associé" else d.studios.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, color = if (d.studios.isEmpty()) Nflix.Warning else Nflix.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = d.enabled, onCheckedChange = { on -> save(current.map { if (it.id == d.id) it.copy(enabled = on) else it }) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red),
                        )
                        IconButton(onClick = { deleting = d }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = Nflix.Muted) }
                    }
                }
            }
        }
        deleting?.let { d ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Supprimer « ${d.name} » ?") },
                text = { Text("La rangée disparaît de l'accueil de tous les utilisateurs et son logo est supprimé du serveur.") },
                confirmButton = { TextButton(onClick = { save(list.orEmpty().filterNot { it.id == d.id }); deleting = null }) { Text("Supprimer", color = Nflix.Red) } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
                containerColor = Nflix.SurfaceHigh,
            )
        }
    }
}

/** Logo picked in the editor, applied once the distributor exists on the server. */
private sealed interface PendingLogo {
    data class Tmdb(val logo: CompanyLogo) : PendingLogo
    data class Upload(val bytes: ByteArray, val preview: Bitmap) : PendingLogo
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDistributorEditScreen(id: String?) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    var original by remember { mutableStateOf<Distributor?>(null) }
    var name by remember { mutableStateOf("") }
    var lightPlate by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    val studios = remember { mutableStateListOf<String>() }
    var pendingLogo by remember { mutableStateOf<PendingLogo?>(null) }
    var logos by remember { mutableStateOf<List<CompanyLogo>?>(null) }
    var matches by remember { mutableStateOf<List<StudioMatch>?>(null) }
    var studioQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        runCatching { deps.admin.distributors(all = true, force = true).firstOrNull { it.id == id } }
            .onSuccess { d ->
                original = d
                if (d != null) { name = d.name; lightPlate = d.lightPlate; enabled = d.enabled; studios.clear(); studios.addAll(d.studios); studioQuery = d.name }
                loaded = true
            }
            .onFailure { error = it.message; loaded = true }
    }
    // Suggestions follow the name as it is typed (debounced): TMDB logos through Horus, studios from Jellyfin.
    LaunchedEffect(name, searchTick) {
        if (name.isBlank()) { logos = emptyList(); return@LaunchedEffect }
        delay(450)
        logos = null
        logos = runCatching { deps.admin.searchLogos(name) }.getOrElse { error = "Recherche de logos impossible : ${it.message}"; emptyList() }
    }
    LaunchedEffect(studioQuery.ifBlank { name }) {
        val query = studioQuery.ifBlank { name }
        if (query.isBlank()) { matches = emptyList(); return@LaunchedEffect }
        delay(450)
        matches = null
        matches = runCatching { deps.admin.matchingStudios(query) }.getOrElse { emptyList() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { prepareLogo(context, uri) } }
                .onSuccess { pendingLogo = it }
                .onFailure { error = "Image illisible : ${it.message}" }
        }
    }

    fun save() {
        saving = true
        error = null
        scope.launch {
            runCatching {
                val all = deps.admin.distributors(all = true, force = true)
                val edited = (original ?: Distributor()).copy(name = name.trim(), studios = studios.toList(), lightPlate = lightPlate, enabled = enabled)
                val next = if (original != null) all.map { if (it.id == original!!.id) edited else it } else all + edited
                val saved = deps.admin.saveDistributors(next)
                // A new row gets its id from the server: it is the one that was not there before.
                val target = original?.let { o -> saved.first { it.id == o.id } } ?: saved.first { s -> all.none { it.id == s.id } }
                // Keep server-assigned id if logo upload fails, so retry updates row instead of duplicating it.
                original = target
                when (val logo = pendingLogo) {
                    is PendingLogo.Tmdb -> deps.admin.logoFromTmdb(target.id, logo.logo.logoPath)
                    is PendingLogo.Upload -> deps.admin.uploadLogo(target.id, logo.bytes, "image/png")
                    null -> Unit
                }
            }.onSuccess {
                ItemMenu.changed("Distributeur « ${name.trim()} » enregistré")
                navigator.back()
            }.onFailure { error = "Enregistrement impossible : ${it.message}" }
            saving = false
        }
    }

    AdminOnly("les distributeurs") {
        if (!loaded) {
            CircularProgressIndicator(color = Nflix.Red)
            return@AdminOnly
        }
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item { AdminTitle(if (id == null) "Nouveau distributeur" else "Modifier le distributeur") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Nom (ex. Netflix)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, focusedLabelColor = Nflix.Red),
                    )
                    // Live preview on the chosen plate.
                    Text("Aperçu", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val plate = if (lightPlate) Color(0xFFF3F3F3) else Color(0xFF1C1C1C)
                        Box(Modifier.width(200.dp).height(72.dp).clip(RoundedCornerShape(10.dp)).background(plate).padding(10.dp), contentAlignment = Alignment.Center) {
                            when (val logo = pendingLogo) {
                                is PendingLogo.Tmdb -> NImage(logo.logo.previewUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholderIcon = null, background = Color.Transparent)
                                is PendingLogo.Upload -> androidx.compose.foundation.Image(logo.preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                null -> original?.let { DistributorLogo(it.copy(lightPlate = lightPlate), Modifier.fillMaxSize(), height = 52.dp) }
                                    ?: Text(name.ifBlank { "Logo" }, fontWeight = FontWeight.Black, color = if (lightPlate) Color.Black else Color.White)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Fond clair", style = MaterialTheme.typography.labelLarge)
                            Text("Pour les logos sombres", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                            Switch(checked = lightPlate, onCheckedChange = { lightPlate = it }, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Afficher sur l'accueil", modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Logos trouvés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Envoyer un logo") }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (val found = logos) {
                        null -> Row(Modifier.padding(16.dp)) { CircularProgressIndicator(Modifier.size(22.dp), color = Nflix.Red, strokeWidth = 2.dp) }
                        else -> if (found.isEmpty()) {
                            Text(
                                if (name.isBlank()) "Saisissez un nom pour chercher son logo." else "Aucun logo trouvé pour « $name ». Envoyez votre propre image.",
                                style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(found, key = { it.logoPath }) { logo ->
                                val selected = (pendingLogo as? PendingLogo.Tmdb)?.logo?.logoPath == logo.logoPath
                                Column(Modifier.width(140.dp).clickable { pendingLogo = logo.let { PendingLogo.Tmdb(it) } }) {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(10.dp))
                                            .background(if (lightPlate) Color(0xFFF3F3F3) else Color(0xFF1C1C1C))
                                            .border(if (selected) 3.dp else 1.dp, if (selected) Nflix.Red else Nflix.Outline, RoundedCornerShape(10.dp)).padding(10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { NImage(logo.previewUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholderIcon = null, background = Color.Transparent) }
                                    Text(logo.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    logo.country?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Studios Jellyfin rattachés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Les films et séries portant ces studios apparaîtront dans la rangée.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    if (studios.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            studios.forEach { studio ->
                                Row(
                                    Modifier.clip(CircleShape).background(Nflix.Red.copy(alpha = 0.18f)).clickable { studios.remove(studio) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(studio, style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Close, "Retirer", Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = studioQuery, onValueChange = { studioQuery = it.take(60) }, singleLine = true,
                        label = { Text("Chercher un studio (par défaut : le nom)") }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, focusedLabelColor = Nflix.Red),
                    )
                }
            }
            when (val found = matches) {
                null -> item { CircularProgressIndicator(Modifier.padding(16.dp).size(22.dp), color = Nflix.Red, strokeWidth = 2.dp) }
                else -> {
                    if (found.isEmpty()) item {
                        Text("Aucun studio de la bibliothèque ne correspond.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(16.dp))
                    }
                    items(found, key = { "m-${it.name}" }) { match ->
                        val checked = studios.any { it.equals(match.name, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().clickable { if (checked) studios.removeAll { it.equals(match.name, true) } else studios.add(match.name) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) Nflix.Red else Color.Transparent)
                                    .border(2.dp, if (checked) Nflix.Red else Nflix.Muted, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Text(match.name, modifier = Modifier.weight(1f))
                            Text("${match.count} titre(s)", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    error?.let { Text(it, color = Nflix.Red, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
                    Button(
                        onClick = { save() }, enabled = !saving && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red), modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer", fontWeight = FontWeight.Bold) }
                    }
                    if (studios.isEmpty()) Text("Sans studio rattaché, la rangée restera vide.", style = MaterialTheme.typography.labelSmall, color = Nflix.Warning, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Reads a picked image, scales it to at most 800 px wide and re-encodes it as PNG (keeps transparency, strips metadata). */
private fun prepareLogo(context: android.content.Context, uri: Uri): PendingLogo.Upload {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            total += count
            if (total > 20 * 1024 * 1024) error("image trop lourde (20 Mo maximum)")
            out.write(chunk, 0, count)
        }
        out.toByteArray()
    } ?: error("fichier vide")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("format non reconnu")
    val sample = generateSequence(1) { it * 2 }
        .takeWhile { bounds.outWidth / it > 800 || bounds.outHeight / it > 800 }
        .lastOrNull() ?: 1
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("format non reconnu")
    val scale = minOf(800f / source.width, 800f / source.height, 1f)
    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).coerceAtLeast(1f).toInt(), (source.height * scale).coerceAtLeast(1f).toInt(), true) else source
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    if (out.size() > 2 * 1024 * 1024) error("logo trop lourd après conversion (2 Mo maximum)")
    return PendingLogo.Upload(out.toByteArray(), bitmap)
}

@Composable
fun AdminRequestsScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<PendingRequest>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { runCatching { deps.admin.pendingRequests() }.onSuccess { requests = it }.onFailure { error = it.message } }
    AdminOnly("les demandes") {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item { AdminTitle("Demandes à valider", requests?.let { "${it.size} en attente" }) }
            val list = requests
            when {
                list == null && error != null -> item { EmptyState(Icons.Rounded.Inbox, "Horus injoignable", error) }
                list == null -> items(3) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(120.dp)) }
                list.isEmpty() -> item { EmptyState(Icons.Rounded.Inbox, "Rien à valider", "Toutes les demandes ont été traitées.") }
                else -> items(list, key = { it.request.id }) { pending ->
                    val request = pending.request
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
                            .clickable { navigator.open(Route.HorusDetail(request.type, request.media.id)) }.padding(12.dp),
                    ) {
                        NImage(request.media.poster, Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(request.media.title.ifBlank { request.type.label }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(request.type.label, request.media.year, if (request.is4k) "4K" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Nflix.Muted,
                            )
                            if (request.seasons.isNotEmpty()) Text("Saisons : " + request.seasons.joinToString(", ") { "S${it.number}" }, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                            pending.requestedBy?.let { Text("Demandé par $it", style = MaterialTheme.typography.labelMedium, color = Nflix.Text) }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fun decide(approve: Boolean) {
                                    busy = request.id
                                    scope.launch {
                                        runCatching { deps.admin.decide(request, approve) }
                                            .onSuccess {
                                                requests = requests?.filterNot { it.request.id == request.id }
                                                ItemMenu.changed(if (approve) "Demande approuvée" else "Demande refusée")
                                            }
                                            .onFailure { ItemMenu.message = "Action refusée par Horus : ${it.message}" }
                                        busy = null
                                    }
                                }
                                Button(onClick = { decide(true) }, enabled = busy == null, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Success, contentColor = Color.Black)) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Approuver")
                                }
                                OutlinedButton(onClick = { decide(false) }, enabled = busy == null) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = Nflix.Red); Spacer(Modifier.width(4.dp)); Text("Refuser", color = Nflix.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminActivityScreen() {
    val deps = LocalDeps.current
    var sessions by remember { mutableStateOf<List<AdminSession>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Live view: refreshed every 10 s while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { deps.admin.sessions() }.onSuccess { sessions = it; error = null }.onFailure { error = it.message }
            delay(10_000)
        }
    }
    AdminOnly("l'activité") {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item { AdminTitle("Activité en direct", sessions?.let { "${it.count { s -> s.item != null }} en lecture · actualisé toutes les 10 s" }) }
            val list = sessions
            when {
                list == null && error != null -> item { EmptyState(Icons.Rounded.Sensors, "Activité indisponible", error) }
                list == null -> items(3) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(80.dp)) }
                list.isEmpty() -> item { EmptyState(Icons.Rounded.Sensors, "Personne en ligne", "Aucun appareil actif depuis 16 minutes.") }
                else -> items(list, key = { "${it.user}-${it.device}-${it.client}" }) { session ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NImage(session.itemId?.let { deps.api.imageUrl(it, maxWidth = 200) }, Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)), placeholderIcon = Icons.Rounded.Sensors)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(session.user, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(listOf(session.device, session.client).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1)
                            if (session.item != null) {
                                Text(session.item, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                session.progress?.let { p ->
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Nflix.Red))
                                    }
                                }
                                if (session.transcoding) Text("Transcodage en cours", style = MaterialTheme.typography.labelSmall, color = Nflix.Warning)
                            } else {
                                Text("Inactif", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                            }
                        }
                        if (session.item != null) Icon(if (session.paused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (session.paused) "En pause" else "En lecture", tint = if (session.paused) Nflix.Muted else Nflix.Success)
                    }
                }
            }
        }
    }
}
