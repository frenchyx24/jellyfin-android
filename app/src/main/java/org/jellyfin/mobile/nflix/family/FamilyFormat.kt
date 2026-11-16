package org.jellyfin.mobile.nflix.family

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** French labels for family requests, friends and errors. Pure functions, unit-tested. */
object FamilyFormat {
    /** One line describing a join request or invite from the point of view of the viewer. */
    fun requestTitle(request: JoinRequestInfo): String = when {
        request.kind == "invite" && request.direction == "incoming" -> "${request.createdByName} vous invite dans « ${request.familyName} »"
        request.kind == "invite" -> "Invitation envoyée à ${request.userName}"
        request.direction == "incoming" -> "${request.userName} demande à rejoindre la famille"
        else -> "Demande envoyée à « ${request.familyName} »"
    }

    fun stateLabel(state: String): String = when (state) {
        "pending" -> "En attente"
        "accepted" -> "Acceptée"
        "declined" -> "Refusée"
        "cancelled" -> "Annulée"
        "expired" -> "Expirée"
        else -> state
    }

    /** Actions the viewer may take on a request: the other side decides, the own side may cancel. */
    fun requestActions(request: JoinRequestInfo): List<String> = when {
        request.state != "pending" -> emptyList()
        request.direction == "incoming" -> listOf("accept", "decline")
        else -> listOf("cancel")
    }

    fun actionLabel(action: String): String = when (action) {
        "accept" -> "Accepter"
        "decline" -> "Refuser"
        "cancel" -> "Annuler"
        else -> action
    }

    fun roleLabel(member: FamilyMemberInfo): String = when {
        member.isHead -> "Responsable"
        member.isKid -> "Enfant"
        else -> "Membre"
    }

    /** Badge count shown on the Famille menu entry. */
    fun badgeCount(me: FamilyMe?): Int = me?.let { it.unreadMessageCount + it.pendingRequestCount + it.pendingFriendCount } ?: 0

    fun badgeText(count: Int): String? = when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }

    /** The server's French "Message" field, or a generic text. */
    fun errorMessage(body: String?, code: Int? = null): String {
        val parsed = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject["Message"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
        return parsed?.takeIf { it.isNotBlank() } ?: when (code) {
            401 -> "Session expirée. Reconnectez-vous."
            403 -> "Action non autorisée."
            404 -> "Introuvable."
            429 -> "Trop d’essais. Réessayez plus tard."
            else -> "Nflix ne répond pas. Réessayez."
        }
    }

    /** "14:05" for today, "16/11 14:05" otherwise, in [zone]. Accepts .NET ISO dates (7 fraction digits). */
    fun messageTime(iso: String?, now: Long = System.currentTimeMillis(), zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        if (iso == null || iso.length < 19) return ""
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val date = runCatching { parser.parse(iso.substring(0, 19)) }.getOrNull() ?: return ""
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).apply { timeZone = zone }
        val pattern = if (day.format(date) == day.format(java.util.Date(now))) "HH:mm" else "dd/MM HH:mm"
        return java.text.SimpleDateFormat(pattern, java.util.Locale.FRANCE).apply { timeZone = zone }.format(date)
    }

    fun initials(name: String): String =
        name.trim().split(' ', '-', '_').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
