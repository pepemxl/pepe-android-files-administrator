package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.TransferChannel
import java.io.InputStream
import java.io.OutputStream

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

    /**
     * Streams [item] from the remote into [sink] (the caller owns and closes it),
     * reporting cumulative bytes read via [onProgress] for checkpointing.
     */
    suspend fun download(
        settings: AppSettings,
        item: DownloadItem,
        sink: OutputStream,
        onProgress: (bytesRead: Long) -> Unit,
    ): Result<Unit>
}

/** Resolves the active [DestinationProvider] for the current settings. */
interface DestinationResolver {
    fun resolve(settings: AppSettings): DestinationProvider
}
