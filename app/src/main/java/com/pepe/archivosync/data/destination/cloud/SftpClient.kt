package com.pepe.archivosync.data.destination.cloud

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/**
 * SFTP backend via sshj (blocking; call off the main thread). Used when the
 * cloud provider is "FTP" and the host is `sftp://…`.
 *
 * The host key is verified trust-on-first-use against [knownHostsFile] via
 * [PinningHostKeyVerifier]: unseen hosts are pinned, a changed key aborts the
 * connection (possible MITM). See docs/seguridad.md H-1.
 */
class SftpClient(private val knownHostsFile: File) {

    private inline fun <T> withSftp(s: AppSettings, block: (SFTPClient) -> T): T {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PinningHostKeyVerifier(knownHostsFile))
        val (host, port) = CloudIo.hostPort(s.host, 22)
        ssh.connect(host, port)
        try {
            ssh.authPassword(s.accessKey, s.secretKey)
            ssh.newSFTPClient().use { sftp -> return block(sftp) }
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    fun test(s: AppSettings): Boolean = withSftp(s) { true }

    fun list(s: AppSettings): List<DownloadItem> = withSftp(s) { sftp ->
        val dir = s.cloudPath.trim().trim('/').ifEmpty { "." }
        sftp.ls(dir).filter { !it.isDirectory }.map { r ->
            val remote = CloudIo.joinRemote(s.cloudPath, r.name)
            DownloadItem(remote, r.name, FileKind.fromName(r.name), r.attributes.size, DownloadStatus.AVAILABLE, 0, remotePath = remote)
        }
    }

    fun upload(s: AppSettings, fileName: String, sizeBytes: Long, input: InputStream, onProgress: (Long) -> Unit): String =
        withSftp(s) { sftp ->
            val dir = s.cloudPath.trim().trim('/')
            if (dir.isNotEmpty()) runCatching { sftp.mkdirs(dir) }
            val remote = CloudIo.joinRemote(s.cloudPath, fileName)
            val rf = sftp.open(remote, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            try {
                var offset = 0L
                val buffer = ByteArray(64 * 1024)
                input.use { i ->
                    while (true) {
                        val read = i.read(buffer)
                        if (read == -1) break
                        rf.write(offset, buffer, 0, read)
                        offset += read
                        onProgress(offset)
                    }
                }
            } finally {
                rf.close()
            }
            "sftp://${CloudIo.hostPort(s.host, 22).first}/$remote"
        }

    fun download(s: AppSettings, item: DownloadItem, sink: OutputStream, onProgress: (Long) -> Unit) =
        withSftp(s) { sftp ->
            val remote = item.remotePath ?: item.id
            val rf = sftp.open(remote)
            try {
                var offset = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = rf.read(offset, buffer, 0, buffer.size)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    offset += read
                    onProgress(offset)
                }
                sink.flush()
            } finally {
                rf.close()
            }
        }
}
