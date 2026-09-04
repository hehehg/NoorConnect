package com.noorconnect.data.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.core.tdlib.TdLibManager
import com.noorconnect.domain.repository.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

private data class CachedUser(val displayName: String, val username: String?, val photoFileId: Int?)

/**
 * @Singleton so the cache is shared app-wide, not rebuilt per screen — a user resolved once
 * while reading one chat is already known the next time the same sender appears anywhere.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val tdLib: TdLibManager,
) : UserRepository {

    private val cache = mutableMapOf<Long, CachedUser>()
    private val mutex = Mutex() // guards the plain HashMap against concurrent lookups racing

    override suspend fun getDisplayName(userId: Long): AppResult<String> =
        resolve(userId).let { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.data.displayName)
                is AppResult.Failure -> result
                is AppResult.Loading -> AppResult.Loading
            }
        }

    override suspend fun getUsername(userId: Long): AppResult<String?> =
        resolve(userId).let { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.data.username)
                is AppResult.Failure -> result
                is AppResult.Loading -> AppResult.Loading
            }
        }

    override suspend fun getProfilePhotoFileId(userId: Long): AppResult<Int?> =
        resolve(userId).let { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.data.photoFileId)
                is AppResult.Failure -> result
                is AppResult.Loading -> AppResult.Loading
            }
        }

    private suspend fun resolve(userId: Long): AppResult<CachedUser> {
        mutex.withLock { cache[userId] }?.let { return AppResult.Success(it) }

        return when (val result = tdLib.send(TdApi.GetUser(userId))) {
            is AppResult.Success -> {
                val name = listOfNotNull(
                    result.data.firstName.takeIf { it.isNotBlank() },
                    result.data.lastName.takeIf { it.isNotBlank() },
                ).joinToString(" ").ifBlank { "مستخدم" }
                val username = result.data.usernames?.activeUsernames?.firstOrNull()
                // .small is the low-res version, same choice as ChatMapper makes for chat
                // photos — an avatar never needs the full-resolution .big version.
                val cached = CachedUser(displayName = name, username = username, photoFileId = result.data.profilePhoto?.small?.id)
                mutex.withLock { cache[userId] = cached }
                AppResult.Success(cached)
            }
            is AppResult.Failure -> result
            is AppResult.Loading -> AppResult.Loading
        }
    }
}
