@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.neb.ians.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Waiting, in one shape.
//
// Expressive's LoadingIndicator is a sequence of MaterialShapes morphing into
// one another, which reads as the same drawing as the rest of the app; a
// circular arc reads as the platform's. Every wait in the app goes through
// here so that they are the same wait, at one of four sizes: inside a button,
// inside a row, inside a card, or holding a whole screen.
// ---------------------------------------------------------------------------

enum class NebLoaderSize(val diameter: Dp) {
    Inline(20.dp),
    Small(26.dp),
    Standard(36.dp),
    Screen(48.dp)
}

@Composable
fun NebLoader(
    modifier: Modifier = Modifier,
    size: NebLoaderSize = NebLoaderSize.Standard,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    LoadingIndicator(
        modifier = modifier.size(size.diameter),
        color = color
    )
}


