package com.neb.ians.ui.screens.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neb.ians.ui.components.M3xNoteCover

/**
 * M3X art library v7 note covers (concepts/art-v7, kind `note`, android = true), drawn by code like the 40 banners before them: no pictures
 * in the APK, sharp at any size, light and dark from one drawing. The recipes are `assets/m3x/notes.json` (72 x [layout, palette, seed]).
 * Cover indexes continue after the 40 drawn banners: 41 to 112. The index is stored in `NoteEntity.cover`, so it never changes meaning.
 */
object NoteArtCovers {
    const val first = 41
    const val count = 72
    fun has(cover: Int): Boolean = cover >= first && cover < first + count
}

/** Library cover. [live] makes it breathe (the editor header); list cards and the picker stay still. */
@Composable
fun NoteArtCover(cover: Int, modifier: Modifier = Modifier, live: Boolean = false) =
    M3xNoteCover(cover - NoteArtCovers.first, modifier, live)
