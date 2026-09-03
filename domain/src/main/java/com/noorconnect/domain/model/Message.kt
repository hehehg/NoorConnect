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

/** A message's generic file attachment (TdApi.MessageDocument) — anything that isn't a photo,
 *  audio, or video (PDFs, zips, etc.). Backs the "الملفات" search tab. */
data class MessageDocument(
    val fileId: Int,
    val fileName: String,
    val mimeType: String,
)

/** A message's audio attachment (TdApi.MessageAudio) — music/voice files with metadata, as
 *  opposed to a plain document. Backs the "الصوتيات" search tab. */
data class MessageAudio(
    val fileId: Int,
    val title: String,
    val performer: String,
    val durationSeconds: Int,
)

/** A message's video attachment (TdApi.MessageVideo). Backs the "الفيديوهات" search tab. */
data class MessageVideo(
    val fileId: Int,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
)

data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val photo: MessagePhoto? = null,
    val document: MessageDocument? = null,
    val audio: MessageAudio? = null,
    val video: MessageVideo? = null,
)
