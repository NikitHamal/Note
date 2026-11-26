package com.neb.ians.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A personal note, stored only on the device. [body] is Markdown; checklist lines are
 * `- [ ]` / `- [x]`. [tone] indexes the tonal fills in `NoteTones`. [trashedAt] is 0 for a
 * live note and the deletion time while it waits in the bin.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val tone: Int,
    val pinned: Boolean,
    val archived: Boolean,
    val trashedAt: Long,
    /** Lowercase tags separated by single spaces. */
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** The note as blocks (JSON). Empty for notes written before blocks; they open from [body]. */
    @ColumnInfo(defaultValue = "") val doc: String = "",
    /** Page icon key from `NoteIcons`; empty for none. */
    @ColumnInfo(defaultValue = "") val icon: String = "",
    /** Page cover index from `NoteCovers`; 0 for none. */
    @ColumnInfo(defaultValue = "0") val cover: Int = 0,
    /** File name of a gallery cover inside `notes/<id>/`; empty when the cover is a built-in one. `cover` is -1 while it is set. */
    @ColumnInfo(defaultValue = "") val coverPhoto: String = "",
    /** Page paper from `NotePaper`: 0 plain, 1 lines, 2 grid, 3 dots. */
    @ColumnInfo(defaultValue = "0") val paper: Int = 0,
    /** File name of a full-page background picture inside `notes/<id>/`; empty for none. */
    @ColumnInfo(defaultValue = "") val bgPhoto: String = "",
) {
    @delegate:androidx.room.Ignore
    val tagList: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (tags.isBlank()) emptyList() else tags.split(' ').filter { it.isNotBlank() }
    }

    val isTrashed: Boolean
        get() = trashedAt > 0L
}
