package com.neuropocket.app.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64 as JBase64

/**
 * Чистый AES-GCM backup crypto (JVM + Android), без android.util.Base64.
 * P0.4: позволяет unit-тестировать round-trip без Context.
 * Параметры совпадают с Vault.kt: PBKDF2-HMAC-SHA256 120k, AES-256-GCM.
 */
object BackupCrypto {
    data class Sealed(val saltB64: String, val ivB64: String, val dataB64: String)

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: ByteArray, password: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): Sealed {
        require(password.length >= 4) { "пароль мин. 4 символа" }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val enc = c.doFinal(plain)
        val e = JBase64.getEncoder()
        return Sealed(e.encodeToString(salt), e.encodeToString(iv), e.encodeToString(enc))
    }

    fun decrypt(sealed: Sealed, password: String): ByteArray {
        require(password.length >= 4) { "нужен пароль" }
        val d = JBase64.getDecoder()
        val salt = d.decode(sealed.saltB64)
        val iv = d.decode(sealed.ivB64)
        val cipher = d.decode(sealed.dataB64)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return c.doFinal(cipher)
    }
}
