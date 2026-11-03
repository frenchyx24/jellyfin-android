package org.jellyfin.mobile.nflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.nflix.NflixSession
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NflixHttpException(val code: Int, message: String) : IOException(message)

/** Lightweight Jellyfin HTTP client: one OkHttp pool, HTTP cache, JSON decoding off the main thread. */
class NflixApi(cacheDir: File, private val session: NflixSession, private val apiClient: ApiClient) {
    val baseUrl: String = BuildConfig.NFLIX_SERVER_URL.trimEnd('/')
    private val base: HttpUrl = baseUrl.toHttpUrl()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "nflix-http"), 50L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val userId: String get() = session.userId ?: error("No Nflix session")
    val token: String? get() = session.accessToken

    fun authorization(): String {
        val device = apiClient.deviceInfo
        val clientInfo = apiClient.clientInfo
        return AuthorizationHeaderBuilder.buildHeader(
            clientName = clientInfo.name,
            clientVersion = clientInfo.version,
            deviceId = device.id,
            deviceName = device.name.ifBlank { "Android" },
            accessToken = session.accessToken,
        )
    }

    fun url(path: String, query: Map<String, Any?> = emptyMap()): HttpUrl {
        val builder = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is Collection<*> -> if (value.isNotEmpty()) builder.addQueryParameter(key, value.joinToString(","))
                else -> builder.addQueryParameter(key, value.toString())
            }
        }
        return builder.build()
    }

    suspend fun raw(method: String, url: HttpUrl, body: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Authorization", authorization())
            .header("Accept", "application/json")
            .apply {
                val payload = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
                when (method) {
                    "GET" -> get()
                    "DELETE" -> if (payload != null) delete(payload) else delete()
                    else -> method(method, payload ?: ByteArray(0).toRequestBody(null))
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw NflixHttpException(response.code, "HTTP ${response.code} ${url.encodedPath}")
            text
        }
    }

    suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>, query: Map<String, Any?> = emptyMap()): T {
        val text = raw("GET", url(path, query))
        return withContext(Dispatchers.Default) { json.decodeFromString(strategy, text) }
    }

    suspend fun getJson(path: String, query: Map<String, Any?> = emptyMap()): JsonElement {
        val text = raw("GET", url(path, query))
        return withContext(Dispatchers.Default) { json.parseToJsonElement(text.ifBlank { "{}" }) }
    }

    suspend fun send(method: String, path: String, query: Map<String, Any?> = emptyMap(), body: JsonElement? = null): JsonElement? {
        val text = raw(method, url(path, query), body?.toString())
        return if (text.isBlank()) null else withContext(Dispatchers.Default) { runCatching { json.parseToJsonElement(text) }.getOrNull() }
    }

    fun imageUrl(itemId: String?, type: String = "Primary", tag: String? = null, maxWidth: Int = 400, index: Int? = null): String? {
        if (itemId == null) return null
        val path = if (index != null) "Items/$itemId/Images/$type/$index" else "Items/$itemId/Images/$type"
        return url(path, mapOf("tag" to tag, "maxWidth" to maxWidth, "quality" to 90)).toString()
    }

    fun userImageUrl(userId: String, tag: String?): String = url("Users/$userId/Images/Primary", mapOf("tag" to tag, "maxWidth" to 200)).toString()

    fun streamUrl(path: String, query: Map<String, Any?>): String =
        url(path, query + mapOf("ApiKey" to session.accessToken)).toString()
}

/** Tiny TTL cache so back navigation and tab switches render instantly. */
class TtlCache<V : Any>(private val ttlMs: Long) {
    private data class Entry<V>(val value: V, val at: Long)

    private val map = ConcurrentHashMap<String, Entry<V>>()

    fun peek(key: String): V? = map[key]?.value

    fun fresh(key: String): V? = map[key]?.takeIf { System.currentTimeMillis() - it.at < ttlMs }?.value

    fun put(key: String, value: V) {
        map[key] = Entry(value, System.currentTimeMillis())
    }

    fun invalidate(prefix: String = "") {
        if (prefix.isEmpty()) map.clear() else map.keys.removeAll { it.startsWith(prefix) }
    }

    suspend fun getOrLoad(key: String, force: Boolean = false, loader: suspend () -> V): V {
        if (!force) fresh(key)?.let { return it }
        return loader().also { put(key, it) }
    }
}

internal fun JsonElement?.obj(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
