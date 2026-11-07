package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.ButlerEntry
import org.jellyfin.mobile.nflix.data.ButlerStatus
import org.jellyfin.mobile.nflix.data.ButlerUnmatched
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.ItemMenu
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.value

/**
 * Nflix Butler, administrators only. The server enforces `RequiresElevation` on every route it
 * calls; this screen just drives the passes, the journal, and the repairs one item at a time.
 */
@Composable
fun ButlerScreen() {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val reloader = rememberReloader()
    var busy by remember { mutableStateOf<String?>(null) }

    AdminOnly("l'assistant médias") {
        val status = rememberLoad("butler-status", reloader = reloader) { deps.butler.status() }
        val entries = rememberLoad("butler-entries", reloader = reloader) { deps.butler.entries() }
        val unmatched = rememberLoad("butler-unmatched", reloader = reloader) { deps.butler.unmatched() }

        fun run(label: String, block: suspend () -> Unit, done: String) {
            if (busy != null) return
            busy = label
            scope.launch {
                runCatching { block() }
                    .onSuccess { ItemMenu.message = done }
                    .onFailure { ItemMenu.message = it.message ?: "Action impossible" }
                busy = null
                reloader.reload()
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ContentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AdminTitle("Assistant médias", "Nouveautés, relecture ciblée, réparation des fiches")
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { run("run", { deps.butler.runNow() }, "Analyse lancée") },
                        enabled = busy == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
                    ) {
                        if (busy == "run") {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Nflix.Text, strokeWidth = 2.dp)
                        } else {
                            Text("Analyser maintenant")
                        }
                    }
                    OutlinedButton(onClick = { reloader.reload() }, enabled = busy == null) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Text("Rafraîchir", Modifier.padding(start = 6.dp))
                    }
                }
            }
            when {
                status is Load.Failed -> item {
                    EmptyState(Icons.Rounded.ErrorOutline, "État indisponible", status.message)
                }
                status.value == null -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Nflix.Red)
                    }
                }
                else -> item { StatusCard(status.value!!) }
            }
            item {
                Text(
                    "Dernières prises en charge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            val journal = entries.value
            when {
                entries is Load.Failed -> item { EmptyState(Icons.Rounded.ErrorOutline, "Journal illisible", entries.message) }
                journal == null || journal.isEmpty() -> item {
                    Text(
                        "Aucun passage enregistré pour le moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Nflix.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> items(journal, key = { it.id }) { entry -> EntryRow(entry) }
            }
            item {
                Text(
                    "Non identifiés",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
            val unknown = unmatched.value
            when {
                unmatched is Load.Failed -> item { EmptyState(Icons.Rounded.ErrorOutline, "Liste illisible", unmatched.message) }
                unknown == null || unknown.isEmpty() -> item {
                    Text(
                        "Toutes les fiches sont identifiées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Nflix.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> items(unknown, key = { it.itemId }) { item ->
                    UnmatchedRow(
                        item = item,
                        busy = busy,
                        onFix = { run("fix", { deps.butler.fix(item.itemId) }, "Réparation demandée pour ${item.name}") },
                        onRefresh = { run("refresh", { deps.butler.refresh(item.itemId) }, "Relecture demandée pour ${item.name}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: ButlerStatus) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Nflix.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoFixHigh, null, tint = if (status.running) Nflix.Success else Nflix.Muted)
            Text(
                if (status.running) "  Passage en cours" else if (status.enabled) "  Actif" else "  Désactivé",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("Fichiers vus", status.runs.toString())
            Stat("Classés", status.filed.toString())
            Stat("Réparés", status.repaired.toString())
            Stat("En attente", status.pending.toString())
        }
        Text(
            "Dernier passage : ${relative(status.lastRunUtc)}",
            style = MaterialTheme.typography.bodySmall,
            color = Nflix.Muted,
        )
        if (status.roots.isNotEmpty()) {
            Text(
                "Surveille ${status.roots.size} dossier(s) · toutes les ${status.pollSeconds} s",
                style = MaterialTheme.typography.bodySmall,
                color = Nflix.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        status.lastError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
    }
}

@Composable
private fun EntryRow(entry: ButlerEntry) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Nflix.Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(time(entry.atUtc), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.library.ifBlank { "—" }} · ${entry.action}${if (entry.message.isBlank()) "" else " · ${entry.message}"}",
                style = MaterialTheme.typography.labelSmall,
                color = Nflix.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            entry.status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when (entry.status) {
                "ok" -> Nflix.Success
                "pending", "needs-review" -> Nflix.Warning
                else -> Nflix.Red
            },
        )
    }
}

@Composable
private fun UnmatchedRow(
    item: ButlerUnmatched,
    busy: String?,
    onFix: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Nflix.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.name.ifBlank { "Sans titre" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(
            "${item.type}${item.year?.let { " · $it" } ?: ""} · ${item.path}",
            style = MaterialTheme.typography.labelSmall,
            color = Nflix.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onFix,
                enabled = busy == null,
                colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red),
            ) { Text("Réparer", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(onClick = onRefresh, enabled = busy == null) {
                Text("Relire", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** "2026-11-07T12:34:56Z" → "12:34"; anything unexpected is shown as-is. */
private fun time(iso: String?): String =
    if (iso != null && iso.length >= 16 && iso[10] == 'T') iso.substring(11, 16) else ""

private fun relative(iso: String?): String {
    if (iso.isNullOrBlank()) return "jamais"
    return runCatching {
        val instant = java.time.Instant.parse(iso)
        val minutes = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
        when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 1440 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / 1440} j"
        }
    }.getOrDefault(iso)
}
