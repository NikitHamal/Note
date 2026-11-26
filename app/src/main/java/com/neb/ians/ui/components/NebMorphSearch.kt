package com.neb.ians.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Reusable M3 Expressive morphing title + search bar (shared by Pathways Exams, Notes, Syllabus & Interactive).
 * - At rest: a 44dp search icon button (with an 8dp primary dot indicator when [query] is active and results exist).
 * - When searching: morphs into a full-width search pill growing leftwards over the title.
 * - Auto-collapses back to the icon button with dot indicator when focus/IME leaves and [hasResults] is true.
 * - If [query] is non-blank and [hasResults] is false (0 matches), stays expanded above the empty results state.
 */
@Composable
fun NebMorphTitleSearch(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    openInitially: Boolean = false,
    hasResults: Boolean = true,
    isSearching: Boolean = false,
    titleStyle: TextStyle? = null,
    buttonWidth: Dp = 44.dp,
    buttonHeight: Dp = 44.dp,
    collapsedStartRadius: Dp = 22.dp,
    collapsedEndRadius: Dp = 22.dp,
    expandedRadius: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    trailingTools: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(openInitially) }
    val prog = remember { Animatable(if (expanded) 1f else 0f) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var openedAtMs by remember { mutableLongStateOf(0L) }
    var requestFocusOnOpen by remember { mutableStateOf(openInitially) }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var wasImeVisible by remember { mutableStateOf(false) }

    // If the user dismissed the soft keyboard (via system back/chevron or IME search),
    // auto-collapse when results exist; stay open if 0 results.
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            wasImeVisible = true
        } else if (wasImeVisible) {
            wasImeVisible = false
            val elapsed = android.os.SystemClock.uptimeMillis() - openedAtMs
            if (expanded && elapsed > 320L) {
                focus.clearFocus()
                if (!isSearching && (hasResults || query.isBlank())) {
                    expanded = false
                }
            }
        }
    }

    // When async search completes or filter changes:
    // - If 0 results for a non-blank query, keep/make the search bar expanded so it never disappears on empty results.
    // - If focus already left while waiting for async search and results arrived, auto-collapse to show the dot indicator.
    LaunchedEffect(hasResults, isSearching, query) {
        if (query.isNotBlank() && !isSearching && !hasResults) {
            if (!expanded) {
                requestFocusOnOpen = false
                expanded = true
            }
        } else if (!isSearching && hasResults && expanded && !hadFocus && !imeVisible) {
            val elapsed = android.os.SystemClock.uptimeMillis() - openedAtMs
            if (elapsed > 350L) {
                expanded = false
            }
        }
    }

    LaunchedEffect(expanded) {
        onExpandedChange?.invoke(expanded)
        if (expanded) {
            if (prog.value < 0.08f) prog.snapTo(0.08f)
            prog.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 400f))
        } else {
            hadFocus = false
            prog.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 480f))
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            if (requestFocusOnOpen) {
                openedAtMs = android.os.SystemClock.uptimeMillis()
                withFrameNanos { }
                runCatching { requester.requestFocus() }
                withFrameNanos { }
                if (!hadFocus) runCatching { requester.requestFocus() }
                keyboard?.show()
            }
        } else {
            keyboard?.hide()
        }
    }
    BackHandler(enabled = expanded) {
        if (query.isNotBlank() && !hasResults) {
            onQueryChange("")
        }
        focus.clearFocus()
        expanded = false
    }

    val showField by remember { derivedStateOf { expanded || prog.value > 0.02f } }
    val pillShape = if (collapsedStartRadius == expandedRadius && collapsedEndRadius == expandedRadius) {
        RoundedCornerShape(expandedRadius)
    } else {
        val pVal = prog.value.coerceIn(0f, 1f)
        val startR = lerp(collapsedStartRadius, expandedRadius, pVal)
        val endR = lerp(collapsedEndRadius, expandedRadius, pVal)
        RoundedCornerShape(topStart = startR, bottomStart = startR, topEnd = endR, bottomEnd = endR)
    }

    Row(
        modifier = modifier.fillMaxWidth().height(buttonHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).height(buttonHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                modifier = Modifier.graphicsLayer {
                    val p = prog.value
                    alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    translationX = -14.dp.toPx() * p
                },
                style = titleStyle ?: TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    color = c.onSurface,
                ),
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .layout { m, cons ->
                        val minW = buttonWidth.roundToPx()
                        val maxW = cons.maxWidth.coerceAtLeast(minW)
                        val w = (minW + (maxW - minW) * prog.value).roundToInt().coerceIn(minW, maxW)
                        val h = buttonHeight.roundToPx()
                        val pl = m.measure(Constraints.fixed(w, h))
                        layout(w, h) { pl.place(0, 0) }
                    }
                    .clip(pillShape)
                    .background(containerColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        openedAtMs = android.os.SystemClock.uptimeMillis()
                        requestFocusOnOpen = true
                        if (!expanded) {
                            hadFocus = false
                            expanded = true
                        } else {
                            runCatching { requester.requestFocus() }
                            keyboard?.show()
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(start = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, if (expanded) null else "Search", Modifier.size(22.dp), tint = c.onSurface)
                    if (showField) {
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(start = 10.dp, end = if (query.isEmpty()) 12.dp else 4.dp)
                                .layout { m, cons ->
                                    val minFieldW = 140.dp.roundToPx()
                                    val targetW = cons.maxWidth.coerceAtLeast(minFieldW)
                                    val pl = m.measure(Constraints.fixed(targetW, cons.maxHeight))
                                    layout(cons.maxWidth, cons.maxHeight) { pl.place(0, 0) }
                                }
                                .graphicsLayer { alpha = ((prog.value - 0.25f) / 0.75f).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                enabled = expanded,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            hadFocus = true
                                            if (!expanded) {
                                                openedAtMs = android.os.SystemClock.uptimeMillis()
                                                requestFocusOnOpen = true
                                                expanded = true
                                            }
                                        } else if (hadFocus && expanded) {
                                            val elapsed = android.os.SystemClock.uptimeMillis() - openedAtMs
                                            if (elapsed > 320L) {
                                                hadFocus = false
                                                if (!isSearching && (hasResults || query.isBlank())) {
                                                    expanded = false
                                                }
                                            } else if (requestFocusOnOpen) {
                                                runCatching { requester.requestFocus() }
                                            }
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onSurface, fontSize = 15.sp),
                                cursorBrush = SolidColor(c.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    if (!isSearching && (hasResults || query.isBlank())) {
                                        expanded = false
                                    }
                                }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) {
                                            Text(placeholder, color = c.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(34.dp)
                                    .graphicsLayer { alpha = prog.value }
                                    .clip(RoundedCornerShape(17.dp))
                                    .clickable {
                                        openedAtMs = android.os.SystemClock.uptimeMillis()
                                        onQueryChange("")
                                        if (hadFocus || imeVisible) {
                                            requestFocusOnOpen = true
                                            runCatching { requester.requestFocus() }
                                            keyboard?.show()
                                        } else {
                                            focus.clearFocus()
                                            expanded = false
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Close, "Clear", Modifier.size(18.dp), tint = c.onSurfaceVariant) }
                        }
                    }
                }
                if (query.isNotBlank()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 9.dp)
                            .size(8.dp)
                            .graphicsLayer { alpha = (1f - prog.value * 2f).coerceIn(0f, 1f) }
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.primary),
                    )
                }
            }
        }
        if (trailingTools != null) {
            val showTrailing by remember { derivedStateOf { !expanded && prog.value < 0.85f } }
            Box(
                Modifier
                    .clipToBounds()
                    .layout { m, cons ->
                        val p = prog.value.coerceIn(0f, 1f)
                        if (p >= 0.85f) {
                            layout(0, 0) {}
                        } else {
                            val pl = m.measure(cons.copy(minWidth = 0))
                            val w = (pl.width * (1f - p)).roundToInt().coerceAtLeast(0)
                            layout(w, pl.height) {
                                if (w > 0) {
                                    pl.place(0, 0)
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = prog.value
                        alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                        scaleX = 1f - p * 0.12f
                        scaleY = 1f - p * 0.12f
                    },
            ) {
                if (showTrailing) {
                    trailingTools()
                }
            }
        }
    }
}
