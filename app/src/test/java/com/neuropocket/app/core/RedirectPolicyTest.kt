package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class RedirectPolicyTest {
    @Test fun `https redirect allowed`() {
        val d = RedirectPolicy.check(
            "https://api.groq.com/openai/v1",
            "https://api.groq.com/openai/v2"
        )
        assertTrue(d is RedirectPolicy.Decision.Allow)
    }
    @Test fun `lan http to lan http allowed`() {
        val d = RedirectPolicy.check(
            "http://192.168.1.100:1234/v1",
            "http://192.168.1.100:1234/v1/chat"
        )
        assertTrue(d is RedirectPolicy.Decision.Allow)
    }
    @Test fun `lan http to public http blocked`() {
        // NSC base cleartext=true пропустил бы; policy — нет
        val d = RedirectPolicy.check(
            "http://192.168.1.100:1234/v1",
            "http://evil.example.com/steal"
        )
        assertTrue(d is RedirectPolicy.Decision.Block)
    }
    @Test fun `lan http to public https allowed`() {
        // upgrade на https всегда безопасен
        val d = RedirectPolicy.check(
            "http://192.168.1.100:1234/v1",
            "https://example.com/v1"
        )
        assertTrue(d is RedirectPolicy.Decision.Allow)
    }
    @Test fun `relative location resolved against base`() {
        val d = RedirectPolicy.check("http://127.0.0.1:8080/v1", "/v2/models")
        val allow = d as? RedirectPolicy.Decision.Allow
        assertNotNull(allow)
        assertEquals("http://127.0.0.1:8080/v2/models", allow!!.url)
    }
    @Test fun `blank location blocked`() {
        assertTrue(RedirectPolicy.check("http://127.0.0.1:8080/", null) is RedirectPolicy.Decision.Block)
        assertTrue(RedirectPolicy.check("http://127.0.0.1:8080/", "") is RedirectPolicy.Decision.Block)
    }
    @Test fun `redirect codes recognized`() {
        for (c in listOf(301, 302, 303, 307, 308)) assertTrue(RedirectPolicy.isRedirect(c))
        assertFalse(RedirectPolicy.isRedirect(200))
        assertFalse(RedirectPolicy.isRedirect(404))
        assertFalse(RedirectPolicy.isRedirect(500))
    }
}
