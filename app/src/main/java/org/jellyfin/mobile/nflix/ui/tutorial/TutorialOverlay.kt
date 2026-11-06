package org.jellyfin.mobile.nflix.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.Nflix
import kotlin.math.absoluteValue

/** Tutorial visibility; the Profile can open it again at any time. */
object Tutorial {
    /** Requested state: true from [open] until the user finishes or skips. */
    var visible by mutableStateOf(false)
        private set

    /** True while the overlay is drawn, fade-out included; popups and dialogs wait for it. */
    var covering by mutableStateOf(false)
        internal set

    fun open() {
        // Set together so popups never flash for a frame before the host recomposes.
        covering = true
        visible = true
    }

    internal fun close() {
        visible = false
    }
}

/**
 * Shows the tutorial once per device after the first sign-in, and whenever [Tutorial.open] is called.
 * [backEnabled] is false while a player or settings screen sits on top of the native app.
 */
@Composable
fun TutorialHost(backEnabled: Boolean) {
    val deps = LocalDeps.current
    val context = LocalContext.current.applicationContext
    val progress = remember { TutorialProgress(SharedPrefsTutorialStorage(context)) }
    // null: not known yet (or the check failed), asked again the next time the tutorial opens.
    var isAdmin by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var includeAdmin by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (progress.shouldShow()) Tutorial.open()
    }
    LaunchedEffect(Tutorial.visible) {
        if (Tutorial.visible && isAdmin == null) {
            // Never block the tutorial on the network: it opens with the member pages right away.
            isAdmin = try {
                withTimeoutOrNull(4_000) { deps.admin.isAdmin() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            }
        }
    }
    LaunchedEffect(isAdmin, Tutorial.visible) {
        if (isAdmin == true && Tutorial.visible && TutorialPages.canInsertAdmin(page)) includeAdmin = true
    }

    val shown = remember { MutableTransitionState(false) }
    shown.targetState = Tutorial.visible
    val covering = shown.currentState || shown.targetState
    SideEffect { Tutorial.covering = covering }
    DisposableEffect(Unit) { onDispose { Tutorial.covering = false } }

    AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(280)), exit = fadeOut(tween(220))) {
        val pages = remember(includeAdmin) { TutorialPages.pages(includeAdmin) }
        TutorialOverlay(
            pages = pages,
            initialPage = page,
            backEnabled = backEnabled && Tutorial.visible,
            onPageChange = { page = it },
            onFinish = {
                // Ignores a second tap or back press during the fade-out.
                if (Tutorial.visible) {
                    progress.markSeen()
                    Tutorial.close()
                    page = 0
                }
            },
        )
    }
}

@Composable
private fun TutorialOverlay(
    pages: List<TutorialPage>,
    initialPage: Int,
    backEnabled: Boolean,
    onPageChange: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val count = pages.size
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, count - 1)) { count }
    val scope = rememberCoroutineScope()
    val index = pager.currentPage
    val last = TutorialFlow.isLast(index, count)

    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onPageChange) }
    BackHandler(enabled = backEnabled) {
        if (TutorialFlow.backCloses(pager.currentPage)) onFinish()
        else scope.launch { pager.animateScrollToPage(TutorialFlow.previous(pager.currentPage)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Nflix.RedDeep.copy(alpha = 0.55f), Nflix.Night.copy(alpha = 0.97f), Nflix.Night)))
            // Swallows taps so nothing underneath reacts while the tutorial is open.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Didacticiel Nflix" },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1} / $count",
                    style = MaterialTheme.typography.labelLarge,
                    color = Nflix.Muted,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Spacer(Modifier.weight(1f))
                if (!last) {
                    TextButton(onClick = onFinish, modifier = Modifier.semantics { contentDescription = "Passer le didacticiel" }) {
                        Text("Passer", color = Nflix.Text)
                    }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { pages[it].topic }) { position ->
                TutorialPageView(
                    pages[position],
                    Modifier.graphicsLayer {
                        // Read during drawing only, so a swipe does not recompose the pages.
                        val offset = ((pager.currentPage - position) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        alpha = 1f - offset * 0.6f
                        val scale = 1f - offset * 0.06f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }

            PageDots(index, count, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))

            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(TutorialFlow.previous(index)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Précédent", color = Nflix.Muted)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(TutorialFlow.next(index, count)) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red, contentColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 148.dp),
                ) {
                    Text(TutorialFlow.primaryLabel(index, count), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PageDots(index: Int, count: Int, modifier: Modifier = Modifier) {
    val description = TutorialFlow.pageDescription(index, count)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { dot ->
            val active = dot == index
            val width by animateDpAsState(if (active) 22.dp else 7.dp, tween(260), label = "tutorial-dot")
            val color by animateColorAsState(if (active) Nflix.Red else Nflix.Muted.copy(alpha = 0.4f), tween(260), label = "tutorial-dot-color")
            Box(Modifier.height(7.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun TutorialPageView(page: TutorialPage, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 460.dp
        val viewport = maxHeight
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = viewport),
            contentAlignment = Alignment.Center,
        ) {
            if (landscape) {
                Row(
                    Modifier.widthIn(max = 760.dp).padding(horizontal = 32.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopicBadge(page.topic, if (compact) 88.dp else 120.dp)
                    Spacer(Modifier.width(28.dp))
                    PageText(page, Modifier.weight(1f), centered = false)
                }
            } else {
                Column(
                    Modifier.widthIn(max = 560.dp).padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopicBadge(page.topic, if (compact) 96.dp else 136.dp)
                    Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
                    PageText(page, Modifier.fillMaxWidth(), centered = true)
                }
            }
        }
    }
}

@Composable
private fun PageText(page: TutorialPage, modifier: Modifier, centered: Boolean) {
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Nflix.Text,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = Nflix.Text.copy(alpha = 0.86f),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (page.tips.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nflix.Surface.copy(alpha = 0.92f)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                page.tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(Nflix.Red))
                        Spacer(Modifier.width(12.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = Nflix.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicBadge(topic: TutorialTopic, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Nflix.Red.copy(alpha = 0.42f), Nflix.Red.copy(alpha = 0.08f)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size * 0.66f).clip(CircleShape).background(Nflix.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the page title right next to it says the same thing.
            Icon(topic.icon(), contentDescription = null, tint = Nflix.Red, modifier = Modifier.size(size * 0.34f))
        }
    }
}

private fun TutorialTopic.icon(): ImageVector = when (this) {
    TutorialTopic.Welcome -> Icons.Rounded.Celebration
    TutorialTopic.Home -> Icons.Rounded.Home
    TutorialTopic.Videos -> Icons.Rounded.VideoLibrary
    TutorialTopic.Detail -> Icons.Rounded.Movie
    TutorialTopic.Player -> Icons.Rounded.PlayCircle
    TutorialTopic.QuickMenu -> Icons.Rounded.TouchApp
    TutorialTopic.Music -> Icons.Rounded.LibraryMusic
    TutorialTopic.Books -> Icons.Rounded.AutoStories
    TutorialTopic.Horus -> Icons.Rounded.TravelExplore
    TutorialTopic.Search -> Icons.Rounded.Search
    TutorialTopic.Profile -> Icons.Rounded.Person
    TutorialTopic.Admin -> Icons.Rounded.AdminPanelSettings
    TutorialTopic.Done -> Icons.Rounded.RocketLaunch
}
