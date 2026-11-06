package org.jellyfin.mobile.nflix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.nflix.MiraAccountBridge
import org.jellyfin.mobile.nflix.MiraProfile
import org.jellyfin.mobile.nflix.ui.screens.AchievementsScreen
import org.jellyfin.mobile.nflix.ui.screens.AlbumScreen
import org.jellyfin.mobile.nflix.ui.screens.ArtistScreen
import org.jellyfin.mobile.nflix.ui.screens.BooksScreen
import org.jellyfin.mobile.nflix.ui.screens.BrowseScreen
import org.jellyfin.mobile.nflix.ui.screens.DetailScreen
import org.jellyfin.mobile.nflix.ui.screens.HomeScreen
import org.jellyfin.mobile.nflix.ui.screens.HorusDetailScreen
import org.jellyfin.mobile.nflix.ui.screens.HorusRequestsScreen
import org.jellyfin.mobile.nflix.ui.screens.HorusScreen
import org.jellyfin.mobile.nflix.ui.screens.LibraryScreen
import org.jellyfin.mobile.nflix.ui.screens.MusicScreen
import org.jellyfin.mobile.nflix.ui.screens.NoticesScreen
import org.jellyfin.mobile.nflix.ui.screens.NowPlayingScreen
import org.jellyfin.mobile.nflix.ui.screens.PersonScreen
import org.jellyfin.mobile.nflix.ui.screens.PlaylistScreen
import org.jellyfin.mobile.nflix.ui.screens.ProfileScreen
import org.jellyfin.mobile.nflix.ui.screens.ReaderScreen
import org.jellyfin.mobile.nflix.ui.screens.SearchScreen
import org.jellyfin.mobile.nflix.ui.tutorial.Tutorial
import org.jellyfin.mobile.nflix.ui.tutorial.TutorialHost
import androidx.compose.ui.semantics.clearAndSetSemantics

private data class TabSpec(val tab: Route.Tab, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Route.Home, "Accueil", Icons.Rounded.Home),
    TabSpec(Route.Library, "Vidéos", Icons.Rounded.VideoLibrary),
    TabSpec(Route.Music, "Musique", Icons.Rounded.LibraryMusic),
    TabSpec(Route.Books, "Livres", Icons.Rounded.AutoStories),
    TabSpec(Route.Horus, "Horus", Icons.Rounded.TravelExplore),
)

/** Root of the native Nflix app. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NflixRoot(deps: AppDeps, navigator: Navigator, backEnabled: Boolean = true) {
    val holder = rememberSaveableStateHolder()
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val music by deps.music.state.collectAsState()
    remember { SeasonalThemes.init(context) }
    LaunchedEffect(Unit) {
        // Festive themes switch on and off with the date, even if the app stays open overnight.
        while (true) {
            delay(15 * 60_000L)
            SeasonalThemes.refreshDate()
        }
    }
    LaunchedEffect(Unit) {
        deps.music.connect()
        deps.plugins.refreshConfigs()
        deps.plugins.refreshNotices()
        if (deps.session.lastMethod == "mira" && deps.session.miraAvatar.isNullOrBlank()) {
            val account = withContext(Dispatchers.IO) { MiraAccountBridge(context).account() }
            account?.let { miraAccount ->
                MiraProfile.fromAcceptedToken(miraAccount.idToken)?.let { claims ->
                    deps.session.setMiraProfile(
                        claims.copy(
                            name = miraAccount.name.takeIf(String::isNotBlank) ?: claims.name,
                            email = miraAccount.email.takeIf(String::isNotBlank) ?: claims.email,
                            avatar = MiraProfile.safeAvatar(miraAccount.avatar) ?: claims.avatar,
                            groups = miraAccount.groups.toSet().ifEmpty { claims.groups },
                        ),
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalDeps provides deps, LocalNavigator provides navigator) {
        BackHandler(enabled = backEnabled && (navigator.stack.size > 1 || navigator.current != Route.Home)) { navigator.back() }
        Box(Modifier.fillMaxSize().background(Nflix.Night)) {
            // While the tutorial covers the app, screen readers only see the tutorial.
            val hiddenUnderTutorial = if (Tutorial.covering) Modifier.clearAndSetSemantics { } else Modifier
            SharedTransitionLayout(Modifier.fillMaxSize().then(hiddenUnderTutorial)) {
                AnimatedContent(
                    targetState = navigator.current,
                    transitionSpec = {
                        val tabSwitch = initialState.isTab && targetState.isTab
                        when {
                            tabSwitch -> fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(160))
                            targetState == Route.NowPlaying ->
                                slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn() togetherWith fadeOut(tween(300))
                            initialState == Route.NowPlaying ->
                                fadeIn(tween(250)) togetherWith slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeOut()
                            navigator.forward ->
                                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(260)))
                            else ->
                                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(260)))
                        }
                    },
                    label = "nflix-nav",
                ) { route ->
                    CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalScreenScope provides this) {
                        holder.SaveableStateProvider(route.key) {
                            Screen(route)
                        }
                    }
                }
            }

            val showChrome = navigator.current != Route.NowPlaying && navigator.current !is Route.Reader
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(hiddenUnderTutorial)) {
                AnimatedVisibility(
                    visible = showChrome && music.current != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(onOpen = { navigator.open(Route.NowPlaying) })
                }
                AnimatedVisibility(
                    visible = showChrome,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    BottomBar(navigator)
                }
            }
            // Announcement popups wait until the tutorial is closed.
            if (!Tutorial.covering) NoticePopup()
            ItemMenuHost()
            TrailerHost()
            TutorialHost(backEnabled = backEnabled)
        }
    }
}

@Composable
private fun Screen(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Library -> LibraryScreen()
        Route.Music -> MusicScreen()
        Route.Books -> BooksScreen()
        Route.Horus -> HorusScreen()
        is Route.Item -> DetailScreen(route.id, route.origin)
        is Route.Person -> PersonScreen(route.id, route.name)
        is Route.Album -> AlbumScreen(route.id)
        is Route.Artist -> ArtistScreen(route.id)
        is Route.Playlist -> PlaylistScreen(route.id)
        is Route.Reader -> ReaderScreen(route.id)
        is Route.HorusDetail -> HorusDetailScreen(route.type, route.id)
        is Route.Browse -> BrowseScreen(route)
        Route.Search -> SearchScreen()
        Route.NowPlaying -> NowPlayingScreen()
        Route.Notices -> NoticesScreen()
        Route.Profile -> ProfileScreen()
        Route.HorusRequests -> HorusRequestsScreen()
        Route.Releases -> org.jellyfin.mobile.nflix.ui.screens.ReleasesScreen()
        Route.AlertSettings -> org.jellyfin.mobile.nflix.ui.screens.AlertSettingsScreen()
        is Route.AchievementHub -> org.jellyfin.mobile.nflix.ui.screens.AchievementHubScreen(route.tab)
        Route.Badges -> AchievementsScreen()
        is Route.Distributor -> org.jellyfin.mobile.nflix.ui.screens.DistributorScreen(route.id)
        Route.Admin -> org.jellyfin.mobile.nflix.ui.screens.AdminScreen()
        Route.AdminDistributors -> org.jellyfin.mobile.nflix.ui.screens.AdminDistributorsScreen()
        is Route.AdminDistributorEdit -> org.jellyfin.mobile.nflix.ui.screens.AdminDistributorEditScreen(route.id)
        Route.AdminRequests -> org.jellyfin.mobile.nflix.ui.screens.AdminRequestsScreen()
        Route.AdminActivity -> org.jellyfin.mobile.nflix.ui.screens.AdminActivityScreen()
        Route.LikedSongs -> org.jellyfin.mobile.nflix.ui.screens.LikedSongsScreen()
        is Route.Audiobook -> org.jellyfin.mobile.nflix.ui.screens.AudiobookScreen(route.id)
        is Route.Saga -> org.jellyfin.mobile.nflix.ui.screens.SagaScreen(route.name, route.audio)
    }
}

/** Bottom padding every scrolling screen keeps free for the bar and the mini player. */
val ContentBottomPadding = 150.dp

