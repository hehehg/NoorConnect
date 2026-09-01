package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult

interface UserRepository {
    /** Cached after first lookup — see UserRepositoryImpl. Names don't need to be live-updated
     *  for a chat screen's purposes, just resolved once per sender. */
    suspend fun getDisplayName(userId: Long): AppResult<String>

    /**
     * TDLib file id of the user's small profile photo, or a Success(null) when the user simply
     * has no profile photo set — that's a normal, expected outcome, not a failure. Cached
     * alongside the display name (same TdApi.GetUser call resolves both).
     */
    suspend fun getProfilePhotoFileId(userId: Long): AppResult<Int?>
}
