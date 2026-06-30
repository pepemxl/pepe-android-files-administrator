package com.pepe.archivosync.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Brand accent exposed to the whole composition (chips, toggles, icons). */
val LocalAccent = staticCompositionLocalOf { AccentColor.Default.color }

private fun lightSchemeFor(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.12f),
    onPrimaryContainer = accent,
    background = AppColors.Background,
    onBackground = AppColors.OnSurface,
    surface = AppColors.Surface,
    onSurface = AppColors.OnSurface,
    surfaceVariant = AppColors.SurfaceAlt,
    onSurfaceVariant = AppColors.OnSurfaceVariant,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
    onError = Color.White,
)

@Composable
fun ArchivoSyncTheme(
    accent: AccentColor = AccentColor.Default,
    content: @Composable () -> Unit,
) {
    // The design is a single light theme; we keep it stable across system mode
    // to preserve the intended look. (Dark support can layer on later.)
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    val scheme = lightSchemeFor(accent.color)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = accent.color.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                accent.color.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalAccent provides accent.color) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
