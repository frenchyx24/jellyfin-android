package org.jellyfin.mobile.nflix.tv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jellyfin.mobile.nflix.ui.Nflix

/**
 * "Connecter une TV": watches the Wi-Fi while open and lists the Nflix TVs waiting for a code.
 * Discovery starts when the screen appears and stops when it closes (multicast lock released).
 */
@Composable
fun TvDiscoverySheet() {
    val context = LocalContext.current
    val discovery = remember { TvDiscovery(context) }
    val state by discovery.state.collectAsState()
    // Android 17+ (targetSdk 37) blocks mDNS without the runtime local network permission
    var granted by remember { mutableStateOf(TvDiscovery.hasLocalNetworkPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(Unit) {
        if (!granted) runCatching { permission.launch(TvDiscovery.LOCAL_NETWORK_PERMISSION) }.onFailure { denied = true }
    }

    DisposableEffect(discovery, granted) {
        if (granted) discovery.start()
        onDispose { discovery.stop() }
    }

    Dialog(onDismissRequest = { TvLink.closeDiscovery() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Nflix.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Nflix.Red.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tv, null, tint = Nflix.Red, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connecter une TV", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nflix.Text)
                    Text("Sur le même Wi-Fi que la TV", fontSize = 13.sp, color = Nflix.Muted)
                }
                IconButton(onClick = { discovery.retry() }) {
                    Icon(Icons.Rounded.Refresh, "Rechercher à nouveau", tint = Nflix.Muted)
                }
                IconButton(onClick = { TvLink.closeDiscovery() }) {
                    Icon(Icons.Rounded.Close, "Fermer", tint = Nflix.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!granted && denied) {
                PermissionNeeded(
                    onAllow = {
                        denied = false
                        runCatching { permission.launch(TvDiscovery.LOCAL_NETWORK_PERMISSION) }.onFailure { denied = true }
                    },
                    onManual = ::openManual,
                )
            } else when (val current = state) {
                TvDiscoveryState.Idle, TvDiscoveryState.Searching -> Searching()
                TvDiscoveryState.Empty -> EmptyState(onRetry = { discovery.retry() }, onManual = ::openManual)
                TvDiscoveryState.NoWifi -> NoWifi(onRetry = { discovery.retry() })
                is TvDiscoveryState.Failed -> Failed(current.message, onRetry = { discovery.retry() })
                is TvDiscoveryState.Found -> TvList(current.tvs) { tv ->
                    TvLink.propose(TvRequest(tv.code, tv.name, TvRequest.Source.Network), explicit = true)
                    TvLink.closeDiscovery()
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = ::openManual, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Keyboard, null, tint = Nflix.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Saisir le code affiché sur la TV", color = Nflix.Muted)
            }
        }
    }
}

private fun openManual() {
    TvLink.closeDiscovery()
    TvLink.openManual()
}

@Composable
private fun Searching() {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(34.dp), color = Nflix.Red, strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text("Recherche…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text)
        Spacer(Modifier.height(6.dp))
        Text("Recherche des TV Nflix en attente de connexion sur votre réseau.", fontSize = 13.sp, color = Nflix.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Tv, null, tint = Nflix.Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Aucune TV trouvée", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Vérifiez que la TV est allumée, sur Nflix, et sur le même Wi-Fi que ce téléphone. Sinon, saisissez le code affiché sur la TV.",
            fontSize = 13.sp, color = Nflix.Muted, textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Réessayer", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onManual,
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.SurfaceHigh, contentColor = Nflix.Text),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Saisir le code") }
        }
    }
}

@Composable
private fun PermissionNeeded(onAllow: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = Nflix.Warning, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Autorisation requise", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pour trouver les TV sur votre Wi-Fi, autorisez Nflix à accéder aux appareils à proximité (réseau local). Sinon, saisissez le code affiché sur la TV.",
            fontSize = 13.sp, color = Nflix.Muted, textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Autoriser", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onManual,
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.SurfaceHigh, contentColor = Nflix.Text),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Saisir le code") }
        }
    }
}

@Composable
private fun NoWifi(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.WifiOff, null, tint = Nflix.Warning, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Wi-Fi désactivé", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Activez le Wi-Fi et connectez ce téléphone au même réseau que la TV.",
            fontSize = 13.sp, color = Nflix.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Réessayer", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontSize = 14.sp, color = Nflix.Warning, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Réessayer", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun TvList(tvs: List<DiscoveredTv>, onPick: (DiscoveredTv) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (tvs.size == 1) "1 TV trouvée" else "${tvs.size} TV trouvées",
            fontSize = 13.sp, color = Nflix.Muted,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tvs, key = { it.key }) { tv ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Nflix.SurfaceHigh)
                        .clickable { onPick(tv) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tv, null, tint = Nflix.Red, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tv.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        tv.host?.let { host ->
                            Text(if (tv.port > 0) "$host:${tv.port}" else host, fontSize = 12.sp, color = Nflix.Muted, maxLines = 1)
                        }
                    }
                    Text("Connecter", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Red)
                }
            }
        }
    }
}
