package com.noorconnect.domain.model

import kotlinx.serialization.Serializable

@Serializable
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
