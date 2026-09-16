package com.noorconnect.domain.model

data class ChatSendPermission(
    val canSend: Boolean,
    val reason: String? = null,
)