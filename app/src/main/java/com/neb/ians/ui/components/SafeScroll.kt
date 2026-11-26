package com.neb.ians.ui.components

import android.content.res.Resources
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Gives the child a finite max width when an ancestor passed an infinite one (a scroller above, a wrap-content
 * parent, an intrinsic probe). A horizontal scroller measured with an infinite width throws, which is a crash
 * report we have had; this keeps it a layout that merely scrolls. Never remove it from [nebHorizontalScroll].
 */
fun Modifier.boundedWidth(): Modifier = layout { measurable, constraints ->
    val bounded = if (constraints.hasBoundedWidth) constraints
    else constraints.copy(maxWidth = maxOf(constraints.minWidth, Resources.getSystem().displayMetrics.widthPixels))
    val placeable = measurable.measure(bounded)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The only horizontal scroller the app uses: `horizontalScroll` behind a width guard. */
fun Modifier.nebHorizontalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = boundedWidth().horizontalScroll(state, enabled, flingBehavior, reverseScrolling)

/**
 * Scrolls [listState] to the top ONLY when [filterKey] actually changes (never when returning from a
 * detail screen via Back navigation), and keeps it pinned at index 0 while async filtered data settles.
 */
@androidx.compose.runtime.Composable
fun NebResetScrollOnFilterChange(
    listState: androidx.compose.foundation.lazy.LazyListState,
    filterKey: String,
    isLoading: Boolean = false,
    dataKey: Any? = null,
) {
    var lastFilterKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(filterKey) }
    var pendingReset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(filterKey) {
        if (lastFilterKey != filterKey) {
            lastFilterKey = filterKey
            pendingReset = true
            listState.scrollToItem(0, 0)
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingReset, isLoading, dataKey) {
        if (pendingReset) {
            listState.scrollToItem(0, 0)
            if (!isLoading) {
                pendingReset = false
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun NebResetScrollOnFilterChange(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    filterKey: String,
    isLoading: Boolean = false,
    dataKey: Any? = null,
) {
    var lastFilterKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(filterKey) }
    var pendingReset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(filterKey) {
        if (lastFilterKey != filterKey) {
            lastFilterKey = filterKey
            pendingReset = true
            gridState.scrollToItem(0, 0)
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingReset, isLoading, dataKey) {
        if (pendingReset) {
            gridState.scrollToItem(0, 0)
            if (!isLoading) {
                pendingReset = false
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun NebResetScrollOnFilterChange(
    staggeredState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    filterKey: String,
    isLoading: Boolean = false,
    dataKey: Any? = null,
) {
    var lastFilterKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(filterKey) }
    var pendingReset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(filterKey) {
        if (lastFilterKey != filterKey) {
            lastFilterKey = filterKey
            pendingReset = true
            staggeredState.scrollToItem(0, 0)
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingReset, isLoading, dataKey) {
        if (pendingReset) {
            staggeredState.scrollToItem(0, 0)
            if (!isLoading) {
                pendingReset = false
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun NebResetScrollOnFilterChange(
    scrollState: ScrollState,
    filterKey: String,
) {
    var lastFilterKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(filterKey) }
    androidx.compose.runtime.LaunchedEffect(filterKey) {
        if (lastFilterKey != filterKey) {
            lastFilterKey = filterKey
            scrollState.scrollTo(0)
        }
    }
}
