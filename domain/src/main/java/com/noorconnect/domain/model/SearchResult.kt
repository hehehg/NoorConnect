package com.noorconnect.domain.model

/**
 * One found message, carrying just enough chat context to render a search result row without
 * the UI needing a second lookup. [textPreview] is already redacted by SearchUseCase when the
 * owning chat is not [ChatModerationStatus.Whitelisted] — the UI should never show [Message.text]
 * directly for a search hit, only this field.
 */
data class SearchMessageResult(
    val message: Message,
    val chatId: Long,
    val chatTitle: String,
    val textPreview: String,
)

/** Output of [com.noorconnect.domain.usecase.SearchUseCase]. */
sealed class SearchResult {
    /**
     * The query itself matched a banned word — no TDLib search was even performed. [bannedWord]
     * is intentionally NOT included: surfacing which exact word tripped the filter back to the
     * person typing it defeats the point of blocking it in the first place. The UI should show
     * a generic "هذا البحث غير متاح" message, nothing more specific.
     */
    data object QueryBlocked : SearchResult()

    /**
     * [chats] never contains a [ChatModerationStatus.Blacklisted] chat — those are dropped
     * before this is built. A chat that is present but not [Chat.isContentVisible] must be
     * rendered name-only (no photo, no last-message preview) by the UI.
     */
    data class Found(
        val chats: List<Chat>,
        val messages: List<SearchMessageResult>,
        val personalMessages: List<SearchMessageResult> = emptyList(),
    ) : SearchResult()
}
