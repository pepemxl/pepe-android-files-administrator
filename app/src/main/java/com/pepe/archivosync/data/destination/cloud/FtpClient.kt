package com.pepe.archivosync.data.destination.cloud

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import java.io.OutputStream

/** Plain FTP backend via Apache commons-net (blocking; call off the main thread). */
class FtpClient {

    private fun connect(s: AppSettings): FTPClient {
        val (host, port) = CloudIo.hostPort(s.host, 21)
        val ftp = FTPClient()
        ftp.connect(host, port)
        val user = s.accessKey.ifEmpty { "anonymous" }
        check(ftp.login(user, s.secretKey)) { "FTP login failed: ${ftp.replyString}" }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        return ftp
    }

    private fun disconnect(ftp: FTPClient) {
        runCatching { ftp.logout() }
        runCatching { ftp.disconnect() }
    }

    fun test(s: AppSettings): Boolean {
        val ftp = connect(s)
        return try { ftp.sendNoOp() } finally { disconnect(ftp) }
    }

    fun list(s: AppSettings): List<DownloadItem> {
        val ftp = connect(s)
        try {
            val dir = s.cloudPath.trim().trim('/')
            if (dir.isNotEmpty()) ftp.changeWorkingDirectory(dir)
            return (ftp.listFiles() ?: emptyArray()).filter { it.isFile }.map { f ->
                val remote = CloudIo.joinRemote(s.cloudPath, f.name)
                DownloadItem(remote, f.name, FileKind.fromName(f.name), f.size, DownloadStatus.AVAILABLE, 0, remotePath = remote)
            }
        } finally {
            disconnect(ftp)
        }
    }

    fun upload(s: AppSettings, fileName: String, sizeBytes: Long, input: InputStream, onProgress: (Long) -> Unit): String {
        val ftp = connect(s)
        try {
            val dir = s.cloudPath.trim().trim('/')
            if (dir.isNotEmpty()) {
                ftp.makeDirectory(dir)
                ftp.changeWorkingDirectory(dir)
            }
            val out = ftp.storeFileStream(fileName) ?: error("FTP store failed: ${ftp.replyString}")
            var sent = 0L
            val buffer = ByteArray(64 * 1024)
            input.use { i ->
                while (true) {
                    val read = i.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    sent += read
                    onProgress(sent)
                }
            }
            out.close()
            check(ftp.completePendingCommand()) { "FTP upload incomplete" }
            return "ftp://${s.host.trim()}/${CloudIo.joinRemote(s.cloudPath, fileName)}"
        } finally {
            disconnect(ftp)
        }
    }

    fun download(s: AppSettings, item: DownloadItem, sink: OutputStream, onProgress: (Long) -> Unit) {
        val ftp = connect(s)
        try {
            val remote = item.remotePath ?: item.id
            val stream = ftp.retrieveFileStream(remote) ?: error("FTP retrieve failed: ${ftp.replyString}")
            CloudIo.copy(stream, sink, onProgress)
            check(ftp.completePendingCommand()) { "FTP download incomplete" }
        } finally {
            disconnect(ftp)
        }
    }
}
