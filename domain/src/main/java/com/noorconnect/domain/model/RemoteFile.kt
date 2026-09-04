package com.noorconnect.domain.model

/**
 * A TDLib file's current local state. [localPath] is null until TDLib has actually downloaded
 * the bytes — checking [isDownloaded] before touching [localPath] is required, not optional.
 * Used for message photos (see [MessagePhoto]) and, later, anything else file-backed (voice
 * notes, documents) that needs the same "don't auto-download in a channel/group" treatment.
 */
data class RemoteFile(
    val fileId: Int,
    val localPath: String?,
    val isDownloaded: Boolean,
    val downloadedSize: Long = 0L,
    val expectedSize: Long = 0L,
)
