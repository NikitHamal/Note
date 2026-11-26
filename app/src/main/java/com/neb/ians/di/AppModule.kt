package com.neb.ians.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.neb.ians.data.local.dao.NoteDao
import com.neb.ians.data.local.database.NEBiansDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by androidx.datastore.preferences.preferencesDataStore(name = "note_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NEBiansDatabase {
        return Room.databaseBuilder(
            context,
            NEBiansDatabase::class.java,
            "note_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNoteDao(db: NEBiansDatabase): NoteDao = db.noteDao()
}
