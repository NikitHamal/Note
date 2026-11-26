package com.neb.ians.ui.screens.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** A tonal note colour: fill and its label, always a role pair. */
@Immutable
data class NoteTone(val container: Color, val content: Color, val hairline: Boolean)

/** A ready-made look: colour, cover and icon in one tap. */
@Immutable
data class NoteTheme(val label: String, val tone: Int, val cover: Int, val icon: String, val paper: Int = 0)

object NoteTones {
    val labels = listOf(
        "Paper", "Blue", "Slate", "Teal", "Rose", "Sky", "Stone",
        "Sand", "Mint", "Lemon", "Peach", "Ocean", "Sage", "Linen", "Periwinkle", "Graphite", "Midnight", "Forest", "Rose", "Teal", "Coral", "Dusk", "Lilac", "Apricot", "Olive", "Cocoa", "Charcoal", "Steel",
    )
    val count: Int get() = labels.size

    val themes = listOf(
        NoteTheme("Study", 1, 1, "school", 1),
        NoteTheme("Journal", 7, 12, "book", 1),
        NoteTheme("Garden", 8, 19, "eco"),
        NoteTheme("Sunrise", 10, 14, "sun"),
        NoteTheme("Ocean", 11, 2, "world"),
        NoteTheme("Calm", 14, 4, "mind"),
        NoteTheme("Focus", 15, 22, "bolt", 3),
        NoteTheme("Night", 16, 9, "star"),
        NoteTheme("Forest", 17, 24, "park"),
        NoteTheme("Bloom", 18, 33, "heart"),
        NoteTheme("Lagoon", 19, 34, "world"),
        NoteTheme("Ember", 20, 36, "flag"),
        NoteTheme("Dusk", 21, 38, "mind"),
        NoteTheme("Plan", 5, 35, "event", 2),
        NoteTheme("Lab", 3, 29, "science", 2),
        NoteTheme("Notebook", 22, 12, "book", 4),
        NoteTheme("Recipe", 23, 14, "heart", 1),
        NoteTheme("Grove", 24, 19, "eco", 5),
        NoteTheme("Cocoa", 25, 9, "star", 3),
        NoteTheme("Studio", 26, 22, "bolt", 5),
    )

    private class Pair2(val lc: Long, val lt: Long, val dc: Long, val dt: Long)

    private val fixed = listOf(
        Pair2(0xFFF5EBDD, 0xFF4A3B22, 0xFF3A3126, 0xFFEBDDC5), // 7 Sand
        Pair2(0xFFDDF3E4, 0xFF0F3D26, 0xFF17382A, 0xFFBFEBD0), // 8 Mint
        Pair2(0xFFFBF1C4, 0xFF4A4000, 0xFF3B3512, 0xFFF3E8A3), // 9 Lemon
        Pair2(0xFFFFE3D3, 0xFF5A2B10, 0xFF442618, 0xFFFFD0B5), // 10 Peach
        Pair2(0xFFBFE6F7, 0xFF063B57, 0xFF0C3550, 0xFFB4E2FA), // 11 Ocean
        Pair2(0xFFE1EBD9, 0xFF2B3A1E, 0xFF27301F, 0xFFD3E3C6), // 12 Sage
        Pair2(0xFFEFEAE4, 0xFF3B342D, 0xFF2E2B28, 0xFFE5DED5), // 13 Linen
        Pair2(0xFFDDE3FF, 0xFF1D2A6B, 0xFF232B55, 0xFFC9D2FF), // 14 Periwinkle
        Pair2(0xFF2B2E36, 0xFFE6E8EE, 0xFF1A1C22, 0xFFE6E8EE), // 15 Graphite
        Pair2(0xFF0F1A33, 0xFFDCE6FF, 0xFF0A1226, 0xFFDCE6FF), // 16 Midnight
        Pair2(0xFF163B2A, 0xFFD5F0E0, 0xFF0F2A1E, 0xFFD5F0E0), // 17 Forest
        Pair2(0xFFFFDCE0, 0xFF4F1A24, 0xFF4A2229, 0xFFFFD0D6), // 18 Rose
        Pair2(0xFFD2F3EF, 0xFF08403B, 0xFF123A37, 0xFFBDEFE9), // 19 Teal
        Pair2(0xFFFFE0DB, 0xFF5A2018, 0xFF4A2823, 0xFFFFD0C8), // 20 Coral
        Pair2(0xFF222B45, 0xFFDDE5FF, 0xFF151B30, 0xFFDDE5FF), // 21 Dusk
        Pair2(0xFFEADFF7, 0xFF35214F, 0xFF3A2D4D, 0xFFE3D3F6), // 22 Lilac
        Pair2(0xFFFFE6C2, 0xFF553300, 0xFF45331A, 0xFFFFDBA8), // 23 Apricot
        Pair2(0xFFE6E8C8, 0xFF3A3D12, 0xFF2D2F17, 0xFFDEE1B4), // 24 Olive
        Pair2(0xFF4A3426, 0xFFF3E4D8, 0xFF2A1D14, 0xFFF3E4D8), // 25 Cocoa
        Pair2(0xFF1F2124, 0xFFE8EAED, 0xFF121315, 0xFFE8EAED), // 26 Charcoal
        Pair2(0xFFD9E1E8, 0xFF1C2B38, 0xFF2A3540, 0xFFCFDAE4), // 27 Steel
    )

    internal fun fixedTone(index: Int, dark: Boolean): NoteTone? {
        val p = fixed.getOrNull(index - 7) ?: return null
        return if (dark) NoteTone(Color(p.dc), Color(p.dt), false) else NoteTone(Color(p.lc), Color(p.lt), false)
    }
}

@Composable
fun noteTone(index: Int): NoteTone {
    val c = MaterialTheme.colorScheme
    val dark = c.surface.luminance() < 0.5f
    return when (index) {
        1 -> NoteTone(c.primaryContainer, c.onPrimaryContainer, false)
        2 -> NoteTone(c.secondaryContainer, c.onSecondaryContainer, false)
        3 -> NoteTone(c.tertiaryContainer, c.onTertiaryContainer, false)
        4 -> NoteTone(c.errorContainer, c.onErrorContainer, false)
        5 -> NoteTone(c.surfaceContainerHigh, c.onSurface, false)
        6 -> NoteTone(c.surfaceVariant, c.onSurfaceVariant, false)
        in 7..27 -> NoteTones.fixedTone(index, dark) ?: NoteTone(c.surfaceContainerLowest, c.onSurface, true)
        else -> NoteTone(c.surfaceContainerLowest, c.onSurface, true)
    }
}
