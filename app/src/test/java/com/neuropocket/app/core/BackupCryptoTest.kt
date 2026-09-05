package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun `plain round-trip`() {
        val plain = """{"personas":"[]","theme":"dark"}""".toByteArray(Charsets.UTF_8)
        val s = BackupCrypto.encrypt(plain, "test-pass-123")
        val back = BackupCrypto.decrypt(s, "test-pass-123")
        assertArrayEquals(plain, back)
    }
    @Test fun `encrypted full backup round-trip`() {
        val payload = JSONObjectLike()
        val s = BackupCrypto.encrypt(payload.toByteArray(), "secret42")
        assertEquals(payload, String(BackupCrypto.decrypt(s, "secret42"), Charsets.UTF_8))
    }
    @Test(expected = Exception::class)
    fun `wrong password fails`() {
        val s = BackupCrypto.encrypt("hello".toByteArray(), "right-pass")
        BackupCrypto.decrypt(s, "wrong-pass")
    }
    @Test(expected = IllegalArgumentException::class)
    fun `short password rejected`() {
        BackupCrypto.encrypt("x".toByteArray(), "abc")
    }
    @Test fun `api keys round-trip inside payload`() {
        val keys = org.json.JSONObject().put("prov-1", "sk-abc").put("prov-2", "key2").toString()
        val s = BackupCrypto.encrypt(keys.toByteArray(Charsets.UTF_8), "keys-pass")
        val back = org.json.JSONObject(String(BackupCrypto.decrypt(s, "keys-pass"), Charsets.UTF_8))
        assertEquals("sk-abc", back.getString("prov-1"))
        assertEquals("key2", back.getString("prov-2"))
    }
    private fun JSONObjectLike(): String =
        """{"app":"NeuroPocket","v":2,"data":{"personas":"[]","settings":{"theme":"dark"}}}"""
}
