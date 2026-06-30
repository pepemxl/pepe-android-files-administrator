package com.pepe.archivosync.data.repository

import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toDomain
import com.pepe.archivosync.data.local.toEntity
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferDao,
) : TransferRepository {

    override fun observeUploads(): Flow<List<TransferItem>> =
        dao.observeUploads().map { list -> list.map { it.toDomain() } }

    override fun observeDownloads(): Flow<List<DownloadItem>> =
        dao.observeDownloads().map { list -> list.map { it.toDomain() } }

    override suspend fun enqueueUploads(items: List<TransferItem>) {
        dao.upsertUploads(items.map { it.toEntity() })
    }

    override suspend fun updateProgress(id: String, progress: Int, status: TransferStatus) {
        dao.updateProgress(id, progress, status.name, System.currentTimeMillis())
    }

    override suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error, System.currentTimeMillis())
    }

    override suspend fun retry(id: String) {
        dao.resetForRetry(id, System.currentTimeMillis())
    }

    override suspend fun seedDownloads(items: List<DownloadItem>) {
        dao.upsertDownloads(items.map { it.toEntity() })
    }

    override suspend fun startDownload(id: String) {
        dao.updateDownload(id, DownloadStatus.DOWNLOADING.name, 4)
    }
}
