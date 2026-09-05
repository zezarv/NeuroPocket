package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class NetworkPolicyTest {
    @Test fun `https always allowed`() {
        assertTrue(NetworkPolicy.isUrlAllowed("https://api.groq.com/openai/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("https://192.168.1.100:1234/v1"))
    }
    @Test fun `http allowed for lan`() {
        assertTrue(NetworkPolicy.isUrlAllowed("http://192.168.1.100:1234/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://10.0.0.5:11434/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://172.16.0.2:8080/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://127.0.0.1:1234/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://localhost:1234/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://10.0.2.2:1234/v1"))
        assertTrue(NetworkPolicy.isUrlAllowed("http://myserver.local:8080/v1"))
    }
    @Test fun `http blocked for public`() {
        assertFalse(NetworkPolicy.isUrlAllowed("http://api.groq.com/openai/v1"))
        assertFalse(NetworkPolicy.isUrlAllowed("http://example.com/v1"))
        assertFalse(NetworkPolicy.isUrlAllowed("http://8.8.8.8/v1"))
    }
    @Test fun `private host detection`() {
        assertTrue(NetworkPolicy.isPrivateHost("192.168.1.1"))
        assertTrue(NetworkPolicy.isPrivateHost("10.5.6.7"))
        assertTrue(NetworkPolicy.isPrivateHost("172.20.10.5"))
        assertFalse(NetworkPolicy.isPrivateHost("172.32.0.1"))
        assertFalse(NetworkPolicy.isPrivateHost("8.8.8.8"))
        assertFalse(NetworkPolicy.isPrivateHost("example.com"))
    }
}
