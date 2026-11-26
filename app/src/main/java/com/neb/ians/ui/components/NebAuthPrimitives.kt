package com.neb.ians.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.ui.theme.LocalNebAuthPalette
import com.neb.ians.ui.theme.NebAuthTokens
import com.neb.ians.ui.theme.NebMotion
import com.neb.ians.ui.theme.nebEffectsSpec
import com.neb.ians.ui.theme.nebFastEffectsSpec
import com.neb.ians.ui.theme.nebFastSpatialSpec
import com.neb.ians.ui.theme.nebSpatialSpec
import com.neb.ians.ui.theme.Poppins
import com.neb.ians.util.TactileType
import com.neb.ians.util.rememberTactileFeedback

// ---------------------------------------------------------------------------
// The controls the sign-in and onboarding journey is assembled from. Every one
// of them reads its colours from LocalNebAuthPalette, so a screen sets the
// palette once and never passes a Color down again.
// ---------------------------------------------------------------------------

/** Press feedback that runs on the render thread, so a tap never costs a recomposition of the subtree. */
@Composable
fun Modifier.nebPressable(
    enabled: Boolean = true,
    scale: Float = NebMotion.PressScale,
    tactile: TactileType = TactileType.ButtonTap,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedback = rememberTactileFeedback()
    val target by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = nebFastSpatialSpec(),
        label = "neb_press"
    )
    return this
        .graphicsLayer { scaleX = target; scaleY = target }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled
        ) {
            feedback.perform(tactile)
            onClick()
        }
}

// ---------------------------------------------------------------------------
// Type — five styles cover the whole journey.
// ---------------------------------------------------------------------------

object NebAuthType {
    val Headline = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.5).sp,
        lineBreak = androidx.compose.ui.text.style.LineBreak.Heading
    )
    val Title = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    )
    val Body = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
    )
    val Label = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
    val Caption = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
    )
    val Button = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp
    )
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/**
 * The journey top bar. It carries at most one leading control — a close for a
 * screen the user opened, a back arrow for a step they walked into — plus an
 * optional step rail that fills the remaining width.
 */
@Composable
fun NebAuthTopBar(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    progress: Float? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            onBack != null -> NebIconAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            onClose != null -> NebIconAction(Icons.Outlined.Close, "Close", onClose)
            // A step rail keeps the slot even without a control, so the rail does not
            // jump sideways when the back arrow appears on step two.
            else -> Spacer(modifier = Modifier.width(if (progress != null) 44.dp else 4.dp))
        }

        if (progress != null) {
            NebProgressRail(
                progress = progress,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (trailing != null) {
            trailing()
        } else {
            Spacer(modifier = Modifier.width(if (progress != null) 12.dp else 44.dp))
        }
    }
}

@Composable
fun NebIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val palette = LocalNebAuthPalette.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .nebPressable(scale = 0.92f, tactile = TactileType.LightTap, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: palette.ink,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * A single continuous rail rather than one segment per step: the journey has
 * enough steps that segments would turn into a ladder, and a filling line
 * reads as progress at any count.
 */
@Composable
fun NebProgressRail(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalNebAuthPalette.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = nebSpatialSpec(),
        label = "neb_rail"
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(palette.accentSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animated
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .clip(CircleShape)
                .background(palette.accent)
        )
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/** The one primary action per screen: accent pill, full width, optional spinner. */
@Composable
fun NebPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: ImageVector? = null
) {
    val palette = LocalNebAuthPalette.current
    val active = enabled && !loading
    val container by animateColorAsState(
        targetValue = if (active) palette.accent else palette.accent.copy(alpha = 0.32f),
        animationSpec = nebFastEffectsSpec(),
        label = "neb_pill_bg"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NebAuthTokens.ControlHeight)
            .clip(RoundedCornerShape(NebAuthTokens.PillRadius))
            .background(container)
            .nebPressable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            NebLoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = palette.onAccent
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = NebAuthType.Button,
                    color = if (active) palette.onAccent else palette.onAccent.copy(alpha = 0.7f)
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = if (active) palette.onAccent else palette.onAccent.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** The secondary action: same pill, hairline instead of fill. */
@Composable
fun NebOutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val palette = LocalNebAuthPalette.current
    Box(
        modifier = modifier
            .height(NebAuthTokens.ControlHeight)
            .clip(RoundedCornerShape(NebAuthTokens.PillRadius))
            .border(NebAuthTokens.Hairline, palette.hairlineStrong, RoundedCornerShape(NebAuthTokens.PillRadius))
            .nebPressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = NebAuthType.Button, color = palette.ink)
        }
    }
}

