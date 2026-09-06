package com.neuropocket.app.core

import java.io.File

/**
 * Red-team K: честный размер для delete-диалогов и exact-path сравнение.
 * File.length() для directory — НЕ рекурсивная сумма (обычно 0/4096),
 * поэтому для директорий считаем bounded recursive traversal.
 */
object FileInfo {
    private const val MAX_FILES = 20000
    private const val MAX_DEPTH = 12

    /** Размер файла или рекурсивная сумма директории (bounded, без исключений). */
    fun displaySizeBytes(f: File): Long {
        return try {
            if (f.isFile) return f.length().coerceAtLeast(0)
            if (!f.isDirectory) return 0
            var total = 0L
            var count = 0
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.add(f to 0)
            while (stack.isNotEmpty() && count < MAX_FILES) {
                val (dir, depth) = stack.removeLast()
                if (depth > MAX_DEPTH) continue
                val kids = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
                for (k in kids) {
                    if (count++ > MAX_FILES) break
                    if (k.isFile) total += k.length().coerceAtLeast(0)
                    else if (k.isDirectory) stack.add(k to depth + 1)
                }
            }
            total
        } catch (_: Exception) { 0L }
    }

    fun displaySizeMb(f: File): Long = displaySizeBytes(f) / 1048576

    /**
     * Canonical exact-path сравнение (red-team K: loaded marker НЕ через
     * endsWith(filename) — коллизии имён в разных папках).
     */
    fun samePath(knownAbsolute: String?, f: File): Boolean {
        if (knownAbsolute.isNullOrBlank()) return false
        return try {
            File(knownAbsolute).canonicalPath == f.canonicalPath
        } catch (_: Exception) {
            knownAbsolute == f.absolutePath
        }
    }
}
