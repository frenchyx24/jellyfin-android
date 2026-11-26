package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.BadgeEta
import org.jellyfin.mobile.nflix.data.BadgeRecommendation
import org.jellyfin.mobile.nflix.data.CategoryProgress
import org.jellyfin.mobile.nflix.data.CompareHistoryEntry
import org.jellyfin.mobile.nflix.data.LibraryCompletion
import org.jellyfin.mobile.nflix.data.RankedName
import org.jellyfin.mobile.nflix.data.SmartGoal
import org.jellyfin.mobile.nflix.data.StatRecap
import org.jellyfin.mobile.nflix.data.StatRecords
import org.jellyfin.mobile.nflix.data.StreakCalendar
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Reloader
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

private enum class StatsTab(val label: String) {
    Activity("Activité"),
    Records("Records"),
    Recap("Récap"),
    Goals("Objectifs"),
    Categories("Catégories"),
    Watch("À regarder"),
    Compare("Comparaisons"),
}

/** "Mes stats": activity heatmap, records, recap, goals, categories, suggestions and comparisons. */
@Composable
fun AchievementStatsScreen() {
    val deps = LocalDeps.current
    val hub = remember(deps.api) { AchievementHub(deps.api) }
    val reduced = rememberAchievementReducedMotion()
    var current by rememberSaveable { mutableStateOf(StatsTab.Activity.name) }
    val tabs = StatsTab.entries
    val tab = tabs.firstOrNull { it.name == current } ?: StatsTab.Activity
    val index = tabs.indexOf(tab)
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Nflix.Red.copy(alpha = 0.26f), Nflix.RedDeep.copy(alpha = 0.12f), Nflix.Night))))
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(start = 60.dp, top = 10.dp, end = 16.dp)) {
                Text("Mes stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Votre activité, vos records et vos prochains succès", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ScrollableTabRow(
                selectedTabIndex = index, edgePadding = 12.dp, containerColor = Color.Transparent, contentColor = Nflix.Text, divider = {},
                indicator = { positions -> if (index < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[index]), color = Nflix.Red) },
            ) {
                tabs.forEach { t -> Tab(selected = t == tab, onClick = { current = t.name }, text = { Text(t.label) }) }
            }
            AnimatedContent(
                tab,
                transitionSpec = { if (reduced) EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "stats-tab",
                modifier = Modifier.weight(1f),
            ) { shown ->
                when (shown) {
                    StatsTab.Activity -> ActivityStatsTab(hub)
                    StatsTab.Records -> RecordsTab(hub)
                    StatsTab.Recap -> RecapTab(hub)
                    StatsTab.Goals -> GoalsTab(hub)
                    StatsTab.Categories -> CategoriesTab(hub)
                    StatsTab.Watch -> WatchTab(hub)
                    StatsTab.Compare -> CompareHistoryTab(hub)
                }
            }
        }
        BackButton()
    }
}

/** Entry card on the Succès tab. */
@Composable
internal fun MyStatsCard(onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Nflix.Red.copy(alpha = 0.55f), Nflix.RedDeep.copy(alpha = 0.35f), Nflix.Surface)))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Insights, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Mes stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text("Activité, records, récap et objectifs", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White)
    }
}

/** Entry pill in the hub header. */
@Composable
internal fun MyStatsPill(onClick: () -> Unit) {
    Surface(color = Nflix.Red.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Insights, null, tint = Nflix.Red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Mes stats", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- shared

private val frInt: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.FRANCE)

private fun fr(value: Long): String = frInt.format(value)

private fun fr(value: Int): String = frInt.format(value.toLong())

private fun minutesFr(minutes: Long): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"

/** Fallback for one part of a combined tab: a failure leaves that part empty. */
private suspend fun <T> orEmpty(fallback: T, block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    fallback
}

private val CardModifier: Modifier get() = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()

@Composable
private fun Modifier.statCard(): Modifier = this.clip(RoundedCornerShape(16.dp)).background(Nflix.Surface).padding(14.dp)

@Composable
private fun <T> StatsList(
    load: Load<T>,
    reloader: Reloader,
    empty: String,
    isEmpty: (T) -> Boolean,
    header: LazyListScope.() -> Unit = {},
    content: LazyListScope.(T) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = ContentBottomPadding)) {
        header()
        when (load) {
            is Load.Loading -> item("loading") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(84.dp)) } }
            }
            is Load.Failed -> item("failed") { EmptyStats("Statistiques indisponibles pour le moment.") { reloader.reload() } }
            is Load.Ready -> if (isEmpty(load.value)) item("empty") { EmptyStats(empty, null) } else content(load.value)
        }
    }
}

