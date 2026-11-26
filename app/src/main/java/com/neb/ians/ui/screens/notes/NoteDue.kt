package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** To-do due dates are whole calendar days (days since 1970), so they never shift with a time zone. */
object NoteDue {
    private const val DAY = 86_400_000L

    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / DAY
    }

    fun label(day: Long): String {
        val d = day - today()
        return when (d) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> SimpleDateFormat(if (d in 2..6) "EEEE" else "d MMM", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(day * DAY)
        }
    }

    fun overdue(day: Long) = day in 1 until today()
}

/** The date on a to-do. A status label, so it keeps a tonal fill: red tone once it is late and still open. */
@Composable
fun DueChip(day: Long, done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val late = androidx.compose.runtime.remember(day, done) { !done && NoteDue.overdue(day) }
    val label = androidx.compose.runtime.remember(day) { NoteDue.label(day) }
    val fill = if (late) c.errorContainer else c.secondaryContainer
    val ink = if (late) c.onErrorContainer else c.onSecondaryContainer
    Row(
        modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(fill).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Event, null, tint = ink, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 5.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDialog(day: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (if (day > 0L) day else NoteDue.today()) * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        confirmButton = { TextButton(onClick = { onPick((state.selectedDateMillis ?: 0L) / 86_400_000L); onDismiss() }) { Text("Set") } },
        dismissButton = {
            if (day > 0L) TextButton(onClick = { onPick(0L); onDismiss() }) { Text("Clear") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) { DatePicker(state = state) }
}
