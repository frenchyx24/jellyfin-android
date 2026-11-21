package org.jellyfin.mobile.nflix

import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.mobile.nflix.data.NflixApi

/**
 * App settings saved on the Jellyfin server (DisplayPreferences "nflix-settings") and as
 * exportable JSON. Only comfort settings travel: never tokens, cursors or per-title state.
 */
object SettingsBackup {
    val FILES = listOf("nflix_playback", "nflix_player", "nflix_alerts", "nflix_reading", "nflix_plugins")
    private val SKIP = listOf("resume:", "series:", "chat_", "posted", "last_", "token", "cursor", "pin", "password", "secret", "parental", "session")

    fun keep(key: String) = SKIP.none { key.contains(it, ignoreCase = true) }

    fun export(context: Context): JsonObject = buildJsonObject {
        put("version", JsonPrimitive(1))
        FILES.forEach { file ->
            val values = context.getSharedPreferences(file, Context.MODE_PRIVATE).all.filterKeys(::keep)
            put(file, JsonObject(values.mapNotNull { (k, v) ->
                when (v) {
                    is Boolean -> k to JsonPrimitive(v)
                    is Int -> k to JsonPrimitive("i:$v")
                    is Long -> k to JsonPrimitive("l:$v")
                    is Float -> k to JsonPrimitive("f:$v")
                    is String -> k to JsonPrimitive("s:$v")
                    else -> null
                }
            }.toMap()))
        }
    }

    /** Restores known files and keys only; returns how many values were applied. */
    fun import(context: Context, json: JsonObject): Int {
        var n = 0
        FILES.forEach { file ->
            val values = json[file] as? JsonObject ?: return@forEach
            val edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            values.forEach { (k, el) ->
                if (!keep(k)) return@forEach
                val p = el.jsonPrimitive
                p.booleanOrNull?.let { edit.putBoolean(k, it); n++; return@forEach }
                val raw = p.contentOrNull ?: return@forEach
                val v = raw.drop(2)
                when (raw.take(2)) {
                    "i:" -> v.toIntOrNull()?.let { edit.putInt(k, it); n++ }
                    "l:" -> v.toLongOrNull()?.let { edit.putLong(k, it); n++ }
                    "f:" -> v.toFloatOrNull()?.let { edit.putFloat(k, it); n++ }
                    "s:" -> if (v.length <= 2000) { edit.putString(k, v); n++ }
                }
            }
            edit.apply()
        }
        return n
    }

    suspend fun saveToServer(context: Context, api: NflixApi) {
        api.send("POST", "DisplayPreferences/nflix-settings", mapOf("userId" to api.userId, "client" to "nflix"), buildJsonObject {
            put("Id", JsonPrimitive("nflix-settings"))
            put("Client", JsonPrimitive("nflix"))
            put("CustomPrefs", buildJsonObject { put("settings", JsonPrimitive(export(context).toString())) })
        })
    }

    suspend fun loadFromServer(context: Context, api: NflixApi): Int {
        val el: JsonElement = api.getJson("DisplayPreferences/nflix-settings", mapOf("userId" to api.userId, "client" to "nflix"))
        val raw = el.jsonObject["CustomPrefs"]?.jsonObject?.get("settings")?.jsonPrimitive?.contentOrNull ?: return 0
        return import(context, api.json.parseToJsonElement(raw).jsonObject)
    }
}
