package org.jellyfin.mobile.nflix.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.NflixHttpException
import kotlin.math.abs

/** Server verdict for a kid profile about to play a title. */
@Serializable
data class ParentalVerdict(
    @SerialName("Allowed") val allowed: Boolean = true,
    @SerialName("Reason") val reason: String? = null,
    @SerialName("RemainingMinutes") val remainingMinutes: Int = -1,
)

@Serializable
data class ParentalRulesInfo(
    @SerialName("MaxRating") val maxRating: String? = null,
    @SerialName("FromHour") val fromHour: Int = 0,
    @SerialName("ToHour") val toHour: Int = 24,
    @SerialName("DailyLimitMinutes") val dailyLimitMinutes: Int = 0,
    @SerialName("AllowList") val allowList: List<String> = emptyList(),
)

@Serializable
data class UsageRowInfo(
    @SerialName("UserId") val userId: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("WeekMinutes") val weekMinutes: Int = 0,
    @SerialName("TodayMinutes") val todayMinutes: Int = 0,
    @SerialName("ActiveDays") val activeDays: Int = 0,
)

@Serializable
data class PartyMember(@SerialName("UserId") val userId: String = "", @SerialName("Name") val name: String = "")

@Serializable
data class PartyChatLine(@SerialName("Name") val name: String = "", @SerialName("Text") val text: String = "")

@Serializable
data class PartyInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("HostId") val hostId: String = "",
    @SerialName("HostName") val hostName: String = "",
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("Paused") val paused: Boolean = true,
    @SerialName("Members") val members: List<PartyMember> = emptyList(),
    @SerialName("Chat") val chat: List<PartyChatLine> = emptyList(),
)

/** Pure watch-party sync rules (unit-tested). */
object PartySync {
    const val MAX_DRIFT_MS = 2_000L

    /** Where a follower must seek, or null when it is close enough to the host. */
    fun seekTarget(localMs: Long, party: PartyInfo): Long? {
        val hostMs = party.positionTicks / 10_000
        return if (abs(hostMs - localMs) > MAX_DRIFT_MS) hostMs else null
    }

    fun isHost(party: PartyInfo, myUserId: String) = party.hostId.equals(myUserId.replace("-", ""), ignoreCase = true)
}

/** Usage accounting: whole minutes watched, reported in chunks of at most 10. */
object UsageMeter {
    fun chunks(totalMs: Long): List<Int> {
        var minutes = (totalMs / 60_000).toInt()
        val out = ArrayList<Int>()
        while (minutes > 0) { val c = minOf(10, minutes); out += c; minutes -= c }
        return out
    }
}

/** 1.4 plugin routes: presence, parental control, usage, weekly report, watch party. */
class FamilySocialRepository(private val api: NflixApi) {
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: NflixHttpException) {
        throw FamilyException(FamilyFormat.errorMessage(e.body, e.code), e.code)
    } catch (e: java.io.IOException) {
        throw FamilyException("Connexion impossible. Vérifiez le réseau.")
    }

    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, e: kotlinx.serialization.json.JsonElement?) =
        api.json.decodeFromJsonElement(s, e ?: JsonObject(emptyMap()))

    suspend fun heartbeat(): Map<String, Boolean> = call {
        val e = api.send("POST", "NflixFamily/Presence") as? JsonObject
        val online = e?.get("Online") ?: JsonObject(emptyMap())
        decode(MapSerializer(String.serializer(), Boolean.serializer()), online)
    }

    /** Fails open on network errors: offline playback must still work; the server rules apply online. */
    suspend fun checkPlayback(itemId: String, rating: String?, hour: Int): ParentalVerdict = try {
        api.get("NflixFamily/Parental/Check", ParentalVerdict.serializer(), mapOf("itemId" to itemId, "rating" to rating, "hour" to hour))
    } catch (e: NflixHttpException) {
        if (e.code == 404) ParentalVerdict() else throw FamilyException(FamilyFormat.errorMessage(e.body, e.code), e.code)
    } catch (e: java.io.IOException) {
        ParentalVerdict()
    }

    suspend fun setParental(userId: String, rules: ParentalRulesInfo) = call {
        api.send("POST", "NflixFamily/Members/$userId/Parental", body = buildJsonObject {
            rules.maxRating?.let { put("MaxRating", it) }
            put("FromHour", rules.fromHour)
            put("ToHour", rules.toHour)
            put("DailyLimitMinutes", rules.dailyLimitMinutes)
            putJsonArray("AllowList") { rules.allowList.forEach { add(JsonPrimitive(it)) } }
        })
    }

    suspend fun reportUsage(minutes: Int) { runCatching { api.send("POST", "NflixFamily/Usage", mapOf("minutes" to minutes)) } }

    suspend fun weeklyReport(): List<UsageRowInfo> = call { api.get("NflixFamily/Report/Weekly", ListSerializer(UsageRowInfo.serializer())) }

    suspend fun parties(): List<PartyInfo> = runCatching { api.get("NflixFamily/Parties", ListSerializer(PartyInfo.serializer())) }.getOrDefault(emptyList())

    suspend fun createParty(itemId: String): PartyInfo = call {
        decode(PartyInfo.serializer(), api.send("POST", "NflixFamily/Parties", body = buildJsonObject { put("ItemId", itemId) }))
    }

    suspend fun party(id: String): PartyInfo = call { api.get("NflixFamily/Parties/$id", PartyInfo.serializer()) }

    suspend fun pushState(id: String, positionTicks: Long, paused: Boolean): PartyInfo = call {
        decode(PartyInfo.serializer(), api.send("POST", "NflixFamily/Parties/$id/State", body = buildJsonObject { put("PositionTicks", positionTicks); put("Paused", paused) }))
    }

    suspend fun chat(id: String, text: String): PartyInfo = call {
        decode(PartyInfo.serializer(), api.send("POST", "NflixFamily/Parties/$id/Chat", body = buildJsonObject { put("Text", text) }))
    }

    suspend fun leave(id: String) { runCatching { api.send("POST", "NflixFamily/Parties/$id/Leave") } }
}

/** The watch party the local player is part of, if any (set when starting or joining one). */
object PartySession {
    @Volatile var partyId: String? = null
    @Volatile var myUserId: String = ""
    val last = kotlinx.coroutines.flow.MutableStateFlow<PartyInfo?>(null)

    fun clear() { partyId = null; last.value = null }
}
