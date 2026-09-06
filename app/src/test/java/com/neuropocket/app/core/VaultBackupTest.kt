package com.neuropocket.app.core

import com.neuropocket.app.data.Backup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Red-team J: тесты идут через PRODUCTION path
 * (Backup.buildPlainRoot/wrapEncrypted/parse — тот же код, что в приложении).
 */
class VaultBackupTest {
    private fun settings() = mapOf<String, Any>(
        "theme" to "dark", "maxTokens" to 512, "topP" to 0.9f,
        "keepOn" to true, "activeProvider" to "local"
    )

    private fun plain(withKeys: Boolean = false): String {
        val keys = if (withKeys) {
            JSONObject().put("prov-1", "sk-test-123").put("prov-2", "k2").toString()
        } else null
        return Backup.buildPlainRoot(
            personas = """[{"name":"A"}]""", sessions = "[]", msgmap = "{}",
            chars = "[]", posts = "[]", comments = "[]",
            providers = """[{"id":"prov-1"}]""",
            settings = settings(), keysJson = keys, keysPassword = "pass-1234"
        ).toString()
    }

    @Test fun `plain backup round-trip production path`() {
        val p = Backup.parse(plain(), "")
        assertEquals("""[{"name":"A"}]""", p.personas)
        assertEquals("dark", p.settings["theme"])
        assertEquals("512", p.settings["maxTokens"])
        assertTrue(p.keys.isEmpty())
    }

    @Test fun `keys round-trip production path`() {
        val p = Backup.parse(plain(withKeys = true), "pass-1234")
        assertEquals("sk-test-123", p.keys["prov-1"])
        assertEquals("k2", p.keys["prov-2"])
    }

    @Test fun `settings stored as object not string`() {
        val root = JSONObject(plain())
        assertTrue(root.getJSONObject("data").opt("settings") is JSONObject)
    }

    @Test fun `encrypted full backup round-trip production path`() {
        // plain без keys-блока: один пароль на весь файл
        val outer = Backup.wrapEncrypted(JSONObject(plain(withKeys = false)), "full-secret")
        assertEquals(2, outer.optInt("v"))
        val p = Backup.parse(outer.toString(), "full-secret")
        assertEquals("dark", p.settings["theme"])
        assertEquals("""[{"name":"A"}]""", p.personas)
        assertTrue(p.keys.isEmpty())
    }

    @Test fun `mismatched inner keys password is clear error`() {
        // inner keys-блок зашифрован keys-паролем; parse использует один password
        // для обоих уровней (как production restoreBackup) — несовпадение даёт
        // понятную ошибку, а не молчание
        val outer = Backup.wrapEncrypted(JSONObject(plain(withKeys = true)), "full-secret")
        try {
            Backup.parse(outer.toString(), "full-secret")
            fail("ожидалась ошибка keys-блока при несовпадении паролей")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("ключ"))
        }
    }

    @Test fun `encrypted same password both levels`() {
        val keys = JSONObject().put("p", "k").toString()
        val root = Backup.buildPlainRoot(
            "[]", "[]", "{}", "[]", "[]", "[]", "[]",
            settings(), keys, "same-pass"
        )
        val outer = Backup.wrapEncrypted(root, "same-pass")
        val p = Backup.parse(outer.toString(), "same-pass")
        assertEquals("k", p.keys["p"])
    }

    @Test fun `wrong password fails`() {
        val outer = Backup.wrapEncrypted(JSONObject(plain()), "right-pass")
        try {
            Backup.parse(outer.toString(), "wrong-pass")
            fail("должен упасть")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("пароль") || e.message!!.contains("битый"))
        }
    }

    @Test fun `malformed backup rejected`() {
        try {
            Backup.parse("{not json", "")
            fail("должен упасть")
        } catch (_: Exception) { }
        try {
            Backup.parse("""{"app":"Other","v":1,"data":{}}""", "")
            fail("должен упасть")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("наш бэкап"))
        }
    }

    @Test fun `old-format string settings compat production path`() {
        // бэкап v1.24: settings как String(JSON)
        val data = JSONObject()
        data.put("personas", "[]")
        data.put("settings", JSONObject().put("theme", "light").put("ctxSize", 2048).toString())
        val root = JSONObject().put("app", "NeuroPocket").put("v", 1).put("data", data)
        val p = Backup.parse(root.toString(), "")
        assertEquals("light", p.settings["theme"])
        assertEquals("2048", p.settings["ctxSize"])
    }
}
