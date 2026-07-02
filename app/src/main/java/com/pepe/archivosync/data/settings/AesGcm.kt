package com.pepe.archivosync.data.settings

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM string encryption with a versioned, base64 envelope. Pure JCE (no
 * Android framework deps) so it is unit-testable on the JVM; [KeystoreSecretCipher]
 * supplies a hardware-backed key.
 *
 * A value that does not carry [PREFIX] is returned unchanged on decrypt, so any
 * pre-existing plaintext migrates transparently (re-encrypted on the next write).
 * See docs/seguridad.md H-2.
 */
object AesGcm {
    private const val PREFIX = "enc:v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, plain: String): String {
        if (plain.isEmpty()) return plain
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Init without an IV: both the Android Keystore and the JVM provider then
        // generate a fresh random IV (the Keystore forbids caller-supplied IVs).
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().withoutPadding().encodeToString(iv + ct)
    }

    fun decrypt(key: SecretKey, stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored // legacy plaintext / empty
        val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        val iv = raw.copyOfRange(0, IV_BYTES)
        val ct = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
