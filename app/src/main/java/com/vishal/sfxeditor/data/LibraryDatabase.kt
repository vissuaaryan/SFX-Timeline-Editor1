
package com.vishal.sfxeditor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AudioAssetEntity::class,
        AudioCategoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: LibraryDatabase? = null

        fun getDatabase(context: Context): LibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "sfx_library_database"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
