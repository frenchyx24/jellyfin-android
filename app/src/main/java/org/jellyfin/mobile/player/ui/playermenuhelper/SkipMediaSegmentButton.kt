package org.jellyfin.mobile.player.ui.playermenuhelper

import android.widget.Button
import androidx.core.view.isVisible
import org.jellyfin.sdk.model.api.MediaSegmentDto

class SkipMediaSegmentButton(
    private val skipMediaSegmentButton: Button,
    private val nextEpisodeButton: Button? = null,
    private val onNextEpisode: (() -> Unit)? = null,
    callback: (mediaSegment: MediaSegmentDto?) -> Unit,
) {
    private var mediaSegment: MediaSegmentDto? = null

    init {
        skipMediaSegmentButton.setOnClickListener {
            callback(mediaSegment)
        }
        nextEpisodeButton?.setOnClickListener { onNextEpisode?.invoke() }
    }

    fun showSkipSegmentButton(mediaSegmentDto: MediaSegmentDto, hasNextEpisode: Boolean) {
        mediaSegment = mediaSegmentDto
        skipMediaSegmentButton.isVisible = true
        nextEpisodeButton?.isVisible = hasNextEpisode
    }

    fun hideSkipSegmentButton() {
        mediaSegment = null
        skipMediaSegmentButton.isVisible = false
        nextEpisodeButton?.isVisible = false
    }
}
