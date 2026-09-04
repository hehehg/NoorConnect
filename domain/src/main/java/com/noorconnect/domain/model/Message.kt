package com.noorconnect.domain.model

/**
 * A message's photo attachment, if any — just enough to render it: the file id to resolve via
 * [com.noorconnect.domain.usecase.GetFileStateUseCase]/[com.noorconnect.domain.usecase.DownloadFileUseCase],
 * plus the aspect ratio so the UI can reserve the right amount of space before the image (or
 * its "tap to download" placeholder) actually loads.
 */
data class MessagePhoto(
    val fileId: Int,
    val width: Int,
    val height: Int,
)

enum class MessageMediaType {
    TEXT,
    PHOTO,
    VIDEO,
    AUDIO,
    VOICE,
    DOCUMENT,
    UNKNOWN,
}

data class MessageReplyPreview(
    val text: String,
    val senderName: String? = null,
)

data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val replyTo: MessageReplyPreview? = null,
    val mediaType: MessageMediaType = MessageMediaType.TEXT,
    val mediaFileId: Int? = null,
    val mediaMimeType: String? = null,
    val mediaName: String? = null,
    val photo: MessagePhoto? = null,
)
