package com.pepe.archivosync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM uploads ORDER BY createdAt DESC")
    fun observeUploads(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM uploads WHERE status != 'DONE' ORDER BY createdAt")
    suspend fun pendingUploads(): List<TransferEntity>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun uploadById(id: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUploads(items: List<TransferEntity>)

    @Query("UPDATE uploads SET progress = :progress, status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, status: String, now: Long)

    @Query("UPDATE uploads SET status = 'FAILED', error = :error, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: String, error: String, now: Long)

    @Query("UPDATE uploads SET status = 'QUEUED', progress = 5, error = NULL, updatedAt = :now WHERE id = :id")
    suspend fun resetForRetry(id: String, now: Long)

    // ---- downloads ----
    @Query("SELECT * FROM downloads ORDER BY name")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun allDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun downloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloads(items: List<DownloadEntity>)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateDownload(id: String, status: String, progress: Int)

    @Query("UPDATE downloads SET status = :status, progress = :progress, localPath = :localPath WHERE id = :id")
    suspend fun updateDownloadState(id: String, status: String, progress: Int, localPath: String?)

    @Query("DELETE FROM downloads")
    suspend fun deleteAllDownloads()

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun downloadCount(): Int
}
