package org.jellyfin.mobile.nflix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import timber.log.Timber

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

val <T> Load<T>.value: T? get() = (this as? Load.Ready<T>)?.value

@Stable
class Reloader {
    var tick by mutableIntStateOf(0)
    var forced by mutableStateOf(false)
    fun reload(force: Boolean = true) {
        forced = force
        tick++
    }
}

@Composable
fun rememberReloader() = remember { Reloader() }

/**
 * Loads data for a screen. [initial] lets a screen show cached content immediately (no flash),
 * the loader then refreshes it in place.
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, reloader: Reloader? = null, initial: T? = null, loader: suspend (force: Boolean) -> T): Load<T> {
    val tick = reloader?.tick ?: 0
    val state by produceState<Load<T>>(initial?.let { Load.Ready(it) } ?: Load.Loading, *keys, tick) {
        val force = reloader?.forced == true && tick > 0
        value = runCatching { Load.Ready(loader(force)) }.getOrElse { error ->
            Timber.w(error, "Load failed")
            if (value is Load.Ready) value else Load.Failed(error.message ?: "Erreur de chargement")
        }
    }
    return state
}