@Composable
private fun BottomBar(navigator: Navigator) {
    val selected = navigator.currentTab
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Nflix.Night.copy(alpha = 0.92f), Nflix.Night)))
            .navigationBarsPadding()
            .padding(top = 8.dp),
    ) {
        Surface(color = Nflix.Surface, shape = RoundedCornerShape(0.dp), tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    tabs.forEach { spec ->
                        val active = spec.tab == selected
                        val background by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "tab-bg")
                        Column(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Nflix.Red.copy(alpha = 0.16f * background))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navigator.selectTab(spec.tab) }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val iconScale by animateFloatAsState(if (active) 1.06f else 1f, spring(dampingRatio = 0.7f), label = "icon")
                            Icon(spec.icon, spec.label, tint = if (active) Nflix.Red else Nflix.Muted, modifier = Modifier.size(21.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                            Text(
                                spec.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Nflix.Text else Nflix.Muted,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val deps = LocalDeps.current
    val state by deps.music.state.collectAsState()
    val track = state.current ?: return
    var progress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(track.id, state.isPlaying) {
        while (true) {
            val duration = deps.music.duration()
            progress = if (duration > 0) deps.music.position().toFloat() / duration else 0f
            delay(500)
        }
    }
    val corner by animateDpAsState(if (state.isPlaying) 18.dp else 22.dp, label = "mini-corner")
    Surface(
        color = Nflix.SurfaceHigh,
        shape = RoundedCornerShape(corner),
        shadowElevation = 10.dp,
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().pressable(onOpen, { ItemMenu.showPlayer() }),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NImage(track.artwork, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).sharedPoster("now-playing-art"))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { deps.music.toggle() }) {
                    AnimatedContent(state.isPlaying, label = "mini-play") { playing ->
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Lecture", tint = Nflix.Text)
                    }
                }
                IconButton(onClick = { deps.music.next() }) { Icon(Icons.Rounded.SkipNext, "Suivant", tint = Nflix.Text) }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Nflix.Red))
            }
        }
    }
}

/** Popup for NflixNotice announcements flagged "ShowPopup", once per notice. */
@Composable
private fun NoticePopup() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val notices by deps.plugins.notices.collectAsState()
    val dismissed by deps.plugins.dismissed.collectAsState()
    val popup = notices.firstOrNull { it.showPopup && it.id !in dismissed } ?: return
    AlertDialog(
        onDismissRequest = { if (popup.dismissible) deps.plugins.dismiss(popup) },
        icon = { Icon(Icons.Rounded.Campaign, null, tint = Nflix.Red) },
        title = { Text(popup.title) },
        text = { Text(popup.message, style = MaterialTheme.typography.bodyMedium, maxLines = 14, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = {
                deps.plugins.dismiss(popup)
                if (popup.actionUrl.isNotBlank()) deps.openUrl(popup.actionUrl)
            }) { Text(popup.actionLabel.ifBlank { "J'ai compris" }) }
        },
        dismissButton = {
            TextButton(onClick = { deps.plugins.dismiss(popup); navigator.open(Route.Notices) }) { Text("Toutes les annonces") }
        },
        containerColor = Nflix.SurfaceHigh,
    )
}

@Suppress("unused")
@Composable
private fun CloseButton(onClick: () -> Unit) = IconButton(onClick = onClick) { Icon(Icons.Rounded.Close, "Fermer") }
