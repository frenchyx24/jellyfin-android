package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.LibraryFilter
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

internal fun typesFor(collectionType: String?): String? = when (collectionType) {
    "movies" -> "Movie"
    "tvshows" -> "Series"
    "boxsets" -> "BoxSet"
    "playlists" -> "Playlist"
    "music" -> "MusicAlbum"
    "books" -> "Book,AudioBook"
    "musicvideos" -> "MusicVideo"
    "homevideos" -> "Video,Photo"
    else -> null
}

@Composable
fun LibraryScreen() {
    VideoHomeScreen()
}

@Composable
fun BrowseScreen(route: Route.Browse) {
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Text(route.genre ?: route.title, style = MaterialTheme.typography.headlineSmall)
        }
        BrowseGrid(viewId = route.viewId.takeIf { it != "all" }, types = route.types, key = route.key, genre = route.genre)
    }
}

@Composable
fun ScreenTitle(title: String, trailing: @Composable () -> Unit = {}) {
    val navigator = LocalNavigator.current
    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        trailing()
        IconButton(onClick = { navigator.open(Route.Search) }) { Icon(Icons.Rounded.Search, "Rechercher") }
    }
}

@Composable
fun NChip(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Nflix.Red.copy(alpha = 0.2f),
            selectedLabelColor = Nflix.Text,
            selectedLeadingIconColor = Nflix.Red,
            containerColor = Nflix.SurfaceHigh,
            labelColor = Nflix.Muted,
        ),
        border = null,
    )
}

/**
 * Paged poster grid with sort, filter and genre controls.
 * [header] lets callers add content above the grid (e.g. the books "continue reading" row).
 */
@Composable
fun BrowseGrid(
    viewId: String?,
    types: String?,
    key: String,
    genre: String? = null,
    initialFilter: LibraryFilter = LibraryFilter.All,
    square: Boolean = false,
    header: (LazyGridScope.() -> Unit)? = null,
    onItemClick: ((Item, String) -> Unit)? = null,
    personId: String? = null,
) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    var sort by rememberSaveable(key) { mutableStateOf(LibrarySort.Added) }
    var filter by rememberSaveable(key) { mutableStateOf(initialFilter) }
    var selectedGenre by rememberSaveable(key) { mutableStateOf(genre) }
    var sortMenu by remember { mutableStateOf(false) }
    val items = remember(key, sort, filter, selectedGenre) { mutableStateListOf<Item>() }
    var total by remember(key, sort, filter, selectedGenre) { mutableIntStateOf(-1) }
    var loading by remember(key, sort, filter, selectedGenre) { mutableStateOf(false) }
    var loadError by remember(key, sort, filter, selectedGenre) { mutableStateOf<String?>(null) }
    var requestVersion by remember(key, sort, filter, selectedGenre) { mutableLongStateOf(0L) }
    val genres = rememberLoad("genres", viewId) { deps.jellyfin.genres(viewId) }.value.orEmpty()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val userData by deps.jellyfin.userDataChanges.collectAsState()

    suspend fun loadMore() {
        if (loading || (total >= 0 && items.size >= total)) return
        loading = true
        loadError = null
        val request = ++requestVersion
        val start = items.size
        try {
            val page = deps.jellyfin.items(parentId = viewId, includeTypes = types, sort = sort, filter = filter, start = start, limit = PAGE, genre = selectedGenre, personId = personId)
            if (request == requestVersion) {
                total = page.total
                val existing = items.asSequence().map { it.id }.toHashSet()
                val additions = page.items.filter { existing.add(it.id) }
                items.addAll(additions)
                if (additions.isEmpty() && items.size < total) loadError = "Le serveur n'a renvoyé aucun nouveau titre. Réessayez."
            }
        } catch (error: Exception) {
            if (request == requestVersion) loadError = error.message ?: "Impossible de charger les titres. Réessayez."
        } finally {
            if (request == requestVersion) loading = false
        }
    }

    LaunchedEffect(key, sort, filter, selectedGenre) {
        items.clear()
        total = -1
        loadError = null
        loading = false
        requestVersion++
        loadMore()
    }
    LaunchedEffect(grid, key, sort, filter, selectedGenre, items.size, total, loadError, loading) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { it != null }
            .collect { index ->
                val last = index ?: return@collect
                val contentCount = items.size + if (total < 0) 12 else 1 + if (loadError != null) 1 else 0
                if (last >= contentCount - 18) loadMore()
            }
    }
    val countLabel by remember { derivedStateOf { if (total > 0) "$total titres" else "" } }

    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(if (square) 140.dp else 108.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ContentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header?.invoke(this)
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(countLabel, style = MaterialTheme.typography.labelMedium, color = Nflix.Muted, modifier = Modifier.weight(1f))
                    Box {
                        NChip(sort.label, false, Icons.AutoMirrored.Rounded.Sort) { sortMenu = true }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null, tint = Nflix.Red) },
                                    onClick = { sort = option; sortMenu = false },
                                )
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(LibraryFilter.entries, key = { "f-${it.name}" }) { option -> NChip(option.label, option == filter) { filter = option } }
                    items(genres.distinct(), key = { "g-$it" }) { name -> NChip(name, name == selectedGenre) { selectedGenre = if (selectedGenre == name) null else name } }
                }
            }
        }
        items(items, key = { it.id }, contentType = { "poster" }) { item ->
            val origin = "grid-$key/${item.id}"
            val merged = userData[item.id]?.let { item.copy(userData = it) } ?: item
            PosterCard(
                item = merged,
                imageUrl = deps.jellyfin.primary(merged, 300),
                origin = origin,
                onClick = {
                    if (onItemClick != null) onItemClick(merged, origin)
                    else navigator.open(
                        when (merged.type) {
                            "MusicAlbum" -> Route.Album(merged.id)
                            "Playlist" -> Route.Playlist(merged.id)
                            else -> Route.Item(merged.id, origin)
                        },
                    )
                },
                width = androidx.compose.ui.unit.Dp.Unspecified,
                aspect = if (square || merged.type == "MusicAlbum") 1f else 2f / 3f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (total < 0 && loading) {
            items(12, key = { "sk-$it" }) { Skeleton(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 2f / 3f)) }
        } else if (total == 0 && loadError == null) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.VideoLibrary, "Rien ici pour le moment", "Changez les filtres ou revenez plus tard.") }
        }
        if (loadError != null) {
            item(key = "load-error", span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Rounded.VideoLibrary, "Chargement interrompu", loadError)
                    NChip("Réessayer", false) { scope.launch { loadMore() } }
                }
            }
        }
        item(key = "end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.width(1.dp)) }
    }
}

private const val PAGE = 60
