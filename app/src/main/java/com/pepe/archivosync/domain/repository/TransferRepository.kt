package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import kotlinx.coroutines.flow.Flow

/** CRUD + reactive streams for upload/download records (Room-backed). */
interface TransferRepository {
    fun observeUploads(): Flow<List<TransferItem>>
    fun observeDownloads(): Flow<List<DownloadItem>>

    suspend fun enqueueUploads(items: List<TransferItem>)
    suspend fun updateProgress(id: String, progress: Int, status: TransferStatus)
    suspend fun markFailed(id: String, error: String)
    suspend fun retry(id: String)

    suspend fun seedDownloads(items: List<DownloadItem>)
    suspend fun startDownload(id: String)

    /** Replaces the Downloads list with the remote listing, keeping already-downloaded files. */
    suspend fun refreshDownloads(): Result<Unit>
}
