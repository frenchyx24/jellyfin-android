package org.jellyfin.mobile.nflix.ui.screens

import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import org.jellyfin.mobile.nflix.ui.rememberAmbientMotionAllowed

/**
 * Muted, looping clip used as a distributor logo. The player exists only while the logo is
 * composed (a lazy row disposes it off-screen), pauses while the app is in the background and
 * shows its first frame when system animations are off.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun LoopingVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val motion = rememberAmbientMotionAllowed()
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player, motion) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        fun update() { player.playWhenReady = motion && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val texture = TextureView(ctx).apply { isOpaque = false }
                addView(texture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                player.setVideoTextureView(texture)
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                    }
                })
            }
        },
    )
}

/** Lottie (JSON) animation used as a distributor logo; still when system animations are off. */
@Composable
internal fun LottieLogo(url: String, modifier: Modifier = Modifier) {
    val motion = rememberAmbientMotionAllowed()
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever, isPlaying = motion)
    LottieAnimation(composition, { progress }, modifier, contentScale = ContentScale.Fit)
}
