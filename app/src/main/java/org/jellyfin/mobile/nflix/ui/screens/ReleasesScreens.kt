package org.jellyfin.mobile.nflix.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.alerts.AlertKind
import org.jellyfin.mobile.nflix.alerts.CalendarEvent
import org.jellyfin.mobile.nflix.alerts.CalendarModel
import org.jellyfin.mobile.nflix.alerts.NflixAlerts
import org.jellyfin.mobile.nflix.ui.Badge
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.Load
import java.time.LocalDate
import java.time.YearMonth

private fun kindColor(event: CalendarEvent): Color = when (event.kind) {
    "anime" -> Color(0xFFB57BFF)
    "movie" -> Nflix.Warning
    else -> Nflix.Info
}

/** Asks for the notification permission once, then runs [then]. */
@Composable
fun rememberNotificationPermission(then: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) NflixAlerts.runNow(context)
        then(granted)
    }
    return {
        when {
            Build.VERSION.SDK_INT >= 33 && !NflixAlerts.permissionGranted(context) -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> then(true)
        }
    }
}

/** "M'alerter" bell of series and anime pages. */
@Composable
fun FollowBell(seriesId: String, title: String, modifier: Modifier = Modifier) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val follows by deps.alerts.follows.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { deps.alerts.loadFollows() } }
    val followed = follows?.contains(org.jellyfin.mobile.nflix.alerts.AlertsRepository.normalize(seriesId)) == true
    val toggle = {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    deps.alerts.setFollow(seriesId, !followed)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    error = "Impossible de modifier l'alerte. Réessayez."
                } finally {
                    busy = false
                }
            }
        }
        Unit
    }
    val ask = rememberNotificationPermission { toggle() }
    val tint by animateColorAsState(if (followed) Nflix.Red else Nflix.Text, label = "bell-tint")
    val bounce by animateFloatAsState(if (followed) 1.15f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "bell-bounce")
    Column(
        modifier.width(74.dp)
            .toggleable(value = followed, enabled = !busy, role = Role.Switch) { if (followed) toggle() else ask() }
            .semantics { stateDescription = if (followed) "Alertes activées pour $title" else "Alertes désactivées pour $title" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(followed, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "bell") { on ->
            Icon(if (on) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, null, tint = tint, modifier = Modifier.size(26.dp).scale(bounce))
        }
        Spacer(Modifier.height(4.dp))
        Text(if (followed) "Alerté" else "M'alerter", style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, maxLines = 1)
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nflix.Red, textAlign = TextAlign.Center) }
    }
}

