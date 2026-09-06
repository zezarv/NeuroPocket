package com.neuropocket.app.core

/**
 * Phase B: минимальный реальный Agent — Action Registry.
 *
 * Контракт честности:
 *  - planner возвращает СТРУКТУРИРОВАННЫЕ действия из белого списка;
 *  - выполняется только REAL app action через handler;
 *  - недоступное -> UNAVAILABLE, ошибка -> ERROR;
 *  - НИКОГДА не выдавать текстовую галлюцинацию за выполненное действие.
 *
 * Pure Kotlin (executor + parser), handler инжектится (ViewModel даёт настоящий).
 */
object AgentActionTypes {
    const val SEARCH_NOTES = "SEARCH_NOTES"
    const val READ_NOTE = "READ_NOTE"
    const val WRITE_NOTE = "WRITE_NOTE"
    const val SUMMARIZE_TEXT = "SUMMARIZE_TEXT"
    const val TRANSLATE_TEXT = "TRANSLATE_TEXT"
    const val ANALYZE_TEXT = "ANALYZE_TEXT"
    const val TRANSCRIBE_AUDIO = "TRANSCRIBE_AUDIO"
    const val ANALYZE_IMAGE = "ANALYZE_IMAGE"
    const val CREATE_SOCIAL_DRAFT = "CREATE_SOCIAL_DRAFT"
    const val SAVE_RESULT = "SAVE_RESULT"

    val ALL = listOf(
        SEARCH_NOTES, READ_NOTE, WRITE_NOTE, SUMMARIZE_TEXT, TRANSLATE_TEXT,
        ANALYZE_TEXT, TRANSCRIBE_AUDIO, ANALYZE_IMAGE, CREATE_SOCIAL_DRAFT, SAVE_RESULT
    )

    /** Обязательные аргументы по типу. */
    fun requiredArgs(type: String): List<String> = when (type) {
        SEARCH_NOTES -> listOf("query")
        READ_NOTE -> listOf("name")
        WRITE_NOTE -> listOf("name", "text")
        SUMMARIZE_TEXT -> listOf("text")
        TRANSLATE_TEXT -> listOf("text")
        ANALYZE_TEXT -> listOf("text")
        TRANSCRIBE_AUDIO -> listOf("file")
        ANALYZE_IMAGE -> listOf("file")
        CREATE_SOCIAL_DRAFT -> listOf("text")
        SAVE_RESULT -> listOf("text")
        else -> listOf("_unknown_")
    }

    fun describe(type: String): String = when (type) {
        SEARCH_NOTES -> "Поиск по заметкам"
        READ_NOTE -> "Чтение заметки"
        WRITE_NOTE -> "Запись заметки"
        SUMMARIZE_TEXT -> "Саммари текста"
        TRANSLATE_TEXT -> "Перевод текста"
        ANALYZE_TEXT -> "Анализ текста"
        TRANSCRIBE_AUDIO -> "Транскрибация аудио"
        ANALYZE_IMAGE -> "Разбор изображения"
        CREATE_SOCIAL_DRAFT -> "Черновик поста"
        SAVE_RESULT -> "Сохранение результата"
        else -> "Неизвестно"
    }
}

data class AgentAction(
    val type: String,
    val args: Map<String, String> = emptyMap()
)

sealed interface ParsedAction {
    data class Valid(val action: AgentAction) : ParsedAction
    data class Invalid(val line: String, val reason: String) : ParsedAction
}

object AgentActionParser {
    const val MAX_ACTIONS = 5

    private val LINE_RE = Regex("""^\s*(?:ACTION\s*:\s*)?([A-Za-z_]+)\s*(?:\|(.*))?$""")

