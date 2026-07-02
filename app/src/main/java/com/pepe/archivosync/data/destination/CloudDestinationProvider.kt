package com.pepe.archivosync.data.destination

import android.content.Context
import com.pepe.archivosync.data.destination.cloud.FtpClient
import com.pepe.archivosync.data.destination.cloud.S3Client
import com.pepe.archivosync.data.destination.cloud.SftpClient
import com.pepe.archivosync.data.destination.cloud.WebDavClient
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.CloudProvider
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.repository.ConnectionResult
import com.pepe.archivosync.domain.repository.DestinationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Cloud / object-storage destination. Dispatches on [AppSettings.cloudProvider]:
 *  - `S3` / `GCS`  → S3 REST API (SigV4); GCS via its S3-interop endpoint.
 *  - `WEBDAV`      → HTTP PUT/GET/PROPFIND.
 *  - `FTP`         → plain FTP, or SFTP when the host is `sftp://…`.
 *
 * S3/GCS/WebDAV reuse the shared [OkHttpClient]; FTP/SFTP use commons-net / sshj.
 * All transports stream (no whole-file buffering) and report progress.
 */
class CloudDestinationProvider @Inject constructor(
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context,
) : DestinationProvider {

    override val channel = TransferChannel.CLOUD

    private fun isSftp(s: AppSettings) = s.host.trim().lowercase().startsWith("sftp://")

    /** SFTP client with an app-private known_hosts for TOFU host-key pinning (H-1). */
    private fun sftp() = SftpClient(File(context.filesDir, "sftp_known_hosts"))

    override suspend fun test(settings: AppSettings): ConnectionResult = withContext(Dispatchers.IO) {
        runCatching {
            when (settings.cloudProvider) {
                CloudProvider.WEBDAV -> WebDavClient(http).test(settings)
                CloudProvider.FTP -> if (isSftp(settings)) sftp().test(settings) else FtpClient().test(settings)
                CloudProvider.S3, CloudProvider.GCS -> S3Client(http).test(settings)
            }
        }.fold(
            onSuccess = { if (it) ConnectionResult.Ok else ConnectionResult.Failed("unreachable") },
            onFailure = { ConnectionResult.Failed(it.message ?: "error") },
        )
    }

    override suspend fun upload(
        settings: AppSettings,
        fileName: String,
        sizeBytes: Long,
        input: InputStream,
        onProgress: (Long) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (settings.cloudProvider) {
                CloudProvider.WEBDAV -> WebDavClient(http).upload(settings, fileName, sizeBytes, input, onProgress)
                CloudProvider.FTP -> if (isSftp(settings)) {
                    sftp().upload(settings, fileName, sizeBytes, input, onProgress)
                } else {
                    FtpClient().upload(settings, fileName, sizeBytes, input, onProgress)
                }
                CloudProvider.S3, CloudProvider.GCS -> S3Client(http).upload(settings, fileName, sizeBytes, input, onProgress)
            }
        }
    }

    override suspend fun list(settings: AppSettings): Result<List<DownloadItem>> = withContext(Dispatchers.IO) {
        runCatching {
            when (settings.cloudProvider) {
                CloudProvider.WEBDAV -> WebDavClient(http).list(settings)
                CloudProvider.FTP -> if (isSftp(settings)) sftp().list(settings) else FtpClient().list(settings)
                CloudProvider.S3, CloudProvider.GCS -> S3Client(http).list(settings)
            }
        }
    }

    override suspend fun download(
        settings: AppSettings,
        item: DownloadItem,
        sink: OutputStream,
        onProgress: (Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (settings.cloudProvider) {
                CloudProvider.WEBDAV -> WebDavClient(http).download(settings, item, sink, onProgress)
                CloudProvider.FTP -> if (isSftp(settings)) {
                    sftp().download(settings, item, sink, onProgress)
                } else {
                    FtpClient().download(settings, item, sink, onProgress)
                }
                CloudProvider.S3, CloudProvider.GCS -> S3Client(http).download(settings, item, sink, onProgress)
            }
        }
    }
}
