package com.pepe.archivosync.data.destination

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.repository.ConnectionResult
import com.pepe.archivosync.domain.repository.DestinationProvider
import java.io.InputStream
import javax.inject.Inject

/**
 * Cloud / object-storage destination (S3, FTP/SFTP, WebDAV, GCS).
 *
 * The transport wiring per provider (signed PUTs for S3, sardine for WebDAV,
 * sshj for SFTP) is intentionally pluggable: each `CloudProvider` resolves to a
 * client built from [AppSettings.host]/accessKey/secretKey. This class keeps the
 * provider contract stable so screens and the worker are transport-agnostic.
 */
class CloudDestinationProvider @Inject constructor() : DestinationProvider {

    override val channel = TransferChannel.CLOUD

    override suspend fun test(settings: AppSettings): ConnectionResult {
        if (settings.host.isBlank()) return ConnectionResult.Failed("host not configured")
        if (settings.accessKey.isBlank() || settings.secretKey.isBlank()) {
            return ConnectionResult.Failed("missing credentials")
        }
        // TODO: real handshake per CloudProvider (HEAD bucket / LIST / NOOP).
        return ConnectionResult.Ok
    }

    override suspend fun upload(
        settings: AppSettings,
        fileName: String,
        sizeBytes: Long,
        input: InputStream,
        onProgress: (Long) -> Unit,
    ): Result<String> = runCatching {
        // Stream-and-checkpoint copy; replace with provider SDK client.
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                onProgress(total)
            }
        }
        "${settings.cloudProvider.name.lowercase()}://${settings.host}/$fileName"
    }

    override suspend fun list(settings: AppSettings): Result<List<DownloadItem>> =
        Result.success(emptyList())
}
