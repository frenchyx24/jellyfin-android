package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.data.SupportRepository
import org.jellyfin.mobile.nflix.data.SupportTicket
import org.jellyfin.mobile.nflix.ui.*
import java.text.DateFormat
import java.util.Date

private fun statusLabel(status: String): Pair<String, Color> = when (status) {
    "open" -> "Reçu" to Color(0xFF6CB6FF)
    "in_progress", "progress", "acknowledged" -> "En cours" to Color(0xFFFFC05C)
    "resolved", "done", "fixed" -> "Résolu" to Color(0xFF46D39A)
    "closed", "rejected", "wontfix" -> "Clos" to Color(0xFF9E9E9E)
    else -> status.replaceFirstChar { it.uppercase() } to Color(0xFFB388FF)
}

/** The user's own problem reports and improvement ideas, as Mira Connect admins see them. */
@Composable
fun SupportScreen() {
    val deps = LocalDeps.current
    val context = LocalContext.current.applicationContext
    val repo = remember(deps.api, context) { SupportRepository(deps.api, context) }
    var reload by remember { mutableIntStateOf(0) }
    var tickets by remember { mutableStateOf<List<SupportTicket>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        failed = false
        val result = runCatching { repo.mine() }.getOrNull()
        failed = result == null
        tickets = result ?: emptyList()
    }
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "header") {
                Row(Modifier.statusBarsPadding().padding(start = 64.dp, end = 12.dp, top = 14.dp, bottom = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mes signalements", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Actualiser") }
                }
                Text(
                    "Problèmes et idées envoyés depuis Nflix. Les administrateurs les traitent dans Mira Connect.",
                    style = MaterialTheme.typography.bodySmall, color = Nflix.Muted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
            val list = tickets
            when {
                list == null -> items(3) { Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().size(width = 0.dp, height = 96.dp)) }
                failed -> item { EmptyState(Icons.Rounded.SupportAgent, "Connectez Mira Connect", "Vos signalements apparaissent ici une fois Mira Connect connecté.") }
                list.isEmpty() -> item { EmptyState(Icons.Rounded.SupportAgent, "Aucun signalement", "Utilisez « Signaler » sur une fiche pour nous prévenir d'un problème.") }
                else -> items(list, key = { it.id }) { ticket -> TicketCard(ticket) }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: SupportTicket) {
    val improvement = ticket.kind == "improvement"
    val accent = if (improvement) Color(0xFFFFD54F) else Nflix.Red
    val (label, statusColor) = statusLabel(ticket.status)
    Surface(color = Nflix.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().appear()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(if (improvement) Icons.Rounded.Lightbulb else Icons.Rounded.Report, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ticket.mediaName ?: ticket.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val where = listOfNotNull(ticket.season?.let { "S$it" }, ticket.episode?.let { "É$it" }).joinToString(" · ")
                    Text(
                        listOfNotNull(if (improvement) "Amélioration" else "Problème", where.ifBlank { null }, formatDate(ticket.createdAt)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = Nflix.Muted,
                    )
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = statusColor, modifier = Modifier.clip(RoundedCornerShape(50)).background(statusColor.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (ticket.message.isNotBlank()) Text(ticket.message, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text.copy(alpha = 0.9f))
            ticket.adminNote?.let { note ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nflix.SurfaceHigh).padding(10.dp)) {
                    Text("Réponse de l'équipe", style = MaterialTheme.typography.labelSmall, color = statusColor)
                    Text(note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatDate(epoch: Long): String? {
    if (epoch <= 0) return null
    val millis = if (epoch < 10_000_000_000L) epoch * 1000 else epoch
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}
