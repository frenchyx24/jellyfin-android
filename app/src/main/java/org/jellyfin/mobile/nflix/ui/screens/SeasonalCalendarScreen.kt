package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jellyfin.mobile.nflix.ui.AgendaEntry
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.NflixSeason
import org.jellyfin.mobile.nflix.ui.NflixSeasonTheme
import org.jellyfin.mobile.nflix.ui.SeasonAgenda
import org.jellyfin.mobile.nflix.ui.SeasonBadge
import org.jellyfin.mobile.nflix.ui.SeasonScene
import org.jellyfin.mobile.nflix.ui.themeFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Calendrier des 12 prochains mois": every theme the app will show for the coming year, with its
 * dates. Tapping one opens a live preview; "Utiliser ce thème" forces it until the next change.
 */
@Composable
fun SeasonalCalendarScreen() {
    val today = NflixSeason.today
    var preview by remember { mutableStateOf<AgendaEntry?>(null) }
    val entries = remember(today, NflixSeason.birthday) { SeasonAgenda.upcoming(today, NflixSeason.birthday) }
    // Group rows under a month label, keeping the start order.
    val months = remember(entries) { entries.groupBy { it.start.month } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SettingsHeader(
                    "Calendrier des thèmes",
                    "Les 12 prochains mois : fêtes, saisons${if (NflixSeason.birthday != null) " et votre anniversaire" else ""}. Touchez une ligne pour l'aperçu animé.",
                    Modifier.settleIn(0),
                )
            }
            months.forEach { (month, list) ->
                item(key = "month-$month") {
                    Text(
                        month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() },
                        color = NflixSeason.theme.accent, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 12.dp),
                    )
                }
                items(list, key = { "${it.key}-${it.start}" }) { entry ->
                    val theme = NflixSeasonTheme.themeFor(entry.key)
                    val current = !today.isBefore(entry.start) && !today.isAfter(entry.end)
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { preview = entry }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            org.jellyfin.mobile.nflix.ui.SeasonGlyphIcon(theme.decor.badgeGlyph, theme, Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    if (current) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "EN COURS", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(theme.accent).padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                Text(range(entry), fontSize = 12.sp, color = Nflix.Muted)
                            }
                            Text("Aperçu", fontSize = 12.sp, color = theme.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Text(
                    "Le thème de l'anniversaire n'apparaît que si vous avez saisi votre date dans les options.",
                    color = Nflix.Muted.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        BackButton()
    }

    preview?.let { entry ->
        val theme = NflixSeasonTheme.themeFor(entry.key)
        Dialog(onDismissRequest = { preview = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.surface).border(1.dp, theme.accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))) {
                Box(Modifier.fillMaxWidth().height(170.dp)) {
                    SeasonScene(theme, Modifier.fillMaxSize(), animated = true)
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        SeasonBadge(theme)
                        Text(range(entry), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(theme.label, fontWeight = FontWeight.SemiBold)
                        Text(theme.tagline, fontSize = 12.sp, color = Nflix.Muted)
                    }
                    TextButton(onClick = { preview = null }) { Text("Fermer", color = Nflix.Muted) }
                    TextButton(onClick = { NflixSeason.updateMode(theme.key); preview = null }) { Text("Utiliser ce thème", color = theme.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

private fun range(entry: AgendaEntry): String = when {
    entry.start == entry.end -> dayMonth.format(entry.start)
    entry.start.year == entry.end.year -> "${DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE).format(entry.start)} – ${dayMonth.format(entry.end)}"
    else -> "${dayMonth.format(entry.start)} – ${dayMonth.format(entry.end)}"
}

/** Today's window line, used by tests and the settings summary. */
internal fun agendaLine(today: LocalDate): String = SeasonAgenda.upcoming(today).firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }?.label ?: ""
