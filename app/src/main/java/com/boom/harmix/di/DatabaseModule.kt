package com.boom.harmix.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boom.harmix.data.local.HarmixDatabase
import com.boom.harmix.data.local.dao.PlaylistDao
import com.boom.harmix.data.local.dao.SavedSongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHarmixDatabase(@ApplicationContext context: Context): HarmixDatabase =
        Room.databaseBuilder(context, HarmixDatabase::class.java, "harmix.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_songs ADD COLUMN liked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN remoteId TEXT")
        }
    }

    @Provides
    fun provideSavedSongDao(database: HarmixDatabase): SavedSongDao = database.savedSongDao()

    @Provides
    fun providePlaylistDao(database: HarmixDatabase): PlaylistDao = database.playlistDao()
}
