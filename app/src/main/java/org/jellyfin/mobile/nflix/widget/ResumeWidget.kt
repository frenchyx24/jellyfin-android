package org.jellyfin.mobile.nflix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.nflix.alerts.NflixAlerts
import org.jellyfin.mobile.nflix.playback.PlaybackPrefs

/** Home-screen widget "Reprendre" and the launcher shortcut to the last started title. */
class ResumeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlaybackPrefs.ensure(context)
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun openIntent(context: Context, itemId: String?): Intent =
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { itemId?.let { putExtra(NflixAlerts.EXTRA_OPEN_ITEM, it) } }

        fun label(name: String?) = if (name.isNullOrBlank()) "Ouvrir Nflix" else "Reprendre « $name »"

        private fun views(context: Context): RemoteViews {
            val id = PlaybackPrefs.lastId
            val v = RemoteViews(context.packageName, R.layout.widget_resume)
            v.setTextViewText(R.id.widget_title, label(PlaybackPrefs.lastName))
            v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 7001, openIntent(context, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        /** Called when playback starts: refreshes widget and the "Reprendre" launcher shortcut. */
        fun onTitleStarted(context: Context, id: String, name: String) {
            PlaybackPrefs.rememberLast(id, name)
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, ResumeWidget::class.java)).forEach { manager.updateAppWidget(it, views(context)) }
            }
            runCatching {
                ShortcutManagerCompat.pushDynamicShortcut(
                    context,
                    ShortcutInfoCompat.Builder(context, "resume")
                        .setShortLabel(name.take(24).ifBlank { "Reprendre" })
                        .setLongLabel(label(name).take(40))
                        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                        .setIntent(openIntent(context, id))
                        .build(),
                )
            }
        }
    }
}
