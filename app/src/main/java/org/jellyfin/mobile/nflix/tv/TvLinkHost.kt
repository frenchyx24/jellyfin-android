package org.jellyfin.mobile.nflix.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

/**
 * Hosts the "Connecter une TV" screen (live DNS-SD discovery) and the confirmation dialogs for a
 * TV found on the network, scanned from its QR code, or typed in.
 */
@Composable
fun TvLinkHost() {
    val request by TvLink.request.collectAsState()
    val manual by TvLink.manualOpen.collectAsState()
    val discovery by TvLink.discoveryOpen.collectAsState()
    request?.let { ConfirmTvDialog(it) }
    if (discovery && request == null) TvDiscoverySheet()
    if (manual && request == null) ManualCodeDialog()
}

@Composable
private fun ConfirmTvDialog(request: TvRequest) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var busy by remember(request.code) { mutableStateOf(false) }
    var error by remember(request.code) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!busy) TvLink.dismiss(request.code) }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Nflix.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Nflix.Red.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Tv, null, tint = Nflix.Red, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Connecter « ${request.name} » ?",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nflix.Text, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (request.source) {
                    TvRequest.Source.Network -> "Une TV Nflix sur votre Wi-Fi attend une connexion. Vérifiez que le code ci-dessous est bien celui affiché sur la TV."
                    TvRequest.Source.QrCode -> "Vous avez scanné le QR code d'une TV Nflix. Vérifiez que le code correspond à celui de la TV."
                    TvRequest.Source.Manual -> "La TV sera connectée à votre compte."
                },
                fontSize = 14.sp, color = Nflix.Muted, textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                request.code.chunked(3).joinToString(" "),
                fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Nflix.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "La TV utilisera votre compte ${deps.session.userName.orEmpty()}.",
                fontSize = 12.sp, color = Nflix.Muted, textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 13.sp, color = Nflix.Warning, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        TvLink.authorize(deps.api, request.code)
                            .onSuccess {
                                TvLink.dismiss(request.code)
                                TvLink.closeManual()
                                TvLink.closeDiscovery()
                                Toasts.show("« ${request.name} » est connectée à Nflix", ToastKind.Success)
                            }
                            .onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Connecter la TV", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            TextButton(onClick = { TvLink.dismiss(request.code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Ce n'est pas ma TV", color = Nflix.Muted)
            }
        }
    }
}

@Composable
private fun ManualCodeDialog() {
    var code by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { TvLink.closeManual() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Nflix.Surface).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Tv, null, tint = Nflix.Red)
                Text("Connecter une TV", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nflix.Text)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ouvrez Nflix sur votre Android TV et saisissez le code affiché. Sur le même Wi-Fi, la TV est aussi détectée automatiquement.",
                fontSize = 14.sp, color = Nflix.Muted, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { value -> code = value.filter(Char::isDigit).take(TvLink.CODE_LENGTH) },
                label = { Text("Code de la TV") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Nflix.Text),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, focusedLabelColor = Nflix.Red, cursorColor = Nflix.Red),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { TvLink.propose(TvRequest(code, "votre TV", TvRequest.Source.Manual)) },
                enabled = TvLink.isValidCode(code),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Continuer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            TextButton(onClick = { TvLink.closeManual() }, modifier = Modifier.fillMaxWidth()) { Text("Annuler", color = Nflix.Muted) }
        }
    }
}
