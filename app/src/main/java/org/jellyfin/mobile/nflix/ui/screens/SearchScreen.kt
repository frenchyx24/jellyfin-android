package org.jellyfin.mobile.nflix.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType
import org.jellyfin.mobile.nflix.data.SearchRanking
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.VideoCatalog
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.appear
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rowSection

private enum class SearchFilter(val label: String) {
    All("Tout"), Movies("Films"), Series("Séries"), Anime("Animés"), Music("Musique"), Books("Livres"), People("Personnes");

    fun matches(item: Item): Boolean = when (this) {
        All -> true
        Movies -> item.type == "Movie" && !VideoCatalog.isAnime(item)
        Series -> item.type in setOf("Series", "Episode") && !VideoCatalog.isAnime(item)
        Anime -> item.type in setOf("Movie", "Series") && VideoCatalog.isAnime(item)
        Music -> item.type in setOf("MusicAlbum", "MusicArtist", "Audio", "Playlist")
        Books -> item.type in setOf("Book", "AudioBook")
        People -> item.type == "Person"
    }

    fun matches(media: HorusMedia): Boolean = when (this) {
        All -> true
        Movies -> media.type == HorusType.Movie
        Series -> media.type == HorusType.Tv
        Music -> media.type == HorusType.Music
        Books -> media.type == HorusType.Book
        Anime, People -> false
    }
}

/** Requestable titles join the matching row; they are not labelled by their source. */
private val requestRows = mapOf("movies" to HorusType.Movie, "series" to HorusType.Tv, "albums" to HorusType.Music, "books" to HorusType.Book)

private enum class RequestState { Sending, Sent, Failed }

/** Rows shown in "Tout": anime first-class, kept out of Films/Séries. */
private val sections = listOf(
    Triple("movies", "Films") { i: Item -> i.type == "Movie" && !VideoCatalog.isAnime(i) },
    Triple("series", "Séries") { i: Item -> i.type == "Series" && !VideoCatalog.isAnime(i) },
    Triple("anime", "Animés") { i: Item -> i.type in setOf("Movie", "Series") && VideoCatalog.isAnime(i) },
    Triple("episodes", "Épisodes") { i: Item -> i.type == "Episode" },
    Triple("collections", "Collections") { i: Item -> i.type == "BoxSet" },
    Triple("artists", "Artistes") { i: Item -> i.type == "MusicArtist" },
    Triple("albums", "Albums") { i: Item -> i.type == "MusicAlbum" },
    Triple("tracks", "Titres") { i: Item -> i.type == "Audio" },
    Triple("playlists", "Playlists") { i: Item -> i.type == "Playlist" },
    Triple("books", "Livres") { i: Item -> i.type == "Book" },
    Triple("audiobooks", "Livres audio") { i: Item -> i.type == "AudioBook" },
    Triple("people", "Personnes") { i: Item -> i.type == "Person" },
)

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val query: String, val items: List<Item>) : SearchState
    data class Failed(val message: String) : SearchState
}

private object SearchHistory {
    private fun prefs(context: Context) = context.getSharedPreferences("nflix_search", Context.MODE_PRIVATE)
    fun load(context: Context): List<String> = prefs(context).getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun add(context: Context, query: String): List<String> {
        val next = (listOf(query.trim()) + load(context).filterNot { it.equals(query.trim(), true) }).take(8)
        prefs(context).edit { putString("recent", next.joinToString("\n")) }
        return next
    }
    fun clear(context: Context) = prefs(context).edit { remove("recent") }
}

