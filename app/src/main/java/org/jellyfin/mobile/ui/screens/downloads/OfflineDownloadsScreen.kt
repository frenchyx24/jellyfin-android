package org.jellyfin.mobile.ui.screens.downloads

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.NflixTheme
import org.jellyfin.mobile.app.StorageManager
import org.koin.compose.koinInject

private enum class OfflineCategory(val title: String, val icon: ImageVector) {
    ALL("Tout", Icons.Rounded.Download),
    FILMS("Films", Icons.Rounded.Movie),
    SERIES("Séries", Icons.Rounded.Tv),
    ANIME("Animés", Icons.Rounded.Movie),
    MUSIC("Musiques", Icons.Rounded.MusicNote),
    BOOKS("Livres", Icons.Rounded.Book),
}

private fun DownloadFiles.category(): OfflineCategory {
    val item = download.item
    val kind = item.type?.name.orEmpty()
    if (item.genres.orEmpty().any { it.contains("anime", true) || it.contains("animation", true) }) return OfflineCategory.ANIME
    return when {
        kind == "MOVIE" -> OfflineCategory.FILMS
        kind == "SERIES" || kind == "SEASON" || kind == "EPISODE" -> OfflineCategory.SERIES
        kind in setOf("AUDIO", "AUDIO_BOOK", "MUSIC_ALBUM", "MUSIC_ARTIST", "PLAYLIST") -> OfflineCategory.MUSIC
        kind == "BOOK" -> OfflineCategory.BOOKS
        else -> OfflineCategory.FILMS
    }
}

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
) {
    val downloads by viewModel.downloads.collectAsState()
    val selection = remember { mutableStateSetOf<Long>() }
    var category by remember { mutableStateOf(OfflineCategory.ALL) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val filtered = remember(downloads, category) {
        downloads.filter { category == OfflineCategory.ALL || it.category() == category }
    }
    val completed = downloads.count { it.download.status == DownloadStatus.DOWNLOADED }
    val active = downloads.count { it.download.status == DownloadStatus.QUEUED || it.download.status == DownloadStatus.DOWNLOADING }
    val storedBytes = downloads.sumOf { row -> row.files.filter { it.status == DownloadStatus.DOWNLOADED }.sumOf { it.size } }
    val selectedDownloads = downloads.map { it.download }.filter { it.id in selection }

    if (showDeleteConfirm) {
        DownloadRemoveDialog(
            downloads = selectedDownloads,
            onConfirm = { keepFiles ->
                selectedDownloads.forEach { viewModel.removeDownload(it, deleteFiles = !keepFiles) }
                selection.clear()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    NflixTheme {
        Surface(color = Nflix.Night, contentColor = Nflix.Text, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Nflix.Night)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = if (selection.isNotEmpty()) selection::clear else onBackPressed) {
                        Icon(if (selection.isNotEmpty()) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Nflix.Text)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (selection.isEmpty()) "Hors ligne" else "${selection.size} sélectionné(s)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Votre bibliothèque, même sans réseau", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    }
                    if (selection.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.DeleteOutline, "Supprimer la sélection", tint = Nflix.Text)
                        }
                    }
                }

                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Nflix.Surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Nflix.Red.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Lock, null, tint = Nflix.Red, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Stockage privé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Invisible dans la galerie et les fichiers publics", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Formatter.formatShortFileSize(context, storedBytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("$completed prêts", style = MaterialTheme.typography.labelSmall, color = Nflix.Success)
                        }
                    }
                    if (active > 0) {
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Downloading, null, tint = Nflix.Info, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("$active téléchargement(s) en cours", color = Nflix.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OfflineCategory.entries.forEach { option ->
                        val count = if (option == OfflineCategory.ALL) downloads.size else downloads.count { it.category() == option }
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text("${option.title}  $count") },
                            leadingIcon = { Icon(option.icon, null, modifier = Modifier.size(17.dp)) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Nflix.RedDeep,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                            ),
                        )
                    }
                }

                if (downloads.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Download, null, tint = Nflix.Red, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Rien à emporter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Ajoutez films, épisodes, musiques ou livres depuis leur fiche. Ils apparaîtront ici.", color = Nflix.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aucun contenu dans ${category.title.lowercase()}.", color = Nflix.Muted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.download.id }) { row ->
                            OfflineDownloadCard(
                                row = row,
                                selected = row.download.id in selection,
                                selectionMode = selection.isNotEmpty(),
                                onClick = { isVerified ->
                                    if (selection.isNotEmpty()) {
                                        if (!selection.add(row.download.id)) selection.remove(row.download.id)
                                    } else if (row.download.status == DownloadStatus.DOWNLOADED && isVerified) {
                                        viewModel.openDownload(row.download)
                                    } else if (row.download.status != DownloadStatus.DOWNLOADING && row.download.status != DownloadStatus.QUEUED) {
                                        viewModel.download(row.download)
                                    }
                                },
                                onLongClick = {
                                    if (!selection.add(row.download.id)) selection.remove(row.download.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineDownloadCard(
    row: DownloadFiles,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val storageManager: StorageManager = koinInject()
    val verified by produceState(false, row) { value = withContext(Dispatchers.IO) { storageManager.verify(row) } }
    val poster = remember(row.files) { row.files.firstOrNull { it.type == DownloadFileType.IMAGE_PRIMARY }?.uri }
    val download = row.download
    val isBusy = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
    val category = row.category()
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(verified) }, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Nflix.SurfaceHigh else Nflix.Surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).height(94.dp).clip(RoundedCornerShape(12.dp)).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isBusy) CircularProgressIndicator(color = Nflix.Red, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                else if (verified) Icon(Icons.Rounded.CheckCircle, null, tint = Nflix.Success, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(download.getDisplayName(context).orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(category.title, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                when {
                    isBusy -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Nflix.Red, trackColor = Nflix.SurfaceHigh)
                        Text(if (download.status == DownloadStatus.QUEUED) "Dans la file" else "Téléchargement…", color = Nflix.Info, style = MaterialTheme.typography.labelSmall)
                    }
                    verified -> Text(Formatter.formatShortFileSize(context, row.files.sumOf { it.size }), color = Nflix.Muted, style = MaterialTheme.typography.labelSmall)
                    else -> Text("Téléchargement incomplet · toucher pour reprendre", color = Nflix.Warning, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
            if (!selectionMode && verified) {
                Icon(Icons.Rounded.PlayArrow, "Lire hors ligne", tint = Nflix.Text, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}
