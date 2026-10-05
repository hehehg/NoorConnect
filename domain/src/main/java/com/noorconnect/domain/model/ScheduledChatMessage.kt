package com.noorconnect.domain.model

data class ScheduledChatMessage(
    val chatId: Long,
    val chatTitle: String,
    val message: Message,
)