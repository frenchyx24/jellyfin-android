package org.jellyfin.mobile.nflix.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.jellyfin.mobile.BuildConfig

/** Trailer requested from any screen; [TrailerHost] shows it full screen inside Nflix. */
object TrailerSource {
    data class Request(val videoId: String, val title: String)

    var current by mutableStateOf<Request?>(null)
        private set

    fun show(videoId: String, title: String) {
        current = Request(videoId, title)
    }

    fun close() {
        current = null
    }

    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    /** Video id from youtube.com/watch?v=, youtu.be/, /embed/ or /shorts/ links; null for anything else. */
    fun youtubeId(url: String): String? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val id = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> when (uri.pathSegments.firstOrNull()) {
                "watch" -> uri.getQueryParameter("v")
                "embed", "shorts", "v" -> uri.pathSegments.getOrNull(1)
                else -> uri.getQueryParameter("v")
            }
            else -> null
        }
        return id?.takeIf { idPattern.matches(it) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Full-screen landscape trailer player drawn over the app. */
@Composable
fun TrailerHost() {
    val request = TrailerSource.current
    AnimatedVisibility(request != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
        val current = request ?: return@AnimatedVisibility
        TrailerPlayer(current)
    }
}

@SuppressLint("SetJavaScriptEnabled", "SourceLockedOrientationActivity")
@Composable
private fun TrailerPlayer(request: TrailerSource.Request) {
    val context = LocalContext.current
    BackHandler { TrailerSource.close() }
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    // Stay inside Nflix: links to youtube.com (logo, "watch on YouTube") are ignored.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.isForMainFrame
                    }
                    loadDataWithBaseURL(BuildConfig.NFLIX_SERVER_URL.trimEnd('/') + "/", embedHtml(request.videoId), "text/html", "utf-8", null)
                }
            },
            onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = { TrailerSource.close() },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
        ) { Icon(Icons.Rounded.Close, "Fermer la bande-annonce", tint = Color.White) }
    }
}

private fun embedHtml(videoId: String): String {
    val origin = BuildConfig.NFLIX_SERVER_URL.trimEnd('/')
    val src = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&fs=0&cc_load_policy=0&origin=$origin"
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
        </head><body><iframe src="$src" allow="autoplay; encrypted-media; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>
    """.trimIndent()
}
