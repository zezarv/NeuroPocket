package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeVerifyTest {
    @Test fun `sha256 known vector`() {
        val hex = NativeVerify.sha256HexBytes("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
    }
    @Test fun `safeName whitelist`() {
        val allowed = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so")
        assertEquals("libonnxruntime.so", NativeVerify.safeName("libonnxruntime.so", allowed))
        assertEquals("libsherpa-onnx-jni.so", NativeVerify.safeName("a/b/libsherpa-onnx-jni.so", allowed))
        assertNull(NativeVerify.safeName("evil.so", allowed))
        assertNull(NativeVerify.safeName("", allowed))
        assertNull(NativeVerify.safeName("..", allowed))
    }
    @Test fun `zip-slip guard`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "np-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val inside = File(dir, "libonnxruntime.so")
            assertTrue(NativeVerify.isInsideDir(dir, inside))
            val outside = File(dir.parent, "evil.so")
            assertFalse(NativeVerify.isInsideDir(dir, outside))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test fun `voice engine size label sane`() {
        // ~9.6MB релиза v1.24, не 25MB
        assertTrue(VoiceEngine.EXPECTED_SIZE_MB in 5.0..20.0)
        assertEquals("voice-engine-arm64.zip", VoiceEngine.ARCHIVE_NAME)
    }
}
