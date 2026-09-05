package com.neuropocket.app.core

import kotlin.math.sqrt

/**
 * RAG hardening helpers. P0-вынос чистой логики:
 * - безопасный косинус с проверкой размерностей;
 * - корректный маппинг batch -> исходный chunk (без texts.indexOf, ломавшегося на дубликатах);
 * - выбор top-K с порогом релевантности.
 */
object RagUtils {
    /** Косинус; 0.0 при несовпадении размерностей или нулевых нормах. */
    fun cosine(a: List<Float>, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    data class Scored<T>(val item: T, val score: Double)

    /**
     * Маппинг эмбеддингов батча к исходным чанкам БЕЗ indexOf.
     * @param batch исходные тексты батча (порядок = порядок векторов)
     * @param vecs плоский массив dim*batch.size
     * @param dim размерность эмбеддинга (>0)
     * @return список (индекс в batch, вектор) только для полных векторов
     */
    fun splitBatch(vecs: FloatArray?, batchSize: Int, dim: Int): List<Pair<Int, FloatArray>> {
        if (vecs == null || dim <= 0 || batchSize <= 0) return emptyList()
        val out = mutableListOf<Pair<Int, FloatArray>>()
        for (bi in 0 until batchSize) {
            val from = bi * dim
            val to = minOf((bi + 1) * dim, vecs.size)
            if (to - from == dim) out.add(bi to vecs.copyOfRange(from, to))
        }
        return out
    }

    /** Top-K по скору с минимальным порогом; сортировка по убыванию. */
    fun <T> topK(scored: List<Scored<T>>, k: Int, minScore: Double = 0.0): List<Scored<T>> {
        if (k <= 0) return emptyList()
        return scored.filter { it.score >= minScore }.sortedByDescending { it.score }.take(k)
    }
}
