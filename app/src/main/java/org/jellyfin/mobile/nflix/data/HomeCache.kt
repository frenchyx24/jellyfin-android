package org.jellyfin.mobile.nflix.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/** One home row as stored on disk; [style] is the name of the screen's row style. */
@Serializable
data class CachedHomeRow(
    val key: String,
    val title: String,
    val items: List<Item>,
    val style: String,
    val viewId: String? = null,
    val types: String? = null,
)

@Serializable
data class CachedHome(
    val userId: String,
    val savedAt: Long,
    val hero: List<Item>,
    val rows: List<CachedHomeRow>,
)

/**
 * Stale-while-revalidate cache of the home screen: the last hero and rows are shown instantly at launch,
 * then replaced in place by fresh data. Stored per user in the app's private files; cleared on sign-out.
 */
object HomeCache {
    private const val FILE = "nflix_home_cache.json"
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    /** Last snapshot read or written during this process, so tab switches never hit the disk again. */
    @Volatile
    private var memory: CachedHome? = null

    fun peek(userId: String?): CachedHome? = memory?.takeIf { userId != null && it.userId == userId }

    suspend fun read(context: Context, userId: String?): CachedHome? {
        if (userId == null) return null
        peek(userId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE)
            if (!file.exists()) return@withContext null
            runCatching { json.decodeFromString(CachedHome.serializer(), file.readText()) }
                .onFailure { Timber.w(it, "Home cache unreadable, dropped"); file.delete() }
                .getOrNull()
                ?.takeIf { it.userId == userId }
                ?.also { memory = it }
        }
    }

    suspend fun write(context: Context, home: CachedHome) {
        memory = home
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, FILE)
                val tmp = File(context.filesDir, "$FILE.tmp")
                tmp.writeText(json.encodeToString(CachedHome.serializer(), home))
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }.onFailure { Timber.w(it, "Home cache not saved") }
        }
    }

    fun clear(context: Context) {
        memory = null
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
