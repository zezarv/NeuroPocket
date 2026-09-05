package com.neuropocket.app.core

import java.io.File
import java.security.MessageDigest

/**
 * P0.7: проверка native движков перед System.load.
 * Минимум: size + SHA-256 + whitelist + zip-slip guard.
 * Полный trusted manifest (подписанные хэши в релизе) — следующий шаг;
 * пока сверяем размер и формат, SHA считаем и показываем для ручной сверки.
 */
object NativeVerify {
    fun sha256Hex(f: File): String? = try {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                d.update(buf, 0, n)
            }
        }
        d.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    fun sha256HexBytes(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(data).joinToString("") { "%02x".format(it) }
    }

    /** Безопасное имя из zip entry: только basename из whitelist, иначе null. */
    fun safeName(entryName: String, allowed: Set<String>): String? {
        if (entryName.isBlank()) return null
        val base = File(entryName).name.trim()
        if (base.isEmpty() || base != entryName.trim().substringAfterLast('/').substringAfterLast('\\')) {
            // вложенные пути схлопываем до basename
        }
        if (base.isEmpty() || base == "." || base == "..") return null
        if ('/' in base || '\\' in base) return null
        return if (base in allowed) base else null
    }

    /** Проверка что canonical(child) внутри canonical(dir) — защита от zip-slip. */
    fun isInsideDir(dir: File, child: File): Boolean = try {
        child.canonicalPath.startsWith(dir.canonicalPath + File.separator)
    } catch (_: Exception) { false }
}
