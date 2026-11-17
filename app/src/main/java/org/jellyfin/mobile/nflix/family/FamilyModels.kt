package org.jellyfin.mobile.nflix.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTOs of the Nflix Family server plugin (PascalCase JSON, ids as 32 hex chars). */

@Serializable
data class FamilyMe(
    @SerialName("UserId") val userId: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("IsHead") val isHead: Boolean = false,
    /** True when this session was opened by a profile switch (server truth). */
    @SerialName("IsSwitched") val isSwitched: Boolean = false,
    @SerialName("Family") val family: FamilyInfo? = null,
    @SerialName("Requests") val requests: List<JoinRequestInfo> = emptyList(),
    @SerialName("PendingRequestCount") val pendingRequestCount: Int = 0,
    @SerialName("FriendCount") val friendCount: Int = 0,
    @SerialName("PendingFriendCount") val pendingFriendCount: Int = 0,
    @SerialName("UnreadMessageCount") val unreadMessageCount: Int = 0,
)

@Serializable
data class FamilyInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("AvatarColor") val avatarColor: String? = null,
    @SerialName("Members") val members: List<FamilyMemberInfo> = emptyList(),
)

@Serializable
data class FamilyMemberInfo(
    @SerialName("UserId") val userId: String = "",
    @SerialName("UserName") val userName: String = "",
    @SerialName("DisplayName") val displayName: String = "",
    @SerialName("Role") val role: String = "Member",
    @SerialName("AvatarColor") val avatarColor: String? = null,
    @SerialName("UseUserImage") val useUserImage: Boolean = false,
    @SerialName("HasImage") val hasImage: Boolean = false,
    @SerialName("IsKid") val isKid: Boolean = false,
    @SerialName("HasPin") val hasPin: Boolean = false,
) {
    val isHead: Boolean get() = role == "Head"
}

@Serializable
data class JoinRequestInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Kind") val kind: String = "request",
    @SerialName("State") val state: String = "pending",
    @SerialName("FamilyId") val familyId: String = "",
    @SerialName("FamilyName") val familyName: String = "",
    @SerialName("UserId") val userId: String = "",
    @SerialName("UserName") val userName: String = "",
    @SerialName("CreatedByName") val createdByName: String = "",
    @SerialName("Direction") val direction: String = "incoming",
    @SerialName("CreatedUtc") val createdUtc: String? = null,
)

@Serializable
data class FamilyUser(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("HasImage") val hasImage: Boolean = false,
)

@Serializable
data class FriendInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("UserId") val userId: String = "",
    @SerialName("UserName") val userName: String = "",
    @SerialName("HasImage") val hasImage: Boolean = false,
    @SerialName("State") val state: String = "pending",
    @SerialName("Direction") val direction: String = "incoming",
)

@Serializable
data class FriendsInfo(
    @SerialName("Friends") val friends: List<FriendInfo> = emptyList(),
    @SerialName("Incoming") val incoming: List<FriendInfo> = emptyList(),
    @SerialName("Outgoing") val outgoing: List<FriendInfo> = emptyList(),
    @SerialName("Blocked") val blocked: List<FamilyUser> = emptyList(),
)

@Serializable
data class ConversationInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("Kind") val kind: String = "direct",
    @SerialName("Title") val title: String = "",
    @SerialName("PeerUserId") val peerUserId: String? = null,
    @SerialName("Unread") val unread: Int = 0,
    @SerialName("CanSend") val canSend: Boolean = true,
    @SerialName("LastMessage") val lastMessage: MessageInfo? = null,
)

@Serializable
data class MessageInfo(
    @SerialName("Id") val id: Long = 0,
    @SerialName("ConversationId") val conversationId: String = "",
    @SerialName("SenderId") val senderId: String = "",
    @SerialName("SenderName") val senderName: String = "",
    @SerialName("Mine") val mine: Boolean = false,
    @SerialName("Text") val text: String = "",
    @SerialName("SentUtc") val sentUtc: String? = null,
    @SerialName("Read") val read: Boolean = true,
)

@Serializable
data class ActivityInfo(
    @SerialName("Utc") val utc: String? = null,
    @SerialName("ActorName") val actorName: String = "",
    @SerialName("TargetName") val targetName: String = "",
    @SerialName("Device") val device: String = "",
    @SerialName("Success") val success: Boolean = false,
    @SerialName("Detail") val detail: String = "",
)
