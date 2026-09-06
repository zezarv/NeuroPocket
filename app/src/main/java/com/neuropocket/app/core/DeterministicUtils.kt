package com.neuropocket.app.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.math.pow

/**
 * Phase B: deterministic non-AI utilities.
 * Никакого LLM там, где обычный код лучше. Pure Kotlin + unit-тесты.
 */
object DeterministicUtils {

    // ---- 1. Calculator (shunting-yard, без ScriptEngine) ----

    sealed interface CalcResult {
        data class Ok(val value: Double, val formatted: String) : CalcResult
        data class Err(val message: String) : CalcResult
    }

    fun calc(expr: String): CalcResult {
        val t = expr.trim().replace(',', '.')
        if (t.isEmpty()) return CalcResult.Err("Пустое выражение.")
        if (t.length > 500) return CalcResult.Err("Слишком длинное выражение.")
        if (!t.matches(Regex("[0-9+\\-*/%^().\\s]+"))) {
            return CalcResult.Err("Разрешены только цифры и + - * / % ^ ( ) .")
        }
        return try {
            val v = evalExpr(t)
            if (v.isNaN() || v.isInfinite()) CalcResult.Err("Нет результата (деление на ноль?).")
            else CalcResult.Ok(v, formatDouble(v))
        } catch (e: ArithmeticException) {
            CalcResult.Err(e.message ?: "Ошибка вычисления.")
        } catch (_: Exception) {
            CalcResult.Err("Не понял выражение.")
        }
    }

    fun formatDouble(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val asLong = v.toLong()
        if (v == asLong.toDouble() && asLong in -9_000_000_000_000_000L..9_000_000_000_000_000L) {
            return asLong.toString()
        }
        // до 10 значащих, без хвостовых нулей
        var s = "%.10f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
        if (s == "-0") s = "0"
        return s
    }

    private fun evalExpr(expr: String): Double {
        val tokens = tokenize(expr)
        val rpn = toRpn(tokens)
        return evalRpn(rpn)
    }

