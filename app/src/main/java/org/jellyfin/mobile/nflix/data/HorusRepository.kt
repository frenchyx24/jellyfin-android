package org.jellyfin.mobile.nflix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class HorusException(message: String) : Exception(message)

/**
 * Horus (Seerr fork with music and books) through the Moonfin plugin proxy:
 * the Jellyfin session is enough, no Horus password is needed (Quick Connect based SSO).
 */
class HorusRepository(private val api: NflixApi) {
    private val cache = TtlCache<HorusPage>(5 * 60_000L)
    private val details = TtlCache<HorusMedia>(5 * 60_000L)
    private val sessionLock = Mutex()
    @Volatile private var sessionReady = false
    @Volatile var enabled: Boolean = true
        private set
    @Volatile var displayName: String = "Horus"
        private set

    private suspend fun ensureSession(userName: String?) = sessionLock.withLock {
        if (sessionReady) return@withLock
        val config = api.getJson("Moonfin/Seerr/Config").obj()
        enabled = (config?.get("Enabled") as? JsonPrimitive)?.booleanOrNull != false
        displayName = (config?.get("DisplayName") as? JsonPrimitive)?.contentOrNull ?: "Horus"
        if (!enabled) throw HorusException("Horus est désactivé sur le serveur.")
        val status = api.getJson("Moonfin/Seerr/Status").obj()
        if ((status?.get("authenticated") as? JsonPrimitive)?.booleanOrNull != true) {
            val login = runCatching {
                api.send("POST", "Moonfin/Seerr/Login", body = buildJsonObject {
                    put("Username", userName.orEmpty())
                    put("AuthType", "quickconnect")
                })
            }.getOrNull().obj()
            if ((login?.get("success") as? JsonPrimitive)?.booleanOrNull != true) throw HorusException("Connexion à Horus impossible pour ce compte.")
        }
        sessionReady = true
    }

    var userName: String? = null

    /** Raw Horus call for features built on top of the repository (admin tools). */
    suspend fun api(method: String, path: String, query: Map<String, Any?> = emptyMap(), body: JsonElement? = null): JsonElement? = call(method, path, query, body)

    private suspend fun call(method: String, path: String, query: Map<String, Any?> = emptyMap(), body: JsonElement? = null): JsonElement? {
        ensureSession(userName)
        return try {
            api.send(method, "Moonfin/Seerr/Api/$path", query, body)
        } catch (e: NflixHttpException) {
            if (e.code == 401) {
                sessionReady = false
                ensureSession(userName)
                api.send(method, "Moonfin/Seerr/Api/$path", query, body)
            } else {
                throw e
            }
        }
    }

    private suspend fun pageOf(key: String, path: String, query: Map<String, Any?>, type: HorusType? = null, force: Boolean = false) =
        cache.getOrLoad(key, force) { HorusParser.page(call("GET", path, query).obj() ?: JsonObject(emptyMap()), type) }

    suspend fun trending(type: HorusType, page: Int = 1): HorusPage = when (type) {
        HorusType.Movie -> pageOf("movies/$page", "discover/movies", mapOf("page" to page, "language" to "fr"), HorusType.Movie)
        HorusType.Tv -> pageOf("tv/$page", "discover/tv", mapOf("page" to page, "language" to "fr"), HorusType.Tv)
        HorusType.Music -> pageOf("music/$page", "music/trending", mapOf("page" to page), HorusType.Music)
        HorusType.Book -> pageOf("book/$page", "book/trending", mapOf("page" to page), HorusType.Book)
        HorusType.Person -> HorusPage(emptyList(), 1, 1)
    }

    suspend fun trendingAll(): HorusPage = pageOf("trending", "discover/trending", mapOf("page" to 1, "language" to "fr"))

    suspend fun upcoming(type: HorusType): HorusPage = when (type) {
        HorusType.Movie -> pageOf("upcoming/movie", "discover/movies/upcoming", mapOf("page" to 1, "language" to "fr"), HorusType.Movie)
        HorusType.Tv -> pageOf("upcoming/tv", "discover/tv/upcoming", mapOf("page" to 1, "language" to "fr"), HorusType.Tv)
        HorusType.Music -> pageOf("upcoming/music", "music/new", mapOf("page" to 1), HorusType.Music)
        HorusType.Book -> pageOf("upcoming/book", "book/popular", mapOf("page" to 1), HorusType.Book)
        HorusType.Person -> HorusPage(emptyList(), 1, 1)
    }

    suspend fun search(query: String, type: HorusType? = null, page: Int = 1): HorusPage = when (type) {
        HorusType.Music -> pageOf("s/music/$query/$page", "music/search", mapOf("query" to query, "page" to page), HorusType.Music)
        HorusType.Book -> pageOf("s/book/$query/$page", "book/search", mapOf("query" to query, "page" to page), HorusType.Book)
        else -> pageOf("s/all/$query/$page", "search", mapOf("query" to query, "page" to page, "language" to "fr")).let { result ->
            if (type == null) result else result.copy(results = result.results.filter { it.type == type })
        }
    }

