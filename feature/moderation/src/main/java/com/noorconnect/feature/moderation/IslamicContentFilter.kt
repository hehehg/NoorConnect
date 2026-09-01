package com.noorconnect.feature.moderation

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ModerationSettings
import com.noorconnect.domain.moderation.BannedWordMatcher
import com.noorconnect.domain.moderation.ContentFilter
import javax.inject.Inject

/**
 * This is the real product differentiator vs. CloudVeil: rules tuned for an Arabic/Islamic
 * audience instead of a generic "safe messaging" policy, and user-configurable through
 * feature:settings instead of hardcoded like CloudVeil's server-side blocklist.
 *
 * Stateless on purpose — every rule reads only from the ModerationSettings passed in.
 * To add a new rule (e.g. "block groups with fewer than N verified admins"), add another
 * check below; the settings screen and GetChatsUseCase don't need to change.
 */
class IslamicContentFilter @Inject constructor() : ContentFilter {

    override fun isAllowed(chat: Chat, settings: ModerationSettings): Boolean {
        if (chat.isChannel && !settings.allowUnverifiedChannels) return false
        if (chat.isGroup && !chat.isChannel && !settings.allowGroups) return false
        // Routed through BannedWordMatcher (not a raw .contains()) so a chat title can't dodge
        // this the same trivial ways a message text could — see BannedWordMatcher's kdoc for
        // exactly which tricks (diacritics, tatweel, letter-form variants) this now catches.
        if (BannedWordMatcher.containsAny(chat.title, settings.blockedKeywords.toList())) return false
        return true
    }
}
