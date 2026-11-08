package org.jellyfin.mobile.nflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.NflixHttpException

/**
 * Download state of online tracks, shared by every screen: a track shows its spinner or its
 * check mark wherever it appears, and a second tap never starts a second download.
 */
object YtDownloads {
    enum class State { Idle, Running, Done }

    private val states = mutableStateMapOf<String, State>()

    fun state(videoId: String): State = states[videoId] ?: State.Idle

    /** Called once per list with the server's answer: tracks already in the library show a check. */
    fun markDownloaded(videoIds: Collection<String>) {
        videoIds.forEach { if (states[it] != State.Running) states[it] = State.Done }
    }

    /** Downloads one track into the Jellyfin music library; true when it ended up there. */
    suspend fun download(deps: AppDeps, videoId: String, title: String = ""): Boolean {
        if (videoId.isBlank()) return false
        if (state(videoId) != State.Idle) return state(videoId) == State.Done
        states[videoId] = State.Running
        val result = runCatching { deps.yt.download(videoId) }
        val done = result.isSuccess
        states[videoId] = if (done) State.Done else State.Idle
        val label = title.takeIf { it.isNotBlank() }?.let { "« $it » " } ?: ""
        ItemMenu.message = if (done) {
            "${label}ajouté à la bibliothèque musique"
        } else {
            friendly(result.exceptionOrNull())
        }
        return done
    }

    /** Downloads a whole album or artist page, one track after the other. */
    suspend fun downloadAll(deps: AppDeps, tracks: List<Pair<String, String>>) {
        val pending = tracks.filter { it.first.isNotBlank() && state(it.first) == State.Idle }
        if (pending.isEmpty()) {
            ItemMenu.message = "Tous ces titres sont déjà dans la bibliothèque"
            return
        }
        var added = 0
        pending.forEach { (videoId, title) ->
            if (download(deps, videoId, title)) added++
        }
        ItemMenu.message = if (added == 0) "Aucun titre téléchargé" else "$added titre(s) ajouté(s) à la bibliothèque"
    }

    private fun friendly(error: Throwable?): String = when (error) {
        is NflixHttpException -> when (error.code) {
            401 -> "Session expirée : reconnectez-vous."
            403 -> "Téléchargement réservé au compte administrateur."
            412 -> "Aucun dossier musique configuré dans le plugin."
            else -> "Téléchargement impossible : ${error.message}"
        }
        else -> error?.message ?: "Téléchargement impossible"
    }
}

/**
 * Little download control: arrow, spinner while the server writes the file, check when the track
 * is in the library. [onImage] uses a dark disc so it stays readable over a thumbnail.
 */
@Composable
fun YtDownloadButton(
    videoId: String,
    modifier: Modifier = Modifier,
    title: String = "",
    size: Dp = 34.dp,
    onImage: Boolean = false,
    tint: Color = Nflix.Muted,
) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val state = YtDownloads.state(videoId)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onImage) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
            .clickable(enabled = state == YtDownloads.State.Idle) {
                scope.launch { YtDownloads.download(deps, videoId, title) }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            YtDownloads.State.Running -> CircularProgressIndicator(
                Modifier.size(size * 0.55f), strokeWidth = 2.dp, color = if (onImage) Color.White else Nflix.Red,
            )
            YtDownloads.State.Done -> Icon(
                Icons.Rounded.CheckCircle, "Déjà dans la bibliothèque",
                tint = if (onImage) Color.White else Nflix.Success, modifier = Modifier.size(size * 0.62f),
            )
            YtDownloads.State.Idle -> Icon(
                Icons.Rounded.Download, "Télécharger dans Nflix",
                tint = if (onImage) Color.White else tint, modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

/** "Télécharger tout" row used at the top of an online album or artist page. */
@Composable
fun YtDownloadAllButton(
    tracks: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    label: String = "Tout télécharger",
) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    if (tracks.isEmpty()) return
    val pending = tracks.count { YtDownloads.state(it.first) == YtDownloads.State.Idle }
    Row(
        modifier
            .clip(CircleShape)
            .background(if (pending > 0) Nflix.SurfaceHigh else Nflix.Surface)
            .clickable(enabled = pending > 0) { scope.launch { YtDownloads.downloadAll(deps, tracks) } }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (pending == 0) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null,
            tint = if (pending == 0) Nflix.Success else Nflix.Red, modifier = Modifier.size(18.dp),
        )
        Text(
            if (pending == 0) "Tous téléchargés" else "$label ($pending)",
            style = MaterialTheme.typography.labelLarge,
            color = Nflix.Text,
        )
    }
}
