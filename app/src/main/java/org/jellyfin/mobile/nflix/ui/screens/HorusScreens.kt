package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.HorusCard
import org.jellyfin.mobile.nflix.ui.HorusStatusBadge
import org.jellyfin.mobile.nflix.ui.Load
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.SkeletonRow
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.bottomScrim
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.rememberReloader
import org.jellyfin.mobile.nflix.ui.sharedPoster
import org.jellyfin.mobile.nflix.ui.value

private enum class HorusTab(val label: String, val type: HorusType?) {
    All("Tout", null), Movies("Films", HorusType.Movie), Series("Séries", HorusType.Tv), Music("Musique", HorusType.Music), Books("Livres", HorusType.Book)
}

@Composable
fun HorusScreen() {
    val navigator = LocalNavigator.current
    var tab by rememberSaveable { mutableStateOf(HorusTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Horus") {
            IconButton(onClick = { navigator.open(Route.HorusRequests) }) { Icon(Icons.Rounded.Inbox, "Mes demandes") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Film, série, album, livre…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") } },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, unfocusedBorderColor = Nflix.Outline, focusedContainerColor = Nflix.Surface, unfocusedContainerColor = Nflix.Surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HorusTab.entries, key = { it.name }) { t -> NChip(t.label, t == tab) { tab = t } }
        }
        AnimatedContent(query.isBlank() to tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "horus") { (discover, current) ->
            if (discover) HorusDiscover(current) else HorusSearchResults(query, current.type)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorusDiscover(tab: HorusTab) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pageReloader = rememberReloader()
    val requestsReloader = rememberReloader()
    var refreshing by remember { mutableStateOf(false) }
    val cacheKey = tab.name
    // Cached content first (no skeleton flash when coming back), refreshed in place.
    val data = rememberLoad("horus-discover", tab, reloader = pageReloader, initial = HorusHomeCache[cacheKey]) { _ ->
        loadHorusHome(deps.horus, tab.type).also { HorusHomeCache[cacheKey] = it }
    }
    val requestsLoad = rememberLoad("horus-my-requests", reloader = requestsReloader, initial = HorusHomeCache.requests) { force ->
        deps.horus.myRequests(force).also { HorusHomeCache.requests = it }
    }
    LaunchedEffect(data, requestsLoad, refreshing) {
        if (refreshing && data !is Load.Loading && requestsLoad !is Load.Loading) refreshing = false
    }
    val allRequests = requestsLoad.value.orEmpty()
    // Quick requests made here stay marked until Horus' own list confirms (or drops) them.
    LaunchedEffect(requestsLoad) {
        if (requestsLoad !is Load.Ready) return@LaunchedEffect
        // Kept only while the request is known and not yet available (then the real status wins).
        val pending = allRequests.filter { it.media.status != HorusStatus.Available }.map { "${it.type.api}-${it.media.id}" }.toSet()
        HorusHomeCache.overrides.keys.filter { it !in pending }.forEach { HorusHomeCache.overrides.remove(it) }
    }
    val requests = remember(allRequests, tab) { allRequests.filter { tab.type == null || it.type == tab.type }.distinctBy { it.id } }
    val busy = remember { mutableStateMapOf<String, Boolean>() }

    fun openDetail(media: HorusMedia) = navigator.open(Route.HorusDetail(media.type, media.id))

    fun watch(media: HorusMedia) {
        val id = media.jellyfinId ?: return openDetail(media)
        navigator.open(if (media.type == HorusType.Music) Route.Album(id) else Route.Item(id))
    }

    fun quickRequest(media: HorusMedia) {
        // Books ask "ebook or audiobook": that choice lives on the detail page.
        if (media.type == HorusType.Book) return openDetail(media)
        val key = media.horusKey
        if (busy[key] == true) return
        busy[key] = true
        scope.launch {
            val result = runCatching { deps.horus.request(media) }
            busy.remove(key)
            result.onSuccess {
                HorusHomeCache.overrides[key] = HorusStatus.Pending
                requestsReloader.reload()
                Toasts.show("Demande envoyée : ${media.title}", ToastKind.Success)
            }.onFailure {
                Toasts.show(it.message ?: "Demande impossible.", ToastKind.Warning)
            }
        }
    }

    fun openRequest(request: HorusRequest) {
        val jellyfinId = request.media.jellyfinId
        if (jellyfinId != null && request.media.status == HorusStatus.Available) {
            navigator.open(if (request.type == HorusType.Music) Route.Album(jellyfinId) else Route.Item(jellyfinId))
        } else {
            navigator.open(Route.HorusRequests)
        }
    }

    val home = data.value
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; pageReloader.reload(); requestsReloader.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = ContentBottomPadding), modifier = Modifier.fillMaxSize()) {
            when {
                home != null -> {
                    if (home.hero.isNotEmpty()) {
                        item(key = "hero", contentType = "hero") {
                            HorusHero(
                                items = home.hero,
                                busy = busy.keys.toSet(),
                                onOpen = { openDetail(it) },
                                onWatch = { watch(it) },
                                onRequest = { quickRequest(it) },
                            )
                        }
                    }
                    if (requests.isNotEmpty()) {
                        val active = requests.filter { it.isInProgress() }
                        val available = requests.filter { it.media.status == HorusStatus.Available }
                        val shown = active.ifEmpty { requests }.take(20)
                        item(key = "requests-header", contentType = "header") {
                            Column(Modifier.horusAppear("$cacheKey/requests-header")) {
                                HorusSectionHeader(if (active.isNotEmpty()) "Mes demandes en cours" else "Mes demandes", onAction = { navigator.open(Route.HorusRequests) })
                                HorusRequestSummary(requests)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        item(key = "requests-row", contentType = "requests") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(shown, key = { _, r -> "req-${r.id}" }) { index, request ->
                                    Box(if (index < 6) Modifier.horusAppear("$cacheKey/req/${request.id}", index * 60) else Modifier) {
                                        HorusRequestTile(request, { openRequest(request) })
                                    }
                                }
                                item(key = "req-all") { HorusSeeAllTile(requests.size, { navigator.open(Route.HorusRequests) }) }
                            }
                        }
                        if (active.isNotEmpty() && available.isNotEmpty()) {
                            item(key = "available-header", contentType = "header") {
                                HorusSectionHeader("Récemment disponibles", action = "Mes demandes", onAction = { navigator.open(Route.HorusRequests) })
                            }
                            item(key = "available-row", contentType = "requests") {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(available.take(12), key = { "avail-${it.id}" }) { request ->
                                        HorusRequestTile(request, { openRequest(request) })
                                    }
                                }
                            }
                        }
                    }
                    home.rows.forEach { row ->
                        item(key = "header-${row.key}", contentType = "header") {
                            Box(Modifier.horusAppear("$cacheKey/header/${row.key}")) { HorusSectionHeader(row.title) }
                        }
                        item(key = "row-${row.key}", contentType = "row-${row.style.name}") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(row.items, key = { _, m -> m.horusKey }) { index, raw ->
                                    val media = raw.withOverride()
                                    val shared = "${row.key}/${raw.horusKey}" in home.shared
                                    Box(if (index < 6) Modifier.horusAppear("$cacheKey/${row.key}/${raw.horusKey}", index * 60) else Modifier) {
                                        when (row.style) {
                                            HorusRowStyle.Wide -> HorusUpcomingCard(media, { openDetail(media) })
                                            HorusRowStyle.Square -> HorusPosterCard(media, shared, { openDetail(media) }, width = 140.dp)
                                            HorusRowStyle.Poster -> HorusPosterCard(media, shared, { openDetail(media) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (home.rows.isEmpty() && home.hero.isEmpty()) {
                        item(key = "empty") { EmptyState(Icons.Rounded.TravelExplore, "Rien à découvrir pour l'instant", "Utilisez la recherche pour trouver un titre.") }
                    }
                }
                data is Load.Failed -> item(key = "error") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.Rounded.TravelExplore, "Horus est injoignable", data.message)
                        FilledTonalButton(onClick = { pageReloader.reload(); requestsReloader.reload() }) { Text("Réessayer") }
                    }
                }
                else -> {
                    item(key = "hero-skeleton", contentType = "hero") { HorusHeroSkeleton() }
                    items(3, key = { "skeleton-$it" }) { index ->
                        SkeletonRow(if (index == 1) 2 else 4, if (index == 1) 264.dp else 124.dp, if (index == 1) 16f / 9f else 2f / 3f)
                    }
                }
            }
        }
    }
}

@Composable
private fun HorusSearchResults(query: String, type: HorusType?) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val results = rememberLoad("horus-search", query, type) {
        delay(350) // debounce typing
        if (type == null) deps.horus.searchEverything(query) else deps.horus.search(query, type).results
    }
    when (results) {
        Load.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nflix.Red) }
        is Load.Failed -> EmptyState(Icons.Rounded.TravelExplore, "Recherche impossible", results.message)
        is Load.Ready -> if (results.value.isEmpty()) EmptyState(Icons.Rounded.Search, "Aucun résultat", "Essayez un autre titre.")
        else LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = ContentBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(results.value.distinctBy { "${it.type.api}-${it.id}" }, key = { "${it.type.api}-${it.id}" }) { media ->
                HorusCard(media, { navigator.open(Route.HorusDetail(media.type, media.id)) }, Modifier.fillMaxWidth(), width = androidx.compose.ui.unit.Dp.Unspecified)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HorusDetailScreen(type: HorusType, id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val reloader = rememberReloader()
    val load = rememberLoad(type, id, reloader = reloader) { force -> deps.horus.details(type, id, force) }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    fun send(media: HorusMedia, seasons: List<Int>? = null, alternate: Boolean = false) {
        sending = true
        message = null
        scope.launch {
            message = runCatching { deps.horus.request(media, seasons, alternate); "Demande envoyée. Vous serez prévenu dès que c’est disponible." }
                .getOrElse { it.message ?: "Demande impossible." }
            sending = false
            reloader.reload()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (load) {
            Load.Loading -> Column { Skeleton(Modifier.fillMaxWidth().height(420.dp), RoundedCornerShape(0.dp)) }
            is Load.Failed -> EmptyState(Icons.Rounded.TravelExplore, "Titre introuvable", load.message, Modifier.align(Alignment.Center))
            is Load.Ready -> {
                val media = load.value
                LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                    item(key = "header") {
                        Box(Modifier.fillMaxWidth().height(440.dp)) {
                            NImage(media.backdrop ?: media.poster, Modifier.fillMaxSize().then(if (media.backdrop == null) Modifier.blur(30.dp) else Modifier).bottomScrim(0.2f), placeholderIcon = null)
                            Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                                NImage(
                                    media.poster,
                                    Modifier.width(120.dp).aspectRatio(if (media.type == HorusType.Music) 1f else 2f / 3f).clip(RoundedCornerShape(14.dp)).sharedPoster("horus-${media.type.api}-${media.id}"),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    HorusStatusBadge(media.status)
                                    Text(media.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    media.subtitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Nflix.Red) }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(listOfNotNull(media.type.label, media.year, media.runtime?.let { "$it min" }).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Nflix.Muted)
                                        media.rating?.let { Icon(Icons.Rounded.Star, null, tint = Nflix.Warning, modifier = Modifier.size(14.dp)); Text("%.1f".format(it), style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "actions") {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val jellyfinId = media.jellyfinId
                            if (jellyfinId != null && media.status in setOf(HorusStatus.Available, HorusStatus.Partial)) {
                                Button(
                                    onClick = { navigator.open(if (media.type == HorusType.Music) Route.Album(jellyfinId) else Route.Item(jellyfinId)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp))
                                    Text(when (media.type) { HorusType.Music -> "Écouter sur Nflix"; HorusType.Book -> "Lire sur Nflix"; else -> "Regarder sur Nflix" }, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (media.requestable) {
                                when (media.type) {
                                    HorusType.Book -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        RequestButton("Livre numérique", Icons.AutoMirrored.Rounded.MenuBook, sending, Modifier.weight(1f)) { send(media) }
                                        RequestButton("Livre audio", Icons.Rounded.Headphones, sending, Modifier.weight(1f), tonal = true) { send(media, alternate = true) }
                                    }
                                    // Series and anime: every season at once, the default quality; no choice to make.
                                    HorusType.Tv -> RequestButton("Demander la série complète", Icons.Rounded.Send, sending, Modifier.fillMaxWidth()) { send(media) }
                                    else -> RequestButton(if (media.type == HorusType.Music) "Demander l'album" else "Demander le film", Icons.Rounded.Send, sending, Modifier.fillMaxWidth()) { send(media) }
                                }
                            } else if (media.status in setOf(HorusStatus.Pending, HorusStatus.Processing)) {
                                Surface(color = Nflix.SurfaceHigh, shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = Nflix.Warning)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Déjà demandé · ${media.status.label}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            AnimatedVisibility(message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Surface(color = Nflix.SurfaceHigh, shape = RoundedCornerShape(14.dp)) {
                                    Text(message.orEmpty(), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    item(key = "overview") {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            media.tagline?.let { Text(it, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(6.dp)) }
                            media.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted) }
                            if (media.genres.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    media.genres.take(8).forEach { genre ->
                                        Surface(color = Nflix.SurfaceHigh, shape = CircleShape) { Text(genre, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    if (media.seasons.isNotEmpty()) {
                        item(key = "seasons-title") { org.jellyfin.mobile.nflix.ui.SectionHeader("Saisons") }
                        items(media.seasons.distinctBy { it.number }, key = { "s-${it.number}" }) { season ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(season.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text("${season.episodes} ép.", style = MaterialTheme.typography.bodySmall, color = Nflix.Muted)
                                Spacer(Modifier.width(8.dp))
                                HorusStatusBadge(season.status)
                            }
                        }
                    }
                    if (media.tracks.isNotEmpty()) {
                        item(key = "tracks-title") { org.jellyfin.mobile.nflix.ui.SectionHeader("Titres") }
                        itemsIndexed(media.tracks, key = { i, t -> "t-$i-${t.title}" }) { _, track ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text("${track.position}", style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, modifier = Modifier.width(30.dp))
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                track.lengthMs?.let { Text(org.jellyfin.mobile.nflix.ui.formatTicks(it * 10_000), style = MaterialTheme.typography.labelSmall, color = Nflix.Muted) }
                            }
                        }
                    }
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun RequestButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sending: Boolean, modifier: Modifier, tonal: Boolean = false, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        AnimatedContent(sending, label = "request-busy") { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (tonal) FilledTonalButton(onClick = onClick, enabled = !sending, modifier = modifier.height(52.dp)) { content() }
    else Button(onClick = onClick, enabled = !sending, colors = ButtonDefaults.buttonColors(containerColor = Nflix.Red), modifier = modifier.height(52.dp)) { content() }
}

@Suppress("unused")
private fun Modifier.dim() = this.graphicsLayer { alpha = 0.6f }

@Suppress("unused")
private val crop = ContentScale.Crop
