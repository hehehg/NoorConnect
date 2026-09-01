package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChatFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<ChatFolder>>
    suspend fun createFolder(name: String): AppResult<Unit>
    suspend fun renameFolder(folderId: String, newName: String): AppResult<Unit>
    suspend fun deleteFolder(folderId: String): AppResult<Unit>
    suspend fun setChatInFolder(folderId: String, chatId: Long, isMember: Boolean): AppResult<Unit>
}
