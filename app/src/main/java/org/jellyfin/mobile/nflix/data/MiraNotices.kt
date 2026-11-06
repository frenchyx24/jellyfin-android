package org.jellyfin.mobile.nflix.data

import java.net.URI
import java.time.OffsetDateTime

/** Client-side guard for Mira announcements: audience, expiry, id prefix and HTTPS links. */
object MiraNotices {
    const val PREFIX = "mira:"

    private fun normalize(group: String) = group.lowercase().filterNot(Char::isWhitespace)

    fun isHttps(url: String): Boolean = runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrEmpty() } == true

    fun visible(notices: List<Notice>, groups: Set<String>, now: OffsetDateTime = OffsetDateTime.now()): List<Notice> {
        val mine = groups.mapTo(HashSet(), ::normalize)
        return notices
            .filter { it.id.startsWith(PREFIX) && it.id.length > PREFIX.length }
            .filter { notice -> notice.groups.isEmpty() || notice.groups.any { normalize(it) in mine } }
            .filter { notice -> notice.expiresAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }?.isAfter(now) ?: true }
            .map { if (it.actionUrl.isEmpty() || isHttps(it.actionUrl)) it else it.copy(actionUrl = "", actionLabel = "") }
    }
}
