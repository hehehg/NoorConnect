package com.noorconnect.domain.model

/** Mirrors the "status" field an admin sets per chat in the moderation backend. */
sealed class ChatModerationStatus {
    data object Whitelisted : ChatModerationStatus()
    data class Blacklisted(val reason: String?) : ChatModerationStatus()
    data class PendingReview(val reason: String?) : ChatModerationStatus()
    /** No admin decision exists yet for this chat — treated the same as PendingReview for access,
     *  but distinct so the use case can push it into the review queue on first sight. */
    data object Unreviewed : ChatModerationStatus()
}
