package com.noorconnect.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val text: String,
    val senderName: String = "",
    val timestamp: Long = 0L,
)
