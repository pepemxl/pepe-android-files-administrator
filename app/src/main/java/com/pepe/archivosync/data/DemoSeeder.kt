package com.pepe.archivosync.data

import com.pepe.archivosync.BuildConfig
import com.pepe.archivosync.data.local.TransferDao
import com.pepe.archivosync.data.local.toEntity
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only sample data. On **debug** builds it seeds the local DB with
 * representative records on first launch so the Dashboard / Uploads / Downloads
 * screens are populated out of the box (mirrors the design's sample data).
 *
 * On **release** builds it is a no-op: the app starts empty and fills in with
 * real records as the user uploads (Room) and opens Downloads (real remote list
 * via [com.pepe.archivosync.domain.repository.TransferRepository.refreshDownloads]).
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val dao: TransferDao,
) {
    suspend fun seedIfEmpty() {
        // Never fabricate records in production builds.
        if (!BuildConfig.DEBUG) return
        if (dao.downloadCount() > 0) return

        val dest = "api.midominio.com/upload"
        val uploads = listOf(
            TransferItem("u1", "IMG_20260628_0921.jpg", FileKind.IMAGE, 3_400_000, TransferStatus.DONE, 100, dest, TransferChannel.REST),
            TransferItem("u2", "backup_full_2026-06-28.zip", FileKind.ZIP, 820_000_000, TransferStatus.UPLOADING, 58, dest, TransferChannel.REST),
            TransferItem("u3", "datos.csv", FileKind.CSV, 1_200_000, TransferStatus.FAILED, 42, dest, TransferChannel.REST, error = "timeout"),
            TransferItem("u4", "presupuesto.xlsx", FileKind.XLSX, 88_000, TransferStatus.QUEUED, 0, dest, TransferChannel.REST),
            TransferItem("u5", "db_dump.sql", FileKind.DB, 340_000_000, TransferStatus.DONE, 100, dest, TransferChannel.REST),
        )
        dao.upsertUploads(uploads.map { it.toEntity() })

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