@Composable
private fun EmptyStats(text: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Insights, null, tint = Nflix.Muted.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Nflix.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Réessayer", color = Nflix.Red) }
    }
}

@Composable
private fun StatsSection(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
    }
}

@Composable
private fun BigNumber(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, caption: String? = null, accent: Color = Nflix.Red) {
    Column(modifier.statCard()) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, maxLines = 2)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted.copy(alpha = 0.7f), maxLines = 1)
    }
}

// ---------------------------------------------------------------- 1. Activité

private class HeatCell(val date: LocalDate?, val level: Int, val count: Int)

private class HeatGrid(val columns: List<List<HeatCell>>, val months: List<String?>, val current: Int, val best: Int, val activeDays: Int, val today: LocalDate)

private val MonthsFr = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
private val DaysFr = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")

private fun buildHeatmap(cal: StreakCalendar): HeatGrid {
    val counts = HashMap<LocalDate, Int>()
    val parsed = cal.days.mapNotNull { d -> runCatching { LocalDate.parse(d.date) }.getOrNull()?.also { if (d.watched || d.count > 0) counts[it] = d.count.coerceAtLeast(1) } }
    val today = LocalDate.now()
    val end = maxOf(parsed.maxOrNull() ?: today, today)
    val weeks = cal.weeks.coerceIn(1, 60)
    val lastMonday = end.minusDays((end.dayOfWeek.value - 1).toLong())
    val start = lastMonday.minusWeeks((weeks - 1).toLong())
    // Run length of consecutive watched days, used for the shade when the server only sends a flag.
    val run = HashMap<LocalDate, Int>()
    var best = 0
    counts.keys.sorted().forEach { day ->
        if (counts.containsKey(day.minusDays(1))) return@forEach
        var length = 0
        var cursor = day
        while (counts.containsKey(cursor)) { length++; cursor = cursor.plusDays(1) }
        best = maxOf(best, length)
        var fill = day
        repeat(length) { run[fill] = length; fill = fill.plusDays(1) }
    }
    var streak = 0
    var cursor = if (counts.containsKey(today)) today else today.minusDays(1)
    while (counts.containsKey(cursor)) { streak++; cursor = cursor.minusDays(1) }
    val maxCount = counts.values.maxOrNull() ?: 0
    val graded = counts.values.any { it > 1 }
    val columns = (0 until weeks).map { w ->
        (0 until 7).map { r ->
            val day = start.plusDays(w * 7L + r)
            if (day.isAfter(end)) HeatCell(null, 0, 0) else {
                val c = counts[day] ?: 0
                val level = when {
                    c == 0 -> 0
                    graded -> ceil(c * 4.0 / maxCount).toInt().coerceIn(1, 4)
                    else -> when (run[day] ?: 1) { 1 -> 1; in 2..3 -> 2; in 4..6 -> 3; else -> 4 }
                }
                HeatCell(day, level, c)
            }
        }
    }
    val months = (0 until weeks).map { w ->
        val bottom = start.plusWeeks(w.toLong()).plusDays(6)
        if (w > 0 && bottom.month != start.plusWeeks(w - 1L).plusDays(6).month) MonthsFr[bottom.monthValue - 1] else null
    }
    val active = counts.keys.count { !it.isBefore(start) && !it.isAfter(end) }
    return HeatGrid(columns, months, streak, best, active, today)
}

private fun heatColor(level: Int): Color = when (level) {
    0 -> Color.White.copy(alpha = 0.06f)
    1 -> Nflix.Red.copy(alpha = 0.3f)
    2 -> Nflix.Red.copy(alpha = 0.5f)
    3 -> Nflix.Red.copy(alpha = 0.75f)
    else -> Nflix.Red
}

