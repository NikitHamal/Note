package com.neb.ians.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * The one writing field for posts, replies and notes. It shows what the text will look like, never the markers, and
 * pictures sit inside the text as small rounded thumbnails that move with the words around them. Tap a thumbnail to
 * open it, press and hold to drag it elsewhere, tap the cross to take it out.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RichTextField(
    state: RichState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    chipDp: Float = 32f,
    onImageClick: (Int) -> Unit = {},
    onChange: ((androidx.compose.ui.text.input.TextFieldValue) -> Unit)? = null,
    keyboardCapitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    readOnly: Boolean = false,
    enableFormatMenu: Boolean = true,
    isHeadingActive: Boolean? = null,
    isQuoteActive: Boolean? = null,
    isBulletActive: Boolean? = null,
    onTurnHeading: (() -> Unit)? = null,
    onTurnQuote: (() -> Unit)? = null,
    onTurnBullet: (() -> Unit)? = null,
    decorationBox: (@Composable (inner: @Composable () -> Unit) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val doc = state.doc
    val hasImages = doc.keys.isNotEmpty()
    val emPx = with(density) { textStyle.fontSize.toPx() }
    val chipPx = with(density) { chipDp.dp.toPx() }
    val gapPx = emPx * 0.4f

    // The text keeps its own line height everywhere. Only a line that holds a picture grows (see imageLine), and the
    // caret is drawn at the text's own height so it never stretches with the picture.
    val style = textStyle
    val measurer = rememberTextMeasurer()
    // Meta's caret is the font's height (about 1.2 em), not the line's: it never stretches with a picture
    val baseLineH = emPx * 1.2f
    val imageLine = remember(chipDp, density, style) {
        val need = with(density) { (chipDp + 4f).dp.toSp() }
        val cur = style.lineHeight
        ParagraphStyle(
            lineHeight = if (cur.isSp && cur.value >= need.value) cur else need,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
        )
    }
    val advance = remember(style, measurer) {
        val w = runCatching { measurer.measure(AnnotatedString(InlineImageTokens.PLACEHOLDER_CHAR.toString()), style).size.width.toFloat() }
            .getOrDefault(emPx * 0.6f)
        if (w > 1f) w else emPx * 0.6f
    }
    val spacing: TextUnit = remember(advance, chipPx, gapPx, emPx) {
        val n = InlineImageTokens.PLACEHOLDER_LEN
        val extra = ((chipPx + gapPx) - advance * n) / (n - 1)
        if (extra > 0.5f) (extra / emPx).em else TextUnit.Unspecified
    }
    val built = remember(doc, scheme, spacing, imageLine) {
        RichVisual.build(
            doc,
            LiveColors(scheme.surfaceContainerHighest, scheme.onSurface, scheme.primary, scheme.onSurfaceVariant, scheme.onSurfaceVariant),
            spacing,
            imageLine
        )
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // where a dragged picture would land: the writing caret itself moves there while the finger is down
    var dragging by remember { mutableStateOf(false) }
    val dragMemo = remember { intArrayOf(-1, -1, -1) } // last landing offset, saved selection start and end
    val value = state.value
    var focused by remember { mutableStateOf(false) }
    var caretOn by remember { mutableStateOf(true) }
    val ownCaret = hasImages && decorationBox == null
    val sel = value.selection
    LaunchedEffect(focused, sel.start, sel.end, value.text.length) {
        caretOn = true
        while (focused) { delay(530); caretOn = !caretOn }
    }
    val ph = InlineImageTokens.PLACEHOLDER_LEN
    // a drop never lands inside a picture's blank run: it snaps before or after that picture
    fun snap(off: Int): Int {
        for (sl in built.slots) if (off > sl && off < sl + ph) return if (off - sl < ph / 2f) sl else sl + ph
        return off
    }
    // the caret keeps the text's height, centred in its line
    fun slim(r: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect {
        val h = minOf(baseLineH, r.height)
        val cy = r.top + r.height / 2f
        return androidx.compose.ui.geometry.Rect(r.left, cy - h / 2f, r.right, cy + h / 2f)
    }

    // while a picture is dragged, the scrolling page follows the drop caret (a little room above and below it)
    val reveal = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val revealPx = with(density) { 56.dp.toPx() }
    var followJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun follow(r: androidx.compose.ui.geometry.Rect) {
        followJob?.cancel()
        followJob = scope.launch { runCatching { reveal.bringIntoView(androidx.compose.ui.geometry.Rect(r.left, r.top - revealPx, r.right + 1f, r.bottom + revealPx)) } }
    }
    // Writing keeps the caret in view: when the line under it runs past the bottom (or is hidden by the keyboard and
    // the bar above it) the page scrolls just enough, and again once the keyboard has settled.
    val keepPx = with(density) { 112.dp.toPx() }
    LaunchedEffect(sel.start, sel.end, layout, focused) {
        if (!focused || !sel.collapsed || readOnly || dragging) return@LaunchedEffect
        val lr = layout ?: return@LaunchedEffect
        val at = built.transformed.offsetMapping.originalToTransformed(sel.start).coerceIn(0, lr.layoutInput.text.length)
        val r = runCatching { lr.getCursorRect(at) }.getOrNull() ?: return@LaunchedEffect
        val want = androidx.compose.ui.geometry.Rect(r.left, r.top - emPx, r.right + 1f, r.bottom + keepPx)
        runCatching { reveal.bringIntoView(want) }
        delay(280)
        runCatching { reveal.bringIntoView(want) }
    }
    // the overlay shares the text layout's origin, so a point in a picture converts straight to a text position
    val overlayRef = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    fun toField(from: androidx.compose.ui.layout.LayoutCoordinates, at: Offset): Offset? {
        val o = overlayRef[0] ?: return null
        return if (o.isAttached && from.isAttached) o.localPositionOf(from, at) else null
    }

    var showLinkDialog by remember { mutableStateOf(false) }
    var showMathDialog by remember { mutableStateOf(false) }

    // Full M3 Expressive formatting bar in the selection menu (Posts, Notes, Comments)
    val selSnapshot = state.value.selection
    val armedSnapshot = state.armed
    val docSnapshot = state.doc
    val extras = remember(
        state, enabled, readOnly, enableFormatMenu,
        selSnapshot, armedSnapshot, docSnapshot,
        isHeadingActive, isQuoteActive, isBulletActive
    ) {
        if (!enabled || readOnly || !enableFormatMenu) emptyList()
        else listOf(
            NebToolAction("Bold", Icons.Outlined.FormatBold, active = state.isActive(Fmt.BOLD)) { state.toggleInline(Fmt.BOLD) },
            NebToolAction("Italic", Icons.Outlined.FormatItalic, active = state.isActive(Fmt.ITALIC)) { state.toggleInline(Fmt.ITALIC) },
            NebToolAction("Underline", Icons.Outlined.FormatUnderlined, active = state.isActive(Fmt.UNDERLINE)) { state.toggleInline(Fmt.UNDERLINE) },
            NebToolAction("Strikethrough", Icons.Outlined.FormatStrikethrough, active = state.isActive(Fmt.STRIKE)) { state.toggleInline(Fmt.STRIKE) },
            NebToolAction("Inline code", Icons.Outlined.Code, active = state.isActive(Fmt.CODE)) { state.toggleInline(Fmt.CODE) },
            NebToolAction("Link", Icons.Outlined.Link, active = false) { showLinkDialog = true },
            NebToolAction("Heading", Icons.Outlined.Title, active = isHeadingActive ?: state.isLineActive(RLine.H2)) {
                if (onTurnHeading != null) onTurnHeading() else state.toggleLine(RLine.H2)
            },
            NebToolAction("Quote", Icons.Outlined.FormatQuote, active = isQuoteActive ?: state.isLineActive(RLine.QUOTE)) {
                if (onTurnQuote != null) onTurnQuote() else state.toggleLine(RLine.QUOTE)
            },
            NebToolAction("Bullet list", Icons.Outlined.FormatListBulleted, active = isBulletActive ?: state.isLineActive(RLine.BULLET)) {
                if (onTurnBullet != null) onTurnBullet() else state.toggleLine(RLine.BULLET)
            },
            NebToolAction("Formula & symbols", Icons.Outlined.Functions, active = false) { showMathDialog = true },
        )
    }
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    if (showLinkDialog) {
        var linkUrl by remember { mutableStateOf("") }
        NebModalSheet(
            title = "Add link",
            showClose = true,
            onDismiss = { showLinkDialog = false }
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it.replace("\n", "").take(255) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (linkUrl.isNotBlank()) scheme.primary else scheme.surfaceContainerHigh)
                        .clickable(enabled = linkUrl.isNotBlank()) {
                            state.insertLink(linkUrl)
                            showLinkDialog = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add link",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (linkUrl.isNotBlank()) scheme.onPrimary else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    if (showMathDialog) {
        var formulaText by remember { mutableStateOf("") }
        val quickSymbols = remember {
            listOf("²", "³", "√", "π", "±", "×", "÷", "≠", "≤", "≥", "≈", "∞", "→", "⇌", "°", "∴", "∑", "∫", "Δ", "α", "β", "θ", "λ", "Ω")
        }
        NebModalSheet(
            title = "Formula or symbol",
            showClose = true,
            onDismiss = { showMathDialog = false }
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = formulaText,
                    onValueChange = { formulaText = it.take(240) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("LaTeX or equation (e.g. E = mc^2)") },
                    modifier = Modifier.fillMaxWidth()
                )
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    quickSymbols.forEach { sym ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.secondaryContainer)
                                .clickable {
                                    state.insertSymbol(sym)
                                    showMathDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(sym, style = MaterialTheme.typography.titleSmall, color = scheme.onSecondaryContainer)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (formulaText.isNotBlank()) scheme.primary else scheme.surfaceContainerHigh)
                        .clickable(enabled = formulaText.isNotBlank()) {
                            state.insertSymbol("$$${formulaText.trim()}$$")
                            showMathDialog = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Insert formula",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (formulaText.isNotBlank()) scheme.onPrimary else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    NebTextToolbarProvider(extras) {
    Box(modifier = modifier.bringIntoViewRequester(reveal), propagateMinConstraints = true) {
        BasicTextField(
            value = value,
            onValueChange = onChange ?: state::onChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = style.copy(color = if (style.color.isSpecified) style.color else scheme.onSurface),
            cursorBrush = SolidColor(if (ownCaret) Color.Transparent else scheme.primary),
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = built.transformation,
            keyboardOptions = KeyboardOptions(capitalization = keyboardCapitalization),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) false
                    else if (e.isCtrlPressed || e.isMetaPressed) when (e.key) {
                        Key.B -> { state.toggleInline(Fmt.BOLD); true }
                        Key.I -> { state.toggleInline(Fmt.ITALIC); true }
                        Key.U -> { state.toggleInline(Fmt.UNDERLINE); true }
                        else -> false
                    }
                    else e.key == Key.Backspace && state.backspaceAtLineStart()
                },
            decorationBox = { inner ->
                if (decorationBox != null) decorationBox(inner) else Box {
                    if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(text = placeholder, style = style.copy(color = scheme.onSurfaceVariant), maxLines = 1)
                    }
                    inner()
                }
            }
        )
        val lr = layout
        if (lr != null && decorationBox == null && hasImages) Box(Modifier.matchParentSize().onGloballyPositioned { overlayRef[0] = it }) {
            doc.keys.forEachIndexed { index, imageKey ->
                val slot = built.slots.getOrNull(index) ?: return@forEachIndexed
                val box = runCatching { lr.getBoundingBox(slot) }.getOrNull() ?: return@forEachIndexed
                val left = box.left + gapPx / 2f
                val line = runCatching { lr.getLineForOffset(slot) }.getOrDefault(0)
                val lineTop = lr.getLineTop(line)
                val lineBottom = lr.getLineBottom(line)
                // centred in its own line and never above it, so the line the keyboard scrolls into view holds the whole picture
                val top = (lineTop + (lineBottom - lineTop - chipPx) / 2f).coerceAtLeast(lineTop)
                key(imageKey) {
                    RichChip(
                        model = imageKey.toIntOrNull()?.let { resolveMediaUrl("/media/content_images/${it}t.webp") } ?: state.uris[imageKey],
                        sizeDp = chipDp,
                        leftPx = left,
                        topPx = top,
                        enabled = enabled,
                        onOpen = { onImageClick(index) },
                        onRemove = { state.removeImage(state.doc.keys.indexOf(imageKey)) },
                        onHover = { from, local ->
                            if (from == null) {
                                // finger up or cancelled: give the caret back (a drop sets its own)
                                if (dragging) {
                                    dragging = false
                                    if (dragMemo[1] >= 0) state.restoreSelection(dragMemo[1], dragMemo[2])
                                    dragMemo[0] = -1; dragMemo[1] = -1
                                }
                            } else {
                                val finger = toField(from, local)
                                if (finger != null) {
                                    if (!dragging) {
                                        dragging = true
                                        dragMemo[1] = state.value.selection.start; dragMemo[2] = state.value.selection.end
                                    }
                                    val t = snap(lr.getOffsetForPosition(finger))
                                    val orig = built.toOriginal(t)
                                    if (orig != dragMemo[0]) {
                                        dragMemo[0] = orig
                                        // the real caret of the keyboard walks to where the picture will land
                                        state.moveCaret(orig)
                                        runCatching { slim(lr.getCursorRect(t)) }.getOrNull()?.let { follow(it) }
                                    }
                                }
                            }
                        },
                        onDrop = { from, local ->
                            val finger = toField(from, local)
                            if (finger != null) {
                                val at = built.toOriginal(snap(lr.getOffsetForPosition(finger)))
                                state.moveImage(state.doc.keys.indexOf(imageKey), at)
                            }
                        }
                    )
                }
            }
        }
        if (ownCaret && (focused || dragging) && sel.collapsed && lr != null) {
            val r = runCatching { slim(lr.getCursorRect(built.transformed.offsetMapping.originalToTransformed(sel.start))) }.getOrNull()
            if (r != null) Box(
                Modifier.matchParentSize().drawBehind {
                    if (caretOn || dragging) drawRoundRect(
                        scheme.primary, Offset(r.left - 0.75.dp.toPx(), r.top), Size(2.dp.toPx(), r.height), CornerRadius(1.dp.toPx())
                    )
                }
            )
        }
    }
    }
}

@Composable
private fun RichChip(
    model: Any?,
    sizeDp: Float,
    leftPx: Float,
    topPx: Float,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onHover: (androidx.compose.ui.layout.LayoutCoordinates?, Offset) -> Unit,
    onDrop: (androidx.compose.ui.layout.LayoutCoordinates, Offset) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var lifted by remember { mutableStateOf(false) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val liftPx = with(density) { 30.dp.toPx() }
    val left by rememberUpdatedState(leftPx)
    val top by rememberUpdatedState(topPx)
    val drop by rememberUpdatedState(onDrop)
    val hover by rememberUpdatedState(onHover)
    val shape = RoundedCornerShape(10.dp)
    val node = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var last by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset((left + drag.x).roundToInt(), (top + drag.y - if (lifted) liftPx else 0f).roundToInt()) }
            .size(sizeDp.dp)
            .graphicsLayer { val k = if (lifted) 1.12f else 1f; scaleX = k; scaleY = k }
            .alpha(if (lifted) 0.85f else 1f)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .onGloballyPositioned { node[0] = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> grab = at; last = at; lifted = true; drag = Offset.Zero; node[0]?.let { hover(it, at) } },
                    onDrag = { change, amount ->
                        change.consume(); drag += amount
                        // the caret follows the fingertip (read from the screen, so a scrolling page can not make it drift)
                        last = change.position
                        node[0]?.let { hover(it, change.position) }
                    },
                    onDragEnd = {
                        val n = node[0]
                        lifted = false; drag = Offset.Zero
                        hover(null, Offset.Zero)
                        if (n != null) drop(n, last)
                    },
                    onDragCancel = { lifted = false; drag = Offset.Zero; hover(null, Offset.Zero) }
                )
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Picture in text",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove picture", tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * An editor state wired to Markdown held somewhere else (a view model, a draft). Edits are sent out as Markdown; a value
 * that arrives from outside and is not the echo of what was just sent replaces the text (a cleared draft, a mention).
 */
@Composable
fun rememberRichState(
    markdown: String,
    caret: Int = markdown.length,
    maxChars: Int = Int.MAX_VALUE,
    onMarkdown: (markdown: String, caret: Int) -> Unit
): RichState {
    val state = remember { RichState(maxChars).also { it.setMarkdown(markdown, caret) } }
    val latest by rememberUpdatedState(onMarkdown)
    remember(state) { state.onEdited = { r -> latest(r.lastMarkdown, r.lastCaret) } }
    LaunchedEffect(markdown, caret) { state.adopt(markdown, caret) }
    return state
}
