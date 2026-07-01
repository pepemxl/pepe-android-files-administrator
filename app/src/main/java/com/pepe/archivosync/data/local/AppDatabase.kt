package com.pepe.archivosync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransferEntity::class, DownloadEntity::class],
    version = 2, // v2: downloads.localPath
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        const val NAME = "archivosync.db"
    }
}