@Composable
private fun ActivityStatsTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-calendar", reloader = reloader) { hub.streakCalendar() }
    StatsList(load, reloader, "Aucune activité enregistrée pour l'instant.", { it.days.isEmpty() }) { cal ->
        item("heat") { ActivityContent(cal) }
    }
}

@Composable
private fun ActivityContent(cal: StreakCalendar) {
    val grid = remember(cal) { buildHeatmap(cal) }
    var selected by remember { mutableStateOf<HeatCell?>(null) }
    Column {
        Row(CardModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigNumber("Série actuelle", "${fr(grid.current)} j", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), accent = Nflix.Warning)
            BigNumber("Meilleure série", "${fr(grid.best)} j", Icons.Rounded.EmojiEvents, Modifier.weight(1f))
            BigNumber("Jours actifs", fr(grid.activeDays), Icons.Rounded.CalendarMonth, Modifier.weight(1f), caption = "sur ${grid.columns.size} sem.")
        }
        StatsSection("Calendrier de visionnage", "${grid.columns.size} semaines")
        Column(CardModifier.statCard()) {
            val cell = 12.dp
            val gap = 3.dp
            Row(Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE))) {
                Column(Modifier.padding(end = 6.dp)) {
                    Spacer(Modifier.height(16.dp))
                    DaysFr.forEachIndexed { i, d ->
                        Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                            if (i % 2 == 0) Text(d, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                        }
                    }
                }
                grid.columns.forEachIndexed { w, column ->
                    Column(Modifier.width(cell + gap)) {
                        Box(Modifier.height(16.dp)) {
                            grid.months[w]?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                            }
                        }
                        column.forEach { c ->
                            Box(Modifier.size(cell + gap), contentAlignment = Alignment.TopStart) {
                                if (c.date != null) {
                                    val isToday = c.date == grid.today
                                    val isSelected = selected?.date == c.date
                                    Box(
                                        Modifier.size(cell).clip(RoundedCornerShape(3.dp)).background(heatColor(c.level))
                                            .then(if (isToday || isSelected) Modifier.border(1.dp, Color.White.copy(alpha = if (isSelected) 0.9f else 0.5f), RoundedCornerShape(3.dp)) else Modifier)
                                            .clickable { selected = c },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sel = selected
                Text(
                    if (sel?.date != null) {
                        val d = sel.date
                        "${DaysFr[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MonthsFr[d.monthValue - 1]} ${d.year} · " + if (sel.count > 0) "regardé" else "rien regardé"
                    } else "Touchez un jour pour le détail",
                    style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("Moins", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                (0..4).forEach { level -> Box(Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(level))) }
                Text("Plus", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        }
        Text(
            "Plus la case est rouge, plus la série de jours consécutifs est longue.",
            style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- 2. Records

private class RecordTile(val label: String, val value: String, val icon: ImageVector, val caption: String? = null)

private fun recordTiles(r: StatRecords): List<RecordTile> = buildList {
    fun add(key: String, label: String, icon: ImageVector, caption: String? = null, format: (Long) -> String = ::fr) {
        r[key]?.let { add(RecordTile(label, format(it), icon, caption)) }
    }
    add("TotalItemsWatched", "Titres vus", Icons.Rounded.VideoLibrary)
    add("MoviesWatched", "Films", Icons.Rounded.Movie)
    add("SeriesCompleted", "Séries terminées", Icons.Rounded.Tv)
    add("DaysWatched", "Jours de visionnage", Icons.Rounded.CalendarMonth)
    add("BestWatchStreak", "Plus longue série", Icons.Rounded.LocalFireDepartment, "jours d'affilée") { "${fr(it)} j" }
    add("MaxEpisodesInSingleDay", "Max épisodes en un jour", Icons.Rounded.Bolt)
    add("MaxMoviesInSingleDay", "Max films en un jour", Icons.Rounded.Theaters)
    add("LongestItemMinutes", "Plus long titre", Icons.Rounded.Timer) { minutesFr(it) }
    add("LateNightSessions", "Nuits tardives", Icons.Rounded.NightsStay)
    add("EarlyMorningSessions", "Matinales", Icons.Rounded.WbSunny)
    add("WeekendSessions", "Week-ends", Icons.Rounded.Weekend)
    add("UniqueGenresWatched", "Genres uniques", Icons.Rounded.Category)
    add("UniqueCountriesWatched", "Pays uniques", Icons.Rounded.Public)
    add("UniqueLanguagesWatched", "Langues uniques", Icons.Rounded.Translate)
    add("UniqueDecadesWatched", "Décennies uniques", Icons.Rounded.History)
    add("UniqueLibrariesVisited", "Bibliothèques visitées", Icons.Rounded.Apps)
    add("RewatchCount", "Revisionnages", Icons.Rounded.Replay)
    add("BestComboCount", "Meilleur combo", Icons.Rounded.Whatshot)
    add("ShortItemsWatched", "Formats courts", Icons.Rounded.Speed)
    add("LongSeriesCompleted", "Longues séries terminées", Icons.Rounded.EmojiEvents)
    add("VeryLongSeriesCompleted", "Très longues séries", Icons.Rounded.MilitaryTech)
    add("DaysLoggedIn", "Jours connectés", Icons.Rounded.Person)
    add("BestLoginStreak", "Série de connexions", Icons.Rounded.Star, "record") { "${fr(it)} j" }
    r["PrestigeLevel"]?.takeIf { it > 0 }?.let { add(RecordTile("Prestige", fr(it), Icons.Rounded.AutoAwesome)) }
    r["LifetimeScore"]?.takeIf { it > 0 }?.let { add(RecordTile("Score à vie", "${fr(it)} pts", Icons.Rounded.Leaderboard)) }
}

@Composable
private fun RecordsTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-records", reloader = reloader) { hub.records() }
    StatsList(load, reloader, "Aucun record pour l'instant.", { it.values.isEmpty() }) { records ->
        val minutes = records["TotalMinutesWatched"]
        val hours = records["TotalHoursWatched"] ?: minutes?.div(60)
        if (hours != null) item("hours") {
            Column(
                CardModifier.clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Nflix.Red.copy(alpha = 0.5f), Nflix.RedDeep.copy(alpha = 0.3f), Nflix.Surface))).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Heures totales", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                }
                Text("${fr(hours)} h", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                val days = hours / 24
                Text(
                    listOfNotNull(if (days > 0) "soit ${fr(days)} jours non-stop" else null, minutes?.let { "${fr(it)} minutes" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        val tiles = recordTiles(records)
        itemsIndexed(tiles.chunked(2), key = { i, _ -> "rec-$i" }) { i, pair ->
            Row(CardModifier.achievementAppear(i), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { t -> BigNumber(t.label, t.value, t.icon, Modifier.weight(1f), t.caption) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- 3. Récap

private val Periods = listOf("week" to "Semaine", "month" to "Mois", "year" to "Année")

@Composable
private fun RecapTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    var period by rememberSaveable { mutableStateOf("week") }
    val load = rememberLoad("st-recap", period, reloader = reloader) { hub.recap(period) }
    StatsList(
        load, reloader, "Rien à récapituler sur cette période.", { it.isEmpty },
        header = {
            item("periods") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Periods.forEach { (id, label) -> HubChip(label, period == id) { period = id } }
                }
            }
        },
    ) { recap -> recapContent(recap) }
}

private fun LazyListScope.recapContent(recap: StatRecap) {
    item("counts") {
        Column {
            Row(CardModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumber("Titres", fr(recap.total), Icons.Rounded.VideoLibrary, Modifier.weight(1f))
                BigNumber("Films", fr(recap.movies), Icons.Rounded.Movie, Modifier.weight(1f))
            }
            Row(CardModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumber("Épisodes", fr(recap.episodes), Icons.Rounded.Tv, Modifier.weight(1f))
                BigNumber("Jours actifs", fr(recap.daysWatched), Icons.Rounded.CalendarMonth, Modifier.weight(1f), recap.days.takeIf { it > 0 }?.let { "sur $it jours" })
            }
        }
    }
    item("badges-title") { StatsSection("Badges débloqués", fr(recap.badgesUnlocked)) }
    if (recap.badges.isEmpty()) item("badges-empty") {
        Text("Aucun badge débloqué sur cette période.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    itemsIndexed(recap.badges, key = { i, b -> "badge-$i-${b.id}" }) { i, badge ->
        val color = rarityColor(badge.rarity ?: "Common")
        Row(CardModifier.achievementAppear(i).statCard(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)).border(1.5.dp, color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(badge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(badge.rarity, relativeFr(badge.unlockedAt).takeIf { it.isNotEmpty() }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            }
        }
    }
    rankedSection("genres", "Top genres", recap.topGenres)
    rankedSection("directors", "Top réalisateurs", recap.topDirectors)
    rankedSection("actors", "Top acteurs", recap.topActors)
}

private fun LazyListScope.rankedSection(key: String, title: String, list: List<RankedName>) {
    if (list.isEmpty()) return
    item("$key-title") { StatsSection(title) }
    item("$key-bars") { RankedBars(list.take(5)) }
}

@Composable
private fun RankedBars(list: List<RankedName>) {
    val max = list.maxOf { it.count }.coerceAtLeast(1)
    Column(CardModifier.statCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (i == 0) Nflix.Red else Nflix.Muted, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(fr(r.count), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    AchievementProgressBar(r.count.toFloat() / max, if (i == 0) Nflix.Red else Nflix.Red.copy(alpha = 0.6f), delayMs = 120 + i * 60)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 4. Objectifs

private class GoalsData(val goals: List<SmartGoal>, val etas: List<BadgeEta>)

@Composable
private fun GoalsTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-goals", reloader = reloader) { GoalsData(orEmpty(emptyList()) { hub.smartGoals() }, orEmpty(emptyList()) { hub.badgeEta() }) }
    StatsList(load, reloader, "Aucun objectif pour l'instant.", { it.goals.isEmpty() && it.etas.isEmpty() }) { data ->
        if (data.goals.isNotEmpty()) item("goals-title") { StatsSection("Objectifs conseillés") }
        itemsIndexed(data.goals, key = { i, g -> "goal-$i-${g.badgeId}" }) { i, goal ->
            val color = goal.rarity?.let { rarityColor(it) } ?: Nflix.Red
            Column(CardModifier.achievementAppear(i).statCard()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Nflix.Red.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Flag, null, tint = Nflix.Red, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(goal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        goal.action?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Text.copy(alpha = 0.85f), maxLines = 2) }
                    }
                    goal.rarity?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                AchievementProgressBar(goal.progress, Nflix.Red)
                Text("${fr(goal.current)} / ${fr(goal.target)} · ${(goal.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (data.etas.isNotEmpty()) item("eta-title") { StatsSection("Prochains badges") }
        itemsIndexed(data.etas, key = { i, e -> "eta-$i-${e.badgeId}" }) { i, eta ->
            Row(CardModifier.achievementAppear(i).statCard(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(eta.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    AchievementProgressBar(eta.progress, Nflix.Red)
                    Text("${fr(eta.current)} / ${fr(eta.target)}", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(12.dp))
                val days = eta.daysRemaining
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (days != null) "≈ ${fr(days.coerceAtLeast(1))} ${if (days <= 1) "jour" else "jours"}" else "${(eta.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (days != null) Nflix.Warning else Nflix.Text,
                    )
                    if (days != null) Text("estimé", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 5. Catégories

private class CategoriesData(val categories: List<CategoryProgress>, val libraries: List<LibraryCompletion>)

@Composable
private fun CategoriesTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-categories", reloader = reloader) {
        CategoriesData(orEmpty(emptyList()) { hub.categoryProgress() }, orEmpty(emptyList()) { hub.libraryCompletion() })
    }
    StatsList(load, reloader, "Aucune catégorie pour l'instant.", { it.categories.isEmpty() && it.libraries.isEmpty() }) { data ->
        if (data.libraries.isNotEmpty()) {
            item("lib-title") { StatsSection("Bibliothèques regardées") }
            item("libs") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.libraries, key = { it.name }) { lib ->
                        Column(Modifier.width(112.dp).statCard(), horizontalAlignment = Alignment.CenterHorizontally) {
                            AchievementProgressRing(lib.percent / 100f, Nflix.Red, 76.dp, 7.dp) {
                                Text("${lib.percent} %", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(lib.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (data.categories.isNotEmpty()) {
            val done = data.categories.count { it.total > 0 && it.unlocked >= it.total }
            item("cat-title") { StatsSection("Catégories de badges", "$done/${data.categories.size} complètes") }
        }
        itemsIndexed(data.categories.sortedByDescending { it.percent }, key = { i, c -> "cat-$i-${c.category}" }) { i, cat ->
            val complete = cat.total > 0 && cat.unlocked >= cat.total
            Column(CardModifier.achievementAppear(i).statCard()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${cat.unlocked}/${cat.total}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                    Spacer(Modifier.width(10.dp))
                    Text("${cat.percent} %", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (complete) Nflix.Success else Nflix.Text)
                }
                Spacer(Modifier.height(8.dp))
                AchievementProgressBar(cat.percent / 100f, if (complete) Nflix.Success else Nflix.Red)
            }
        }
    }
}

// ---------------------------------------------------------------- 6. À regarder

@Composable
private fun WatchTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-reco", reloader = reloader) { hub.recommendations() }
    StatsList(load, reloader, "Aucune suggestion pour l'instant.", { it.isEmpty() }) { list ->
        itemsIndexed(list, key = { i, r -> "reco-$i-${r.badgeId}" }) { i, reco -> RecommendationRow(reco, Modifier.achievementAppear(i)) }
    }
}

@Composable
private fun RecommendationRow(reco: BadgeRecommendation, modifier: Modifier) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    Column(modifier.padding(vertical = 8.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = Nflix.Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(reco.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (reco.target > 0) Text("${fr(reco.current)} / ${fr(reco.target)}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
            }
            if (reco.target > 0) {
                Spacer(Modifier.height(6.dp))
                AchievementProgressBar(reco.progress, Nflix.Red, height = 4.dp)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reco.items, key = { it.id }) { item ->
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).clickable { navigator.open(Route.Item(item.id)) }) {
                    NImage(deps.api.imageUrl(item.id, maxWidth = 300), Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)), description = item.name)
                    Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    val meta = listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.runTimeMinutes.takeIf { it > 0 }?.let { minutesFr(it.toLong()) })
                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 7. Comparaisons

@Composable
private fun CompareHistoryTab(hub: AchievementHub) {
    val reloader = rememberReloader()
    val load = rememberLoad("st-compare-history", reloader = reloader) { hub.compareHistory() }
    var compareWith by remember { mutableStateOf<String?>(null) }
    StatsList(load, reloader, "Aucune comparaison récente.", { it.isEmpty() }) { list ->
        item("cmp-title") { StatsSection("Comparaisons récentes") }
        itemsIndexed(list, key = { i, e -> "cmp-$i-${e.otherUserId}" }) { i, entry -> CompareHistoryRow(entry, Modifier.achievementAppear(i)) { compareWith = entry.otherUserId } }
    }
    compareWith?.let { other -> CompareDialog(hub, other) { compareWith = null } }
}

@Composable
private fun CompareHistoryRow(entry: CompareHistoryEntry, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Nflix.Red.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(entry.otherUserName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Nflix.Red)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.otherUserName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            relativeFr(entry.at).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nflix.Muted)
    }
}

private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
}

/** "il y a 3 jours", "hier"… empty when the date is unreadable. */
private fun relativeFr(iso: String?): String {
    val instant = parseInstant(iso) ?: return ""
    val seconds = Duration.between(instant, Instant.now()).seconds
    if (seconds < 60) return "à l'instant"
    if (seconds < 3600) return "il y a ${seconds / 60} min"
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(instant.atZone(zone).toLocalDate(), LocalDate.now(zone))
    return when {
        days <= 0L -> "il y a ${seconds / 3600} h"
        days == 1L -> "hier"
        days < 7L -> "il y a $days jours"
        days < 30L -> "il y a ${days / 7} sem."
        days < 365L -> "il y a ${days / 30} mois"
        else -> (days / 365).let { "il y a $it ${if (it > 1) "ans" else "an"}" }
    }
}
