package com.pepe.archivosync.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Validates the at-rest secret encryption used for token / cloud keys (H-2). */
class AesGcmTest {

    private fun aesKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun roundTripsAndDoesNotStorePlaintext() {
        val key = aesKey()
        val secret = "sk-abc123-super-secret-token"

        val enc = AesGcm.encrypt(key, secret)

        assertTrue("must carry the versioned envelope", enc.startsWith("enc:v1:"))
        assertFalse("ciphertext must not contain the plaintext", enc.contains(secret))
        assertEquals(secret, AesGcm.decrypt(key, enc))
    }

    @Test
    fun eachEncryptionUsesAFreshIv() {
        val key = aesKey()
        // Same input twice → different ciphertext (random IV), both decrypt back.
        val a = AesGcm.encrypt(key, "same-value")
        val b = AesGcm.encrypt(key, "same-value")
        assertNotEquals(a, b)
        assertEquals("same-value", AesGcm.decrypt(key, a))
        assertEquals("same-value", AesGcm.decrypt(key, b))
    }

    @Test
    fun legacyPlaintextIsReturnedUnchangedOnDecrypt() {
        val key = aesKey()
        // A value without the envelope is pre-existing plaintext: pass it through
        // so old installs keep working (it gets re-encrypted on the next write).
        assertEquals("legacy-token", AesGcm.decrypt(key, "legacy-token"))
        assertEquals("", AesGcm.decrypt(key, ""))
    }

    @Test
    fun emptyStringIsNotEnveloped() {
        assertEquals("", AesGcm.encrypt(aesKey(), ""))
    }
}
