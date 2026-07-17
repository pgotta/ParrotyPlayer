package com.parroty.player.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ParrotyColorScheme = lightColorScheme(
    primary = ParrotyPalette.Spine,
    onPrimary = ParrotyPalette.Paper,
    primaryContainer = ParrotyPalette.SpineDeep,
    onPrimaryContainer = ParrotyPalette.Paper,
    secondary = ParrotyPalette.Gilt,
    onSecondary = ParrotyPalette.Ink,
    background = ParrotyPalette.Paper,
    onBackground = ParrotyPalette.Ink,
    surface = ParrotyPalette.PaperDeep,
    onSurface = ParrotyPalette.Ink,
    surfaceVariant = ParrotyPalette.PaperDeep,
    onSurfaceVariant = ParrotyPalette.InkSoft,
    outline = ParrotyPalette.Rule,
    outlineVariant = ParrotyPalette.Rule,
    error = ParrotyPalette.Spine,
    onError = ParrotyPalette.Paper
)

/**
 * Parroty is a light, paper-coloured design and does not have a dark variant, so
 * this deliberately does not follow the system theme. Flipping the phone to dark
 * mode should not repaint a book interior.
 */
@Composable
fun ParrotyTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ParrotyPalette.Paper.toArgb()
            window.navigationBarColor = ParrotyPalette.Paper.toArgb()
            // Dark icons, because the bars are paper.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = ParrotyColorScheme,
        typography = ParrotyTypography,
        content = content
    )
}
