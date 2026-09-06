package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileInfoTest {
    @Test fun `file size direct`() {
        val f = File.createTempFile("np-size", ".bin")
        try {
            f.writeBytes(ByteArray(1024))
            assertEquals(1024L, FileInfo.displaySizeBytes(f))
            assertEquals(0L, FileInfo.displaySizeMb(f))
        } finally {
            f.delete()
        }
    }
    @Test fun `directory recursive sum not file length`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "np-dir-${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "a.bin").writeBytes(ByteArray(1000))
            val sub = File(dir, "sub").apply { mkdirs() }
            File(sub, "b.bin").writeBytes(ByteArray(2000))
            // File.length() директории — НЕ сумма (обычно 0/4096)
            assertEquals(3000L, FileInfo.displaySizeBytes(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test fun `samePath canonical`() {
        val f = File.createTempFile("np-same", ".gguf")
        try {
            val same = File(f.parent, f.name)
            assertTrue(FileInfo.samePath(f.absolutePath, same))
            assertFalse(FileInfo.samePath(f.absolutePath + ".other", same))
            assertFalse(FileInfo.samePath(null, same))
            // коллизия имён в разных папках — НЕ same
            val other = File(System.getProperty("java.io.tmpdir"), "np-other-${System.nanoTime()}")
            other.mkdirs()
            try {
                val twin = File(other, f.name)
                twin.writeBytes(ByteArray(4))
                assertFalse(FileInfo.samePath(f.absolutePath, twin))
            } finally {
                other.deleteRecursively()
            }
        } finally {
            f.delete()
        }
    }
}
