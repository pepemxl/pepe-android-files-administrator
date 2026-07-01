package com.pepe.archivosync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pepe.archivosync.data.download.DownloadStorage
import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toDomain
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.repository.DestinationResolver
import com.pepe.archivosync.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream

/**
 * Pulls one remote file to device storage. Runs as a foreground service so large
 * downloads survive the app leaving the foreground; checkpoints progress into
 * Room (~5% steps) and marks the record DOWNLOADED with its local path, or
 * reverts to AVAILABLE (and deletes the partial file) on failure.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransferDao,
    private val settingsRepo: SettingsRepository,
    private val resolver: DestinationResolver,
    private val storage: DownloadStorage,
    private val notifier: BackupNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val entity = dao.downloadById(id) ?: return Result.failure()
        val item = entity.toDomain()
        val settings = settingsRepo.settings.first()
        val provider = resolver.resolve(settings)

        dao.updateDownload(id, DownloadStatus.DOWNLOADING.name, 1)
        setForeground(notifier.downloadForegroundInfo(item.name, "0%", 0))

        val file = storage.fileFor(item.name)
        var lastPct = 0
        val result = runCatching {
            FileOutputStream(file).use { out ->
                provider.download(settings, item, out) { readBytes ->
                    val pct = if (item.sizeBytes > 0) {
                        ((readBytes * 100) / item.sizeBytes).toInt().coerceIn(0, 100)
                    } else 0
                    if (pct >= lastPct + 5) {
                        lastPct = pct
                        runBlocking { dao.updateDownload(id, DownloadStatus.DOWNLOADING.name, pct) }
                    }
                }.getOrThrow()
            }
        }

        return if (result.isSuccess) {
            dao.updateDownloadState(id, DownloadStatus.DOWNLOADED.name, 100, file.absolutePath)
            Result.success()
        } else {
            runCatching { file.delete() }
            dao.updateDownloadState(id, DownloadStatus.AVAILABLE.name, 0, null)
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        fun uniqueName(id: String) = "archivosync_download_$id"
    }
}
