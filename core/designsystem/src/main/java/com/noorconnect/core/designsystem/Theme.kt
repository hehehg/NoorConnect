package com.noorconnect.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = NoorColors.Green50,
    onPrimary = NoorColors.Neutral99,
    primaryContainer = NoorColors.Green90,
    onPrimaryContainer = NoorColors.Green10,
    secondary = NoorColors.Gold50,
    onSecondary = NoorColors.Neutral10,
    secondaryContainer = NoorColors.Gold90,
    onSecondaryContainer = NoorColors.Green10,
    background = NoorColors.Neutral95,
    onBackground = NoorColors.Neutral10,
    surface = NoorColors.Neutral95,
    onSurface = NoorColors.Neutral10,
    error = NoorColors.Error40,
    errorContainer = NoorColors.Error90,
)

private val DarkColors = darkColorScheme(
    primary = NoorColors.Green80,
    onPrimary = NoorColors.Green20,
    primaryContainer = NoorColors.Green40,
    onPrimaryContainer = NoorColors.Green90,
    secondary = NoorColors.Gold80,
    onSecondary = NoorColors.Neutral10,
    secondaryContainer = NoorColors.Gold40,
    onSecondaryContainer = NoorColors.Gold90,
    background = NoorColors.Green10,
    onBackground = NoorColors.Neutral95,
    surface = NoorColors.Green10,
    onSurface = NoorColors.Neutral95,
    error = NoorColors.Error40,
    errorContainer = NoorColors.Error90,
)

/**
 * App-wide theme + forced RTL. RTL is forced regardless of the device's system locale —
 * this app is Arabic-first by design (see strings.xml), not merely Arabic-supported, so its
 * layout direction shouldn't depend on a device setting the person may never have changed.
 * Every feature module's Composables inherit this automatically through MaterialTheme —
 * no per-screen changes needed anywhere else.
 */
@Composable
fun NoorConnectTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
