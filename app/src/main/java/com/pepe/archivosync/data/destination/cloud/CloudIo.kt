package com.pepe.archivosync.data.destination.cloud

import java.io.InputStream
import java.io.OutputStream

/** Shared streaming copy that reports cumulative bytes (for download progress). */
internal object CloudIo {
    fun copy(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        input.use { i ->
            while (true) {
                val read = i.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                total += read
                onProgress(total)
            }
        }
        output.flush()
    }

    /** Splits `[scheme://]host[:port][/..]` into (host, port). */
    fun hostPort(raw: String, defaultPort: Int): Pair<String, Int> {
        var s = raw.trim()
        val scheme = s.indexOf("://")
        if (scheme >= 0) s = s.substring(scheme + 3)
        s = s.substringBefore('/')
        val colon = s.lastIndexOf(':')
        if (colon > 0) {
            val port = s.substring(colon + 1).toIntOrNull()
            if (port != null) return s.substring(0, colon) to port
        }
        return s to defaultPort
    }

    /** Joins a prefix and a name into a clean POSIX remote path. */
    fun joinRemote(prefix: String, name: String): String {
        val p = prefix.trim().trim('/')
        val n = name.trim().trimStart('/')
        return if (p.isEmpty()) n else "$p/$n"
    }
}
