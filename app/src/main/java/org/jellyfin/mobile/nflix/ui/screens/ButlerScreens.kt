package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.jellyfin.mobile.nflix.ui.value

/**
 * Nflix Butler, administrators only. The server enforces `RequiresElevation` on every route it
 * calls; this screen drives the passes, the journal, and the repairs one item at a time.
 *
 * Deliberately plain: no list keys, no non-null assertions, no date library. A refresh bumps an
 * integer that the three loads use as a key, so nothing else can throw while the screen renders.
 */
@Composable
fun ButlerScreen() {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var pass by remember { mutableIntStateOf(0) }

    AdminOnly("l'assistant médias") {
        val status = rememberLoad("butler-status", pass) { deps.butler.status() }
        val entries = rememberLoad("butler-entries", pass) { deps.butler.entries() }
        val unmatched = rememberLoad("butler-unmatched", pass) { deps.butler.unmatched() }

        fun run(label: String, block: suspend () -> Unit, done: String) {
            if (busy != null) return
            busy = label
            scope.launch {
                val failure = runCatching { block() }.exceptionOrNull()
                ItemMenu.message = failure?.message?.takeIf { it.isNotBlank() } ?: done
                busy = null
                pass++
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
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Nflix.Text,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Analyser maintenant")
                        }
                    }
                    OutlinedButton(onClick = { pass++ }, enabled = busy == null) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Text("Rafraîchir", Modifier.padding(start = 6.dp))
                    }
                }
            }

            val state = status.value
            val stateError = (status as? Load.Failed)?.message
            when {
                stateError != null -> item { EmptyState(Icons.Rounded.ErrorOutline, "État indisponible", stateError) }
                state == null -> item { Spinner() }
                else -> item { state?.let { StatusCard(it) } }
            }

            item { SectionTitle("Dernières prises en charge", 12.dp) }

            val journal = entries.value
            val journalError = (entries as? Load.Failed)?.message
            when {
                journalError != null -> item { EmptyState(Icons.Rounded.ErrorOutline, "Journal illisible", journalError) }
                journal == null -> item { Spinner() }
                journal.isEmpty() -> item { Hint("Aucun passage enregistré pour le moment.") }
                else -> {
                    val rows = journal.take(100)
                    items(rows.size) { index -> EntryRow(rows[index]) }
                }
            }

            item { SectionTitle("Non identifiés", 16.dp) }

            val unknown = unmatched.value
            val unknownError = (unmatched as? Load.Failed)?.message
            when {
                unknownError != null -> item { EmptyState(Icons.Rounded.ErrorOutline, "Liste illisible", unknownError) }
                unknown == null -> item { Spinner() }
                unknown.isEmpty() -> item { Hint("Toutes les fiches sont identifiées.") }
                else -> {
                    val rows = unknown.take(50)
                    items(rows.size) { index ->
                        val row = rows[index]
                        UnmatchedRow(
                            row = row,
                            busy = busy,
                            onFix = { run("fix", { deps.butler.fix(row.itemId) }, "Réparation demandée pour ${row.name}") },
                            onRefresh = { run("refresh", { deps.butler.refresh(row.itemId) }, "Relecture demandée pour ${row.name}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Spinner() {
    Row(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Nflix.Red, strokeWidth = 2.dp)
    }
}

@Composable
private fun SectionTitle(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = top),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Nflix.Muted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
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
                "  " + when {
                    status.running -> "Passage en cours"
                    status.enabled -> "Actif"
                    else -> "Désactivé"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Stat("Fichiers vus", status.runs.toString())
            Stat("Classés", status.filed.toString())
            Stat("Réparés", status.repaired.toString())
            Stat("En attente", status.pending.toString())
        }
        Text(
            "Dernier passage : ${stamp(status.lastRunUtc)}",
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
        val error = status.lastError
        if (!error.isNullOrBlank()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = Nflix.Warning,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Stat(label: String, number: String) {
    Column {
        Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
        Text(hhmm(entry.atUtc), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
        Column(Modifier.weight(1f)) {
            Text(
                entry.label.ifBlank { entry.title.ifBlank { "Sans titre" } },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(entry.library.ifBlank { "—" })
                    append(" · ")
                    append(entry.action.ifBlank { "—" })
                    if (entry.message.isNotBlank()) {
                        append(" · ")
                        append(entry.message)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Nflix.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            entry.status.ifBlank { "—" },
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
    row: ButlerUnmatched,
    busy: String?,
    onFix: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Nflix.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            row.name.ifBlank { "Sans titre" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            buildString {
                append(row.type.ifBlank { "—" })
                val year = row.year
                if (year != null) {
                    append(" · ")
                    append(year)
                }
                if (row.path.isNotBlank()) {
                    append(" · ")
                    append(row.path)
                }
            },
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

/** "2026-11-07T12:34:56Z" → "2026-11-07 à 12:34"; anything shorter or empty → "jamais". */
private fun stamp(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 16) return "jamais"
    return iso.substring(0, 10) + " à " + iso.substring(11, 16)
}

/** "2026-11-07T12:34:56Z" → "12:34"; anything unexpected → "". */
private fun hhmm(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 16) return ""
    return iso.substring(11, 16)
}
