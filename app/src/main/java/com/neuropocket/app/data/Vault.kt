package com.neuropocket.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ключи API — только в шифрованном хранилище (Android Keystore),
 * никогда открытым текстом рядом с остальными настройками.
 */
object KeyVault {
    private const val PREF = "np_secret_keys"

    private fun prefs(ctx: Context): SharedPreferences {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, PREF, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun get(ctx: Context, id: String): String? = try {
        prefs(ctx).getString("k_$id", null)
    } catch (_: Exception) { null }

    fun put(ctx: Context, id: String, key: String) { try {
        prefs(ctx).edit().putString("k_$id", key).apply()
    } catch (_: Exception) {} }

    fun remove(ctx: Context, id: String) { try {
        prefs(ctx).edit().remove("k_$id").apply()
    } catch (_: Exception) {} }

    /** P0.5: корректная очистка всех ключей (через EncryptedSharedPreferences). */
    fun clear(ctx: Context) { try {
        prefs(ctx).edit().clear().apply()
    } catch (_: Exception) {} }
}

/**
 * Бэкап всего состояния в один JSON (папка models/).
 * Ключи API — только в AES-GCM блоке под паролем, иначе не включаются.
 */
object Backup {
    fun make(
        ctx: Context,
        personas: String,
        sessions: String,
        msgmap: String,
        chars: String,
        posts: String,
        comments: String,
        providers: String,
        settings: Map<String, Any>,
        withKeys: Boolean,
        password: String,
        fullPassword: String = ""
    ): File {
        val root = JSONObject()
        root.put("app", "NeuroPocket")
        root.put("v", 1)
        val data = JSONObject()
        data.put("personas", personas)
        data.put("sessions", sessions)
        data.put("msgmap", msgmap)
        data.put("chars", chars)
        data.put("posts", posts)
        data.put("comments", comments)
        data.put("providers", providers)
        // P0.4: новый корректный формат — settings как JSONObject (не String).
        // Старые бэкапы со String(JSON) читаем в parse() для backward compat.
        val st = com.neuropocket.app.core.BackupSettings.encode(settings)
        data.put("settings", st)
        root.put("data", data)
        if (withKeys && password.length >= 4) {
            val keys = JSONObject()
            // соберём ключи из vault по id из providers
            val prov = org.json.JSONArray(providers)
            for (i in 0 until prov.length()) {
                val id = prov.getJSONObject(i).optString("id")
                KeyVault.get(ctx, id)?.let { if (it.isNotEmpty()) keys.put(id, it) }
            }
            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val enc = aesEncrypt(keys.toString().toByteArray(Charsets.UTF_8), password, salt)
            root.put("keysSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            root.put("keysIv", Base64.encodeToString(enc.first, Base64.NO_WRAP))
            root.put("keysData", Base64.encodeToString(enc.second, Base64.NO_WRAP))
        }
        if (fullPassword.length >= 4) {
            // шифруем ВЕСЬ бэкап целиком (включая ключи, если их добавили выше)
            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val enc = aesEncrypt(root.toString().toByteArray(Charsets.UTF_8), fullPassword, salt)
            val outer = JSONObject()
            outer.put("app", "NeuroPocket")
            outer.put("v", 2)
            outer.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            outer.put("iv", Base64.encodeToString(enc.first, Base64.NO_WRAP))
            outer.put("data", Base64.encodeToString(enc.second, Base64.NO_WRAP))
            val f = File(ctx.getExternalFilesDir(null), "models/NeuroPocket-backup-${System.currentTimeMillis()}.enc.json")
            f.writeText(outer.toString())
            return f
        }
        val f = File(ctx.getExternalFilesDir(null), "models/NeuroPocket-backup-${System.currentTimeMillis()}.json")
        f.writeText(root.toString())
        return f
    }

    data class Parsed(
        val personas: String, val sessions: String, val msgmap: String,
        val chars: String, val posts: String, val comments: String, val providers: String,
        val settings: Map<String, String>, val keys: Map<String, String>
    )

    fun parse(text: String, password: String): Parsed {
        var root = JSONObject(text)
        require(root.optString("app") == "NeuroPocket") { "не наш бэкап" }
        if (root.optInt("v") == 2) {
            require(password.length >= 4) { "бэкап зашифрован: нужен пароль" }
            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val cipher = Base64.decode(root.getString("data"), Base64.NO_WRAP)
            try {
                root = JSONObject(String(aesDecrypt(cipher, password, salt, iv), Charsets.UTF_8))
            } catch (_: Exception) {
                throw Exception("неверный пароль или битый файл")
            }
            require(root.optString("app") == "NeuroPocket") { "неверный пароль" }
        }
        val data = root.getJSONObject("data")
        val keys = mutableMapOf<String, String>()
        if (root.has("keysData")) {
            require(password.length >= 4) { "бэкап с ключами: нужен пароль" }
            val salt = Base64.decode(root.getString("keysSalt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("keysIv"), Base64.NO_WRAP)
            val cipher = Base64.decode(root.getString("keysData"), Base64.NO_WRAP)
            val plain = aesDecrypt(cipher, password, salt, iv)
            val jo = JSONObject(String(plain, Charsets.UTF_8))
            for (k in jo.keys()) keys[k] = jo.getString(k)
        }
        val st: Map<String, String> = com.neuropocket.app.core.BackupSettings.decode(data)
        return Parsed(
            data.optString("personas", "[]"), data.optString("sessions", "[]"),
            data.optString("msgmap", "{}"), data.optString("chars", "[]"),
            data.optString("posts", "[]"), data.optString("comments", "[]"),
            data.optString("providers", "[]"),
            st, keys
        )
    }

    private fun aesKey(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    private fun aesEncrypt(data: ByteArray, password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, aesKey(password, salt), GCMParameterSpec(128, iv))
        return iv to c.doFinal(data)
    }

    private fun aesDecrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, aesKey(password, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data)
    }
}