    /** Searches movies/series, albums and books together. */
    suspend fun searchEverything(query: String): List<HorusMedia> = coroutineScope {
        val parts = listOf(
            async { runCatching { search(query).results.take(12) }.getOrDefault(emptyList()) },
            async { runCatching { search(query, HorusType.Music).results.take(8) }.getOrDefault(emptyList()) },
            async { runCatching { search(query, HorusType.Book).results.take(8) }.getOrDefault(emptyList()) },
        ).awaitAll()
        parts.flatten()
    }

    suspend fun details(type: HorusType, id: String, force: Boolean = false): HorusMedia = details.getOrLoad("${type.api}/$id", force) {
        val path = when (type) {
            HorusType.Movie -> "movie/$id"
            HorusType.Tv -> "tv/$id"
            HorusType.Music -> "music/album/$id"
            HorusType.Book -> "book/$id"
            HorusType.Person -> "person/$id"
        }
        // Horus validates query strings against its OpenAPI spec: album and book routes accept no
        // "language" parameter and answer HTTP 400 when one is sent.
        val query = if (type == HorusType.Music || type == HorusType.Book) emptyMap() else mapOf("language" to "fr")
        HorusParser.media(call("GET", path, query).obj() ?: throw HorusException("Titre introuvable."), type)
            ?: throw HorusException("Titre introuvable.")
    }

    /**
     * Sends a request. Seasons: null = all (series). For books, [alternate] asks for the audiobook;
     * for movies and series it asks for the 4K version.
     */
    suspend fun request(media: HorusMedia, seasons: List<Int>? = null, alternate: Boolean = false) {
        val body = buildJsonObject {
            put("mediaType", media.type.api)
            when (media.type) {
                HorusType.Movie, HorusType.Tv -> put("mediaId", media.id.toIntOrNull() ?: throw HorusException("Identifiant invalide."))
                else -> {
                    put("mediaId", 0)
                    put("externalId", media.id)
                }
            }
            if (media.type == HorusType.Tv) {
                if (seasons == null) put("seasons", "all") else putJsonArray("seasons") { seasons.forEach { add(JsonPrimitive(it)) } }
            }
            put("is4k", alternate)
        }
        try {
            call("POST", "request", body = body)
        } catch (e: NflixHttpException) {
            throw HorusException(
                when (e.code) {
                    403 -> "Votre quota de demandes est atteint ou vous n'avez pas ce droit."
                    409 -> "Ce titre a déjà été demandé."
                    202 -> "Aucune saison à demander."
                    else -> "Demande refusée par Horus (${e.code})."
                },
            )
        }
        details.invalidate("${media.type.api}/${media.id}")
        cachedRequests = null
        requestsAt = 0L
    }

    suspend fun myRequests(force: Boolean = false): List<HorusRequest> {
        cachedRequests?.takeIf { !force && System.currentTimeMillis() - requestsAt < 60_000 }?.let { return it }
        val me = call("GET", "auth/me").obj()
        val userId = (me?.get("id") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: throw HorusException("Impossible d'identifier votre compte Horus.")
        val response = call("GET", "user/$userId/requests", mapOf("take" to 40, "skip" to 0)).obj()
            ?: throw HorusException("Réponse de Horus invalide.")
        if (response["results"] !is JsonArray) throw HorusException("Liste de demandes Horus invalide.")
        val raw = HorusParser.requests(response)
        // Movie and series requests only carry TMDB ids: enrich them with titles and posters
        val enriched = coroutineScope {
            raw.map { request ->
                async {
                    // Movies and series always get TMDB details (backdrop, per-season status and episode counts).
                    if (request.type !in setOf(HorusType.Movie, HorusType.Tv)) request
                    else runCatching {
                        val full = details(request.type, request.media.id)
                        request.copy(media = full.copy(status = request.media.status, status4k = request.media.status4k, jellyfinId = request.media.jellyfinId ?: full.jellyfinId))
                    }.getOrDefault(request)
                }
            }.awaitAll()
        }
        cachedRequests = enriched
        requestsAt = System.currentTimeMillis()
        return enriched
    }

    /** Withdraws one of the user's own requests; Horus only allows it while the request is still pending. */
    suspend fun cancel(request: HorusRequest) {
        try {
            call("DELETE", "request/${request.id}")
        } catch (e: NflixHttpException) {
            throw HorusException(if (e.code == 403) "Horus n'autorise plus l'annulation de cette demande." else "Annulation refusée par Horus (${e.code}).")
        }
        details.invalidate("${request.type.api}/${request.media.id}")
        cachedRequests = null
        requestsAt = 0L
    }

    @Volatile private var cachedRequests: List<HorusRequest>? = null
    @Volatile private var requestsAt = 0L

    @Suppress("unused")
    private fun JsonElement?.array(): JsonArray? = this as? JsonArray
}
