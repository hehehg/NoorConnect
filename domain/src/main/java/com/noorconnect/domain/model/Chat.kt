package com.noorconnect.domain.model

/**
 * @param photoFileId TDLib file id of the chat's small profile photo, or null when there is no
 *   photo, or when the photo is deliberately withheld by moderation (see [moderationStatus]).
 *   Kept as a raw TDLib file id (not a URL/bitmap) — resolving it into actual bytes is a
 *   per-file TdApi.DownloadFile call, which belongs in the UI/data layer that owns the
 *   download lifecycle, not here.
 * @param moderationStatus the backend (Firestore) verdict for this chat, kept on the model
 *   itself (not looked up separately per screen) so every place that renders a Chat — chat
 *   list, search results — applies the exact same masking rule. Defaults to [ChatModerationStatus.Unreviewed],
 *   never [ChatModerationStatus.Whitelisted]: an unmapped/never-checked chat must never be
 *   treated as safe by default.
 */
data class Chat(
    val id: Long,
    val title: String,
    val lastMessage: Message?,
    val unreadCount: Int,
    val isChannel: Boolean,
    val isGroup: Boolean,
    val photoFileId: Int? = null,
    val moderationStatus: ChatModerationStatus = ChatModerationStatus.Unreviewed,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val order: Long = 0L,
    val isMember: Boolean = false,
    val canSendMessages: Boolean = true,
    val sendRestrictionReason: String? = null,
) {
    /**
     * Single source of truth for "should this chat's photo/content ever be rendered". Used by
     * GetChatsUseCase (chat list) and SearchUseCase (search results) so the rule can't drift
     * between the two screens.
     */
    val isContentVisible: Boolean
        get() = moderationStatus is ChatModerationStatus.Whitelisted
}
