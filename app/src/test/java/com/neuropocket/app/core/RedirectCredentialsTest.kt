package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class RedirectCredentialsTest {
    @Test fun `same-origin redirect preserves authorization allowed`() {
        val f = RedirectPolicy.decideFollow(
            "https://api.example.com/v1/chat", "/v1/chat2", isPost = true
        )
        assertTrue(f is RedirectPolicy.Follow.KeepHeaders)
    }
    @Test fun `cross-origin https strips authorization`() {
        val f = RedirectPolicy.decideFollow(
            "https://a.example.com/models", "https://b.example.com/models", isPost = false
        )
        assertTrue(f is RedirectPolicy.Follow.Stripped)
        // Authorization обязан отрезаться вызывающим кодом
        assertTrue(RedirectPolicy.isSensitiveHeader("Authorization"))
        assertTrue(RedirectPolicy.isSensitiveHeader("Proxy-Authorization"))
        assertTrue(RedirectPolicy.isSensitiveHeader("Cookie"))
        assertFalse(RedirectPolicy.isSensitiveHeader("Accept"))
        assertFalse(RedirectPolicy.isSensitiveHeader("Content-Type"))
    }
    @Test fun `cross-origin POST with bearer blocked`() {
        val f = RedirectPolicy.decideFollow(
            "https://a.example.com/v1/chat", "https://other-host.example/v1/chat", isPost = true
        )
        assertTrue(f is RedirectPolicy.Follow.Block)
        val reason = (f as RedirectPolicy.Follow.Block).reason
        assertTrue(reason.contains("direct API URL"))
        // ключ нигде не фигурирует
        assertFalse(reason.contains("Bearer"))
    }
    @Test fun `https to http LAN blocked`() {
        val f = RedirectPolicy.decideFollow(
            "https://api.example.com/v1", "http://192.168.1.100:1234/v1", isPost = false
        )
        assertTrue(f is RedirectPolicy.Follow.Block)
    }
    @Test fun `lan http to public http blocked`() {
        val f = RedirectPolicy.decideFollow(
            "http://192.168.1.100:1234/v1", "http://evil.example.com/x", isPost = false
        )
        assertTrue(f is RedirectPolicy.Follow.Block)
    }
    @Test fun `relative same-origin allow`() {
        val f = RedirectPolicy.decideFollow(
            "http://127.0.0.1:8080/v1", "/v2/models", isPost = false
        )
        val keep = f as? RedirectPolicy.Follow.KeepHeaders
        assertNotNull(keep)
        assertEquals("http://127.0.0.1:8080/v2/models", keep!!.url)
    }
    @Test fun `origin effective ports`() {
        assertTrue(RedirectPolicy.sameOrigin("https://h.example/a", "https://h.example:443/b"))
        assertFalse(RedirectPolicy.sameOrigin("https://h.example/a", "http://h.example/b"))
        assertFalse(RedirectPolicy.sameOrigin("https://h.example:8443/a", "https://h.example/b"))
    }
}
