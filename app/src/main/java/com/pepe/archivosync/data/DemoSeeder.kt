package com.pepe.archivosync.data

import com.pepe.archivosync.BuildConfig
import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toEntity
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only sample data. Uploads (the Dashboard's "recent activity") are never
 * fabricated — that list reflects real backups only. On **debug** builds it seeds
 * a few sample *downloads* so the Downloads screen isn't empty before the first
 * remote refresh; on **release** it seeds nothing.
 *
 * It also removes any legacy demo upload rows left by older versions, so the
 * Dashboard stops showing mock recent activity after updating.
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val dao: TransferDao,
) {
    suspend fun seedIfEmpty() {
        // Always drop legacy fake "recent activity" rows (real uploads use UUIDs).
        dao.deleteDemoUploads()

        // Never fabricate records in production builds.
        if (!BuildConfig.DEBUG) return
        if (dao.downloadCount() > 0) return

        val downloads = listOf(
            DownloadItem("d1", "factura_abril.pdf", FileKind.PDF, 240_000, DownloadStatus.AVAILABLE, 0),
            DownloadItem("d2", "dataset_export.csv", FileKind.CSV, 56_000_000, DownloadStatus.AVAILABLE, 0),
            DownloadItem("d3", "firmware_v3.bin", FileKind.BIN, 18_000_000, DownloadStatus.DOWNLOADED, 100),
            DownloadItem("d4", "logs_junio.txt", FileKind.TXT, 2_200_000, DownloadStatus.DOWNLOADING, 35),
            DownloadItem("d5", "fotos_2025.zip", FileKind.ZIP, 1_400_000_000, DownloadStatus.AVAILABLE, 0),
        )
        dao.upsertDownloads(downloads.map { it.toEntity() })
    }
}