    /**
     * Парсинг плана: построчные ACTION-строки ИЛИ JSON-массив.
     * Формат строки: ACTION: TYPE | key=value | key2="quoted value"
     */
    fun parsePlan(raw: String, maxActions: Int = MAX_ACTIONS): List<ParsedAction> {
        val t = raw.trim()
        if (t.isEmpty()) return emptyList()
        if (t.startsWith("[")) {
            val fromJson = tryParseJson(t, maxActions)
            if (fromJson != null) return fromJson
            // иначе падаем вниз к построчному (честная invalid, а не молчание)
        }
        val out = mutableListOf<ParsedAction>()
        for (line in t.lines()) {
            val s = line.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '.', ')', ' ').trim()
            if (s.isEmpty()) continue
            // Чат-болтовня планировщика — не action: пропускаем молча.
            // Action-строка либо содержит маркер ACTION, либо начинается с известного типа.
            val up = s.uppercase()
            val startsKnown = AgentActionTypes.ALL.any { up.startsWith(it) }
            if (!up.contains("ACTION") && !startsKnown) continue
            out.add(parseLine(s))
            if (out.size >= maxActions) break
        }
        return out.take(maxActions)
    }

    fun parseLine(line: String): ParsedAction {
        val m = LINE_RE.find(line) ?: return ParsedAction.Invalid(line.take(200), "Не формат ACTION.")
        val type = m.groupValues[1].trim().uppercase()
        if (type !in AgentActionTypes.ALL) {
            return ParsedAction.Invalid(line.take(200), "Неизвестное действие: $type.")
        }
        val rest = m.groupValues[2]
        val args = parseArgs(rest)
        val missing = AgentActionTypes.requiredArgs(type).filter { (args[it] ?: "").isBlank() }
        if (missing.isNotEmpty()) {
            return ParsedAction.Invalid(line.take(200), "Нет аргументов: ${missing.joinToString(",")}.")
        }
        return ParsedAction.Valid(AgentAction(type, args))
    }

    fun parseArgs(rest: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (rest.isBlank()) return map
        // сплит по | вне кавычек
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var q = false
        for (c in rest) {
            when {
                c == '"' -> { q = !q; cur.append(c) }
                c == '|' && !q -> { parts.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
        }
        parts.add(cur.toString())
        for (p in parts) {
            val i = p.indexOf('=')
            if (i <= 0) continue
            val k = p.substring(0, i).trim().lowercase()
            var v = p.substring(i + 1).trim()
            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length - 1)
            }
            if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v.take(8000)
        }
        return map
    }

    private fun tryParseJson(raw: String, maxActions: Int): List<ParsedAction>? {
        return try {
            val arr = org.json.JSONArray(raw)
            val out = mutableListOf<ParsedAction>()
            for (i in 0 until minOf(arr.length(), maxActions)) {
                val o = arr.optJSONObject(i)
                if (o == null) {
                    out.add(ParsedAction.Invalid(arr.opt(i)?.toString()?.take(200) ?: "", "Не объект."))
                    continue
                }
                val type = (o.optString("action").ifBlank { o.optString("type") }).trim().uppercase()
                if (type !in AgentActionTypes.ALL) {
                    out.add(ParsedAction.Invalid(o.toString().take(200), "Неизвестное действие: ${type.ifBlank { "?" }}."))
                    continue
                }
                val args = mutableMapOf<String, String>()
                val ao = o.optJSONObject("args")
                if (ao != null) {
                    for (k in ao.keys()) {
                        args[k.lowercase()] = ao.optString(k, "").take(8000)
                    }
                }
                // плоские поля тоже принимаем
                for (k in o.keys()) {
                    if (k.lowercase() !in listOf("action", "type", "args")) {
                        args[k.lowercase()] = o.optString(k, "").take(8000)
                    }
                }
                val missing = AgentActionTypes.requiredArgs(type).filter { (args[it] ?: "").isBlank() }
                if (missing.isNotEmpty()) {
                    out.add(ParsedAction.Invalid(o.toString().take(200), "Нет аргументов: ${missing.joinToString(",")}."))
                } else {
                    out.add(ParsedAction.Valid(AgentAction(type, args)))
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun formatLine(a: AgentAction): String {
        val args = a.args.entries.joinToString(" | ") { "${it.key}=${it.value.take(80)}" }
        return if (args.isBlank()) "ACTION: ${a.type}" else "ACTION: ${a.type} | $args"
    }
}

// ---------------------------------------------------------------------------
// Execution
// ---------------------------------------------------------------------------

sealed interface AgentExecResult {
    data class Success(val output: String) : AgentExecResult
    data class Unavailable(val reason: String) : AgentExecResult
    data class Error(val message: String) : AgentExecResult
}

/** Реальный исполнитель действий (ViewModel). Тесты подсовывают fake. */
interface AgentActionHandler {
    /** Быстрый gate возможностей (без тяжёлых вызовов). */
    fun isAvailable(type: String): Boolean
    suspend fun execute(action: AgentAction): AgentExecResult
}

data class AgentExecStep(
    val action: AgentAction?,
    val rawLine: String,
    var status: String = "wait", // wait | run | done | unavailable | error | invalid | skipped
    var result: String = ""
)

object AgentExecutor {
    const val ST_DONE = "done"
    const val ST_UNAVAILABLE = "unavailable"
    const val ST_ERROR = "error"
    const val ST_INVALID = "invalid"
    const val ST_SKIPPED = "skipped"

    suspend fun run(
        planRaw: String,
        handler: AgentActionHandler,
        shouldStop: () -> Boolean = { false }
    ): Pair<List<AgentExecStep>, String> {
        val parsed = AgentActionParser.parsePlan(planRaw)
        if (parsed.isEmpty()) {
            return emptyList<AgentExecStep>() to
                "[Нет исполнимых действий: планировщик не вернул ACTION-строки.]"
        }
        val steps = mutableListOf<AgentExecStep>()
        for (p in parsed) {
            if (shouldStop()) {
                steps.filter { it.status == "wait" }.forEach { it.status = ST_SKIPPED; it.result = "[Пропущено: остановка.]" }
                break
            }
            when (p) {
                is ParsedAction.Invalid -> steps.add(
                    AgentExecStep(null, p.line, ST_INVALID, "[INVALID] ${p.reason}")
                )
                is ParsedAction.Valid -> {
                    val st = AgentExecStep(p.action, AgentActionParser.formatLine(p.action), "wait", "")
                    steps.add(st)
                    if (shouldStop()) {
                        st.status = ST_SKIPPED; st.result = "[Пропущено: остановка.]"
                        continue
                    }
                    st.status = "run"
                    if (!handler.isAvailable(p.action.type)) {
                        st.status = ST_UNAVAILABLE
                        st.result = "[UNAVAILABLE] ${unavailableHint(p.action.type)}"
                        continue
                    }
                    when (val r = try {
                        handler.execute(p.action)
                    } catch (e: Exception) {
                        AgentExecResult.Error(e.message?.take(200) ?: "?")
                    }) {
                        is AgentExecResult.Success -> {
                            st.status = ST_DONE
                            st.result = r.output.take(1500)
                        }
                        is AgentExecResult.Unavailable -> {
                            st.status = ST_UNAVAILABLE
                            st.result = "[UNAVAILABLE] ${r.reason.take(500)}"
                        }
                        is AgentExecResult.Error -> {
                            st.status = ST_ERROR
                            st.result = "[ERROR] ${r.message.take(500)}"
                        }
                    }
                }
            }
        }
        return steps to buildFinalSummary(steps)
    }

    fun unavailableHint(type: String): String = when (type) {
        AgentActionTypes.TRANSCRIBE_AUDIO -> "нужна загруженная Whisper-модель в RAM и аудиофайл на устройстве."
        AgentActionTypes.ANALYZE_IMAGE -> "нужна загруженная vision-модель в RAM и файл изображения."
        else -> "действие недоступно на устройстве прямо сейчас."
    }

    /**
     * Честный итог ТОЛЬКО из реальных результатов шагов.
     * Успехом считается лишь status=done; остальное явно помечено.
     */
    fun buildFinalSummary(steps: List<AgentExecStep>): String {
        if (steps.isEmpty()) return "[Нет исполнимых действий.]"
        val sb = StringBuilder()
        val done = steps.count { it.status == ST_DONE }
        sb.append("Выполнено ${done} из ${steps.size} действий. Честный разбор:\n")
        for ((i, s) in steps.withIndex()) {
            val label = s.action?.type ?: "INVALID"
            val mark = when (s.status) {
                ST_DONE -> "OK"
                ST_UNAVAILABLE -> "UNAVAILABLE"
                ST_ERROR -> "ERROR"
                ST_INVALID -> "INVALID"
                ST_SKIPPED -> "SKIPPED"
                else -> s.status.uppercase()
            }
            sb.append("\n${i + 1}. [$mark] $label")
            if (s.result.isNotBlank()) sb.append("\n${s.result.take(600)}")
            sb.append("\n")
        }
        if (done == 0) {
            sb.append("\nНи одно действие не выполнено успешно — выше честные причины. Ничего не выдумано.")
        }
        return sb.toString().take(2500)
    }

    fun buildPlanPrompt(task: String): String =
        "Ты планировщик локального ассистента. Доступные действия (только они):\n" +
            AgentActionTypes.ALL.joinToString(", ") + ".\n" +
            "Верни ТОЛЬКО пронумерованные ACTION-строки (максимум 5), без вступлений и пояснений.\n" +
            "Формат строки: ACTION: TYPE | key=value | key2=value\n" +
            "Ключи: SEARCH_NOTES(query), READ_NOTE(name), WRITE_NOTE(name, text), " +
            "SUMMARIZE_TEXT(text), TRANSLATE_TEXT(text, target, source), ANALYZE_TEXT(text), " +
            "TRANSCRIBE_AUDIO(file, lang), ANALYZE_IMAGE(file, question), " +
            "CREATE_SOCIAL_DRAFT(text, persona), SAVE_RESULT(name, text).\n" +
            "Если задача не требует действий из списка — верни одну строку: ACTION: SUMMARIZE_TEXT | text=<суть задачи>.\n" +
            "Задача: ${task.take(800)}"
}
