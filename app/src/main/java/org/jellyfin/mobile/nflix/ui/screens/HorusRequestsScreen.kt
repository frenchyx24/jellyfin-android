package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.HorusDownload
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a request stands, from the user's point of view. */
private enum class Stage(val label: String, val color: Color, val icon: ImageVector) {
    Pending("En attente de validation", Nflix.Warning, Icons.Rounded.HourglassTop),
    Approved("Approuvée · en recherche", Nflix.Info, Icons.Rounded.ThumbUp),
    Downloading("Téléchargement en cours", Nflix.Info, Icons.Rounded.CloudDownload),
    Partial("Partiellement disponible", Nflix.Info, Icons.Rounded.CheckCircle),
    Available("Disponible sur Nflix", Nflix.Success, Icons.Rounded.CheckCircle),
    Declined("Refusée", Nflix.Red, Icons.Rounded.Block),
    Failed("Échec", Nflix.Red, Icons.Rounded.Block),
}

private val HorusRequest.stage: Stage
    get() = when {
        status == 3 -> Stage.Declined
        status == 4 -> Stage.Failed
        downloading -> Stage.Downloading
        (if (is4k) media.status4k else media.status) == HorusStatus.Available || status == 5 -> Stage.Available
        (if (is4k) media.status4k else media.status) == HorusStatus.Partial -> Stage.Partial
        status == 1 -> Stage.Pending
        else -> Stage.Approved
    }

private enum class RequestFilter(val label: String, val accepts: (Stage) -> Boolean) {
    All("Toutes", { true }),
    Active("En cours", { it == Stage.Approved || it == Stage.Downloading || it == Stage.Partial }),
    Pending("En attente", { it == Stage.Pending }),
    Available("Disponibles", { it == Stage.Available }),
    Declined("Refusées", { it == Stage.Declined || it == Stage.Failed }),
}

