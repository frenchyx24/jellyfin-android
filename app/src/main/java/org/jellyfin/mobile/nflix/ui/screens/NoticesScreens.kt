package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.Notice
import org.jellyfin.mobile.nflix.data.NoticeCategory
import org.jellyfin.mobile.nflix.data.NoticeFormat
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.appear

private fun NoticeCategory.accent(): Color = when (this) {
    NoticeCategory.Info -> Nflix.Info
    NoticeCategory.Maintenance -> Nflix.Warning
    NoticeCategory.New -> Nflix.Red
}

@Composable
fun NoticesScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val notices by deps.plugins.notices.collectAsState()
    val read by deps.plugins.read.collectAsState()
    val dismissed by deps.plugins.dismissed.collectAsState()
    val failed by deps.plugins.noticesError.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val unread = NoticeFormat.unread(notices, read, dismissed)

    LaunchedEffect(Unit) {
        deps.plugins.refreshNotices()
        loaded = true
    }
    fun refresh() {
        scope.launch {
            refreshing = true
            deps.plugins.refreshNotices()
            refreshing = false
            loaded = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                item {
                    Column(Modifier.statusBarsPadding().padding(start = 64.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                        Text("Annonces", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            when {
                                unread > 0 -> if (unread == 1) "1 nouvelle annonce" else "$unread nouvelles annonces"
                                else -> "Les nouvelles de Nflix"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Nflix.Muted,
                        )
                        if (unread > 0) {
                            TextButton(
                                onClick = { deps.plugins.markRead(notices.map { it.id }) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Rounded.DoneAll, null, tint = Nflix.Red, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tout marquer comme lu", color = Nflix.Red, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                when {
                    notices.isEmpty() && failed -> item { EmptyState(Icons.Rounded.CloudOff, "Annonces indisponibles", "Vérifiez votre connexion, puis tirez pour actualiser.") }
                    notices.isEmpty() && loaded -> item { EmptyState(Icons.Rounded.NotificationsOff, "Aucune annonce", "Les nouvelles de Nflix apparaîtront ici.") }
                    notices.isEmpty() -> item { org.jellyfin.mobile.nflix.ui.SkeletonRow() }
                    else -> items(notices, key = { it.id }) { notice ->
                        NoticeCard(
                            notice = notice,
                            unread = notice.id !in read && notice.id !in dismissed,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().appear(),
                        ) { navigator.open(Route.NoticeDetail(notice.id)) }
                    }
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun NoticeCard(notice: Notice, unread: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val category = NoticeFormat.category(notice)
    Surface(
        color = Nflix.Surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable { onClick() },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category)
                if (notice.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Badge("Épinglée", Nflix.Warning)
                }
                Spacer(Modifier.weight(1f))
                if (notice.version.isNotBlank()) {
                    Text(notice.version, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                }
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Nflix.Red))
                }
            }
            Text(notice.title, style = MaterialTheme.typography.titleMedium)
            val date = NoticeFormat.relative(notice.createdAt)
            if (date.isNotEmpty()) Text(date, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
            Text(
                notice.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Nflix.Text.copy(alpha = 0.9f),
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: NoticeCategory) = Badge(category.label, category.accent())

@Composable
private fun Badge(label: String, accent: Color) {
    Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun NoticeDetailScreen(id: String) {
    val deps = LocalDeps.current
    val notices by deps.plugins.notices.collectAsState()
    val notice = notices.firstOrNull { it.id == id }
    LaunchedEffect(id) { notice?.let { deps.plugins.markRead(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 64.dp, end = 20.dp, top = 14.dp, bottom = ContentBottomPadding)) {
            if (notice == null) {
                EmptyState(Icons.Rounded.Campaign, "Annonce introuvable", "Elle a peut-être été retirée. Revenez à la liste des annonces.")
            } else {
                val category = NoticeFormat.category(notice)
                CategoryBadge(category)
                Spacer(Modifier.height(12.dp))
                Text(notice.title, style = MaterialTheme.typography.headlineSmall)
                val date = NoticeFormat.full(notice.createdAt)
                if (date.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(date.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                }
                Spacer(Modifier.height(18.dp))
                Text(notice.message, style = MaterialTheme.typography.bodyLarge, color = Nflix.Text.copy(alpha = 0.92f))
                if (notice.actionUrl.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    TextButton(onClick = { deps.openUrl(notice.actionUrl) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(notice.actionLabel.ifBlank { "Ouvrir le lien" }, color = Nflix.Red, textAlign = TextAlign.Start)
                    }
                }
                if (notice.dismissible) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { deps.plugins.dismiss(notice) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Masquer cette annonce", color = Nflix.Muted)
                    }
                }
            }
        }
        BackButton()
    }
}
