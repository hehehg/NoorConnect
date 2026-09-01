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
)
