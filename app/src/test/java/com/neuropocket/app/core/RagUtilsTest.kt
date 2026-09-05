package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class RagUtilsTest {
    @Test fun `cosine identical is 1`() {
        val a = listOf(1f, 0f, 0f)
        assertEquals(1.0, RagUtils.cosine(a, floatArrayOf(1f, 0f, 0f)), 1e-6)
    }
    @Test fun `cosine orthogonal is 0`() {
        assertEquals(0.0, RagUtils.cosine(listOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6)
    }
    @Test fun `cosine dim mismatch safe 0`() {
        assertEquals(0.0, RagUtils.cosine(listOf(1f, 2f), floatArrayOf(1f)), 1e-9)
        assertEquals(0.0, RagUtils.cosine(emptyList(), floatArrayOf()), 1e-9)
    }
    @Test fun `splitBatch maps without indexOf duplicates bug`() {
        // два одинаковых текста в батче — старый texts.indexOf брал всегда первый
        val dim = 2
        val vecs = floatArrayOf(1f, 0f, 0f, 1f, 1f, 1f)
        val splits = RagUtils.splitBatch(vecs, batchSize = 3, dim = dim)
        assertEquals(3, splits.size)
        assertEquals(0, splits[0].first)
        assertEquals(1, splits[1].first)
        assertEquals(2, splits[2].first)
        assertArrayEquals(floatArrayOf(0f, 1f), splits[1].second, 1e-6f)
    }
    @Test fun `splitBatch drops incomplete tail`() {
        val splits = RagUtils.splitBatch(floatArrayOf(1f, 2f, 3f), batchSize = 2, dim = 2)
        assertEquals(1, splits.size)
    }
    @Test fun `topK threshold filters irrelevant`() {
        val items = listOf(
            RagUtils.Scored("a", 0.9),
            RagUtils.Scored("b", 0.1),
            RagUtils.Scored("c", 0.5)
        )
        val top = RagUtils.topK(items, 3, minScore = 0.25)
        assertEquals(listOf("a", "c"), top.map { it.item })
    }
    @Test fun `topK empty when nothing relevant`() {
        val items = listOf(RagUtils.Scored("a", 0.1))
        assertTrue(RagUtils.topK(items, 3, minScore = 0.25).isEmpty())
    }
}
