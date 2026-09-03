package com.noorconnect.domain.model

/**
 * One found message, carrying just enough chat context to render a search result row without
 * the UI needing a second lookup. [textPreview] is already redacted by SearchUseCase when
 * [isContentVisible] is false — the UI should never show [Message.text] directly for a search
 * hit, only this field. [isContentVisible] also gates any attachment on [message] (photo,
 * document, audio, video): when false, the UI must show a generic placeholder for the
 * attachment too, the same way [Chat.isContentVisible] gates a chat's photo — a message from an
 * unreviewed/pending chat shouldn't leak its actual file thumbnail or name any more than its text.
 */
data class SearchMessageResult(
    val message: Message,
    val chatId: Long,
    val chatTitle: String,
    val textPreview: String,
    val isContentVisible: Boolean,
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
     * [channels] and [groups] never contain a [ChatModerationStatus.Blacklisted] chat — those
     * are dropped before this is built. A chat that is present but not [Chat.isContentVisible]
     * must be rendered name-only (no photo, no last-message preview) by the UI. A chat is in
     * exactly one of [channels]/[groups], never both (see [Chat.isChannel]/[Chat.isGroup]).
     * [files]/[audio]/[photos]/[videos] follow the same blacklist-drop rule as [messages], via
     * [SearchMessageResult.isContentVisible].
     */
    data class Found(
        val channels: List<Chat>,
        val groups: List<Chat>,
        val messages: List<SearchMessageResult>,
        val files: List<SearchMessageResult> = emptyList(),
        val audio: List<SearchMessageResult> = emptyList(),
        val photos: List<SearchMessageResult> = emptyList(),
        val videos: List<SearchMessageResult> = emptyList(),
    ) : SearchResult() {
        val isEmpty: Boolean
            get() = channels.isEmpty() && groups.isEmpty() && messages.isEmpty() &&
                files.isEmpty() && audio.isEmpty() && photos.isEmpty() && videos.isEmpty()
    }
}