@Composable
fun ReleasesScreen() {
    val deps = LocalDeps.current
    val today = remember { LocalDate.now() }
    var monthView by rememberSaveable { mutableStateOf(true) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var filter by rememberSaveable { mutableStateOf(CalendarModel.Filter.All) }
    var followedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    var direction by remember { mutableIntStateOf(1) }
    val reloader = rememberReloader()
    val shownMonth = YearMonth.parse(month)
    val range = if (monthView) CalendarModel.gridRange(shownMonth) else today.minusDays(1) to today.plusDays(60)
    val load = rememberLoad("releases", range, reloader = reloader) { force -> deps.alerts.calendar(range.first, range.second, force) }
    val page = (load as? Load.Ready)?.value
    val events = page?.events.orEmpty().distinctBy { it.id }.filter { CalendarModel.matches(it, filter, followedOnly) }
    val byDay = remember(events) { CalendarModel.byDay(events) }

    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item(key = "title") {
                Column(Modifier.statusBarsPadding().padding(start = 64.dp, end = 16.dp, top = 10.dp)) {
                    Text("Prochaines sorties", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Dates estimées d'arrivée sur Nflix", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
            item(key = "controls") {
                Column(Modifier.padding(top = 14.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Segmented(listOf("Mois" to Icons.Rounded.CalendarMonth, "Liste" to Icons.Rounded.ViewAgenda), if (monthView) 0 else 1) { monthView = it == 0 }
                        Spacer(Modifier.weight(1f))
                        Text("Suivies", style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)
                        Spacer(Modifier.width(8.dp))
                        Switch(followedOnly, { followedOnly = it }, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CalendarModel.Filter.entries) { option ->
                            val on = option == filter
                            val bg by animateColorAsState(if (on) Nflix.Red else Nflix.SurfaceHigh, label = "chip")
                            Surface(color = bg, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable { filter = option }.semantics { this.selected = on }) {
                                Text(option.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            if (monthView) {
                item(key = "month") {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { direction = -1; shownMonth.minusMonths(1).let { m -> month = m.toString(); selected = (if (YearMonth.from(today) == m) today else m.atDay(1)).toString() } }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mois précédent") }
                            Text(CalendarModel.monthTitle(shownMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { direction = 1; shownMonth.plusMonths(1).let { m -> month = m.toString(); selected = (if (YearMonth.from(today) == m) today else m.atDay(1)).toString() } }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mois suivant") }
                        }
                        AnimatedContent(
                            shownMonth,
                            transitionSpec = { (slideInHorizontally(tween(260)) { it * direction / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120)) },
                            label = "month-grid",
                        ) { current ->
                            MonthGrid(current, today, LocalDate.parse(selected), byDay) { selected = it.toString() }
                        }
                    }
                }
                val day = LocalDate.parse(selected)
                item(key = "day-title") {
                    Text(CalendarModel.dayTitle(day, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
                }
                val dayEvents = byDay[day].orEmpty()
                if (page != null && dayEvents.isEmpty()) item(key = "day-empty") {
                    Text("Rien de prévu ce jour-là.", style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                items(dayEvents, key = { "d-" + it.id }) { EventCard(it, Modifier.appear()) }
            } else {
                byDay.filterKeys { !it.isBefore(today.minusDays(1)) }.forEach { (day, list) ->
                    item(key = "h-$day") {
                        Text(CalendarModel.dayTitle(day, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
                    }
                    items(list, key = { "l-" + it.id }) { EventCard(it, Modifier.appear()) }
                }
                if (page != null && byDay.isEmpty()) item(key = "empty") {
                    EmptyState(Icons.Rounded.EventBusy, "Aucune sortie prévue", if (followedOnly) "Activez « M'alerter » sur vos séries pour les suivre ici." else "Revenez bientôt.", Modifier.padding(top = 40.dp))
                }
            }
            when (load) {
                is Load.Loading -> item(key = "loading") { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) } } }
                is Load.Failed -> item(key = "failed") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Calendrier indisponible pour le moment.", color = Nflix.Muted)
                        Text("Réessayer", color = Nflix.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp).clickable { reloader.reload() })
                    }
                }
                else -> if (page?.sourceAvailable == false) item(key = "nosource") {
                    Text("Le calendrier du serveur n'est pas activé.", color = Nflix.Muted, modifier = Modifier.padding(20.dp))
                }
            }
            item(key = "legend") {
                Text(
                    "Les dates sont des estimations : elles se précisent à mesure que les épisodes arrivent sur Nflix.",
                    style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(20.dp),
                )
            }
        }
        BackButton()
    }
}

@Composable
private fun Segmented(options: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Nflix.SurfaceHigh).padding(3.dp)) {
        options.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, label = "segment")
            Row(
                Modifier.clip(CircleShape).background(bg).clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 7.dp).semantics { this.selected = on },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (on) Color.Black else Nflix.Text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.Black else Nflix.Text)
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, selected: LocalDate, byDay: Map<LocalDate, List<CalendarEvent>>, onSelect: (LocalDate) -> Unit) {
    val days = remember(month) { CalendarModel.gridDays(month) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nflix.Surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            CalendarModel.weekDays.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted, textAlign = TextAlign.Center) }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                week.forEach { date ->
                    val events = byDay[date].orEmpty()
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    val bg by animateColorAsState(if (isSelected) Nflix.Red else Color.Transparent, label = "day")
                    Column(
                        Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                            .then(if (date == today && !isSelected) Modifier.border(1.dp, Nflix.Red, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onSelect(date) }
                            .semantics {
                                this.selected = isSelected
                                contentDescription = "${CalendarModel.dayTitle(date, today)}, ${events.size} sortie(s)"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (date == today) FontWeight.Black else FontWeight.Medium,
                            color = when { isSelected -> Color.White; inMonth -> Nflix.Text; else -> Nflix.Muted.copy(alpha = 0.45f) },
                        )
                        Row(Modifier.padding(top = 3.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            events.distinctBy { it.kind }.take(3).forEach { event ->
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else kindColor(event)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, modifier: Modifier = Modifier) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val target = event.itemId ?: event.seriesId
    val image = target?.let { deps.api.imageUrl(it, "Primary", null, 240) } ?: event.posterUrl
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
            .then(if (target != null) Modifier.pressable({ navigator.open(Route.Item(target)) }) else Modifier).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
            NImage(image, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(kindColor(event)))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (event.subtitle.isNotBlank()) Text(event.subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Text.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(CalendarModel.detail(event), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(CalendarModel.confidenceLabel(event), if (event.available) Nflix.Success.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f))
                if (event.followed) Badge("Suivie", Nflix.Red.copy(alpha = 0.35f))
            }
        }
        if (event.followed) Icon(Icons.Rounded.Notifications, "Série suivie", tint = Nflix.Red, modifier = Modifier.padding(start = 6.dp).size(20.dp))
    }
}

@Composable
fun AlertSettingsScreen() {
    val deps = LocalDeps.current
    val context = LocalContext.current
    val version by deps.alertPrefs.version.collectAsState()
    var allowed by remember { mutableStateOf(NflixAlerts.canNotify(context)) }
    val ask = rememberNotificationPermission {
        allowed = NflixAlerts.canNotify(context)
        // Refused for good, or turned off in the system: only the settings page can fix it.
        if (!allowed) NflixAlerts.openSettings(context)
    }
    @Suppress("DEPRECATION") val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = NflixAlerts.canNotify(context)
                if (now && !allowed) NflixAlerts.runNow(context)
                allowed = now
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val follows by deps.alerts.follows.collectAsState()
    LaunchedEffect(Unit) { runCatching { deps.alerts.loadFollows(force = true) } }
    Box(Modifier.fillMaxSize().background(Nflix.Night)) {
        LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
            item {
                Column(Modifier.statusBarsPadding().padding(start = 64.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Vérifiées environ toutes les 15 minutes", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                }
            }
            if (!allowed) item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Red.copy(alpha = 0.18f)).clickable { ask() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.NotificationsOff, null, tint = Nflix.Red)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Notifications bloquées", style = MaterialTheme.typography.titleSmall)
                        Text("Touchez pour les autoriser. Vos alertes vous attendent.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                    }
                }
            }
            item(key = "master-$version") {
                SettingSwitch("Alertes Nflix", "Toutes les notifications de l'app", deps.alertPrefs.masterEnabled) { deps.alertPrefs.masterEnabled = it }
            }
            AlertKind.entries.forEach { kind ->
                item(key = "${kind.wire}-$version") {
                    SettingSwitch(kind.label, kind.description, deps.alertPrefs.kindSwitch(kind), enabled = deps.alertPrefs.masterEnabled) { deps.alertPrefs.setEnabled(kind, it) }
                }
            }
            item {
                Text(
                    "Séries suivies : ${follows?.size ?: "…"}. Activez « M'alerter » sur la page d'une série ou d'un animé.",
                    style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, modifier = Modifier.padding(20.dp),
                )
            }
        }
        BackButton()
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nflix.Surface)
            .clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { role = Role.Switch; stateDescription = if (checked) "Activé" else "Désactivé" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Nflix.Text else Nflix.Muted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
        }
        Switch(checked, null, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red))
    }
}
