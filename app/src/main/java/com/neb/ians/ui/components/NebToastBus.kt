package com.neb.ians.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * One way to say something short from anywhere (a screen, a click handler, a download callback) without a
 * scaffold of your own. [nebToast] sends the message to the host [NebToastBusHost] draws over the whole app, so it is
 * the same card as every other toast. With no host on screen (a crash screen, a background callback while the app is
 * away) it falls back to the system toast, so a message is never lost.
 */
object NebToastBus {
    internal class Message(
        val text: String,
        val long: Boolean,
        val kind: NebToastKind?,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
    )

    internal val messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)

    fun show(
        context: Context,
        text: String,
        long: Boolean = false,
        kind: NebToastKind? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        if (text.isBlank()) return
        if (messages.subscriptionCount.value == 0 || !messages.tryEmit(Message(text, long, kind, actionLabel, onAction))) {
            Toast.makeText(context.applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Short message, drawn as [NebToast]. Safe to call from any thread. Pass a [kind] when you know it
 * (success, info, warning, error); an [actionLabel] adds a bold action such as Undo or Retry.
 */
fun Context.nebToast(
    text: String,
    long: Boolean = false,
    kind: NebToastKind? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val ctx = this
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        NebToastBus.show(ctx, text, long, kind, actionLabel, onAction)
    } else {
        android.os.Handler(android.os.Looper.getMainLooper()).post { NebToastBus.show(ctx, text, long, kind, actionLabel, onAction) }
    }
}

/** The app-wide host: place once, over the navigation host. Keeps clear of the navigation bar. */
@Composable
fun BoxScope.NebToastBusHost() {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        var lastText = ""
        var lastAt = 0L
        NebToastBus.messages.collect { m ->
            val now = android.os.SystemClock.elapsedRealtime()
            // the same words twice in a breath are one toast, never a stack of copies
            if (m.text == lastText && now - lastAt < 2500L && state.currentSnackbarData != null) return@collect
            lastText = m.text
            lastAt = now
            state.currentSnackbarData?.dismiss()
            launch {
                val result = state.showSnackbar(
                    NebToastVisuals(
                        message = m.text,
                        kind = m.kind ?: nebToastKindOf(m.text),
                        actionLabel = m.actionLabel,
                        duration = if (m.actionLabel != null) SnackbarDuration.Long
                        else if (m.long) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) m.onAction?.invoke()
            }
        }
    }
    Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)) {
        NebToastHost(state)
    }
}
