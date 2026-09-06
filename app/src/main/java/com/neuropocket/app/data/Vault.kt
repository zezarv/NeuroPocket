package com.neuropocket.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.neuropocket.app.core.BackupCrypto
import org.json.JSONObject
import java.io.File

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
 *
 * Red-team J: ЕДИНСТВЕННАЯ crypto-реализация — core.BackupCrypto
 * (её же тестируют unit tests). Здесь только JSON-каркас + vault/file IO.
 * Wire format НЕ менялся: те же поля app/v/data/keysSalt/keysIv/keysData/
 * salt/iv/data; settings — JSONObject (старый String(JSON) читается в parse).
 * Base64: java.util (minSdk 28) вместо android.util — wire-совместимо
 * (тот же алфавит/паддинг, NO_WRAP) и JVM-testable.
 */
object Backup {
    /**
     * Чистый каркас plain-бэкапа (без Context — тестируем).
     * @param keysJson собранный JSON ключей API или null (без ключей)
     * @param keysPassword пароль для keys-блока (игнорируется при keysJson==null)
     */
    fun buildPlainRoot(
        personas: String,
        sessions: String,
        msgmap: String,
        chars: String,
        posts: String,
        comments: String,
        providers: String,
        settings: Map<String, Any>,
        keysJson: String?,
        keysPassword: String
    ): JSONObject {
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
        data.put("settings", com.neuropocket.app.core.BackupSettings.encode(settings))
        root.put("data", data)
        if (keysJson != null && keysPassword.length >= 4) {
            val sealed = BackupCrypto.encrypt(keysJson.toByteArray(Charsets.UTF_8), keysPassword)
            root.put("keysSalt", sealed.saltB64)
            root.put("keysIv", sealed.ivB64)
            root.put("keysData", sealed.dataB64)
        }
        return root
    }

    /** Чистая обёртка full-шифрования (без Context — тестируема). */
    fun wrapEncrypted(root: JSONObject, fullPassword: String): JSONObject {
        val sealed = BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), fullPassword)
        val outer = JSONObject()
        outer.put("app", "NeuroPocket")
        outer.put("v", 2)
        outer.put("salt", sealed.saltB64)
        outer.put("iv", sealed.ivB64)
        outer.put("data", sealed.dataB64)
        return outer
    }

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
        var keysJson: String? = null
        if (withKeys && password.length >= 4) {
            val keys = JSONObject()
            // соберём ключи из vault по id из providers
            val prov = org.json.JSONArray(providers)
            for (i in 0 until prov.length()) {
                val id = prov.getJSONObject(i).optString("id")
                KeyVault.get(ctx, id)?.let { if (it.isNotEmpty()) keys.put(id, it) }
            }
            keysJson = keys.toString()
        }
        val root = buildPlainRoot(
            personas, sessions, msgmap, chars, posts, comments, providers,
            settings, keysJson, password
        )
        if (fullPassword.length >= 4) {
            // шифруем ВЕСЬ бэкап целиком (включая ключи, если их добавили выше)
            val outer = wrapEncrypted(root, fullPassword)
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
            try {
                val sealed = BackupCrypto.Sealed(
                    root.getString("salt"), root.getString("iv"), root.getString("data")
                )
                root = JSONObject(String(BackupCrypto.decrypt(sealed, password), Charsets.UTF_8))
            } catch (_: Exception) {
                throw Exception("неверный пароль или битый файл")
            }
            require(root.optString("app") == "NeuroPocket") { "неверный пароль" }
        }
        val data = root.getJSONObject("data")
        val keys = mutableMapOf<String, String>()
        if (root.has("keysData")) {
            require(password.length >= 4) { "бэкап с ключами: нужен пароль" }
            try {
                val sealed = BackupCrypto.Sealed(
                    root.getString("keysSalt"), root.getString("keysIv"), root.getString("keysData")
                )
                val jo = JSONObject(String(BackupCrypto.decrypt(sealed, password), Charsets.UTF_8))
                for (k in jo.keys()) keys[k] = jo.getString(k)
            } catch (e: Exception) {
                // неверный пароль keys-блока неотличим от битого файла
                throw Exception("неверный пароль или битый блок ключей")
            }
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
}
