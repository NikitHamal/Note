package com.neb.ians.ui.screens.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio of two opaque colours. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * [ink] if it reads on [bg] at [min] (4.5 for text, 3 for icons and glyphs), otherwise the nearest of black or white that does.
 * Every icon or glyph drawn straight on a note colour goes through this, so no look can hide one.
 */
internal fun readableOn(ink: Color, bg: Color, min: Float = 4.5f): Color {
    val solid = ink.compositeOver(bg)
    if (contrastRatio(solid, bg) >= min) return ink
    val light = Color(0xFFFFFFFF)
    val dark = Color(0xFF0B1530)
    return if (contrastRatio(light, bg) >= contrastRatio(dark, bg)) light else dark
}

/** A softened [ink] (disabled, secondary) that still keeps [min] contrast on [bg]. */
internal fun softReadable(ink: Color, bg: Color, alpha: Float, min: Float = 3f): Color {
    val base = readableOn(ink, bg, min)
    val soft = base.copy(alpha = alpha)
    return if (contrastRatio(soft.compositeOver(bg), bg) >= min) soft else base.copy(alpha = 1f).copy(alpha = (alpha + 0.2f).coerceAtMost(1f))
}
