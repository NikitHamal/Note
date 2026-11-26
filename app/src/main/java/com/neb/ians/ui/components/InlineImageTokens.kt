package com.neb.ians.ui.components

import kotlin.random.Random

/**
 * Inline text-image tokens (`[[img:ID]]`) shared by the composer and every
 * content renderer. Mirrors `api/content_images.py` on the server:
 * real uploads carry a numeric id; in-flight uploads carry a temporary
 * `uXXXXXXXX` key that is swapped for the numeric id once the upload lands.
 */
object InlineImageTokens {

    const val MAX_IMAGES_PER_CONTENT = 8

    /** Numeric ids = uploaded; `u`-prefixed keys = pending upload. */
    val REGEX = Regex("\\[\\[img:(\\d{1,10}|u[A-Za-z0-9]{6,16})\\]\\]")

    /** Private Use Area char each token collapses to inside the editor. */
    const val PLACEHOLDER_CHAR = '\uE107'
    const val PLACEHOLDER_LEN = 4

    fun newPendingKey(): String = "u" + Random.nextInt(0x1000000, 0xFFFFFFF).toString(16)

    /** Ordered unique numeric image ids referenced by the text. */
    fun extractIds(text: String): List<Int> {
        val seen = linkedSetOf<Int>()
        REGEX.findAll(text).forEach { m ->
            m.groupValues[1].toIntOrNull()?.let { seen.add(it) }
        }
        return seen.toList()
    }

    /** Replace tokens with a plain-text placeholder (previews, share sheets). */
    fun plainText(text: String, placeholder: String = "[image]"): String =
        REGEX.replace(text, placeholder)

    fun stripTokens(text: String): String = REGEX.replace(text, "")
}

/**
 * Visual transformation that collapses every `[[img:…]]` token into ONE
 * private-use character so Backspace deletes the whole chip at once and the
 * caret can never sit between the token's characters — matching the web
 * contenteditable chip behaviour.
 *
 * The placeholder glyph itself is painted fully transparent; visible
 * thumbnails are drawn by [InlineImageField]'s overlay positioned via the
 * transformed layout's bounding boxes.
 */

