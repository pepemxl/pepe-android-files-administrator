package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.TransferChannel
import java.io.InputStream

/** Outcome of a connectivity probe against a remote. */
sealed interface ConnectionResult {
    data object Ok : ConnectionResult
    data class Failed(val reason: String) : ConnectionResult
}

/**
 * A backup destination. The app ships several implementations (REST API,
 * Cloud S3/FTP/WebDAV/GCS, P2P) selected at runtime by [AppSettings.remoteType].
 * New mechanisms plug in by implementing this interface — nothing else changes.
 */
interface DestinationProvider {
    val channel: TransferChannel

    /** Quick reachability/auth probe shown by "Test connection". */
    suspend fun test(settings: AppSettings): ConnectionResult

    /**
     * Uploads one stream. [onProgress] receives cumulative bytes written so the
     * worker can checkpoint and update the foreground notification.
     */
    suspend fun upload(
        settings: AppSettings,
        fileName: String,
        sizeBytes: Long,
        input: InputStream,
        onProgress: (bytesSent: Long) -> Unit,
    ): Result<String>

    /** Lists files available on the remote for the Downloads screen. */
    suspend fun list(settings: AppSettings): Result<List<DownloadItem>>
}

/** Resolves the active [DestinationProvider] for the current settings. */
interface DestinationResolver {
    fun resolve(settings: AppSettings): DestinationProvider
}
