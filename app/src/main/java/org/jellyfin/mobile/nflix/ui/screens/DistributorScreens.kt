package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.data.Distributor
import org.jellyfin.mobile.nflix.ui.ContentBottomPadding
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.NImage
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.PosterCard
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.Skeleton
import org.jellyfin.mobile.nflix.ui.pressable
import org.jellyfin.mobile.nflix.ui.rememberLoad
import org.jellyfin.mobile.nflix.ui.value

/** Distributor logo on its plate (light plate for dark logos); the name is the fallback when no logo is stored. */
@Composable
internal fun DistributorLogo(distributor: Distributor, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val deps = LocalDeps.current
    val logo = deps.admin.logoUrl(distributor)
    val plate = if (distributor.lightPlate) Color(0xFFF3F3F3) else Color(0xFF1C1C1C)
    Box(
        modifier.height(height).clip(RoundedCornerShape(8.dp)).background(plate).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            NImage(logo, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, description = distributor.name, placeholderIcon = null, background = Color.Transparent)
        } else {
            Text(
                distributor.name, fontWeight = FontWeight.Black, color = if (distributor.lightPlate) Color(0xFF111111) else Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class DistributorTab(val label: String, val types: String) { All("Tout", "Movie,Series"), Movies("Films", "Movie"), Series("Séries", "Series") }

/** Every title of one distributor, with films / series filters. */
@Composable
fun DistributorScreen(id: String) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val distributor = rememberLoad("distributor", id) { force -> deps.admin.distributors(force = force).firstOrNull { it.id == id } }.value
    var tab by rememberSaveable { mutableStateOf(DistributorTab.All) }
    val titles = rememberLoad("distributor-titles", id, tab, distributor) {
        distributor?.let { deps.admin.titles(it, tab.types, limit = 300).items }
    }.value
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = ContentBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Nflix.Red.copy(alpha = 0.25f), Color.Transparent)))
                        .statusBarsPadding().padding(top = 60.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (distributor != null) DistributorLogo(distributor, Modifier.width(220.dp), height = 72.dp)
                    Spacer(Modifier.height(10.dp))
                    titles?.let { Text("${it.size} titre(s)", style = MaterialTheme.typography.bodyMedium, color = Nflix.Muted) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DistributorTab.entries.forEach { t -> NChip(t.label, t == tab) { tab = t } }
                    }
                }
            }
            when {
                titles == null -> items(9, key = { "sk$it" }) { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }
                titles.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Rounded.Movie, "Aucun titre", "Aucun titre de la bibliothèque n'est rattaché à ce distributeur.")
                }
                else -> items(titles, key = { it.id }) { item ->
                    val origin = "distributor-$id/${item.id}"
                    PosterCard(item, deps.jellyfin.primary(item, 300), origin, { navigator.open(Route.Item(item.id, origin)) }, modifier = Modifier.fillMaxWidth(), width = Dp.Unspecified)
                }
            }
        }
        BackButton()
    }
}

/** Home strip of distributor logos, each opening its catalogue. */
@Composable
internal fun DistributorStrip(distributors: List<Distributor>) {
    val navigator = LocalNavigator.current
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(distributors.size) { index ->
            val d = distributors[index]
            DistributorLogo(
                d,
                Modifier.width(132.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Nflix.Outline, RoundedCornerShape(12.dp))
                    .pressable({ navigator.open(Route.Distributor(d.id)) }),
                height = 64.dp,
            )
        }
    }
}
