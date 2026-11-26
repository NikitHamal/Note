package com.neb.ians.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    surfaceTint = md_theme_light_surfaceTint,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceDim = md_theme_light_surfaceDim,
    surfaceBright = md_theme_light_surfaceBright,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
    scrim = md_theme_light_scrim,
    primaryFixed = md_theme_primaryFixed,
    primaryFixedDim = md_theme_primaryFixedDim,
    onPrimaryFixed = md_theme_onPrimaryFixed,
    onPrimaryFixedVariant = md_theme_onPrimaryFixedVariant,
    secondaryFixed = md_theme_secondaryFixed,
    secondaryFixedDim = md_theme_secondaryFixedDim,
    onSecondaryFixed = md_theme_onSecondaryFixed,
    onSecondaryFixedVariant = md_theme_onSecondaryFixedVariant,
    tertiaryFixed = md_theme_tertiaryFixed,
    tertiaryFixedDim = md_theme_tertiaryFixedDim,
    onTertiaryFixed = md_theme_onTertiaryFixed,
    onTertiaryFixedVariant = md_theme_onTertiaryFixedVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    surfaceTint = md_theme_dark_surfaceTint,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceDim = md_theme_dark_surfaceDim,
    surfaceBright = md_theme_dark_surfaceBright,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
    scrim = md_theme_dark_scrim,
    primaryFixed = md_theme_primaryFixed,
    primaryFixedDim = md_theme_primaryFixedDim,
    onPrimaryFixed = md_theme_onPrimaryFixed,
    onPrimaryFixedVariant = md_theme_onPrimaryFixedVariant,
    secondaryFixed = md_theme_secondaryFixed,
    secondaryFixedDim = md_theme_secondaryFixedDim,
    onSecondaryFixed = md_theme_onSecondaryFixed,
    onSecondaryFixedVariant = md_theme_onSecondaryFixedVariant,
    tertiaryFixed = md_theme_tertiaryFixed,
    tertiaryFixedDim = md_theme_tertiaryFixedDim,
    onTertiaryFixed = md_theme_onTertiaryFixed,
    onTertiaryFixedVariant = md_theme_onTertiaryFixedVariant,
)

/**
 * The app runs on Material 3 Expressive and on one graphite scheme.
 *
 * Wallpaper-extracted dynamic colour is deliberately not offered. The whole
 * point of the palette is that there is no hue in it; letting the launcher
 * wallpaper choose a primary would put one back, and a different one on every
 * device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NEBiansTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            com.neb.ians.util.NebBars.defaultDarkIcons = !darkTheme
            activity?.let { act -> com.neb.ians.util.NebBars.apply(act.window) }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = NEBiansTypography,
        shapes = NEBiansShapes,
        content = {
            // press feedback that always keeps a rounded shape (see NebIndication)
            // Two defaults used to be light everywhere outside the sign-in journey:
            // LocalContentColor (a bare Text on a bare Box drew black on graphite)
            // and the auth palette (chips and fields drew white in dark). Both now
            // follow the theme, so a screen that forgets to pick a colour is still legible.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colorScheme.onBackground,
                LocalNebAuthPalette provides nebAuthPalette(darkTheme),
                androidx.compose.foundation.LocalIndication provides NebIndication,
                // our own menu for selected text (Cut, Copy, Paste, Select all)
                content = { com.neb.ians.ui.components.NebTextToolbarProvider(content = content) }
            )
        }
    )
}