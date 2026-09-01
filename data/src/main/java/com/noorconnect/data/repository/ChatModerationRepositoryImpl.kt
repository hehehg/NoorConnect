package com.noorconnect.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelAudience
import com.noorconnect.domain.model.ChannelRecord
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.repository.ChatModerationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore schema this repository reads/writes (see README.md "هيكل بيانات Firebase"):
 *
 *   channels/{chatId}          — one document per Telegram chat, chatId as the document id
 *     status:   "whitelist" | "blacklist" | "pending"   (missing entirely = unreviewed)
 *     reason:   string, optional — shown to the person if blacklisted/pending
 *     audience: "male" | "female" | "both"               (missing = "both")
 *
 *   moderation_config/banned_words
 *     words: array<string>
 *
 * This is exactly the shape the admin panel (separate project, not built yet) is expected to
 * read and write — changing field names here means changing them there too.
 */
@Singleton
class ChatModerationRepositoryImpl @Inject constructor() : ChatModerationRepository {

    private val firestore get() = FirebaseFirestore.getInstance()

    override suspend fun getChannelRecord(chatId: Long): AppResult<ChannelRecord> = try {
        val doc = firestore.collection(CHANNELS_COLLECTION).document(chatId.toString()).get().await()
        AppResult.Success(doc.toChannelRecord())
    } catch (e: Exception) {
        AppResult.Failure(-1, e.message ?: "Firestore error")
    }

    override suspend fun flagForReview(chatId: Long, reason: String): AppResult<Unit> = try {
        firestore.collection(CHANNELS_COLLECTION).document(chatId.toString())
            .set(mapOf("status" to "pending", "reason" to reason), SetOptions.merge())
            .await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(-1, e.message ?: "Firestore error")
    }

    override fun observeBannedWords(): Flow<List<String>> = callbackFlow {
        val registration = firestore.collection(MODERATION_CONFIG_COLLECTION).document(BANNED_WORDS_DOC)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val words = (snapshot?.get("words") as? List<Any?>)?.filterIsInstance<String>().orEmpty()
                trySend(words)
            }
        awaitClose { registration.remove() }
    }

    /**
     * One listener on the whole `channels` collection, kept live for as long as anything
     * collects it — GetChatsUseCase and SearchUseCase both need "is this chat blacklisted right
     * now", and a single shared listener here is simpler and cheaper (one Firestore listener,
     * not one per visible chat row) than per-chat listeners would be. Fine at this app's scale;
     * if the collection grows very large this is the one place to revisit (e.g. paging by the
     * chat ids actually on screen instead of the whole collection).
     */
    override fun observeAllChannelRecords(): Flow<Map<Long, ChannelRecord>> = callbackFlow {
        val registration = firestore.collection(CHANNELS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val records = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.id.toLongOrNull()?.let { it to doc.toChannelRecord() } }
                    .toMap()
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toChannelRecord(): ChannelRecord {
        val status = when (getString("status")) {
            "whitelist" -> ChatModerationStatus.Whitelisted
            "blacklist" -> ChatModerationStatus.Blacklisted(getString("reason"))
            "pending" -> ChatModerationStatus.PendingReview(getString("reason"))
            else -> ChatModerationStatus.Unreviewed // covers both "doc doesn't exist" and "no status field"
        }
        val audience = when (getString("audience")) {
            "male" -> ChannelAudience.MALE
            "female" -> ChannelAudience.FEMALE
            else -> ChannelAudience.BOTH
        }
        return ChannelRecord(status, audience)
    }

    companion object {
        private const val CHANNELS_COLLECTION = "channels"
        private const val MODERATION_CONFIG_COLLECTION = "moderation_config"
        private const val BANNED_WORDS_DOC = "banned_words"
    }
}
