package com.pepe.archivosync.domain.usecase

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.BackupScheduler
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.domain.repository.TransferRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Turns a selection of source files into queued upload records and schedules a
 * background backup run. Returns the created record ids.
 */
class BackupSelectionUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val transfers: TransferRepository,
    private val scheduler: BackupScheduler,
) {
    suspend operator fun invoke(files: List<FileNode>): List<String> {
        if (files.isEmpty()) return emptyList()
        val current: AppSettings = settings.settings.first()
        val channel = when (current.remoteType) {
            com.pepe.archivosync.domain.model.RemoteType.REST -> TransferChannel.REST
            com.pepe.archivosync.domain.model.RemoteType.CLOUD -> TransferChannel.CLOUD
        }
        val destination = current.remoteLabel.substringAfter("://")

        val records = files.filter { !it.isDirectory }.map { node ->
            TransferItem(
                id = UUID.randomUUID().toString(),
                name = node.name,
                kind = node.kind,
                sizeBytes = node.sizeBytes,
                status = TransferStatus.QUEUED,
                progress = 0,
                destination = destination,
                channel = channel,
                sourceUri = node.id,
            )
        }
        transfers.enqueueUploads(records)
        scheduler.scheduleBackup(records.map { it.id })
        return records.map { it.id }
    }
}