/** An inline link. Accent is the only colour text is ever allowed to take. */
@Composable
fun NebTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalNebAuthPalette.current
    Text(
        text = text,
        style = NebAuthType.Body.copy(fontWeight = FontWeight.SemiBold),
        color = if (enabled) palette.accent else palette.inkFaint,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .nebPressable(enabled = enabled, scale = 0.96f, tactile = TactileType.LightTap, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

// ---------------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------------

/**
 * The journey's one text field. Label sits above the box rather than floating
 * inside it: with a password toggle, a validity tick and a helper line already
 * competing for the row, a floating label is the thing that has to go.
 */
@Composable
fun NebAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
    error: String? = null,
    helper: String? = null,
    helperTone: Color? = null,
    singleLine: Boolean = true,
    minHeight: Dp = NebAuthTokens.FieldHeight
) {
    val palette = LocalNebAuthPalette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> palette.danger
            focused -> palette.accent
            else -> palette.hairline
        },
        animationSpec = nebFastEffectsSpec(),
        label = "neb_field_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused || error != null) 1.6.dp else NebAuthTokens.Hairline,
        animationSpec = nebFastSpatialSpec(),
        label = "neb_field_border_w"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = NebAuthType.Label,
                color = if (error != null) palette.danger else palette.inkMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = NebAuthType.Body.copy(
                color = if (enabled) palette.ink else palette.inkMuted,
                fontSize = 15.5.sp
            ),
            cursorBrush = SolidColor(palette.accent),
            interactionSource = interaction,
            visualTransformation = if (isPassword && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onNext = { onImeAction() },
                onGo = { onImeAction() },
                onSend = { onImeAction() }
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .clip(RoundedCornerShape(NebAuthTokens.FieldRadius))
                        .background(palette.field)
                        .border(borderWidth, borderColor, RoundedCornerShape(NebAuthTokens.FieldRadius))
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = if (focused) palette.accent else palette.inkFaint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = NebAuthType.Body.copy(fontSize = 15.5.sp),
                                color = palette.inkFaint
                            )
                        }
                        inner()
                    }
                    when {
                        isPassword -> NebIconAction(
                            icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            onClick = { revealed = !revealed },
                            modifier = Modifier.size(36.dp),
                            tint = palette.inkFaint
                        )
                        trailing != null -> trailing()
                    }
                }
            }
        )

        val note = error ?: helper
        AnimatedVisibility(
            visible = note != null,
            enter = expandVertically(nebFastSpatialSpec()) + fadeIn(nebFastEffectsSpec()),
            exit = fadeOut(nebFastEffectsSpec()) + shrinkVertically(nebFastSpatialSpec())
        ) {
            Text(
                text = note ?: "",
                style = NebAuthType.Caption,
                color = when {
                    error != null -> palette.danger
                    helperTone != null -> helperTone
                    else -> palette.inkMuted
                },
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

/**
 * The read-only twin of [NebAuthField] for values chosen elsewhere — a date, a
 * district, a class. It is a field, not a button, because the user is filling a
 * form and the row has to line up with the ones above it.
 */
@Composable
fun NebPickerField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    error: String? = null
) {
    val palette = LocalNebAuthPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = NebAuthType.Label,
            color = if (error != null) palette.danger else palette.inkMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NebAuthTokens.FieldHeight)
                .clip(RoundedCornerShape(NebAuthTokens.FieldRadius))
                .background(palette.field)
                .border(
                    if (error != null) 1.6.dp else NebAuthTokens.Hairline,
                    if (error != null) palette.danger else palette.hairline,
                    RoundedCornerShape(NebAuthTokens.FieldRadius)
                )
                .nebPressable(scale = 0.985f, tactile = TactileType.LightTap, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.inkFaint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = value.ifBlank { placeholder },
                style = NebAuthType.Body.copy(fontSize = 15.5.sp),
                color = if (value.isBlank()) palette.inkFaint else palette.ink,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = palette.inkFaint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = NebAuthType.Caption,
                color = palette.danger,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Notes, cards and chips
// ---------------------------------------------------------------------------

enum class NebNoteTone { Error, Info, Success }

/** A whole-screen message — a failed sign-in, a resent code. Never inside a field. */
@Composable
fun NebInlineNote(
    text: String?,
    modifier: Modifier = Modifier,
    tone: NebNoteTone = NebNoteTone.Error
) {
    val palette = LocalNebAuthPalette.current
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = expandVertically(nebSpatialSpec()) + fadeIn(nebEffectsSpec()),
        exit = fadeOut(nebEffectsSpec()) + shrinkVertically(nebSpatialSpec()),
        modifier = modifier
    ) {
        val (bg, fg) = when (tone) {
            NebNoteTone.Error -> palette.dangerSoft to palette.danger
            NebNoteTone.Success -> palette.successSoft to palette.success
            NebNoteTone.Info -> palette.accentSoft to palette.accent
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(
                text = text ?: "",
                style = NebAuthType.Caption.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The eyebrow above a group of fields. */
@Composable
fun NebFieldGroupLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalNebAuthPalette.current
    Text(
        text = text.uppercase(),
        style = NebAuthType.Label.copy(letterSpacing = 1.4.sp, fontSize = 11.sp),
        color = palette.inkFaint,
        modifier = modifier
    )
}

/** Headline plus one supporting line — the opening of every journey step. */
@Composable
fun NebStepHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    val palette = LocalNebAuthPalette.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = NebAuthType.Headline,
            color = palette.ink,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = NebAuthType.Body,
                color = palette.inkMuted,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


