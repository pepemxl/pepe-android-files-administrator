package com.pepe.archivosync.data.destination.cloud

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * Trust-on-first-use SSH host-key verifier backed by an app-private known_hosts
 * file. On an unseen host it records the key's SHA-256 fingerprint and accepts;
 * on a known host it requires the same key; on a CHANGED key it refuses (possible
 * MITM). Replaces sshj's [net.schmizz.sshj.transport.verification.PromiscuousVerifier],
 * which accepted any key without record. See docs/seguridad.md H-1.
 *
 * The file holds one `host:port SHA256:<base64>` line per pinned host.
 */
class PinningHostKeyVerifier(private val file: File) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val id = "$hostname:$port"
        val fp = fingerprint(key)
        synchronized(lock) {
            return when (read()[id]) {
                null -> { append(id, fp); true } // accept-new: pin on first use
                fp -> true                        // matches the pinned key
                else -> false                     // key changed → reject
            }
        }
    }

    /** sshj (>= 0.31) queries this to prefer known key algorithms; none pinned. */
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    /** OpenSSH-style `SHA256:<base64>` fingerprint of the SSH-encoded public key. */
    private fun fingerprint(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun read(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val sp = t.indexOf(' ')
            if (sp <= 0) null else t.substring(0, sp) to t.substring(sp + 1).trim()
        }.toMap()
    }

    private fun append(id: String, fingerprint: String) {
        file.parentFile?.mkdirs()
        file.appendText("$id $fingerprint\n")
    }

    private companion object {
        // Guards read/append across the parallel upload/download workers.
        val lock = Any()
    }
}
