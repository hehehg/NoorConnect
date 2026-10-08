package com.noorconnect.domain.moderation

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ModerationSettings

interface ContentFilter {
    fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean
}

class NoOpContentFilter : ContentFilter {
    override fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean = true
}
