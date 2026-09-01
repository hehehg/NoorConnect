package com.noorconnect.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChatFolder
import com.noorconnect.domain.repository.FolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.folderDataStore by preferencesDataStore(name = "chat_folders")
private val FOLDERS_KEY = stringPreferencesKey("folders_json")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Folders are local-only (per-device), stored as a single JSON-encoded list — there's no
 * server sync concept for them yet (unlike moderation, which is shared via Firestore). If
 * that ever needs to change, this is the only file that changes; the interface stays the same.
 */
@Singleton
class FolderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FolderRepository {

    override fun observeFolders(): Flow<List<ChatFolder>> =
        context.folderDataStore.data.map { prefs -> decode(prefs[FOLDERS_KEY]) }

    override suspend fun createFolder(name: String): AppResult<Unit> = mutate { folders ->
        folders + ChatFolder(id = UUID.randomUUID().toString(), name = name)
    }

    override suspend fun renameFolder(folderId: String, newName: String): AppResult<Unit> = mutate { folders ->
        folders.map { if (it.id == folderId) it.copy(name = newName) else it }
    }

    override suspend fun deleteFolder(folderId: String): AppResult<Unit> = mutate { folders ->
        folders.filterNot { it.id == folderId }
    }

    override suspend fun setChatInFolder(folderId: String, chatId: Long, isMember: Boolean): AppResult<Unit> =
        mutate { folders ->
            folders.map { folder ->
                if (folder.id != folderId) folder
                else folder.copy(chatIds = if (isMember) folder.chatIds + chatId else folder.chatIds - chatId)
            }
        }

    private suspend fun mutate(transform: (List<ChatFolder>) -> List<ChatFolder>): AppResult<Unit> {
        context.folderDataStore.edit { prefs ->
            val current = decode(prefs[FOLDERS_KEY])
            prefs[FOLDERS_KEY] = json.encodeToString(transform(current))
        }
        return AppResult.Success(Unit)
    }

    private fun decode(raw: String?): List<ChatFolder> =
        raw?.let { runCatching { json.decodeFromString<List<ChatFolder>>(it) }.getOrNull() }.orEmpty()
}
