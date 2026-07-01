package com.pepe.archivosync.data.destination

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.repository.ConnectionResult
import com.pepe.archivosync.domain.repository.DestinationProvider
import com.pepe.archivosync.data.remote.ArchivoSyncApi
import com.pepe.archivosync.data.remote.ProgressRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Backup over an HTTP REST API. Lists via Retrofit; uploads via a streaming,
 * progress-reporting OkHttp PUT/POST so 200 MB files never hit the heap.
 */
class RestDestinationProvider @Inject constructor(
    private val api: ArchivoSyncApi,
    private val client: OkHttpClient,
) : DestinationProvider {

    override val channel = TransferChannel.REST

    private fun authHeader(settings: AppSettings): String? =
        settings.token.takeIf { it.isNotBlank() }

    private fun joinUrl(base: String, path: String): String =
        base.trimEnd('/') + "/" + path.trimStart('/')

    override suspend fun test(settings: AppSettings): ConnectionResult = withContext(Dispatchers.IO) {
        runCatching {
            api.listFiles(joinUrl(settings.baseUrl, settings.listEndpoint), authHeader(settings))
        }.fold(
            onSuccess = { ConnectionResult.Ok },
            onFailure = { ConnectionResult.Failed(it.message ?: "unreachable") },
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
            val url = joinUrl(settings.baseUrl, settings.uploadEndpoint)
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            val body = ProgressRequestBody(input, sizeBytes, mediaType, onProgress)
            val request = Request.Builder()
                .url(url)
                .apply { authHeader(settings)?.let { header("Authorization", it) } }
                .header("X-File-Name", fileName)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.header("Location") ?: url
            }
        }
    }

    override suspend fun list(settings: AppSettings): Result<List<DownloadItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.listFiles(joinUrl(settings.baseUrl, settings.listEndpoint), authHeader(settings))
                    .map { dto ->
                        DownloadItem(
                            id = dto.id ?: dto.name,
                            name = dto.name,
                            kind = FileKind.fromName(dto.name),
                            sizeBytes = dto.sizeBytes,
                            status = DownloadStatus.AVAILABLE,
                            progress = 0,
                            remotePath = dto.remotePath,
                        )
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
            // remotePath is an absolute API path (e.g. /v1/files/{id}/content);
            // resolve it against the base URL's origin. Fall back to a
            // conventional content endpoint if the listing omitted it.
            val base = settings.baseUrl.toHttpUrlOrNull() ?: error("invalid base URL")
            val path = item.remotePath ?: "/${settings.listEndpoint.trim('/')}/${item.id}/content"
            val url = base.resolve(path) ?: error("invalid download URL")

            val request = Request.Builder()
                .url(url)
                .apply { authHeader(settings)?.let { header("Authorization", it) } }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty response body")
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                    sink.flush()
                }
            }
            Unit
        }
    }
}
