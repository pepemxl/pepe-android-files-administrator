package com.pepe.archivosync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toDomain
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.DestinationResolver
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.domain.repository.SourceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Background backup. Runs as a foreground service so it survives the app being
 * swept from Recents. Resumes from Room: it only processes uploads that are not
 * yet DONE, marking each file VERIFIED/FAILED as it goes (per-file checkpoint).
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransferDao,
    private val settingsRepo: SettingsRepository,
    private val source: SourceRepository,
    private val resolver: DestinationResolver,
    private val notifier: BackupNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepo.settings.first()
        val provider = resolver.resolve(settings)
        val pending = dao.pendingUploads().map { it.toDomain() }
        if (pending.isEmpty()) return Result.success()

        setForeground(notifier.foregroundInfo("ArchivoSync", "0/${pending.size}", 0))

        var failures = 0
        pending.forEachIndexed { index, item ->
            if (isStopped) return Result.retry()

            setForeground(
                notifier.foregroundInfo(
                    title = item.name,
                    content = "${index + 1}/${pending.size}",
                    progress = (index * 100) / pending.size,
                ),
            )
            dao.updateProgress(item.id, 1, TransferStatus.UPLOADING.name, now())

            val uri = item.sourceUri
            val input = uri?.let { runCatching { source.openInput(it) }.getOrNull() }
            if (input == null) {
                dao.markFailed(item.id, "source unavailable", now())
                failures++
                return@forEachIndexed
            }

            var lastPct = 0
            val result = provider.upload(settings, item.name, item.sizeBytes, input) { sent ->
                val pct = if (item.sizeBytes > 0) ((sent * 100) / item.sizeBytes).toInt() else 0
                // Checkpoint in ~5% steps to avoid hammering the DB on large files.
                if (pct >= lastPct + 5) {
                    lastPct = pct
                    runBlocking { dao.updateProgress(item.id, pct, TransferStatus.UPLOADING.name, now()) }
                }
            }
            if (result.isSuccess) {
                dao.updateProgress(item.id, 100, TransferStatus.DONE.name, now())
            } else {
                dao.markFailed(item.id, result.exceptionOrNull()?.message ?: "upload failed", now())
                failures++
            }
        }

        return if (failures == 0) Result.success() else Result.success()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val UNIQUE_NAME = "archivosync_backup"
        const val KEY_TRANSFER_IDS = "transfer_ids"
    }
}
