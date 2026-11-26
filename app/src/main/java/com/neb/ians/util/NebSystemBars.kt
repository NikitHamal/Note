package com.neb.ians.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/**
 * One owner of the look of the status and navigation bars. Screens that sit on something other than the app surface
 * (a reader page, a video, the splash, a sign-in page) ask for dark or light icons through [NebSystemBars]; the
 * request on top of the stack wins and, when it leaves, the one below (or the app default) is applied again.
 * Restoring "what it was before" from each screen raced when two screens overlapped during a transition and left
 * dark icons on a dark bar. The app is always edge to edge with transparent bars, so [apply] also puts that back.
 */
object NebBars {
    /** Icons that suit the app surface: dark icons in the light theme. Set by the theme. */
    @Volatile var defaultDarkIcons: Boolean = true
    private val owners = ArrayList<Pair<Any, Boolean>>()

    fun request(token: Any, darkIcons: Boolean, window: Window) {
        owners.removeAll { it.first === token }
        owners.add(token to darkIcons)
        apply(window)
    }

    fun release(token: Any, window: Window) {
        owners.removeAll { it.first === token }
        apply(window)
    }

    /** Edge to edge, transparent bars, icons of the top request. Safe to call at any time. */
    fun apply(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val dark = owners.lastOrNull()?.second ?: defaultDarkIcons
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = dark
        controller.isAppearanceLightNavigationBars = dark
    }
}

/** Says what is behind the bars for the screen that calls it. [darkIcons] is true over a light background. */
@Composable
fun NebSystemBars(darkIcons: Boolean) {
    val activity = LocalContext.current.hostActivity()
    val token = remember { Any() }
    DisposableEffect(activity, darkIcons) {
        val window = activity?.window
        if (window != null) NebBars.request(token, darkIcons, window)
        onDispose { if (window != null) NebBars.release(token, window) }
    }
}

/**
 * Keeps touch targets clear of what sits at the left and right edge: a navigation bar in landscape (three button
 * navigation), a camera cut-out, rounded corners. The top and bottom stay with each screen, which already pads for
 * the status and navigation bars. Uses the insets as if the bars were shown, so the layout does not move when they hide.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.nebSafeSides(): Modifier = this.windowInsetsPadding(
    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
)
