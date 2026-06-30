package com.pepe.archivosync.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

/**
 * Streams an [InputStream] to the network in fixed-size chunks, reporting
 * cumulative bytes written. Avoids loading large files (200 MB+) into memory.
 */
class ProgressRequestBody(
    private val input: InputStream,
    private val contentLength: Long,
    private val contentType: MediaType?,
    private val onProgress: (Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER)
        var uploaded = 0L
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read
                onProgress(uploaded)
            }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024 // 64 KiB
    }
}
