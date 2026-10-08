package com.noorconnect.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ChatModerationStatus {
    @Serializable
    data object Whitelisted : ChatModerationStatus()

    @Serializable
    data class Blacklisted(val reason: String?) : ChatModerationStatus()

    @Serializable
    data class PendingReview(val reason: String?) : ChatModerationStatus()

    @Serializable
    data object Unreviewed : ChatModerationStatus()
}
