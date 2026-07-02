package com.pepe.archivosync.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts secret settings values (auth token, cloud access/secret keys, the
 * saved-profiles blob) with an AES-256-GCM key held in the Android Keystore —
 * hardware-backed where available, and never extractable from the app process.
 * Replaces the previous plaintext-at-rest storage in DataStore. See docs/seguridad.md H-2.
 */
@Singleton
class KeystoreSecretCipher @Inject constructor() {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    fun encrypt(plain: String): String = AesGcm.encrypt(key, plain)

    /** Decrypts a stored value; corrupt/undecryptable ciphertext yields "". */
    fun decrypt(stored: String): String =
        runCatching { AesGcm.decrypt(key, stored) }.getOrDefault("")

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "archivosync_secrets_v1"
    }
}
