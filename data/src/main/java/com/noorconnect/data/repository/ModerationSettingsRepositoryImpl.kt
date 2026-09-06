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
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val NOTIFICATION_PREVIEW = booleanPreferencesKey("notification_preview")
    val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
    val NOTIFICATION_VIBRATION = booleanPreferencesKey("notification_vibration")
    val IN_APP_SOUNDS = booleanPreferencesKey("in_app_sounds")
    val AUTO_DOWNLOAD_PHOTOS = booleanPreferencesKey("auto_download_photos")
    val AUTO_DOWNLOAD_VIDEOS = booleanPreferencesKey("auto_download_videos")
    val AUTO_DOWNLOAD_FILES = booleanPreferencesKey("auto_download_files")
    val SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
    val AUTOPLAY_VIDEOS = booleanPreferencesKey("autoplay_videos")
    val AUTOPLAY_GIFS = booleanPreferencesKey("autoplay_gifs")
    val SEND_BY_ENTER = booleanPreferencesKey("send_by_enter")
    val REDUCE_DATA_USAGE = booleanPreferencesKey("reduce_data_usage")
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
                notificationsEnabled = prefs[ModerationKeys.NOTIFICATIONS_ENABLED] ?: true,
                notificationPreview = prefs[ModerationKeys.NOTIFICATION_PREVIEW] ?: true,
                notificationSound = prefs[ModerationKeys.NOTIFICATION_SOUND] ?: true,
                notificationVibration = prefs[ModerationKeys.NOTIFICATION_VIBRATION] ?: true,
                inAppSounds = prefs[ModerationKeys.IN_APP_SOUNDS] ?: true,
                autoDownloadPhotos = prefs[ModerationKeys.AUTO_DOWNLOAD_PHOTOS] ?: true,
                autoDownloadVideos = prefs[ModerationKeys.AUTO_DOWNLOAD_VIDEOS] ?: false,
                autoDownloadFiles = prefs[ModerationKeys.AUTO_DOWNLOAD_FILES] ?: false,
                saveToGallery = prefs[ModerationKeys.SAVE_TO_GALLERY] ?: false,
                autoplayVideos = prefs[ModerationKeys.AUTOPLAY_VIDEOS] ?: true,
                autoplayGifs = prefs[ModerationKeys.AUTOPLAY_GIFS] ?: true,
                sendByEnter = prefs[ModerationKeys.SEND_BY_ENTER] ?: false,
                reduceDataUsage = prefs[ModerationKeys.REDUCE_DATA_USAGE] ?: false,
            )
        }

    override suspend fun updateSettings(settings: ModerationSettings): AppResult<Unit> {
        context.moderationDataStore.edit { prefs ->
            prefs[ModerationKeys.ALLOW_UNVERIFIED_CHANNELS] = settings.allowUnverifiedChannels
            prefs[ModerationKeys.ALLOW_GROUPS] = settings.allowGroups
            prefs[ModerationKeys.BLOCKED_KEYWORDS] = settings.blockedKeywords
            prefs[ModerationKeys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            prefs[ModerationKeys.NOTIFICATION_PREVIEW] = settings.notificationPreview
            prefs[ModerationKeys.NOTIFICATION_SOUND] = settings.notificationSound
            prefs[ModerationKeys.NOTIFICATION_VIBRATION] = settings.notificationVibration
            prefs[ModerationKeys.IN_APP_SOUNDS] = settings.inAppSounds
            prefs[ModerationKeys.AUTO_DOWNLOAD_PHOTOS] = settings.autoDownloadPhotos
            prefs[ModerationKeys.AUTO_DOWNLOAD_VIDEOS] = settings.autoDownloadVideos
            prefs[ModerationKeys.AUTO_DOWNLOAD_FILES] = settings.autoDownloadFiles
            prefs[ModerationKeys.SAVE_TO_GALLERY] = settings.saveToGallery
            prefs[ModerationKeys.AUTOPLAY_VIDEOS] = settings.autoplayVideos
            prefs[ModerationKeys.AUTOPLAY_GIFS] = settings.autoplayGifs
            prefs[ModerationKeys.SEND_BY_ENTER] = settings.sendByEnter
            prefs[ModerationKeys.REDUCE_DATA_USAGE] = settings.reduceDataUsage
        }
        return AppResult.Success(Unit)
    }
}