private fun bestMatch(items: List<Item>, query: String): Item? {
    val q = VideoCatalog.normalize(query)
    val videos = items.filter { it.type in setOf("Movie", "Series") }
    return videos.firstOrNull { VideoCatalog.normalize(it.name) == q }
        ?: videos.firstOrNull { VideoCatalog.normalize(it.name).startsWith(q) }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var horus by remember { mutableStateOf<List<HorusMedia>>(emptyList()) }
    var recent by remember { mutableStateOf(SearchHistory.load(context)) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Local library: debounced, the previous request is cancelled as soon as the user types again.
    LaunchedEffect(Unit) {
        snapshotFlow { query.trim() }.debounce(220).distinctUntilChanged().collectLatest { q ->
            if (q.length < 2) {
                state = SearchState.Idle
                return@collectLatest
            }
            if (state !is SearchState.Done) state = SearchState.Loading
            state = runCatching {
                var ranked = SearchRanking.rank(q, deps.jellyfin.search(q, 40))
                // Typos: the server finds nothing for "harry poter"; retry with the start of the longest word.
                val fallback = SearchRanking.fallbackTerm(q)
                if (ranked.size < 3 && fallback != null) {
                    ranked = (ranked + SearchRanking.rank(q, runCatching { deps.jellyfin.search(fallback, 60) }.getOrDefault(emptyList()))).distinctBy { it.id }
                }
                SearchState.Done(q, ranked)
            }.getOrElse { SearchState.Failed(it.message ?: "Serveur injoignable") }
        }
    }
    // Horus runs on its own and never holds back local results.
    LaunchedEffect(Unit) {
        snapshotFlow { query.trim() }.debounce(450).distinctUntilChanged().collectLatest { q ->
            horus = emptyList()
            if (q.length < 2) return@collectLatest
            horus = withTimeoutOrNull(8_000) { runCatching { deps.horus.searchEverything(q) }.getOrNull() }.orEmpty()
                .filter { it.jellyfinId == null && it.type != HorusType.Person && it.status != HorusStatus.Available }
                .map { it to SearchRanking.score(q, it.title) }
                .filter { it.second >= SearchRanking.THRESHOLD }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    val requests = remember { mutableStateMapOf<String, RequestState>() }
    val scope = rememberCoroutineScope()
    fun request(media: HorusMedia) {
        val key = "${media.type.api}-${media.id}"
        if (requests[key] == RequestState.Sending || requests[key] == RequestState.Sent) return
        requests[key] = RequestState.Sending
        scope.launch {
            // Series and anime: every season, no questions asked. Books: the e-book.
            requests[key] = runCatching { deps.horus.request(media) }.fold({ RequestState.Sent }, { error -> if (error.message?.contains("déjà") == true) RequestState.Sent else RequestState.Failed })
        }
    }
    val localTitles = remember(state) { (state as? SearchState.Done)?.items.orEmpty().mapTo(HashSet()) { VideoCatalog.normalize(it.name) } }
    val requestable = remember(horus, localTitles) { horus.filter { VideoCatalog.normalize(it.title) !in localTitles } }

    val busy = query.trim().length >= 2 && (state is SearchState.Loading || (state as? SearchState.Done)?.query != query.trim())
    fun saveRecent(q: String = query) { if (q.trim().length >= 2) recent = SearchHistory.add(context, q) }
    fun open(item: Item) {
        saveRecent()
        val origin = "search/${item.id}"
        navigator.open(
            when (item.type) {
                "MusicAlbum" -> Route.Album(item.id)
                "MusicArtist" -> Route.Artist(item.id)
                "Playlist" -> Route.Playlist(item.id)
                "Person" -> Route.Person(item.id, item.name)
                "Audio" -> Route.Album(item.albumId ?: item.id)
                else -> Route.Item(item.id, origin)
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Films, séries, animés, musique, livres…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = if (query.isNotEmpty()) Nflix.Red else Nflix.Muted) },
                trailingIcon = {
                    AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { query = ""; runCatching { focus.requestFocus() } }) { Icon(Icons.Rounded.Close, "Effacer") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { saveRecent(); focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Nflix.Red, unfocusedBorderColor = Nflix.Outline, focusedContainerColor = Nflix.Surface, unfocusedContainerColor = Nflix.Surface),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        // Thin progress line while a request is in flight: results stay on screen, no flashing.
        Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 20.dp)) {
            androidx.compose.animation.AnimatedVisibility(busy, enter = fadeIn(), exit = fadeOut(tween(400))) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Nflix.Red, trackColor = Color.Transparent)
            }
        }
        val done = state as? SearchState.Done
        AnimatedVisibility(done != null && done.items.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val items = done?.items.orEmpty()
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchFilter.entries.filter { f -> f == SearchFilter.All || items.any(f::matches) || requestable.any(f::matches) }, key = { it.name }) { option ->
                    FilterPill(option.label, items.count(option::matches) + requestable.count(option::matches), option == filter) { filter = option }
                }
            }
        }

        val phase = when {
            query.trim().length < 2 -> "idle"
            state is SearchState.Failed -> "error"
            done == null -> "loading"
            done.items.isEmpty() && requestable.isEmpty() -> "empty"
            else -> "results"
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 14 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it / 20 })
            },
            label = "search-phase",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                "idle" -> SearchIdle(recent, onPick = { query = it }, onClear = { SearchHistory.clear(context); recent = emptyList() })
                "loading" -> SearchSkeleton()
                "error" -> SearchMessage(Icons.Rounded.CloudOff, "Recherche indisponible", (state as? SearchState.Failed)?.message)
                "empty" -> SearchMessage(Icons.Rounded.SearchOff, "Aucun résultat pour « ${query.trim()} »", "Essayez un autre titre, un acteur ou un artiste.")
                else -> SearchResults(
                    query = done?.query.orEmpty(),
                    items = done?.items.orEmpty(),
                    requestable = requestable,
                    requests = requests,
                    filter = filter,
                    onOpen = ::open,
                    onDetails = { media -> saveRecent(); navigator.open(Route.HorusDetail(media.type, media.id)) },
                    onRequest = { media -> saveRecent(); request(media) },
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) Nflix.Red else Nflix.SurfaceHigh, tween(220), label = "pill-bg")
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, spring(dampingRatio = 0.6f), label = "pill-scale")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(background).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Nflix.Text)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.8f) else Nflix.Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdle(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    val navigator = LocalNavigator.current
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = ContentBottomPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (recent.isNotEmpty()) {
            item(key = "recent-title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recherches récentes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("Effacer", color = Nflix.Red, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(8.dp))
                }
            }
            item(key = "recent") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEachIndexed { index, term ->
                        Row(
                            Modifier.appear(index * 35).clip(CircleShape).background(Nflix.Surface).clickable { onPick(term) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Nflix.Muted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(term, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item(key = "explore-title") { Text("Explorer", style = MaterialTheme.typography.titleMedium) }
        item(key = "explore") {
            val tiles = listOf(
                Triple("Films", Color(0xFFE50914), { navigator.selectTab(Route.Library) }),
                Triple("Séries", Color(0xFF3D6BFF), { navigator.selectTab(Route.Library) }),
                Triple("Musique", Color(0xFFB46CFF), { navigator.selectTab(Route.Music) }),
                Triple("Livres", Color(0xFF46D39A), { navigator.selectTab(Route.Books) }),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { col, (label, color, action) ->
                            Box(
                                Modifier.weight(1f).height(72.dp).appear((row * 2 + col) * 50).clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.35f))))
                                    .pressable(action).padding(14.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) { Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(16.dp))
        repeat(2) {
            Skeleton(Modifier.width(140.dp).height(16.dp), RoundedCornerShape(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { Skeleton(Modifier.width(110.dp).aspectRatio(2f / 3f)) } }
        }
    }
}

@Composable
private fun SearchMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String?) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Nflix.Muted, modifier = Modifier.size(46.dp).appear())
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.appear(60))
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted, modifier = Modifier.appear(120))
    }
}

