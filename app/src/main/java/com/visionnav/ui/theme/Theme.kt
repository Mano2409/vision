package com.visionnav.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VisionColorScheme = darkColorScheme(
    primary = VisionAmberPrimary,
    onPrimary = VisionTextOnAccent,
    primaryContainer = VisionAmberDark,
    onPrimaryContainer = VisionTextHighContrast,
    secondary = VisionCyanVoice,
    onSecondary = VisionTextOnAccent,
    background = VisionPureBlack,
    onBackground = VisionTextHighContrast,
    surface = VisionSurfaceDark,
    onSurface = VisionTextHighContrast,
    surfaceVariant = VisionSurfaceCard,
    onSurfaceVariant = VisionTextMediumContrast,
    error = VisionObstacleCritical,
    onError = VisionTextHighContrast
)

@Composable
fun VisionNavTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VisionPureBlack.toArgb()
            window.navigationBarColor = VisionPureBlack.toArgb()
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = VisionColorScheme,
        typography = VisionTypography,
        content = content
    )
}