@Composable
fun HorusRequestsScreen() {
    val deps = LocalDeps.current
    var requests by remember { mutableStateOf<List<HorusRequest>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(RequestFilter.All) }
    val scope = rememberCoroutineScope()

    // Live refresh: every 15 s while something downloads, every minute otherwise. Stops with the screen.
    LaunchedEffect(refreshTick) {
        while (true) {
            refreshing = true
            runCatching { deps.horus.myRequests(force = true) }
                .onSuccess { requests = it; error = null }
                .onFailure { if (requests == null) error = it.message ?: "Horus est injoignable." }
            refreshing = false
            delay(if (requests.orEmpty().any { it.downloading }) 15_000L else 60_000L)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val all = requests.orEmpty().distinctBy { it.id }
        val shown = all.filter { filter.accepts(it.stage) }
            .sortedWith(compareByDescending<HorusRequest> { it.downloading }.thenByDescending { it.createdAt })
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "title") {
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 64.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mes demandes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val active = all.count { it.downloading }
                        Text(
                            when {
                                requests == null -> "Chargement…"
                                active > 0 -> "$active en téléchargement · actualisé en direct"
                                else -> "${all.size} demande(s)"
                            },
                            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted,
                        )
                    }
                    IconButton(onClick = { refreshTick++ }, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = Nflix.Red, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, "Actualiser")
                    }
                }
            }
            item(key = "filters") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RequestFilter.entries, key = { it.name }) { f ->
                        val count = all.count { f.accepts(it.stage) }
                        NChip(if (f == RequestFilter.All) f.label else "${f.label} · $count", f == filter) { filter = f }
                    }
                }
            }
            when {
                requests == null && error != null -> item(key = "error") { EmptyState(Icons.Rounded.Inbox, "Horus est injoignable", error) }
                requests == null -> items(4, key = { "sk$it" }) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(190.dp), RoundedCornerShape(18.dp)) }
                shown.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.Inbox,
                        if (all.isEmpty()) "Aucune demande" else "Rien ici",
                        if (all.isEmpty()) "Demandez un film, une série, un album ou un livre depuis l'onglet Horus." else "Aucune demande dans ce filtre.",
                    )
                }
                else -> items(shown, key = { it.id }) { request ->
                    RequestCard(request, onCancel = {
                        scope.launch {
                            runCatching { deps.horus.cancel(request) }
                                .onSuccess { requests = requests?.filterNot { it.id == request.id } }
                                .onFailure { error = it.message }
                        }
                    })
                }
            }
        }
        BackButton()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestCard(request: HorusRequest, onCancel: () -> Unit) {
    val navigator = LocalNavigator.current
    val stage = request.stage
    var expanded by rememberSaveable(request.id) { mutableStateOf(request.downloading) }
    val media = request.media
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(Nflix.Surface)
            .border(1.dp, if (stage == Stage.Downloading) Nflix.Info.copy(alpha = 0.35f) else Nflix.Outline, RoundedCornerShape(18.dp))
            .clickable { navigator.open(Route.HorusDetail(request.type, media.id)) }
            .animateContentSize(),
    ) {
        // Backdrop band with the poster overlapping it.
        Box(Modifier.fillMaxWidth().height(118.dp)) {
            NImage(media.backdrop ?: media.poster, Modifier.fillMaxSize(), placeholderIcon = null)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Nflix.Surface))))
            Row(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                NImage(
                    media.poster,
                    Modifier.width(64.dp).aspectRatio(if (request.type == HorusType.Music) 1f else 2f / 3f).clip(RoundedCornerShape(10.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title.ifBlank { request.type.label }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(request.type.label, media.year, media.subtitle, if (request.is4k) (if (request.type == HorusType.Book) "Audio" else "4K") else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stage.icon, null, tint = stage.color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stage.label, style = MaterialTheme.typography.labelLarge, color = stage.color, modifier = Modifier.weight(1f))
                Text(requestDate(request.createdAt), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
            StageTrack(stage)

            if (request.downloading) {
                val downloaded = request.downloads.sumOf { it.size - it.sizeLeft.coerceIn(0, it.size) }
                val total = request.downloads.sumOf { it.size }
                val eta = request.downloads.mapNotNull { parseEta(it) }.maxOrNull()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(request.progress?.let { "${(it * 100).toInt()} %" } ?: "Préparation…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(
                                if (total > 0) "${formatBytes(downloaded)} sur ${formatBytes(total)}" else null,
                                eta?.let { "reste ${formatDuration(it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    BigProgress(request.progress ?: 0f)
                }
                if (request.downloads.size > 1 || request.downloads.any { it.episode != null }) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${request.downloads.size} fichier(s) en cours",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                        )
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Réduire" else "Détails", tint = Nflix.Muted)
                    }
                    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { request.downloads.forEach { DownloadRow(it) } }
                    }
                }
            }

            if (request.type == HorusType.Tv && request.seasons.isNotEmpty()) {
                val bySeason = media.seasons.associateBy { it.number }
                val active = request.downloads.mapNotNull { it.season }.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    request.seasons.forEach { requested ->
                        val season = bySeason[requested.number]
                        val (label, color) = when {
                            requested.number in active -> "téléchargement" to Nflix.Info
                            season?.status == HorusStatus.Available -> "disponible" to Nflix.Success
                            season?.status == HorusStatus.Partial -> "partielle" to Nflix.Info
                            requested.status == 3 -> "refusée" to Nflix.Red
                            requested.status == 1 -> "en attente" to Nflix.Warning
                            else -> "en recherche" to Nflix.Muted
                        }
                        Row(
                            Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "S${requested.number}" + (season?.episodes?.takeIf { it > 0 }?.let { " · $it ép." } ?: "") + " · $label",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                request.requestedBy?.let { Text("Demandé par $it", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                val jellyfinId = media.jellyfinId
                if (jellyfinId != null && stage in setOf(Stage.Available, Stage.Partial)) {
                    TextButton(onClick = { navigator.open(if (request.type == HorusType.Music) Route.Album(jellyfinId) else Route.Item(jellyfinId)) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ouvrir", color = Nflix.Text)
                    }
                }
                if (stage == Stage.Pending) TextButton(onClick = onCancel) { Text("Annuler", color = Nflix.Red) }
            }
        }
    }
}

/** Four-step tracker: requested → approved → downloading → available. */
@Composable
private fun StageTrack(stage: Stage) {
    val reached = when (stage) {
        Stage.Pending -> 1
        Stage.Approved -> 2
        Stage.Downloading -> 3
        Stage.Partial, Stage.Available -> 4
        Stage.Declined, Stage.Failed -> 1
    }
    val failed = stage == Stage.Declined || stage == Stage.Failed
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Demandée", "Approuvée", "Téléchargement", "Disponible").forEachIndexed { i, label ->
            val on = i < reached
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(if (failed && i == 0) Nflix.Red else if (on) stage.color else Color.White.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) Nflix.Text else Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BigProgress(progress: Float) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "dl")
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Nflix.RedDeep, Nflix.Red))),
        )
    }
}

