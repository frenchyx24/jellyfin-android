package org.jellyfin.mobile.nflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun achievementUrl(base: HttpUrl, path: String): HttpUrl {
    val pathPart = path.substringBefore('?')
    val query = path.substringAfter('?', "")
    return base.newBuilder().addPathSegments(pathPart).apply {
        if (query.isNotEmpty()) encodedQuery(query)
    }.build()
}

class AchievementPluginMissingException : Exception("Le plugin de succès n’est pas installé sur ce serveur.")

class AchievementRepository internal constructor(
    private val userId: () -> String,
    private val request: suspend (String, String) -> String,
    private val json: Json,
) {
    constructor(api: NflixApi) : this(
        { api.userId },
        { method, path -> api.raw(method, achievementUrl(api.baseUrl.toHttpUrl(), path)) },
        api.json,
    )

    suspend fun profile(): AchievementProfile {
        val path = "$ROOT/users/${userId()}"
        val text = try {
            request("GET", "$path/nflix")
        } catch (error: NflixHttpException) {
            if (error.code != 404) throw error
            return upstream(path)
        }
        return withContext(Dispatchers.Default) {
            json.decodeFromString(AchievementProfile.serializer(), text).also {
                check(it.schemaVersion == 1 && it.language == "fr") { "Format des succès non pris en charge." }
            }
        }
    }

    private suspend fun upstream(path: String): AchievementProfile {
        val badgesText: String
        val summaryText: String
        val rankText: String
        val equippedText: String
        val rarityText: String
        try {
            badgesText = request("GET", "$path?lang=fr")
            summaryText = request("GET", "$path/summary")
            rankText = request("GET", "$path/rank")
            equippedText = request("GET", "$path/equipped?lang=fr")
            rarityText = request("GET", "$ROOT/badges/rarity-stats")
        } catch (error: NflixHttpException) {
            if (error.code == 404) throw AchievementPluginMissingException()
            throw error
        }
        return withContext(Dispatchers.Default) {
            val badges = json.decodeFromString(ListSerializer(AchievementBadge.serializer()), badgesText)
            val summary = json.decodeFromString(AchievementSummary.serializer(), summaryText)
            val rank = json.decodeFromString(AchievementRank.serializer(), rankText)
            val equipped = json.decodeFromString(ListSerializer(AchievementBadge.serializer()), equippedText)
            val rarity = json.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), rarityText)
            AchievementProfile(1, "fr", badges, summary, rank, equipped.map { badge -> badges.find { it.id == badge.id } ?: badge }, rarity)
        }
    }

    suspend fun setEquipped(badge: AchievementBadge, equipped: Boolean) {
        require(badge.unlocked) { "Ce badge n’est pas encore débloqué." }
        require(badge.id.isNotBlank() && '/' !in badge.id && '?' !in badge.id && '#' !in badge.id) { "Identifiant de badge invalide." }
        request(if (equipped) "POST" else "DELETE", "$ROOT/users/${userId()}/equipped/${badge.id}")
    }

    private companion object {
        const val ROOT = "Plugins/AchievementBadges"
    }
}
