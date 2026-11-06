package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.cast.CastCommand
import org.jellyfin.mobile.nflix.cast.CastManager
import org.jellyfin.mobile.nflix.cast.CastTarget


/** Opens the screen picker, or the remote when a screen is connected. */
@Composable
fun CastButton(manager: CastManager, tint: Color = Color.White, onOpenRemote: () -> Unit = {}) {
    val active by manager.active.collectAsState()
    var picking by remember { mutableStateOf(false) }
    IconButton(onClick = { if (active != null) onOpenRemote() else picking = true }) {
        Icon(if (active != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast, if (active != null) "Télécommande de ${active?.target?.name}" else "Diffuser sur un écran", tint = if (active != null) Nflix.Red else tint)
    }
    if (picking) CastPicker(manager, onDismiss = { picking = false }) { picking = false }
}

/** Screens around: Chromecasts and Jellyfin apps. Discovery only runs while the sheet is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastPicker(manager: CastManager, onDismiss: () -> Unit, onConnected: (CastTarget) -> Unit) {
    val scope = rememberCoroutineScope()
    val targets by manager.targets.collectAsState()
    var connecting by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        manager.startDiscovery()
        onDispose { manager.stopDiscovery() }
    }
    LaunchedEffect(Unit) { delay(8_000); searching = false }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Diffuser sur…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Chromecast et applis Jellyfin de vos écrans (TV, navigateur).", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
            Spacer(Modifier.height(12.dp))
            targets.forEach { target ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Nflix.Surface)
                        .clickable(enabled = connecting == null) {
                            connecting = target.id
                            error = null
                            scope.launch {
                                val ok = manager.connect(target)
                                connecting = null
                                if (ok) onConnected(target) else error = "Connexion à ${target.name} impossible."
                            }
                        }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (target.kind == CastTarget.Kind.Chromecast) Icons.Rounded.Cast else Icons.Rounded.Tv, null, tint = Nflix.Red)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(target.name, style = MaterialTheme.typography.titleSmall)
                        Text(if (target.kind == CastTarget.Kind.Chromecast) "Chromecast" else target.detail.ifBlank { "Appli Jellyfin" }, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    }
                    if (connecting == target.id) CircularProgressIndicator(Modifier.size(20.dp), color = Nflix.Red, strokeWidth = 2.dp)
                }
            }
            if (targets.isEmpty()) {
                Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), color = Nflix.Red, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (searching) "Recherche des écrans…" else "Aucun écran trouvé. Vérifiez que la TV est sur le même Wi-Fi et que l'appli Jellyfin y est ouverte.", color = Nflix.Muted)
                }
            }
            error?.let { Text(it, color = Nflix.Red, modifier = Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Compact remote above the tab bar while a screen is connected. */
@Composable
fun CastRemoteBar(manager: CastManager, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val status by manager.active.collectAsState()
    AnimatedVisibility(status != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = modifier) {
        val s = status ?: return@AnimatedVisibility
        Surface(color = Nflix.SurfaceHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().clickable(onClick = onExpand)) {
            Column {
                val progress = s.durationTicks?.takeIf { it > 0 }?.let { (s.positionTicks.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Nflix.Red))
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CastConnected, null, tint = Nflix.Red)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.title ?: if (s.idle) "Prêt à diffuser" else "Diffusion", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Sur ${s.target.name}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
                    }
                    if (!s.idle) IconButton(onClick = { scope.launch { manager.command(if (s.paused) CastCommand.Resume else CastCommand.Pause) } }) {
                        Icon(if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.paused) "Reprendre" else "Pause")
                    }
                    IconButton(onClick = { scope.launch { manager.disconnect() } }) { Icon(Icons.Rounded.Close, "Arrêter la diffusion") }
                }
            }
        }
    }
}

private fun ticksLabel(ticks: Long): String = formatTicks(ticks)

/** Full remote: seek, volume, audio and subtitles, hand-over back to the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRemoteSheet(manager: CastManager, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val status by manager.active.collectAsState()
    val s = status ?: run { LaunchedEffect(Unit) { onDismiss() }; return }
    val item = rememberLoad("cast-item", s.itemId) { s.itemId?.let { deps.jellyfin.item(it) } }.value
    var seeking by remember { mutableStateOf<Float?>(null) }
    var volume by remember(s.target.id) { mutableFloatStateOf(s.volume) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.title ?: "Diffusion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("Sur ${s.target.name}", color = Nflix.Muted, style = MaterialTheme.typography.bodySmall)
            val duration = s.durationTicks ?: item?.runTimeTicks ?: 0L
            if (duration > 0) {
                val fraction = seeking ?: (s.positionTicks.toFloat() / duration).coerceIn(0f, 1f)
                Slider(
                    value = fraction, onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { f -> scope.launch { manager.command(CastCommand.Seek((f * duration).toLong())) } }; seeking = null },
                    colors = SliderDefaults.colors(thumbColor = Nflix.Red, activeTrackColor = Nflix.Red),
                )
                Row { Text(ticksLabel((fraction * duration).toLong()), color = Nflix.Muted); Spacer(Modifier.weight(1f)); Text(ticksLabel(duration), color = Nflix.Muted) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { manager.command(CastCommand.Seek((s.positionTicks - 100_000_000L).coerceAtLeast(0))) } }) { Text("−10 s") }
                IconButton(onClick = { scope.launch { manager.command(if (s.paused) CastCommand.Resume else CastCommand.Pause) } }, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White)) {
                    Icon(if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.paused) "Reprendre" else "Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                }
                TextButton(onClick = { scope.launch { manager.command(CastCommand.Seek(s.positionTicks + 300_000_000L)) } }) { Text("+30 s") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Volume", tint = Nflix.Muted)
                Slider(volume, { volume = it }, onValueChangeFinished = { scope.launch { manager.command(CastCommand.Volume(volume)) } }, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            }
            val streams = item?.mediaSources?.firstOrNull()?.streams ?: item?.mediaStreams.orEmpty()
            val audio = streams.filter { it.type == "Audio" }
            val subs = streams.filter { it.type == "Subtitle" }
            if (audio.size > 1) StreamRow("Audio", audio.map { it.index to (it.displayTitle ?: it.language ?: "Piste ${it.index}") }, s.audioIndex) { scope.launch { manager.command(CastCommand.Audio(it)) } }
            if (subs.isNotEmpty()) StreamRow("Sous-titres", listOf(-1 to "Aucun") + subs.map { it.index to (it.displayTitle ?: it.language ?: "Piste ${it.index}") }, s.subtitleIndex ?: -1) { scope.launch { manager.command(CastCommand.Subtitle(it)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        val back = manager.takeBack() ?: return@launch
                        onDismiss()
                        back.itemId?.let { deps.playVideos(scope, listOf(it), 0, back.positionTicks) }
                    }
                }, enabled = s.itemId != null) {
                    Icon(Icons.Rounded.PhoneAndroid, null, tint = Nflix.Red); Spacer(Modifier.width(6.dp)); Text("Reprendre sur ce téléphone", color = Nflix.Red)
                }
                TextButton(onClick = { scope.launch { manager.command(CastCommand.Stop); manager.disconnect(); onDismiss() } }) {
                    Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text("Arrêter")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StreamRow(label: String, options: List<Pair<Int, String>>, selected: Int?, onPick: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.first }) { (index, name) ->
                Surface(color = if (index == selected) Nflix.Red else Nflix.Surface, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable { onPick(index) }) {
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
    }
}
