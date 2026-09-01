package com.noorconnect.domain.moderation

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ModerationSettings

/**
 * THE extension point that is the whole point of this project vs. plain Telegram.
 *
 * Deliberately a pure function: (Chat, ModerationSettings) -> Boolean. No injected state,
 * no reading settings itself — GetChatsUseCase combines the live settings Flow from
 * ModerationSettingsRepository with the chats Flow and calls this per chat. That means:
 *  - Changing a setting in feature:settings updates the visible chat list immediately.
 *  - This class is trivially unit-testable with a Chat + a ModerationSettings, no mocks.
 *  - Swapping the whole rule set later means providing a different ContentFilter binding —
 *    nothing in :domain, :data, or the UI screens changes.
 */
interface ContentFilter {
    fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean
}

class NoOpContentFilter : ContentFilter {
    override fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean = true
}
