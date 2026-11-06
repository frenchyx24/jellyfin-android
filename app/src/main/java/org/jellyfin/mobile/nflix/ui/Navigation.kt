package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import org.jellyfin.mobile.nflix.data.HorusType

/** Destinations of the native Nflix app. */
sealed class Route(val key: String) {
    sealed class Tab(key: String) : Route(key)
    data object Home : Tab("home")
    data object Library : Tab("library")
    data object Music : Tab("music")
    data object Books : Tab("books")
    data object Horus : Tab("horus")

    /** [origin] is the shared element key of the card that opened the page (poster morph). */
    data class Item(val id: String, val origin: String? = null) : Route("item/$id")
    data class Person(val id: String, val name: String) : Route("person/$id")
    data class Album(val id: String) : Route("album/$id")
    data class Artist(val id: String) : Route("artist/$id")
    data class Playlist(val id: String) : Route("playlist/$id")
    data class Reader(val id: String) : Route("reader/$id")
    data class HorusDetail(val type: HorusType, val id: String) : Route("horus/${type.api}/$id")
    data class Browse(val viewId: String, val title: String, val types: String?, val genre: String? = null) : Route("browse/$viewId/$genre")
    data object Search : Route("search")
    data object NowPlaying : Route("nowplaying")
    data object Notices : Route("notices")
    data object Profile : Route("profile")
    data object Badges : Route("badges")
    data class Audiobook(val id: String) : Route("audiobook/$id")
    data object LikedSongs : Route("liked-songs")
    data class Distributor(val id: String) : Route("distributor/$id")
    data object Admin : Route("admin")
    data object AdminDistributors : Route("admin/distributors")
    data class AdminDistributorEdit(val id: String?) : Route("admin/distributor/${id ?: "new"}")
    data object AdminRequests : Route("admin/requests")
    data object AdminActivity : Route("admin/activity")
    data class Saga(val name: String, val audio: Boolean) : Route("saga/$audio/$name")
    data object HorusRequests : Route("horus-requests")

    val isTab: Boolean get() = this is Tab
}

@Stable
class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    var forward: Boolean = true
        private set

    val current: Route get() = stack.last()
    val currentTab: Route.Tab get() = stack.firstOrNull() as? Route.Tab ?: Route.Home

    fun open(route: Route) {
        if (route == current) return
        forward = true
        if (route is Route.Tab) {
            stack.clear()
            stack.add(route)
        } else {
            stack.add(route)
        }
    }

    fun selectTab(tab: Route.Tab) {
        forward = true
        if (stack.size == 1 && stack.first() == tab) return
        stack.clear()
        stack.add(tab)
    }

    fun back(): Boolean {
        if (stack.size <= 1) {
            if (current != Route.Home) {
                forward = false
                stack.clear()
                stack.add(Route.Home)
                return true
            }
            return false
        }
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalScreenScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Poster morph between lists and detail pages. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(key: String?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val screen = LocalScreenScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedPoster.sharedElement(rememberSharedContentState("poster-$key"), animatedVisibilityScope = screen)
    }
}
