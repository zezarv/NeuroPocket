package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AssetManifestTest {
    @Test fun `pinned voice zip size matches release asset`() {
        // измерено из релиза v1.24.0 (api.github.com), не из metadata файла
        assertEquals(9642281L, AssetManifest.VOICE_ENGINE.sizeBytes)
        assertEquals("voice-engine-arm64.zip", AssetManifest.VOICE_ENGINE.assetName)
    }
    @Test fun `pinned sd size matches release asset`() {
        assertEquals(53672384L, AssetManifest.SD_ENGINE.sizeBytes)
        assertEquals("libnpsd-arm64-v8a.so", AssetManifest.SD_ENGINE.assetName)
    }
    @Test fun `sha format sane`() {
        for (a in listOf(AssetManifest.VOICE_ENGINE, AssetManifest.SD_ENGINE)) {
            assertEquals(64, a.sha256Hex.length)
            assertTrue(a.sha256Hex.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }
    @Test fun `verifyFile rejects wrong size`() {
        val tmp = File.createTempFile("np-manifest", ".bin")
        try {
            tmp.writeBytes(ByteArray(16))
            assertFalse(AssetManifest.verifyFile(tmp, AssetManifest.VOICE_ENGINE))
        } finally {
            tmp.delete()
        }
    }
    @Test fun `verifyFile rejects missing`() {
        assertFalse(
            AssetManifest.verifyFile(
                File("/nonexistent/np-test-file.bin"), AssetManifest.SD_ENGINE
            )
        )
    }
    @Test fun `voice zip whitelist exact`() {
        assertEquals(
            setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            AssetManifest.VOICE_ZIP_FILES.keys
        )
        assertEquals(21684872L, AssetManifest.VOICE_ZIP_FILES.getValue("libonnxruntime.so"))
        assertEquals(4761536L, AssetManifest.VOICE_ZIP_FILES.getValue("libsherpa-onnx-jni.so"))
    }
}
