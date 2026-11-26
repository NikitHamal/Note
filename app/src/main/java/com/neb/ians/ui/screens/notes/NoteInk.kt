package com.neb.ians.ui.screens.notes

import androidx.compose.ui.graphics.Color

/**
 * Text colours and highlights for a block. Nine inks, each with a light and a dark pair so the words stay readable on
 * any note colour. Flat tints only. Index 0 is "no ink" and never reaches these tables.
 */
object NoteInk {
    const val count = 9
    val names = listOf("Red", "Orange", "Amber", "Green", "Teal", "Blue", "Violet", "Pink", "Grey")

    private val textLight = longArrayOf(0xFFB3261E, 0xFFB35A00, 0xFF8A6A00, 0xFF1E7A3C, 0xFF00796B, 0xFF1A5FB4, 0xFF7B3FA0, 0xFFB0286A, 0xFF5F6368)
    private val textDark = longArrayOf(0xFFF2B8B5, 0xFFFFB77A, 0xFFF5D547, 0xFF8FD9A0, 0xFF80D5C8, 0xFFA8C7FA, 0xFFD9B8F5, 0xFFF5A9CC, 0xFFB8BCC2)
    private val fillLight = longArrayOf(0xFFFDE3E1, 0xFFFFE8D0, 0xFFFFF2B8, 0xFFDDF3E2, 0xFFD3F2EE, 0xFFDCE8FB, 0xFFEBDDF7, 0xFFFBDDEA, 0xFFE8EAED)
    private val fillDark = longArrayOf(0xFF5A2A27, 0xFF5C3B17, 0xFF5A4D12, 0xFF1F4A2B, 0xFF17463F, 0xFF1F3B63, 0xFF46305F, 0xFF5C2A43, 0xFF3C4043)

    fun text(index: Int, dark: Boolean): Color? =
        if (index in 1..count) Color((if (dark) textDark else textLight)[index - 1]) else null

    fun fill(index: Int, dark: Boolean): Color? =
        if (index in 1..count) Color((if (dark) fillDark else fillLight)[index - 1]) else null
}
