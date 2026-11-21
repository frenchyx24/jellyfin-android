package org.jellyfin.mobile.nflix.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.ui.LocalDeps

/** Settings: save/restore on the server, export/import as text. */
@Composable
fun SettingsBackupRow() {
    val deps = LocalDeps.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Réglages synchronisés", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.Row {
            androidx.compose.material3.TextButton(onClick = { scope.launch {
                runCatching { org.jellyfin.mobile.nflix.SettingsBackup.saveToServer(context, deps.api) }
                    .onSuccess { org.jellyfin.mobile.nflix.ui.Toasts.show("Réglages enregistrés sur le serveur.", org.jellyfin.mobile.nflix.ui.ToastKind.Success) }
                    .onFailure { org.jellyfin.mobile.nflix.ui.Toasts.show("Enregistrement impossible.", org.jellyfin.mobile.nflix.ui.ToastKind.Warning) }
            } }) { Text("Enregistrer") }
            androidx.compose.material3.TextButton(onClick = { scope.launch {
                runCatching { org.jellyfin.mobile.nflix.SettingsBackup.loadFromServer(context, deps.api) }
                    .onSuccess { org.jellyfin.mobile.nflix.ui.Toasts.show("$it réglages restaurés.", org.jellyfin.mobile.nflix.ui.ToastKind.Success) }
                    .onFailure { org.jellyfin.mobile.nflix.ui.Toasts.show("Aucun réglage sur le serveur.", org.jellyfin.mobile.nflix.ui.ToastKind.Warning) }
            } }) { Text("Restaurer") }
            androidx.compose.material3.TextButton(onClick = {
                val text = org.jellyfin.mobile.nflix.SettingsBackup.export(context).toString()
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, text), "Exporter les réglages").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Exporter") }
            androidx.compose.material3.TextButton(onClick = { importOpen = true }) { Text("Importer") }
        }
    }
    if (importOpen) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("Importer des réglages") },
            text = { androidx.compose.material3.OutlinedTextField(text, { text = it.take(100_000) }, label = { Text("Collez le texte exporté") }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                val n = runCatching { org.jellyfin.mobile.nflix.SettingsBackup.import(context, kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject) }.getOrNull()
                org.jellyfin.mobile.nflix.ui.Toasts.show(if (n != null) "$n réglages importés." else "Texte invalide.", if (n != null) org.jellyfin.mobile.nflix.ui.ToastKind.Success else org.jellyfin.mobile.nflix.ui.ToastKind.Warning)
                importOpen = false
            }) { Text("Importer") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { importOpen = false }) { Text("Annuler") } },
        )
    }
}
