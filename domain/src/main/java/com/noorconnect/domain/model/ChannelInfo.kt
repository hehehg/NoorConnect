package com.noorconnect.domain.model

data class ChannelInfo(
    val chatId: Long,
    val title: String,
    val username: String?,
    val editableUsername: String?,
    val link: String?,
    val description: String?,
    val subscriberCount: Int?,
    val photoFileId: Int?,
    val canManage: Boolean,
    val canEditUsername: Boolean,
)