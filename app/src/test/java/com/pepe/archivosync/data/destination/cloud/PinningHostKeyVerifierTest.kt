package com.pepe.archivosync.data.destination.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.PublicKey

/** Validates the TOFU host-key logic that replaced PromiscuousVerifier (H-1). */
class PinningHostKeyVerifierTest {

    private fun rsaKey(): PublicKey =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public

    @Test
    fun pinsOnFirstUseMatchesSameKeyAndRejectsChangedKey() {
        val file = File.createTempFile("known_hosts", "").apply { delete() }
        try {
            val verifier = PinningHostKeyVerifier(file)
            val keyA = rsaKey()
            val keyB = rsaKey()

            // First contact with an unseen host: accepted and pinned.
            assertTrue(verifier.verify("sftp.example.com", 22, keyA))
            // Same host + same key on later connects: still accepted.
            assertTrue(verifier.verify("sftp.example.com", 22, keyA))
            // Same host, a DIFFERENT key (possible MITM): rejected.
            assertFalse(verifier.verify("sftp.example.com", 22, keyB))
            // A different host is pinned independently (accept-new).
            assertTrue(verifier.verify("other.example.com", 22, keyB))
        } finally {
            file.delete()
        }
    }
}
