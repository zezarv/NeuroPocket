package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class ReleaseAssetsTest {
    @Test fun `tag url pinned not latest`() {
        val u = ReleaseAssets.tagUrl("v1.24.0")
        assertTrue(u.contains("/releases/tags/v1.24.0"))
        assertFalse(u.contains("latest"))
    }
    @Test fun `exact match wins`() {
        val assets = listOf(
            "libnpsd-arm64-v8a.so" to "https://dl/sd",
            "voice-engine-arm64.zip" to "https://dl/voice"
        )
        assertEquals("https://dl/voice", ReleaseAssets.findExactUrl(assets, "voice-engine-arm64.zip"))
        assertEquals("https://dl/sd", ReleaseAssets.findExactUrl(assets, "libnpsd-arm64-v8a.so"))
    }
    @Test fun `prefix does not match`() {
        val assets = listOf("libnpsd-arm64-v8a.so" to "https://dl/sd")
        // startsWith("libnpsd") было бы true — EXACT требует полное имя
        assertNull(ReleaseAssets.findExactUrl(assets, "libnpsd"))
        assertNull(ReleaseAssets.findExactUrl(assets, "libnpsd-arm64"))
    }
    @Test fun `blank rejected`() {
        assertNull(ReleaseAssets.findExactUrl(listOf("a" to "u"), ""))
        assertNull(ReleaseAssets.findExactUrl(emptyList(), "voice-engine-arm64.zip"))
    }
}
