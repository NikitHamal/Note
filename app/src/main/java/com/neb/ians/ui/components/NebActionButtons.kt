@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.neb.ians.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neb.ians.util.TactileType
import com.neb.ians.util.rememberTactileFeedback

// ---------------------------------------------------------------------------
// The app's buttons.
//
// One family, built on Expressive's own Button rather than beside it, so every
// button in the app gets the behaviour the library already knows how to do:
// corners that morph from round at rest to square under the thumb, a height
// scale where the container, its padding, its icon and its label all move
// together, and colours that come from the scheme instead of a call site.
//
// A screen picks a tone and a size. It never picks a colour, a radius or a
// padding, which is why the buttons look like each other across screens that
// were written months apart.
// ---------------------------------------------------------------------------

/**
 * How loud the button is.
 *
 * [Primary] is the one action a screen is for and there should be one of them.
 * [Tonal] is the strong alternative, [Outlined] the reversible one, [Text] the
 * one that only has to be findable. [Danger] is for the action a user cannot
 * take back.
 */
enum class NebButtonTone { Primary, Tonal, Outlined, Text, Danger }

/**
 * How big it is, in Expressive's container heights.
 *
 * Size is a statement about importance, not about how long the word is: a
 * [Hero] button is the thing the screen exists for, [Compact] belongs inside a
 * row of other controls.
 */
@Immutable
enum class NebButtonSize(val height: Dp, val pressedCorner: Dp) {
    Compact(32.dp, 10.dp),
    Small(40.dp, 12.dp),
    Standard(48.dp, 14.dp),
    Hero(56.dp, 18.dp),
    Feature(96.dp, 28.dp)
}

/**
 * Round at rest, squarer under the thumb. Stated here rather than taken from
 * [ButtonDefaults.shapesFor], which hands back a container that draws square
 * at the taller heights.
 */
private fun nebButtonShapes(size: NebButtonSize) = ButtonShapes(
    shape = RoundedCornerShape(percent = 50),
    pressedShape = RoundedCornerShape(size.pressedCorner)
)

/**
 * The button.
 *
 * [loading] replaces the label with Expressive's morphing indicator and takes
 * the click away, so a caller never has to disable and re-enable around its own
 * request.
 */
@Composable
fun NebButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: NebButtonTone = NebButtonTone.Primary,
    size: NebButtonSize = NebButtonSize.Standard,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false
) {
    val height = size.height
    val colors = when (tone) {
        NebButtonTone.Primary -> ButtonDefaults.buttonColors()
        NebButtonTone.Tonal -> ButtonDefaults.filledTonalButtonColors()
        NebButtonTone.Outlined -> ButtonDefaults.outlinedButtonColors()
        NebButtonTone.Text -> ButtonDefaults.textButtonColors()
        NebButtonTone.Danger -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    val border = if (tone == NebButtonTone.Outlined) {
        ButtonDefaults.outlinedButtonBorder(enabled)
    } else null
    val interactionSource = remember { MutableInteractionSource() }
    val tactile = rememberTactileFeedback()
    val iconSize = ButtonDefaults.iconSizeFor(height)

    Button(
        onClick = {
            tactile.perform(TactileType.ButtonTap)
            onClick()
        },
        shapes = nebButtonShapes(size),
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = height),
        enabled = enabled && !loading,
        colors = colors,
        border = border,
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        interactionSource = interactionSource
    ) {
        if (loading) {
            LoadingIndicator(
                modifier = Modifier.size(iconSize + 6.dp),
                color = colors.contentColor
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(
                text = text,
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/**
 * A row of related actions, laid out so the primary one takes the space left
 * over. Saves every screen from re-deciding what a cancel button next to a save
 * button should look like.
 */


