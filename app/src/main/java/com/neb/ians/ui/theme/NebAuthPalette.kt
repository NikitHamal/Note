package com.neb.ians.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// The palette the sign-in and onboarding journey is set in.
//
// The app proper runs on MaterialTheme. The journey does not: it is the only
// place where every surface, hairline and illustration has to agree pixel for
// pixel, so it carries its own flat palette instead of reaching into the
// colour scheme. Three rules hold it together.
//
//   Paper is the page. The brand blue is the single voice of action — the
//   primary pill, a link, a chosen option, a filled code box. And every
//   illustration is drawn from `artTones`, which is a strictly neutral
//   ink-on-paper ramp with no hue in it at all.
//
// That last rule is deliberate and load-bearing. The art is line drawing: it
// reads as something drawn by hand in ink, and tinting it blue would turn it
// into UI. So the blue lives in `brand`/`accent` and never in `art*`. If you
// are reaching for a colour inside a NebArt composable, reach for `artTones`.
//
// Red survives for errors, because an error that reads as ordinary text is not
// an error.
// ---------------------------------------------------------------------------

@Immutable
data class NebAuthPalette(
    val isDark: Boolean,
    val page: Color,
    val card: Color,
    val field: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    /**
     * The brand blue itself, for the few places that must be the brand rather
     * than merely "the action" — the word NEBians in the welcome headline, the
     * Terms and Privacy links, the splash doodle. Usually equal to [accent];
     * kept separate so that a future restyle of the primary action cannot
     * silently repaint the brand.
     */
    val brand: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val artInk: Color,
    val artLine: Color,
    val artMid: Color,
    val artSoft: Color,
    val artFaint: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successSoft: Color
) {
    /**
     * The art ramp, darkest first, so an illustration can index a tone instead
     * of naming one. Strictly neutral by design — see the note at the top of
     * this file. Nothing in here is ever the brand blue.
     */
    val artTones: List<Color> get() = listOf(artInk, artLine, artMid, artSoft, artFaint)
}

private val LightAuthPalette = NebAuthPalette(
    isDark = false,
    page = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    field = Color(0xFFF4F4F5),
    ink = Color(0xFF0A0A0B),
    inkMuted = Color(0xFF5C5C61),
    inkFaint = Color(0xFF9B9BA1),
    hairline = Color(0xFFE9E9EB),
    hairlineStrong = Color(0xFFD5D5D9),
    accent = Color(0xFF004AC6),
    accentSoft = Color(0xFFEDF0FF),
    onAccent = Color(0xFFFFFFFF),
    brand = Color(0xFF004AC6),
    brandSoft = Color(0xFFEDF0FF),
    onBrand = Color(0xFFFFFFFF),
    artInk = Color(0xFF141416),
    artLine = Color(0xFF6B6B72),
    artMid = Color(0xFFA3A3AA),
    artSoft = Color(0xFFD9D9DE),
    artFaint = Color(0xFFF0F0F2),
    danger = Color(0xFFB3261E),
    dangerSoft = Color(0xFFFBEDEC),
    success = Color(0xFF18181B),
    successSoft = Color(0xFFF1F1F3)
)

private val DarkAuthPalette = NebAuthPalette(
    isDark = true,
    page = Color(0xFF17181D),
    card = Color(0xFF202128),
    field = Color(0xFF1F1F25),
    ink = Color(0xFFE4E1E9),
    inkMuted = Color(0xFFA1A1A8),
    inkFaint = Color(0xFF6E6E75),
    hairline = Color(0xFF2E2F36),
    hairlineStrong = Color(0xFF45464F),
    accent = Color(0xFFB4C5FF),
    accentSoft = Color(0xFF2B3050),
    onAccent = Color(0xFF002D78),
    brand = Color(0xFFB4C5FF),
    brandSoft = Color(0xFF2B3050),
    onBrand = Color(0xFF002D78),
    artInk = Color(0xFFE7E7E9),
    artLine = Color(0xFF9A9AA1),
    artMid = Color(0xFF64646B),
    artSoft = Color(0xFF2E2F36),
    artFaint = Color(0xFF1F1F25),
    danger = Color(0xFFF2716A),
    dangerSoft = Color(0xFF2A1614),
    success = Color(0xFFE7E7E9),
    successSoft = Color(0xFF1C1C1F)
)

val LocalNebAuthPalette = staticCompositionLocalOf { LightAuthPalette }

fun nebAuthPalette(isDark: Boolean): NebAuthPalette =
    if (isDark) DarkAuthPalette else LightAuthPalette

/**
 * The palette for a journey screen. It follows the app theme preference by
 * default (the palette `NEBiansTheme` provides); pass [dark] to force a side.
 */
@Composable
@ReadOnlyComposable
fun rememberNebAuthPalette(dark: Boolean = LocalNebAuthPalette.current.isDark): NebAuthPalette =
    nebAuthPalette(dark)

/**
 * The same palette for journey components reused inside the app proper, where
 * the user's own theme choice is in force. Derived from the Material surface so
 * a screen never has to thread a flag down to a control.
 */
@Composable
@ReadOnlyComposable
fun rememberNebSurfacePalette(): NebAuthPalette =
    nebAuthPalette(MaterialTheme.colorScheme.surface.luminance() < 0.5f)

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

// ---------------------------------------------------------------------------
// Motion — one duration ladder and three curves, so a fade in one step and a
// slide in the next read as the same hand.
// ---------------------------------------------------------------------------

object NebMotion {
    /** How far a step slides in from. Short enough to read as a shift, not a page. */
    const val StepSlideFraction = 0.22f

    /** A pressed control shrinks by this much. */
    const val PressScale = 0.97f
}

// ---------------------------------------------------------------------------
// Geometry — the journey is built from three radii and one hairline.
// ---------------------------------------------------------------------------

object NebAuthTokens {
    val Hairline = 1.dp
    val FieldRadius = 16.dp
    val CardRadius = 20.dp
    val PillRadius = 28.dp
    val ControlHeight = 56.dp
    val FieldHeight = 58.dp
    val PageGutter = 24.dp
    val ArtClearance = 18.dp
    val StackGap = 14.dp
    val SectionGap = 28.dp
}