    private sealed interface Tok {
        data class Num(val v: Double) : Tok
        data class Op(val c: Char) : Tok
        data class Par(val c: Char) : Tok
    }

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        var prev: Tok? = null
        fun isUnaryMinus(): Boolean {
            if (prev == null) return true
            return prev is Tok.Op || (prev is Tok.Par && (prev as Tok.Par).c == '(')
        }
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' || (c == '-' && isUnaryMinus() && i + 1 < s.length &&
                    (s[i + 1].isDigit() || s[i + 1] == '.')) -> {
                    var j = i
                    if (s[j] == '-') j++
                    var dot = false
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) {
                        if (s[j] == '.') {
                            if (dot) break
                            dot = true
                        }
                        j++
                    }
                    val num = s.substring(i, j).toDoubleOrNull()
                        ?: throw IllegalArgumentException("bad number")
                    out.add(Tok.Num(num))
                    prev = out.last()
                    i = j
                }
                c in "+-*/%^" -> {
                    out.add(Tok.Op(c))
                    prev = out.last()
                    i++
                }
                c == '(' || c == ')' -> {
                    out.add(Tok.Par(c))
                    prev = out.last()
                    i++
                }
                else -> throw IllegalArgumentException("bad char")
            }
        }
        return out
    }

    private fun prec(op: Char): Int = when (op) {
        '^' -> 3
        '*', '/', '%' -> 2
        '+', '-' -> 1
        else -> 0
    }

    private fun toRpn(tokens: List<Tok>): List<Tok> {
        val out = mutableListOf<Tok>()
        val st = ArrayDeque<Tok.Op>()
        for (t in tokens) {
            when (t) {
                is Tok.Num -> out.add(t)
                is Tok.Op -> {
                    while (st.isNotEmpty()) {
                        val top = st.last()
                        val rightAssoc = t.c == '^'
                        if ((!rightAssoc && prec(top.c) >= prec(t.c)) ||
                            (rightAssoc && prec(top.c) > prec(t.c))
                        ) {
                            out.add(st.removeLast())
                        } else break
                    }
                    st.add(t)
                }
                is Tok.Par -> if (t.c == '(') {
                    st.add(Tok.Op('('))
                } else {
                    while (st.isNotEmpty() && st.last().c != '(') out.add(st.removeLast())
                    if (st.isEmpty()) throw IllegalArgumentException("paren")
                    st.removeLast()
                }
            }
        }
        while (st.isNotEmpty()) {
            val o = st.removeLast()
            if (o.c == '(') throw IllegalArgumentException("paren")
            out.add(o)
        }
        return out
    }

    private fun evalRpn(rpn: List<Tok>): Double {
        val st = ArrayDeque<Double>()
        for (t in rpn) {
            when (t) {
                is Tok.Num -> st.add(t.v)
                is Tok.Op -> {
                    if (st.size < 2) throw IllegalArgumentException("expr")
                    val b = st.removeLast()
                    val a = st.removeLast()
                    st.add(
                        when (t.c) {
                            '+' -> a + b
                            '-' -> a - b
                            '*' -> a * b
                            '/' -> {
                                if (b == 0.0) throw ArithmeticException("Деление на ноль.")
                                a / b
                            }
                            '%' -> {
                                if (b == 0.0) throw ArithmeticException("Деление на ноль.")
                                a % b
                            }
                            '^' -> a.pow(b)
                            else -> throw IllegalArgumentException("op")
                        }
                    )
                }
                else -> throw IllegalArgumentException("expr")
            }
        }
        if (st.size != 1) throw IllegalArgumentException("expr")
        return st.last()
    }

    // ---- 2. Unit converter ----

    // категории: length | mass | temp | data
    private val LENGTH = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344
    )
    private val MASS = mapOf(
        "g" to 1.0, "kg" to 1000.0, "t" to 1_000_000.0,
        "oz" to 28.349523125, "lb" to 453.59237
    )
    private val DATA = mapOf(
        "B" to 1.0, "KB" to 1024.0, "MB" to 1024.0 * 1024,
        "GB" to 1024.0 * 1024 * 1024, "TB" to 1024.0 * 1024 * 1024 * 1024
    )

    fun convertCategories(): List<String> = listOf("length", "mass", "temp", "data")

    fun convertUnits(category: String): List<String> = when (category) {
        "mass" -> MASS.keys.toList()
        "temp" -> listOf("C", "F", "K")
        "data" -> DATA.keys.toList()
        else -> LENGTH.keys.toList()
    }

    fun convert(value: Double, from: String, to: String, category: String): Double? {
        if (from == to) return value
        return when (category) {
            "mass" -> {
                val a = MASS[from] ?: return null
                val b = MASS[to] ?: return null
                value * a / b
            }
            "data" -> {
                val a = DATA[from] ?: return null
                val b = DATA[to] ?: return null
                value * a / b
            }
            "temp" -> convertTemp(value, from, to)
            else -> {
                val a = LENGTH[from] ?: return null
                val b = LENGTH[to] ?: return null
                value * a / b
            }
        }
    }

    private fun convertTemp(v: Double, from: String, to: String): Double? {
        val c = when (from) {
            "C" -> v
            "F" -> (v - 32) * 5 / 9
            "K" -> v - 273.15
            else -> return null
        }
        return when (to) {
            "C" -> c
            "F" -> c * 9 / 5 + 32
            "K" -> c + 273.15
            else -> null
        }
    }

    // ---- 3. JSON formatter + validator ----

    sealed interface JsonResult {
        data class Ok(val pretty: String) : JsonResult
        data class Err(val message: String) : JsonResult
    }

    fun formatJson(raw: String): JsonResult {
        val t = raw.trim()
        if (t.isEmpty()) return JsonResult.Err("Пустой ввод.")
        if (t.length > 200000) return JsonResult.Err("Слишком большой JSON (> 200k).")
        return try {
            val pretty = if (t.startsWith("{")) {
                org.json.JSONObject(t).toString(2)
            } else if (t.startsWith("[")) {
                org.json.JSONArray(t).toString(2)
            } else {
                return JsonResult.Err("JSON должен начинаться с { или [.")
            }
            JsonResult.Ok(pretty)
        } catch (e: Exception) {
            JsonResult.Err("Невалидный JSON: ${e.message?.take(160) ?: "?"}")
        }
    }

    // ---- 4. SHA-256 ----

    fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---- 5. Base64 ----

    sealed interface B64Result {
        data class Ok(val text: String) : B64Result
        data class Err(val message: String) : B64Result
    }

    fun base64Encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun base64Decode(raw: String): B64Result {
        val t = raw.trim().replace("\\s".toRegex(), "")
        if (t.isEmpty()) return B64Result.Err("Пустой ввод.")
        if (t.length > 300000) return B64Result.Err("Слишком длинный ввод.")
        return try {
            val bytes = Base64.getDecoder().decode(t)
            B64Result.Ok(bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            B64Result.Err("Невалидный Base64.")
        }
    }

    // ---- 6. URL encode/decode ----

    fun urlEncode(text: String): String = try {
        URLEncoder.encode(text, "UTF-8")
    } catch (_: Exception) { text }

    sealed interface UrlResult {
        data class Ok(val text: String) : UrlResult
        data class Err(val message: String) : UrlResult
    }

    fun urlDecode(raw: String): UrlResult {
        if (raw.isEmpty()) return UrlResult.Err("Пустой ввод.")
        return try {
            UrlResult.Ok(URLDecoder.decode(raw, "UTF-8"))
        } catch (_: Exception) {
            UrlResult.Err("Не смог декодировать URL.")
        }
    }

    // ---- 7. Text statistics ----

    data class TextStats(
        val chars: Int,
        val charsNoSpaces: Int,
        val words: Int,
        val lines: Int,
        val sentences: Int,
        val readingMin: Double
    )

    fun textStats(text: String): TextStats {
        val chars = text.length
        val noSpaces = text.count { !it.isWhitespace() }
        val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
        val lines = if (text.isEmpty()) 0 else text.split("\n").size
        val sentences = Regex("[.!?…]+").findAll(text).count().let {
            if (it == 0 && words > 0) 1 else it
        }
        return TextStats(chars, noSpaces, words, lines, sentences, words / 200.0)
    }

    // ---- 8. UUID ----

    fun newUuid(): String = UUID.randomUUID().toString()
}
