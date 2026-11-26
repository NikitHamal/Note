package com.neb.ians.util

import java.util.concurrent.TimeUnit

fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        diff < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(diff) / 7}w ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 30}mo ago"
    }
}

/**
 * The accent for a subject outside composition — currently the media
 * notification, which needs an ARGB long and cannot read a colour scheme.
 * Always the brand blue: a notification is drawn by the system, on a surface
 * whose theme we do not control, so the one hue that holds up on both is the
 * only safe answer. Screens inside composition should use
 * [com.neb.ians.ui.theme.getSubjectTheme], which does vary per subject.
 */
fun getSubjectColor(subject: String): Long = 0xFF004AC6L
