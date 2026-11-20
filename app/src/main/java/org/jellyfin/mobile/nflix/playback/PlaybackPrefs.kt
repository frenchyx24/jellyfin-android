package org.jellyfin.mobile.nflix.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.jellyfin.mobile.nflix.playback.PlaybackMemory.TrackChoice

/**
 * SharedPreferences-backed stores for [PlaybackMemory] plus the comfort switches
 * (AMOLED black, text size, data saver). Init once from the Application.
 */
object PlaybackPrefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("nflix_playback", Context.MODE_PRIVATE)
    }

    private fun keySeries(seriesId: String) = "series:$seriesId"

    val trackStore: PlaybackMemory.TrackStore = object : PlaybackMemory.TrackStore {
        override fun get(seriesId: String): TrackChoice? {
            val raw = prefs.getString(keySeries(seriesId), null) ?: return null
            val parts = raw.split("|")
            if (parts.size != 2) return null
            return TrackChoice(parts[0].ifEmpty { null }, parts[1].ifEmpty { null })
        }

        override fun put(seriesId: String, choice: TrackChoice) {
            prefs.edit { putString(keySeries(seriesId), "${choice.audioLanguage ?: ""}|${choice.subtitleLanguage ?: ""}") }
        }
    }

    val resumeStore: PlaybackMemory.ResumeStore = object : PlaybackMemory.ResumeStore {
        override fun get(itemId: String): Long? = prefs.getLong("resume:$itemId", -1L).takeIf { it >= 0 }
        override fun put(itemId: String, positionMs: Long) { if (positionMs > 0) prefs.edit { putLong("resume:$itemId", positionMs) } }
        override fun clear(itemId: String) { prefs.edit { remove("resume:$itemId") } }
    }

    var doubleTapSeek: PlaybackMemory.DoubleTapSeek
        get() = PlaybackMemory.DoubleTapSeek.fromKey(prefs.getString("double_tap_seek", null))
        set(value) = prefs.edit { putString("double_tap_seek", value.name) }

    var alwaysAsk: Boolean
        get() = prefs.getBoolean("always_ask", true)
        set(value) = prefs.edit { putBoolean("always_ask", value) }

    // --- Comfort ---

    var amoledBlack: Boolean
        get() = prefs.getBoolean("amoled_black", false)
        set(value) = prefs.edit { putBoolean("amoled_black", value) }

    /** 1.0 = normal, up to 1.35. */
    var textScale: Float
        get() = prefs.getFloat("text_scale", 1f)
        set(value) = prefs.edit { putFloat("text_scale", value.coerceIn(0.85f, 1.35f)) }

    var dataSaver: Boolean
        get() = prefs.getBoolean("data_saver", false)
        set(value) = prefs.edit { putBoolean("data_saver", value) }
}
