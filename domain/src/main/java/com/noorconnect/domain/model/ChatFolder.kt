package com.noorconnect.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatFolder(
    val id: String,
    val name: String,
    val chatIds: Set<Long> = emptySet(),
)
