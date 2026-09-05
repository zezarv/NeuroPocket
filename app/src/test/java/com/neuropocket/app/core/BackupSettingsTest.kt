package com.neuropocket.app.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSettingsTest {
    @Test fun `new format round-trip`() {
        val src = mapOf("theme" to "dark", "maxTokens" to 512, "topP" to 0.9f, "keepOn" to true)
        val enc = BackupSettings.encode(src)
        val data = JSONObject().put("settings", enc)
        val dec = BackupSettings.decode(data)
        assertEquals("dark", dec["theme"])
        assertEquals("512", dec["maxTokens"])
        assertEquals("true", dec["keepOn"])
    }
    @Test fun `old string format backward compat`() {
        // v1.24 писал settings как String(JSON)
        val st = JSONObject().put("theme", "light").put("ctxSize", 2048).toString()
        val data = JSONObject().put("settings", st)
        val dec = BackupSettings.decode(data)
        assertEquals("light", dec["theme"])
        assertEquals("2048", dec["ctxSize"])
    }
    @Test fun `missing settings empty`() {
        assertTrue(BackupSettings.decode(JSONObject()).isEmpty())
    }
    @Test fun `malformed string empty not crash`() {
        val data = JSONObject().put("settings", "{not json")
        assertTrue(BackupSettings.decode(data).isEmpty())
    }
    @Test fun `empty string empty`() {
        val data = JSONObject().put("settings", "")
        assertTrue(BackupSettings.decode(data).isEmpty())
    }
}
