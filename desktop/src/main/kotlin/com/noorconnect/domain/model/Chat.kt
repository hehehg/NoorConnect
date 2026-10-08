package com.noorconnect.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Long,
    val title: String,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val moderationStatus: ChatModerationStatus = ChatModerationStatus.Unreviewed,
) {
    val isContentVisible: Boolean
        get() = moderationStatus is ChatModerationStatus.Whitelisted
}
