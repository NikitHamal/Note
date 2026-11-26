package com.neb.ians.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neb.ians.data.local.dao.NoteDao
import com.neb.ians.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NEBiansDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
