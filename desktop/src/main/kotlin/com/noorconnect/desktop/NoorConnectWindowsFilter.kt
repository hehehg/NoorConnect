package com.noorconnect.desktop

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ModerationSettings
import com.noorconnect.domain.moderation.BannedWordMatcher
import com.noorconnect.domain.moderation.ContentFilter

object NoorConnectWindowsFilter : ContentFilter {
    override fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean {
        if (chat.isChannel && !settings.allowUnverifiedChannels) return false
        if (chat.isGroup && !chat.isChannel && !settings.allowGroups) return false
        if (BannedWordMatcher.containsAny(chat.title, settings.blockedKeywords.toList())) return false
        return true
    }
}
