package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ToastKind { Info, Success, Warning }

/** One small in-app message: a pill at the top of the screen that never blocks the content under it. */
data class ToastMessage(
    val text: String,
    val kind: ToastKind = ToastKind.Info,
    val title: String? = null,
    val actionLabel: String? = null,
    val durationMs: Long = 2600,
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/** Global, main-thread toast queue: messages show one after the other, each disappears by itself. */
object Toasts {
    private val queue = ArrayDeque<ToastMessage>()
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, kind: ToastKind = ToastKind.Info) = show(ToastMessage(text, kind))

    fun show(message: ToastMessage) {
        if (current?.text == message.text || queue.any { it.text == message.text }) return
        if (current == null) current = message else if (queue.size < 4) queue.addLast(message)
    }

    /** Hides [message] (no-op when another one is already shown) and shows the next queued one. */
    fun hide(message: ToastMessage) {
        if (current?.id != message.id) return
        current = queue.removeFirstOrNull()
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val message = Toasts.current
    var shown by remember { mutableStateOf<ToastMessage?>(null) }
    if (message != null) shown = message
    LaunchedEffect(message?.id) {
        val active = message ?: return@LaunchedEffect
        delay(active.durationMs)
        Toasts.hide(active)
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        ) {
            shown?.let { toast -> ToastPill(toast) }
        }
    }
}

@Composable
private fun ToastPill(toast: ToastMessage) {
    val (icon, tint) = toastStyle(toast.kind)
    val shape = if (toast.title != null) RoundedCornerShape(18.dp) else CircleShape
    Row(
        Modifier
            .widthIn(max = 420.dp)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(Color(0xF2202024))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .pointerInput(toast.id) {
                detectVerticalDragGestures { _, drag -> if (drag < -6f) { toast.onDismiss?.invoke(); Toasts.hide(toast) } }
            }
            .clickable { toast.onAction?.invoke() ?: toast.onDismiss?.invoke(); Toasts.hide(toast) }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            toast.title?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                toast.text, style = MaterialTheme.typography.bodySmall, color = if (toast.title != null) Nflix.Muted else Nflix.Text,
                maxLines = if (toast.title != null) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
        }
        toast.actionLabel?.let { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = Nflix.Red, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { toast.onAction?.invoke(); Toasts.hide(toast) }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
        Icon(
            Icons.Rounded.Close, "Fermer", tint = Nflix.Muted,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable { toast.onDismiss?.invoke(); Toasts.hide(toast) }.padding(6.dp),
        )
    }
}

private fun toastStyle(kind: ToastKind): Pair<ImageVector, Color> = when (kind) {
    ToastKind.Info -> Icons.Rounded.Info to Nflix.Info
    ToastKind.Success -> Icons.Rounded.CheckCircle to Nflix.Success
    ToastKind.Warning -> Icons.Rounded.WarningAmber to Nflix.Warning
}
