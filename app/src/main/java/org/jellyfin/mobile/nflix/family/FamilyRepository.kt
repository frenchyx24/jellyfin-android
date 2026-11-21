package org.jellyfin.mobile.nflix.family

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jellyfin.mobile.nflix.NflixLogin
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.NflixHttpException
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/** Raised with a French message ready to show. */
class FamilyException(message: String, val code: Int? = null) : Exception(message)

/** Calls to the Nflix Family plugin. The server checks every right; the app only hides what cannot be used. */
class FamilyRepository(private val api: NflixApi) {
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: NflixHttpException) {
        throw FamilyException(FamilyFormat.errorMessage(e.body, e.code), e.code)
    } catch (e: java.io.IOException) {
        throw FamilyException("Connexion impossible. Vérifiez le réseau.")
    }

    suspend fun me(): FamilyMe = call { api.get("NflixFamily/Me", FamilyMe.serializer()) }.also { FamilyBadges.update(it) }

    suspend fun createFamily(name: String) = call { api.send("POST", "NflixFamily/Families", body = buildJsonObject { put("Name", name) }) }

    suspend fun renameFamily(name: String) = call { api.send("PATCH", "NflixFamily/Families", body = buildJsonObject { put("Name", name) }) }

    suspend fun deleteFamily() = call { api.send("DELETE", "NflixFamily/Families") }

    suspend fun search(query: String): List<FamilyUser> =
        call { api.get("NflixFamily/Search", ListSerializer(FamilyUser.serializer()), mapOf("q" to query)) }

    suspend fun askToJoin(headUserName: String) = call { api.send("POST", "NflixFamily/Requests", body = buildJsonObject { put("HeadUserName", headUserName) }) }

    suspend fun invite(userId: String) = call { api.send("POST", "NflixFamily/Invites", body = buildJsonObject { put("UserId", dashed(userId)) }) }

    suspend fun resolveRequest(id: String, action: String) = call { api.send("POST", "NflixFamily/Requests/$id/$action") }

    suspend fun memberAction(userId: String, action: String) = call { api.send("POST", "NflixFamily/Members/$userId/$action") }

    suspend fun updateProfile(userId: String, displayName: String? = null, color: String? = null, isKid: Boolean? = null, pin: String? = null, clearPin: Boolean = false) = call {
        api.send(
            "PATCH", "NflixFamily/Members/$userId/profile",
            body = buildJsonObject {
                displayName?.let { put("DisplayName", it) }
                color?.let { put("AvatarColor", it) }
                isKid?.let { put("IsKid", it) }
                pin?.let { put("Pin", it) }
                if (clearPin) put("ClearPin", true)
            },
        )
    }

    suspend fun leave() = call { api.send("POST", "NflixFamily/Leave") }

    suspend fun friends(): FriendsInfo = call { api.get("NflixFamily/Friends", FriendsInfo.serializer()) }

    suspend fun addFriend(userId: String) = call { api.send("POST", "NflixFamily/Friends", body = buildJsonObject { put("UserId", dashed(userId)) }) }

    suspend fun friendAction(id: String, action: String) = call { api.send("POST", "NflixFamily/Friends/$id/$action") }

    suspend fun conversations(): List<ConversationInfo> =
        call { api.get("NflixFamily/Conversations", ListSerializer(ConversationInfo.serializer())) }

    suspend fun openConversation(userId: String): ConversationInfo = call {
        val element = api.send("POST", "NflixFamily/Conversations", body = buildJsonObject { put("UserId", dashed(userId)) })
        api.json.decodeFromJsonElement(ConversationInfo.serializer(), element ?: JsonObject(emptyMap()))
    }

    suspend fun messages(conversationId: String, before: Long? = null): List<MessageInfo> =
        call { api.get("NflixFamily/Conversations/${PathId.check(conversationId)}/Messages", ListSerializer(MessageInfo.serializer()), mapOf("before" to before)) }

    suspend fun send(conversationId: String, text: String, replyToId: Long = 0) =
        call { api.send("POST", "NflixFamily/Conversations/${PathId.check(conversationId)}/Messages", body = buildJsonObject {
            put("Text", text)
            if (replyToId != 0L) put("ReplyToId", replyToId)
        }) }

    // 5.8 social: reactions, polls, typing

    private val allowedEmojis = listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE21", "\uD83C\uDF89", "\u2764\uFE0F", "\uD83D\uDE31")

    suspend fun react(conversationId: String, messageId: Long, emoji: String): MessageInfo {
        require(emoji in allowedEmojis) { "Réaction non autorisée" }
        return call {
            val el = api.send("POST", "NflixFamily/Conversations/${PathId.check(conversationId)}/Messages/$messageId/React", body = buildJsonObject { put("Emoji", emoji) })
            api.json.decodeFromJsonElement(MessageInfo.serializer(), el ?: JsonObject(emptyMap()))
        }
    }

    suspend fun polls(conversationId: String): List<PollInfo> =
        call { api.get("NflixFamily/Conversations/${PathId.check(conversationId)}/Polls", ListSerializer(PollInfo.serializer())) }

    suspend fun createPoll(conversationId: String, question: String, options: List<String>, multiple: Boolean): PollInfo =
        call {
            val el = api.send("POST", "NflixFamily/Conversations/${PathId.check(conversationId)}/Polls", body = buildJsonObject {
                put("Question", question)
                putJsonArray("Options") { options.forEach { add(JsonPrimitive(it)) } }
                put("Multiple", multiple)
            })
            api.json.decodeFromJsonElement(PollInfo.serializer(), el ?: JsonObject(emptyMap()))
        }

    suspend fun vote(conversationId: String, pollId: Long, option: Int): PollInfo =
        call {
            val el = api.send("POST", "NflixFamily/Polls/$pollId/Vote?conversationId=${PathId.check(conversationId)}", body = buildJsonObject { put("Option", option) })
            api.json.decodeFromJsonElement(PollInfo.serializer(), el ?: JsonObject(emptyMap()))
        }

    suspend fun typing(conversationId: String): List<String> = call {
        val el = api.send("POST", "NflixFamily/Conversations/${PathId.check(conversationId)}/Typing")
        val arr = (el as? JsonObject)?.get("Typing") as? JsonArray
        arr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }

    suspend fun markRead(conversationId: String) = call { api.send("POST", "NflixFamily/Conversations/${PathId.check(conversationId)}/Read") }

    suspend fun activity(): List<ActivityInfo> = call { api.get("NflixFamily/Activity", ListSerializer(ActivityInfo.serializer())) }

    /**
     * Asks the server for a session of [targetUserId], authorized with [authToken] (always the
     * family head's own token). [deviceId] is the device id the app will use for that profile.
     */
    suspend fun switchTo(targetUserId: String, pin: String?, authToken: String, deviceId: String, deviceName: String, appVersion: String): NflixLogin = call {
        val body = buildJsonObject {
            pin?.let { put("Pin", it) }
            put("DeviceId", deviceId)
            put("DeviceName", deviceName)
            put("App", "Nflix")
            put("AppVersion", appVersion)
        }
        val text = api.rawAs("POST", api.url("NflixFamily/Switch/$targetUserId"), authToken, body.toString())
        val obj = api.json.parseToJsonElement(text).jsonObject
        val token = obj["AccessToken"]?.jsonPrimitive?.contentOrNull
        val user = obj["User"]?.jsonObject
        val rawId = user?.get("Id")?.jsonPrimitive?.contentOrNull
        val uuid = rawId?.toUUIDOrNull()
        if (token.isNullOrBlank() || rawId == null || uuid == null) throw FamilyException("Réponse de Nflix incomplète. Réessayez.")
        NflixLogin(
            userId = uuid,
            rawUserId = rawId,
            userName = user["Name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            accessToken = token,
            serverId = obj["ServerId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** True when [token] is still a valid session (used before switching back to the head). */
    suspend fun tokenValid(token: String): Boolean = try {
        api.rawAs("GET", api.url("Users/Me"), token)
        true
    } catch (e: NflixHttpException) {
        if (e.code == 401) false else throw FamilyException(FamilyFormat.errorMessage(e.body, e.code), e.code)
    }

    suspend fun logoutToken(token: String) {
        runCatching { api.rawAs("POST", api.url("Sessions/Logout"), token) }
    }

    private fun dashed(id: String): String = id.toUUIDOrNull()?.toString() ?: id
}
