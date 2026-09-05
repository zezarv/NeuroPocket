package com.neuropocket.app.core

/**
 * Semantic version comparison для updater. P0.8.
 * Было: только equality строк (latBase == curBase), что ломается на
 * "1.24.0-plan5" vs "v1.24.0" и не понимает кто новее.
 */
object SemVer {
    data class Parsed(val nums: List<Int>, val pre: String?)

    fun parse(v: String): Parsed {
        var s = v.trim().trimStart('v', 'V')
        // отрезать build metadata (+...) — не влияет на сравнение
        s = s.substringBefore('+')
        // разделить core и pre-release по первому '-'
        val dash = s.indexOf('-')
        val core = if (dash < 0) s else s.substring(0, dash)
        val pre = if (dash < 0) null else s.substring(dash + 1).trim().ifEmpty { null }
        // core: числа через '.' (и мусор игнорируем); "1.24" -> [1,24]
        val nums = core.split('.', '_')
            .map { it.filter { c -> c.isDigit() } }
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
        return Parsed(nums, pre)
    }

    /**
     * compare(a, b): <0 если a<b, 0 если равны, >0 если a>b.
     * Pre-release (1.25.0-rc.1) < release (1.25.0). Сравнение pre — лексикографически
     * с учётом числовых суффиксов (rc.1 < rc.2).
     */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.nums.size, pb.nums.size)
        for (i in 0 until n) {
            val x = pa.nums.getOrElse(i) { 0 }
            val y = pb.nums.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        // numeric core равны — разбираем pre
        if (pa.pre == null && pb.pre == null) return 0
        if (pa.pre == null) return 1 // release новее любого pre
        if (pb.pre == null) return -1
        return comparePre(pa.pre, pb.pre)
    }

    private fun comparePre(a: String, b: String): Int {
        if (a == b) return 0
        val ta = a.split('.', '-', '_')
        val tb = b.split('.', '-', '_')
        val n = maxOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta.getOrElse(i) { "" }
            val y = tb.getOrElse(i) { "" }
            if (x == y) continue
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            if (xn != null && yn != null) {
                if (xn != yn) return xn.compareTo(yn) else continue
            }
            // числовой идентификатор < строкового? по semver numeric < alphanumeric.
            // Упрощённо: сравниваем лексикографически, но числа меньше строк.
            if (xn != null) return -1
            if (yn != null) return 1
            val c = x.compareTo(y)
            if (c != 0) return c
        }
        return ta.size.compareTo(tb.size)
    }

    /** true если latest строго новее current. */
    fun isNewer(latestTag: String, current: String): Boolean =
        try { compare(latestTag, current) > 0 } catch (_: Exception) { false }
}
