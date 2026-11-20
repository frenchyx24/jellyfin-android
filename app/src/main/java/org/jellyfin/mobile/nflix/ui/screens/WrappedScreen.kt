package org.jellyfin.mobile.nflix.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.ui.LocalDeps

/** Yearly personal stats ("Nflix Wrapped"), computed on the phone from played titles. */
object WrappedStats {
    data class Entry(val title: String, val series: String?, val genres: List<String>, val minutes: Int, val day: java.time.LocalDate?)

    data class Result(val year: Int, val titles: Int, val hours: Int, val topGenres: List<String>, val topSeries: List<String>, val longestStreak: Int, val movies: Int, val episodes: Int)

    fun entry(item: Item): Entry = Entry(
        item.name, item.seriesName, item.genres, ((item.runTimeTicks ?: 0) / 600_000_000L).toInt(),
        item.userData?.lastPlayedDate?.let { runCatching { java.time.OffsetDateTime.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z").toLocalDate() }.getOrNull() },
    )

    fun compute(entries: List<Entry>, year: Int): Result {
        val inYear = entries.filter { it.day?.year == year }
        val days = inYear.mapNotNull { it.day }.toSortedSet()
        var best = 0
        var run = 0
        var prev: java.time.LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return Result(
            year = year,
            titles = inYear.size,
            hours = inYear.sumOf { it.minutes } / 60,
            topGenres = inYear.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key },
            topSeries = inYear.mapNotNull { it.series }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key },
            longestStreak = best,
            movies = inYear.count { it.series == null },
            episodes = inYear.count { it.series != null },
        )
    }

    fun shareText(r: Result) = buildString {
        append("Mon Nflix Wrapped ${r.year} : ${r.hours} h de visionnage, ${r.movies} films et ${r.episodes} épisodes")
        if (r.topSeries.isNotEmpty()) append(", série préférée : ${r.topSeries.first()}")
        if (r.topGenres.isNotEmpty()) append(", genre favori : ${r.topGenres.first()}")
        append(", ${r.longestStreak} jours d'affilée.")
    }
}

@Composable
fun WrappedScreen() {
    val deps = LocalDeps.current
    val context = LocalContext.current
    val year = java.time.LocalDate.now().year
    val result by produceState<WrappedStats.Result?>(null) {
        val items = runCatching {
            deps.jellyfin.items(includeTypes = "Movie,Episode", limit = 2000, extra = mapOf("isPlayed" to true, "sortBy" to "DatePlayed", "sortOrder" to "Descending")).items
        }.getOrDefault(emptyList())
        value = WrappedStats.compute(items.map(WrappedStats::entry), year)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A0508), Color.Black)))) {
        val r = result
        if (r == null) {
            CircularProgressIndicator(Modifier.padding(80.dp))
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Nflix Wrapped $year", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(start = 40.dp))
                WrappedCard("Temps passé", "${r.hours} h", "${r.titles} titres · ${r.movies} films · ${r.episodes} épisodes")
                WrappedCard("Série de jours", "${r.longestStreak} j", "Votre plus longue série de jours avec Nflix")
                if (r.topSeries.isNotEmpty()) WrappedCard("Séries préférées", r.topSeries.first(), r.topSeries.drop(1).joinToString(" · "))
                if (r.topGenres.isNotEmpty()) WrappedCard("Genres favoris", r.topGenres.first(), r.topGenres.drop(1).joinToString(" · "))
                Button(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, WrappedStats.shareText(r)), "Partager mon Wrapped").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth()) { Text("Partager") }
            }
        }
        BackButton()
    }
}

@Composable
private fun WrappedCard(label: String, big: String, sub: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .08f)).padding(20.dp)) {
        Text(label, color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
        Text(big, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
    }
}