@Composable
private fun DownloadRow(download: HorusDownload) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nflix.SurfaceHigh).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = if (download.season != null && download.episode != null) {
                "S${download.season} · É${download.episode}" + (download.episodeTitle?.let { " — $it" } ?: "")
            } else {
                download.title
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(download.progress?.let { "${(it * 100).toInt()} %" } ?: "—", style = MaterialTheme.typography.labelLarge, color = Nflix.Text)
        }
        Spacer(Modifier.height(6.dp))
        val animated by animateFloatAsState((download.progress ?: 0f).coerceIn(0f, 1f), tween(600), label = "dl-item")
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(Nflix.Info))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(
                statusLabel(download.status),
                if (download.size > 0) "${formatBytes(download.size - download.sizeLeft.coerceIn(0, download.size))} / ${formatBytes(download.size)}" else null,
                parseEta(download)?.let { "reste ${formatDuration(it)}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun statusLabel(status: String?): String? = when (status?.lowercase(Locale.ROOT)) {
    null -> null
    "downloading" -> "Téléchargement"
    "queued", "delay" -> "En file d'attente"
    "paused" -> "En pause"
    "completed" -> "Importation"
    "warning" -> "Avertissement"
    "failed" -> "Échec"
    else -> status
}

/** Prefers the real completion time; falls back to Sonarr/Radarr "d.hh:mm:ss" time left. */
internal fun parseEta(download: HorusDownload): Duration? {
    download.eta?.let { raw ->
        runCatching { Duration.between(Instant.now(), Instant.parse(raw)) }.getOrNull()?.takeIf { !it.isNegative }?.let { return it }
    }
    val left = download.timeLeft ?: return null
    val match = Regex("""^(?:(\d+)\.)?(\d{1,2}):(\d{2}):(\d{2})""").find(left) ?: return null
    val (d, h, m, s) = match.destructured
    return Duration.ofDays(d.toLongOrNull() ?: 0).plusHours(h.toLong()).plusMinutes(m.toLong()).plusSeconds(s.toLong())
}

internal fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "moins d'une minute"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
        else -> "${minutes / (24 * 60)} j ${(minutes / 60) % 24} h"
    }
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.toDouble().coerceAtLeast(0.0)
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return if (unit <= 1) "${value.toInt()} ${units[unit]}" else String.format(Locale.FRANCE, "%.1f %s", value, units[unit])
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

private fun requestDate(raw: String): String = runCatching {
    val date = OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
    val days = Duration.between(date.toInstant(), Instant.now()).toDays()
    when {
        days < 1 -> "aujourd'hui"
        days < 2 -> "hier"
        days < 7 -> "il y a $days jours"
        else -> dateFormat.format(date)
    }
}.getOrDefault("")
