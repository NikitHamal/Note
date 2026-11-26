package com.neb.ians.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Motion, taken from the theme rather than typed in.
//
// Expressive's motion scheme carries six specs and the only judgement a call
// site has to make is two-dimensional: is the thing moving or is it merely
// changing colour, and how big is it. Spatial specs are underdamped, so
// position, size and shape arrive with a little overshoot. Effects specs are
// critically damped, because an opacity that overshoots is an opacity above one
// and a colour that overshoots is a colour nobody chose.
//
// Speed follows size: fast for a control under the thumb, default for a sheet
// or a card, slow for a whole screen. A slow spring on a toggle feels broken;
// a fast one on a page feels panicked.
//
// All of it collapses to snap when the system animation scale is zero, which is
// how a user says they do not want to watch anything move.
// ---------------------------------------------------------------------------

@Volatile
private var legacyAnimationsReduced: Boolean? = null

/** True when the user has asked the system not to animate anything. Zero Binder IPC on API 26+. */
@Composable
fun nebAnimationsReduced(): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        return !android.animation.ValueAnimator.areAnimatorsEnabled()
    }
    legacyAnimationsReduced?.let { return it }
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        (Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f).also {
            legacyAnimationsReduced = it
        }
    }
}

@Composable
private fun animationsOff(): Boolean = nebAnimationsReduced()

/** Small things that move: a toggle, a chip, a pressed button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> nebFastSpatialSpec(): FiniteAnimationSpec<T> =
    if (animationsOff()) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

/** Partial-screen things that move: a card, a sheet, a step sliding in. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> nebSpatialSpec(): FiniteAnimationSpec<T> =
    if (animationsOff()) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

/** Whole screens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> nebSlowSpatialSpec(): FiniteAnimationSpec<T> =
    if (animationsOff()) snap() else MaterialTheme.motionScheme.slowSpatialSpec()

/** Colour and opacity on a small control. Critically damped; never overshoots. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> nebFastEffectsSpec(): FiniteAnimationSpec<T> =
    if (animationsOff()) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

/** Colour and opacity everywhere else. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> nebEffectsSpec(): FiniteAnimationSpec<T> =
    if (animationsOff()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()
