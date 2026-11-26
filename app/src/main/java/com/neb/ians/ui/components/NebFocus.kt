@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.neb.ians.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * App-wide focus standard for text inputs, installed once at the root:
 *  - a tap that nothing else handled (empty space, plain text, card backgrounds) drops focus, which hides the
 *    keyboard and the caret. Taps on buttons, rows and the field itself are consumed lower down and never reach it.
 *  - when the keyboard is dismissed (back gesture / hide arrow) focus is released too, so a stale caret never
 *    keeps blinking in a field that can no longer be typed into.
 */
fun Modifier.nebFocusOutOnTap(): Modifier = composed {
    val focus = LocalFocusManager.current
    pointerInput(focus) { detectTapGestures(onTap = { focus.clearFocus() }) }
}

@Composable
fun NebClearFocusWhenKeyboardHides() {
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (wasVisible && !imeVisible) focus.clearFocus()
        wasVisible = imeVisible
    }
}
