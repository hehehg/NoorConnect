package com.noorconnect.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * The app's identity palette — deep green + gold, echoing the same colors used in the
 * launcher icon (see app/src/main/res/drawable/ic_launcher_foreground.xml) so the icon and
 * the in-app UI read as one consistent brand, not two unrelated choices.
 */
object NoorColors {
    val Green10 = Color(0xFF07110D)
    val Green20 = Color(0xFF0E2118)
    val Green40 = Color(0xFF1D5A3F)
    val Green50 = Color(0xFF0F6E4C) // primary
    val Green80 = Color(0xFF8FD4B6)
    val Green90 = Color(0xFFD3EFE1)

    val Gold40 = Color(0xFFB8902A)
    val Gold50 = Color(0xFFD4AF37) // secondary / accent
    val Gold80 = Color(0xFFE9D18F)
    val Gold90 = Color(0xFFF6EBCC)

    val Neutral10 = Color(0xFF1A1C1B)
    val Neutral95 = Color(0xFFFBF8F1) // warm off-white, not stark white
    val Neutral99 = Color(0xFFFFFFFB)

    val Error40 = Color(0xFFBA1A1A)
    val Error90 = Color(0xFFFFDAD6)

    /**
     * Fixed palette for initials avatars (chat list rows, message sender avatars) — a person or
     * chat maps to one of these by id, so the same sender always gets the same color everywhere
     * it's shown, without every feature module inventing its own palette. Kept muted/desaturated
     * on purpose so an avatar never reads louder than the app's green/gold identity colors.
     */
    val AvatarPalette = listOf(
        Color(0xFF0F6E4C), // green (same as primary)
        Color(0xFFB8902A), // gold
        Color(0xFF5C7CA6), // muted blue
        Color(0xFF8A6A9E), // muted plum
        Color(0xFFB2694F), // muted terracotta
        Color(0xFF4E8B8B), // muted teal
    )
}
