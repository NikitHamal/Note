package com.neb.ians.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The fill of everything that floats above a page: menus, popovers, dialogs, bottom sheets.
 * White in light (the web's `--x-float`), deep graphite (`surfaceContainerLowest`, `#202128`) in dark so
 * tonal tiles (`surfaceContainer` / `surfaceContainerHigh`, `#2B2C33`–`#33343C`) inside sheets have crisp separation.
 */
@Composable
@ReadOnlyComposable
fun nebFloatColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() > 0.5f) Color.White else scheme.surfaceContainerLowest
}

/** The hairline around a floating surface (the web's `--x-float-line`). */
@Composable
@ReadOnlyComposable
fun nebFloatLine(): Color = MaterialTheme.colorScheme.outlineVariant
