package com.noorconnect.domain.model

/**
 * User-configurable moderation rules — this is the state feature:settings edits and
 * IslamicContentFilter reads. Defaults are conservative (closer to CloudVeil's stance)
 * so a fresh install is safe before the user opens Settings at all.
 */
data class ModerationSettings(
    val allowUnverifiedChannels: Boolean = false,
    val allowGroups: Boolean = true,
    val blockedKeywords: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = true,
    val notificationPreview: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val inAppSounds: Boolean = true,
    val autoDownloadPhotos: Boolean = true,
    val autoDownloadVideos: Boolean = false,
    val autoDownloadFiles: Boolean = false,
    val saveToGallery: Boolean = false,
    val autoplayVideos: Boolean = true,
    val autoplayGifs: Boolean = true,
    val sendByEnter: Boolean = false,
    val reduceDataUsage: Boolean = false,
)
