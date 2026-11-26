@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.neb.ians.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

/**
 * One shape per person, everywhere: the web's `neb-shapes.js` hashes the username (FNV-1a) into
 * the same seven silhouettes and picks one of three tones from the next byte. This is the same
 * function, so a person who is a clover on the site is a clover on the phone.
 */
object AvatarShapes {
    /** The seven silhouettes, in the web's order. */
    val polygons: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Sunny,
            MaterialShapes.Flower,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.SoftBurst,
            MaterialShapes.Oval,
            MaterialShapes.Square
        )
    }

    private fun hash(seed: String): Long {
        var h = 2166136261L
        for (c in seed) {
            h = h xor c.code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h
    }

    /** Index into [polygons] for this seed, or -1 for a blank one. */
    fun indexFor(seed: String): Int {
        if (seed.isBlank()) return -1
        return (hash(seed.trim().lowercase()) % polygons.size).toInt()
    }

    fun toneFor(seed: String): Int {
        if (seed.isBlank()) return 0
        return ((hash(seed.trim().lowercase()) ushr 8) % 3).toInt()
    }
}

/** The person's avatar shape, remembered per seed. */
@Composable
fun rememberAvatarShape(seed: String): Shape {
    val index = remember(seed) { AvatarShapes.indexFor(seed) }
    val shaped = AvatarShapes.polygons[index.coerceAtLeast(0)].toShape()
    return if (index < 0) CircleShape else shaped
}

/** Fill and ink for an initial-only avatar: the web's three tonal pairs, chosen by the same hash. */
@Composable
fun avatarTone(seed: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val tone = remember(seed) { AvatarShapes.toneFor(seed) }
    return when (tone) {
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.primaryContainer to c.onPrimaryContainer
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
}
