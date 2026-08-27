package com.scriverse.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretVault(private val alias: String = "scriverse.app.secrets.v1") {
    fun encrypt(plaintext: CharArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = plaintext.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = cipher.doFinal(bytes)
            val payload = cipher.iv + encrypted
            Base64.getEncoder().encodeToString(payload)
        } finally {
            bytes.fill(0)
            plaintext.fill('\u0000')
        }
    }

    fun decrypt(encoded: String): CharArray {
        val payload = Base64.getDecoder().decode(encoded)
        require(payload.size > IV_BYTES) { "Invalid encrypted payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
            .toString(Charsets.UTF_8)
            .toCharArray()
    }

    fun deleteKey() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

object SecretRedactor {
    private val bearer = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*")
    private val secretField = Regex(
        "(?i)(\\\"?(?:token|password|api[_-]?key|secret)\\\"?\\s*[:=]\\s*\\\"?)[^\\\"\\s,}]+",
    )

    fun redact(message: String): String = message
        .replace(bearer, "$1[REDACTED]")
        .replace(secretField, "$1[REDACTED]")
        .take(MAX_LOG_CHARS)

    private const val MAX_LOG_CHARS = 8_192
}