@Composable
private fun SearchResults(
    query: String,
    items: List<Item>,
    requestable: List<HorusMedia>,
    requests: Map<String, RequestState>,
    filter: SearchFilter,
    onOpen: (Item) -> Unit,
    onDetails: (HorusMedia) -> Unit,
    onRequest: (HorusMedia) -> Unit,
) {
    AnimatedContent(filter, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "search-filter") { selected ->
        if (selected == SearchFilter.All) {
            val best = remember(items, query) { bestMatch(items, query) }
            LazyColumn(contentPadding = PaddingValues(bottom = ContentBottomPadding)) {
                best?.let { item(key = "best-${it.id}") { BestMatch(it, onOpen) } }
                sections.forEach { (key, title, predicate) ->
                    val local: List<Any> = items.filter { predicate(it) && it.id != best?.id }
                    val extra: List<Any> = requestRows[key]?.let { type -> requestable.filter { it.type == type } }.orEmpty()
                    rowSection(key, title, local + extra, ::hitKey) { hit ->
                        when (hit) {
                            is Item -> SearchCard(hit, onOpen)
                            is HorusMedia -> RequestCard(hit, requests[requestKey(hit)], { onDetails(hit) }, { onRequest(hit) })
                            else -> Unit
                        }
                    }
                }
            }
        } else {
            val filtered: List<Any> = remember(items, requestable, selected) { items.filter(selected::matches) + requestable.filter(selected::matches) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(filtered, key = ::hitKey) { hit ->
                    when (hit) {
                        is Item -> SearchCard(hit, onOpen, Modifier.animateItem().fillMaxWidth(), gridWidth = true)
                        is HorusMedia -> RequestCard(hit, requests[requestKey(hit)], { onDetails(hit) }, { onRequest(hit) }, Modifier.animateItem().fillMaxWidth(), gridWidth = true)
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun requestKey(media: HorusMedia) = "${media.type.api}-${media.id}"

private fun hitKey(hit: Any): String = when (hit) {
    is Item -> hit.id
    is HorusMedia -> "r-${requestKey(hit)}"
    else -> hit.hashCode().toString()
}

/** A title not on Nflix yet: same look as the library, with a one-tap request button. */
@Composable
private fun RequestCard(media: HorusMedia, state: RequestState?, onClick: () -> Unit, onRequest: () -> Unit, modifier: Modifier = Modifier, gridWidth: Boolean = false) {
    val square = media.type == HorusType.Music
    val pending = media.status in setOf(HorusStatus.Pending, HorusStatus.Processing) || state == RequestState.Sent
    Column(modifier.appear().then(if (gridWidth) Modifier else Modifier.width(120.dp)).pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 2f / 3f).clip(RoundedCornerShape(8.dp))) {
            NImage(media.poster, Modifier.fillMaxSize(), description = media.title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            val background by animateColorAsState(
                when {
                    pending -> Nflix.Success.copy(alpha = 0.9f)
                    state == RequestState.Failed -> Nflix.Warning
                    else -> Color.White
                },
                tween(250), label = "request-bg",
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(background)
                    .clickable(enabled = !pending && state != RequestState.Sending, onClick = onRequest).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(Triple(pending, state, media.status), label = "request-state") { (isPending, current, status) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            current == RequestState.Sending -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                            isPending -> Icon(if (current == RequestState.Sent) Icons.Rounded.Check else Icons.Rounded.Schedule, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            else -> Icon(Icons.Rounded.Add, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                current == RequestState.Sending -> "Envoi…"
                                current == RequestState.Sent -> "Demandé"
                                isPending -> status.label.ifBlank { "Demandé" }
                                current == RequestState.Failed -> "Réessayer"
                                else -> "Demander"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPending) Color.White else Color.Black,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(media.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Nflix.Text)
        Text(listOfNotNull(media.year, "Bientôt sur Nflix").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1)
    }
}

@Composable
private fun SearchCard(item: Item, onOpen: (Item) -> Unit, modifier: Modifier = Modifier, gridWidth: Boolean = false) {
    val deps = LocalDeps.current
    val square = item.type in setOf("MusicAlbum", "MusicArtist", "Audio", "Person", "Playlist")
    PosterCard(
        item, deps.jellyfin.primary(item, 300), "search/${item.id}", { onOpen(item) },
        modifier = modifier.appear(),
        width = if (gridWidth) androidx.compose.ui.unit.Dp.Unspecified else 120.dp,
        aspect = if (square) 1f else 2f / 3f,
        subtitle = item.seriesName ?: item.albumArtist ?: item.year?.toString(),
        typeLabel = if (VideoCatalog.isAnime(item)) "ANIMÉ" else null,
    )
}

@Composable
private fun BestMatch(item: Item, onOpen: (Item) -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(170.dp).appear().clip(RoundedCornerShape(18.dp)).pressable({ onOpen(item) }),
    ) {
        NImage(deps.jellyfin.backdrop(item, 1000) ?: deps.jellyfin.primary(item, 600), Modifier.fillMaxSize(), description = item.name)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.35f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).padding(18.dp).fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MEILLEUR RÉSULTAT", style = MaterialTheme.typography.labelSmall, color = Nflix.Red, fontWeight = FontWeight.Black)
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
            Text(
                listOfNotNull(if (VideoCatalog.isAnime(item)) "Animé" else if (item.type == "Series") "Série" else "Film", item.year?.toString(), item.genres.firstOrNull()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
            )
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { deps.play(scope, item, navigator = navigator) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Text("Lire", color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
