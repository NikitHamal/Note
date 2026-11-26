package com.neb.ians.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neb.ians.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM notes WHERE trashedAt > 0 AND trashedAt < :before")
    suspend fun purgeTrashedBefore(before: Long)

    @Query("DELETE FROM notes WHERE trashedAt > 0")
    suspend fun emptyTrash()
}
