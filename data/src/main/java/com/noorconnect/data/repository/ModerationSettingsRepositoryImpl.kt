package com.noorconnect.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ModerationSettings
import com.noorconnect.domain.repository.ModerationSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.moderationDataStore by preferencesDataStore(name = "moderation_settings")

private object ModerationKeys {
    val ALLOW_UNVERIFIED_CHANNELS = booleanPreferencesKey("allow_unverified_channels")
    val ALLOW_GROUPS = booleanPreferencesKey("allow_groups")
    val BLOCKED_KEYWORDS = stringSetPreferencesKey("blocked_keywords")
}

/**
 * DataStore-backed — survives app restarts, no server round-trip needed. If you ever want
 * these synced across a user's devices, this is the only file that changes (swap DataStore
 * for a remote-backed implementation); ModerationSettingsRepository stays the same interface.
 */
@Singleton
class ModerationSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModerationSettingsRepository {

    override fun observeSettings(): Flow<ModerationSettings> =
        context.moderationDataStore.data.map { prefs ->
            ModerationSettings(
                allowUnverifiedChannels = prefs[ModerationKeys.ALLOW_UNVERIFIED_CHANNELS] ?: false,
                allowGroups = prefs[ModerationKeys.ALLOW_GROUPS] ?: true,
                blockedKeywords = prefs[ModerationKeys.BLOCKED_KEYWORDS] ?: emptySet(),
            )
        }

    override suspend fun updateSettings(settings: ModerationSettings): AppResult<Unit> {
        context.moderationDataStore.edit { prefs ->
            prefs[ModerationKeys.ALLOW_UNVERIFIED_CHANNELS] = settings.allowUnverifiedChannels
            prefs[ModerationKeys.ALLOW_GROUPS] = settings.allowGroups
            prefs[ModerationKeys.BLOCKED_KEYWORDS] = settings.blockedKeywords
        }
        return AppResult.Success(Unit)
    }
}
