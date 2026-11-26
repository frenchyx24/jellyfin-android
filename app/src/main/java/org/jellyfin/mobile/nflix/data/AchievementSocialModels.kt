package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Lot 4 of the Succès plugin (3.2): family weekly board, duels between friends, reaction feed on
 * unlocks and progress alerts. Every parser is lenient: a missing field never breaks the screen.
 */

@Immutable
data class WeeklyRow(val userId: String, val name: String, val weekXp: Long, val level: Int, val title: String, val rank: Int)

@Immutable
data class Duel(
    val id: String,
    val challengerId: String,
    val challengerName: String,
    val opponentId: String,
    val opponentName: String,
    val kind: String,
    val label: String,
    val target: Int,
    val startsAt: String?,
    val endsAt: String?,
    val status: String,
    val myProgress: Long,
    val theirProgress: Long,
) {
    val progress: Float get() = if (target > 0) (myProgress.toFloat() / target).coerceIn(0f, 1f) else 0f
    val theirProgressRatio: Float get() = if (target > 0) (theirProgress.toFloat() / target).coerceIn(0f, 1f) else 0f
    val pending: Boolean get() = status.equals("pending", true)
    val active: Boolean get() = status.equals("active", true)

    /** French state shown on the card. */
    val statusLabel: String
        get() = when (status.lowercase()) {
            "pending" -> "En attente"
            "active" -> "En cours"
            "won" -> "Gagné"
            "lost" -> "Perdu"
            "draw" -> "Égalité"
            "declined" -> "Refusé"
            "expired" -> "Terminé"
            else -> status
        }

    companion object {
        fun parse(o: JsonObject): Duel? {
            val id = PJ.str(o, "Id") ?: return null
            return Duel(
                id = id,
                challengerId = PJ.str(o, "ChallengerId").orEmpty(),
                challengerName = PJ.str(o, "ChallengerName") ?: "Membre",
                opponentId = PJ.str(o, "OpponentId").orEmpty(),
                opponentName = PJ.str(o, "OpponentName") ?: "Membre",
                kind = PJ.str(o, "Kind") ?: "movies",
                label = PJ.str(o, "Label") ?: "",
                target = PJ.int(o, "Target"),
                startsAt = PJ.str(o, "StartsAt"),
                endsAt = PJ.str(o, "EndsAt"),
                status = PJ.str(o, "Status") ?: "pending",
                myProgress = PJ.long(o, "MyProgress"),
                theirProgress = PJ.long(o, "TheirProgress"),
            )
        }
    }
}

@Immutable
data class UnlockItem(
    val key: String,
    val userId: String,
    val name: String,
    val badgeId: String,
    val title: String,
    val rarity: String?,
    val unlockedAt: String?,
    val reactions: Map<String, Int>,
    val mine: Set<String>,
) {
    companion object {
        fun parse(o: JsonObject): UnlockItem? {
            val key = PJ.str(o, "Key") ?: return null
            val reactions = PJ.field(o, "Reactions") as? JsonObject
            val counts = reactions?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.let { k to it } }?.toMap().orEmpty()
            return UnlockItem(
                key = key,
                userId = PJ.str(o, "UserId").orEmpty(),
                name = PJ.str(o, "Name") ?: "Membre",
                badgeId = PJ.str(o, "BadgeId").orEmpty(),
                title = PJ.str(o, "Title") ?: "Succès",
                rarity = PJ.str(o, "Rarity"),
                unlockedAt = PJ.str(o, "UnlockedAt"),
                reactions = counts,
                mine = PJ.strings(o, "Mine").toSet(),
            )
        }
    }
}

@Immutable
data class ProgressAlert(val kind: String, val title: String, val body: String, val badgeId: String?) {
    companion object {
        fun parse(o: JsonObject): ProgressAlert? {
            val kind = PJ.str(o, "Kind") ?: return null
            return ProgressAlert(kind, PJ.str(o, "Title") ?: "", PJ.str(o, "Body") ?: "", PJ.str(o, "BadgeId"))
        }
    }
}

/** Body of POST duels: one of movies, episodes or hours for [target] over [days] days. */
internal fun duelBody(opponentId: String, kind: String, target: Int, days: Int): JsonObject = buildJsonObject {
    put("OpponentId", opponentId)
    put("Kind", kind)
    put("Target", target.coerceIn(1, 100))
    put("Days", days.coerceIn(1, 14))
}

internal fun reactBody(key: String, emoji: String): JsonObject = buildJsonObject {
    put("Key", key)
    put("Emoji", emoji)
}

internal fun idsParam(ids: Collection<String>): String = ids.map { it.replace("-", "").lowercase() }.filter { it.isNotBlank() }.distinct().take(30).joinToString(",")

internal fun JsonArray?.objects(): List<JsonObject> = this?.filterIsInstance<JsonObject>().orEmpty()
