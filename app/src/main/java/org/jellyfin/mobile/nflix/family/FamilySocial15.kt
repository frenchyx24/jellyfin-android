package org.jellyfin.mobile.nflix.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.NflixHttpException

@Serializable
data class ChallengeInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Goal") val goal: Int = 1,
    @SerialName("Progress") val progress: Int = 0,
    @SerialName("Done") val done: Boolean = false,
)

@Serializable
data class NightInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("ItemId") val itemId: String? = null,
    @SerialName("StartUtc") val startUtc: String = "",
    @SerialName("CreatedByName") val createdByName: String = "",
    @SerialName("Mine") val mine: Boolean = false,
    @SerialName("Going") val going: List<String> = emptyList(),
    @SerialName("IAmGoing") val iAmGoing: Boolean = false,
)

@Serializable
data class GroupInfo(@SerialName("Id") val id: String = "", @SerialName("Name") val name: String = "", @SerialName("Members") val members: List<PartyMember> = emptyList())

@Serializable
data class ListInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Visibility") val visibility: String = "private",
    @SerialName("PublicToken") val publicToken: String? = null,
    @SerialName("OwnerName") val ownerName: String = "",
    @SerialName("Mine") val mine: Boolean = false,
    @SerialName("Items") val items: List<String> = emptyList(),
)

@Serializable
data class WallItem(@SerialName("UserName") val userName: String = "", @SerialName("ItemId") val itemId: String = "", @SerialName("Name") val name: String = "", @SerialName("Utc") val utc: String = "")

@Serializable
data class WallInfo(@SerialName("OptedIn") val optedIn: Boolean = false, @SerialName("Entries") val entries: List<WallItem> = emptyList())

/** Links and texts shared between people (unit-tested). */
object SocialLinks {
    fun inviteLink(server: String, token: String) = "${server.trimEnd('/')}/web/#/nflix-family?invite=$token"

    /** Accepts the full link or the bare 32-hex token. */
    fun parseInvite(text: String): String? {
        val t = text.trim().substringAfter("invite=", text.trim()).take(64)
        return t.lowercase().takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun publicListLink(server: String, token: String) = "${server.trimEnd('/')}/web/#/nflix-list?token=$token"

    fun recommendation(title: String, itemId: String, server: String) = "Je te recommande « $title » : ${server.trimEnd('/')}/web/#/details?id=$itemId"
}

/** Original geometric avatars: colours and shapes derived from a seed, no artwork involved. */
object GeneratedAvatar {
    private val palette = listOf(0xFFE50914, 0xFF2D7FF9, 0xFF20C997, 0xFFF59F00, 0xFF845EF7, 0xFFE64980, 0xFF12B886, 0xFFFF6B6B)

    data class Spec(val background: Long, val accent: Long, val shape: Int, val rotation: Int)

    fun spec(seed: String, variant: Int = 0): Spec {
        val h = (seed + "#" + variant).hashCode()
        val bg = palette[(h ushr 1) % palette.size]
        var accent = palette[(h ushr 5) % palette.size]
        if (accent == bg) accent = palette[(palette.indexOf(bg) + 3) % palette.size]
        return Spec(bg, accent, (h ushr 9) % 4, ((h ushr 12) % 8) * 45)
    }
}

/** Plugin 1.5 routes. */
class FamilySocial15Repository(private val api: NflixApi) {
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: NflixHttpException) {
        throw FamilyException(FamilyFormat.errorMessage(e.body, e.code), e.code)
    } catch (e: java.io.IOException) {
        throw FamilyException("Connexion impossible. Vérifiez le réseau.")
    }

    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, e: JsonElement?) = api.json.decodeFromJsonElement(s, e ?: JsonObject(emptyMap()))

    suspend fun sendImage(conversationId: String, bytes: ByteArray): MessageInfo = call {
        decode(MessageInfo.serializer(), api.sendBytes("POST", "NflixFamily/Conversations/$conversationId/Images", bytes, "application/octet-stream"))
    }

    suspend fun image(imageId: String): ByteArray = call { api.getBytes("NflixFamily/Images/$imageId") }

    suspend fun challenges(): List<ChallengeInfo> = runCatching { api.get("NflixFamily/Challenges", ListSerializer(ChallengeInfo.serializer())) }.getOrDefault(emptyList())

    suspend fun nights(): List<NightInfo> = runCatching { api.get("NflixFamily/Nights", ListSerializer(NightInfo.serializer())) }.getOrDefault(emptyList())

    suspend fun planNight(title: String, startUtcIso: String, itemId: String? = null) = call {
        api.send("POST", "NflixFamily/Nights", body = buildJsonObject { put("Title", title); put("StartUtc", startUtcIso); itemId?.let { put("ItemId", it) } })
    }

    suspend fun toggleGoing(id: String) = call { api.send("POST", "NflixFamily/Nights/$id/Going") }

    suspend fun cancelNight(id: String) = call { api.send("DELETE", "NflixFamily/Nights/$id") }

    suspend fun createInviteLink(): String = call {
        api.send("POST", "NflixFamily/InviteLinks")?.jsonObject?.get("Token")?.jsonPrimitive?.content ?: throw FamilyException("Lien indisponible.")
    }

    suspend fun joinByLink(token: String): String = call {
        api.send("POST", "NflixFamily/InviteLinks/$token/Join")?.jsonObject?.get("FamilyName")?.jsonPrimitive?.content.orEmpty()
    }

    suspend fun groups(): List<GroupInfo> = runCatching { api.get("NflixFamily/Groups", ListSerializer(GroupInfo.serializer())) }.getOrDefault(emptyList())

    suspend fun saveGroup(id: String?, name: String, members: List<String>) = call {
        api.send("POST", "NflixFamily/Groups", body = buildJsonObject {
            id?.let { put("Id", it) }; put("Name", name)
            putJsonArray("Members") { members.forEach { add(JsonPrimitive(it)) } }
        })
    }

    suspend fun lists(): List<ListInfo> = runCatching { api.get("NflixFamily/Lists", ListSerializer(ListInfo.serializer())) }.getOrDefault(emptyList())

    suspend fun saveList(id: String?, name: String, visibility: String, items: List<String>): ListInfo = call {
        decode(ListInfo.serializer(), api.send("POST", "NflixFamily/Lists", body = buildJsonObject {
            id?.let { put("Id", it) }; put("Name", name); put("Visibility", visibility)
            putJsonArray("Items") { items.forEach { add(JsonPrimitive(it)) } }
        }))
    }

    suspend fun publicList(token: String): ListInfo = call { api.get("NflixFamily/Lists/Public/$token", ListInfo.serializer()) }

    suspend fun wall(): WallInfo = runCatching { api.get("NflixFamily/Wall", WallInfo.serializer()) }.getOrDefault(WallInfo())

    suspend fun setWall(on: Boolean) = call { api.send("POST", "NflixFamily/Wall/OptIn", mapOf("on" to on)) }

    suspend fun watched(itemId: String, name: String) { runCatching { api.send("POST", "NflixFamily/Wall/Watched", body = buildJsonObject { put("ItemId", itemId); put("Name", name) }) } }
}
