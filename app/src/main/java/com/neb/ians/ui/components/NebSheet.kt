@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.neb.ians.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visible scrim band between the status bar and the top of a full height sheet. */
private val SheetTopGap = 24.dp
private val SheetHandleAllowance = 26.dp

@Composable
fun NebSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
fun NebModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showClose: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    horizontalPadding: Dp = 22.dp,
    /**
     * Default false: the sheet pads `content` by [horizontalPadding] so nothing can touch the screen edge.
     * Pass true only when the content draws its own edge to edge rows (menu rows, lazy lists) or pads itself.
     */
    fullBleed: Boolean = false,
    /** Space under the content. A sheet that ends in a composer passes 0.dp so the field sits on the bottom edge. */
    bottomGap: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // A sheet never grows under the status bar: keep a visible scrim band above it.
    val density = LocalDensity.current
    val windowH = LocalWindowInfo.current.containerSize.height
    val statusTop = WindowInsets.statusBars.getTop(density)
    val maxSheetContent = with(density) { (windowH - statusTop).toDp() } - SheetTopGap - SheetHandleAllowance
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.neb.ians.ui.theme.nebFloatColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f),
        shape = com.neb.ians.ui.theme.NebShapes.SheetTop,
        dragHandle = { NebSheetHandle() }
    ) {
        NebClearFocusWhenKeyboardHides()
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetContent.coerceAtLeast(240.dp))
                .nebFocusOutOnTap()
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = horizontalPadding, end = if (showClose) 10.dp else horizontalPadding)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showClose) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (fullBleed) {
                content()
            } else {
                Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = horizontalPadding)) { content() }
            }
            if (bottomGap > 0.dp) Spacer(modifier = Modifier.height(bottomGap))
        }
    }
}
