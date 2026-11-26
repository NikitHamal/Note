package com.neb.ians.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.neb.ians.ui.theme.nebFloatColor
import com.neb.ians.ui.theme.nebFloatLine

/**
 * The one popup menu. White (the web's `--x-float`) with a hairline, 20dp corners, no shadow, no
 * tonal elevation. Use it instead of `DropdownMenu` so menus can never pick up a tinted container.
 */
@Composable
fun NebDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(20.dp),
        containerColor = nebFloatColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, nebFloatLine()),
        content = content
    )
}
