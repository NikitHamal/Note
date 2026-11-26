package com.neb.ians.data.repository

import android.content.Context
import com.neb.ians.data.local.dao.NoteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.neb.ians.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Local notes. Everything here is offline; nothing is synced. */
@Singleton
class NotesRepository @Inject constructor(
    private val dao: NoteDao,
    @ApplicationContext private val context: Context,
) {
    val notes: Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun get(id: String): NoteEntity? = dao.get(id)

    fun blank(body: String = ""): NoteEntity {
        val now = System.currentTimeMillis()
        return NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            body = body,
            tone = 0,
            pinned = false,
            archived = false,
            trashedAt = 0L,
            tags = "",
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Saves the note and bumps `updatedAt` only when the text or tags changed. */
    suspend fun save(note: NoteEntity, touch: Boolean = true) {
        dao.upsert(if (touch) note.copy(updatedAt = System.currentTimeMillis()) else note)
    }

    private suspend fun update(ids: Collection<String>, touch: Boolean = false, change: (NoteEntity) -> NoteEntity) {
        val now = System.currentTimeMillis()
        val rows = ids.mapNotNull { dao.get(it) }.map {
            val next = change(it)
            if (touch) next.copy(updatedAt = now) else next
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    suspend fun setPinned(ids: Collection<String>, pinned: Boolean) = update(ids) { it.copy(pinned = pinned) }

    suspend fun setArchived(ids: Collection<String>, archived: Boolean) =
        update(ids) { it.copy(archived = archived, pinned = if (archived) false else it.pinned) }

    suspend fun setTone(ids: Collection<String>, tone: Int) = update(ids) { it.copy(tone = tone) }

    suspend fun trash(ids: Collection<String>) =
        update(ids) { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) }

    suspend fun restore(ids: Collection<String>) = update(ids) { it.copy(trashedAt = 0L) }

    suspend fun deleteForever(ids: Collection<String>) {
        val list = ids.toList()
        if (list.isEmpty()) return
        dao.delete(list)
        withContext(Dispatchers.IO) {
            list.forEach { id -> runCatching { File(context.filesDir, "notes/$id").deleteRecursively() } }
        }
    }

    suspend fun emptyTrash() {
        dao.emptyTrash()
        sweepOrphanedMediaDirs()
    }

    suspend fun duplicate(id: String): String? {
        val src = dao.get(id) ?: return null
        val copy = blank(src.body).copy(
            title = if (src.title.isBlank()) "" else "${src.title} copy",
            tone = src.tone,
            tags = src.tags,
            doc = src.doc,
            icon = src.icon,
            cover = src.cover,
            coverPhoto = src.coverPhoto,
            paper = src.paper,
            bgPhoto = src.bgPhoto,
        )
        // the cover and page picture are files in the note's own folder; the copy needs its own
        if (src.coverPhoto.isNotEmpty() || src.bgPhoto.isNotEmpty()) withContext(Dispatchers.IO) {
            runCatching {
                val from = File(context.filesDir, "notes/${src.id}")
                val to = File(context.filesDir, "notes/${copy.id}").also { it.mkdirs() }
                listOf(src.coverPhoto, src.bgPhoto).filter { it.isNotEmpty() }.forEach { n ->
                    File(from, n).takeIf { it.exists() }?.copyTo(File(to, n), overwrite = true)
                }
            }
        }
        dao.upsert(copy)
        return copy.id
    }

    /** Notes stay in the bin for 30 days. */
    suspend fun purgeOldTrash() {
        dao.purgeTrashedBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
        sweepOrphanedMediaDirs()
    }

    private suspend fun sweepOrphanedMediaDirs() = withContext(Dispatchers.IO) {
        runCatching {
            val notesRoot = File(context.filesDir, "notes")
            val dirs = notesRoot.listFiles() ?: return@withContext
            for (dir in dirs) {
                if (dir.isDirectory && dao.get(dir.name) == null) {
                    dir.deleteRecursively()
                }
            }
        }
    }
}
