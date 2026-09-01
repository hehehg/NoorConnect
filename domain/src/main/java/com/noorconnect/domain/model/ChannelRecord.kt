package com.noorconnect.domain.model

data class ChannelRecord(
    val status: ChatModerationStatus,
    val audience: ChannelAudience = ChannelAudience.BOTH,
)
