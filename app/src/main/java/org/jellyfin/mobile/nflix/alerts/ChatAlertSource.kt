package org.jellyfin.mobile.nflix.alerts

import kotlinx.serialization.builtins.ListSerializer
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.family.ConversationInfo

/** Unread Family messages become notifications; night hours stay silent when asked. */
class ChatAlertSource(private val api: () -> NflixApi?) : LocalAlertSource {
    override suspend fun poll(token: String, prefs: AlertPrefs): List<NflixAlert> {
        if (!prefs.isEnabled(AlertKind.Chat)) return emptyList()
        if (prefs.quietNights && isQuietHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))) return emptyList()
        val client = api() ?: return emptyList()
        val conversations = runCatching { client.get("NflixFamily/Conversations", ListSerializer(ConversationInfo.serializer())) }.getOrNull() ?: return emptyList()
        return plan(conversations, prefs::chatCursor, prefs::setChatCursor)
    }

    companion object {
        fun isQuietHour(hour: Int) = hour >= 22 || hour < 8

        /** First sight of a conversation only stores the cursor; later unread messages are posted once. */
        fun plan(conversations: List<ConversationInfo>, cursor: (String) -> Long, store: (String, Long) -> Unit): List<NflixAlert> =
            conversations.mapNotNull { c ->
                val last = c.lastMessage ?: return@mapNotNull null
                val seen = cursor(c.id)
                store(c.id, last.id)
                if (seen < 0 || last.id <= seen || last.mine || c.unread == 0) return@mapNotNull null
                val text = if (last.imageId != null) "📷 Photo" else last.text
                val body = if (c.unread > 1) "${c.unread} nouveaux messages · ${last.senderName} : $text" else "${last.senderName} : $text"
                NflixAlert(id = -(0x40000000L + (c.id.hashCode().toLong() and 0x3fffffff)), kind = AlertKind.Chat.wire, title = c.title.ifBlank { "Famille" }, body = body.take(300))
            }.take(5)
    }
}
