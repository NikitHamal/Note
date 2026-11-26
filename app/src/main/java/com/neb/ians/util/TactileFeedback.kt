package com.neb.ians.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

enum class TactileType {
    LightTap,
    ButtonTap,
    SelectionChange,
    Success,
    Warning,
    LongPress
}

@androidx.compose.runtime.Stable
class TactileFeedback(
    private val view: View?,
    private val context: Context?
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun perform(type: TactileType = TactileType.LightTap) {
        val viewHandled = performViewHaptic(type)
        if (!viewHandled) {
            performVibratorFallback(type)
        }
    }

    private fun performViewHaptic(type: TactileType): Boolean {
        val targetView = view ?: return false
        if (!targetView.isHapticFeedbackEnabled) {
            targetView.isHapticFeedbackEnabled = true
        }

        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING

        val constant = when (type) {
            TactileType.LightTap -> HapticFeedbackConstants.KEYBOARD_TAP
            TactileType.ButtonTap -> HapticFeedbackConstants.VIRTUAL_KEY
            TactileType.SelectionChange -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    HapticFeedbackConstants.CLOCK_TICK
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
            }
            TactileType.Success -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            TactileType.Warning -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            TactileType.LongPress -> HapticFeedbackConstants.LONG_PRESS
        }

        return try {
            targetView.performHapticFeedback(constant, flags)
        } catch (_: Throwable) {
            false
        }
    }

    private fun performVibratorFallback(type: TactileType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    TactileType.LightTap -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    TactileType.ButtonTap -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    TactileType.SelectionChange -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    TactileType.Success -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    TactileType.Warning -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    TactileType.LongPress -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    TactileType.LightTap -> VibrationEffect.createOneShot(10, 80)
                    TactileType.ButtonTap -> VibrationEffect.createOneShot(18, 140)
                    TactileType.SelectionChange -> VibrationEffect.createOneShot(12, 100)
                    TactileType.Success -> VibrationEffect.createWaveform(
                        longArrayOf(0, 15, 30, 20),
                        intArrayOf(0, 120, 0, 180),
                        -1
                    )
                    TactileType.Warning -> VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 40, 35),
                        intArrayOf(0, 200, 0, 220),
                        -1
                    )
                    TactileType.LongPress -> VibrationEffect.createOneShot(50, 180)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    TactileType.LightTap -> vib.vibrate(10)
                    TactileType.ButtonTap -> vib.vibrate(18)
                    TactileType.SelectionChange -> vib.vibrate(12)
                    TactileType.Success -> vib.vibrate(longArrayOf(0, 15, 30, 20), -1)
                    TactileType.Warning -> vib.vibrate(longArrayOf(0, 30, 40, 35), -1)
                    TactileType.LongPress -> vib.vibrate(50)
                }
            }
        } catch (_: Throwable) {
        }
    }
}

private var cachedTactileView: java.lang.ref.WeakReference<View>? = null
private var cachedTactileInstance: TactileFeedback? = null

@Composable
fun rememberTactileFeedback(): TactileFeedback {
    val view = LocalView.current
    val existing = cachedTactileInstance
    if (existing != null && cachedTactileView?.get() === view) {
        return existing
    }
    val context = LocalContext.current
    return remember(view) {
        val check = cachedTactileInstance
        if (check != null && cachedTactileView?.get() === view) {
            check
        } else {
            TactileFeedback(view, context.applicationContext).also {
                cachedTactileView = java.lang.ref.WeakReference(view)
                cachedTactileInstance = it
            }
        }
    }
}

@Composable
fun Modifier.tactileClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    tactileType: TactileType = TactileType.LightTap,
    onClick: () -> Unit
): Modifier {
    val tactile = rememberTactileFeedback()
    return this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = {
            tactile.perform(tactileType)
            onClick()
        }
    )
}

@Composable
fun Modifier.tactileClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    tactileType: TactileType = TactileType.LightTap,
    onClick: () -> Unit
): Modifier {
    val tactile = rememberTactileFeedback()
    return this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = {
            tactile.perform(tactileType)
            onClick()
        }
    )
}
