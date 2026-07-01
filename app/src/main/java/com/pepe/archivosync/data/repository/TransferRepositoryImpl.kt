package com.pepe.archivosync.data.repository

import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toDomain
import com.pepe.archivosync.data.local.toEntity
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.DestinationResolver
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferDao,
    private val resolver: DestinationResolver,
    private val settingsRepo: SettingsRepository,
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

    override suspend fun refreshDownloads(): Result<Unit> {
        val settings = settingsRepo.settings.first()
        val remote = resolver.resolve(settings).list(settings).getOrElse { return Result.failure(it) }
        // Preserve local state (path/status/progress) for files already fetched.
        val previous = dao.allDownloads().associateBy { it.id }
        val merged = remote.map { item ->
            val prev = previous[item.id]
            if (prev != null && prev.status == DownloadStatus.DOWNLOADED.name) {
                item.copy(status = DownloadStatus.DOWNLOADED, progress = 100, localPath = prev.localPath)
            } else {
                item
            }
        }
        dao.deleteAllDownloads()
        dao.upsertDownloads(merged.map { it.toEntity() })
        return Result.success(Unit)
    }
}
