package com.neuropocket.app.core

/**
 * Phase B: настоящий Tool Workflow слой (personal edition, без переусложнения).
 *
 * Идея: тонкий testable слой поверх LLM:
 *  - validate input
 *  - preprocess (chunking для длинных текстов)
 *  - buildPrompt (строгие промпты на чанк)
 *  - parse/validate output (structured models)
 *  - join в исходном порядке
 *
 * Pure Kotlin, без Android-зависимостей — покрыто unit-тестами.
 * UI state остаётся во ViewModel; здесь только чистая логика.
 */

// ---------------------------------------------------------------------------
// Chunking: paragraph-aware, сохраняет порядок, Markdown-дружелюбный.
// ---------------------------------------------------------------------------

object ToolChunking {
    const val DEFAULT_MAX = 2500

    /**
     * Разбить длинный текст на чанки <= maxChars, стараясь резать по границам:
     * двойной перенос (параграфы) -> одиночный перенос -> предложения -> жёстко.
     */
    fun splitSmart(text: String, maxChars: Int = DEFAULT_MAX): List<String> {
        val src = text.replace("\r\n", "\n")
        if (src.length <= maxChars) return listOf(src)
        // 1) параграфы
        val paras = src.split(Regex("\n\\s*\n"))
        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) {
                chunks.add(cur.toString().trim())
                cur.clear()
            }
        }
        for (p in paras) {
            val piece = p.trim()
            if (piece.isEmpty()) continue
            if (piece.length > maxChars) {
                // слишком большой параграф: режем по строкам/предложениям
                flush()
                chunks.addAll(splitOversize(piece, maxChars))
            } else if (cur.length + piece.length + 2 <= maxChars) {
                if (cur.isNotEmpty()) cur.append("\n\n")
                cur.append(piece)
            } else {
                flush()
                cur.append(piece)
            }
        }
        flush()
        return chunks.filter { it.isNotEmpty() }
    }

    private fun splitOversize(piece: String, maxChars: Int): List<String> {
        // пробуем по строкам
        val lines = piece.split("\n")
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (ln in lines) {
            val t = ln.trimEnd()
            if (t.length > maxChars) {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                out.addAll(splitSentences(t, maxChars))
            } else if (cur.length + t.length + 1 <= maxChars) {
                if (cur.isNotEmpty()) cur.append("\n")
                cur.append(t)
            } else {
                out.add(cur.toString().trim())
                cur.clear()
                cur.append(t)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString().trim())
        return out.filter { it.isNotEmpty() }
    }

    private fun splitSentences(text: String, maxChars: Int): List<String> {
        val sentences = text.split(Regex("(?<=[.!?…;:])\\s+"))
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (s in sentences) {
            if (s.length > maxChars) {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                // жёсткая нарезка очень длинного предложения
                var i = 0
                while (i < s.length) {
                    out.add(s.substring(i, minOf(i + maxChars, s.length)))
                    i += maxChars
                }
            } else if (cur.length + s.length + 1 <= maxChars) {
                if (cur.isNotEmpty()) cur.append(" ")
                cur.append(s)
            } else {
                out.add(cur.toString().trim())
                cur.clear()
                cur.append(s)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString().trim())
        return out.filter { it.isNotEmpty() }
    }

    /** Собрать результаты чанков в исходном порядке. */
    fun joinOrdered(parts: List<String>): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

    fun needsChunking(text: String, maxChars: Int = DEFAULT_MAX): Boolean =
        text.length > maxChars
}

// ---------------------------------------------------------------------------
// Translator
// ---------------------------------------------------------------------------

object TranslatorWorkflow {
    val SUPPORTED_LANGS = listOf(
        "Авто", "Русский", "Украинский", "Английский", "Немецкий",
        "Французский", "Испанский", "Итальянский", "Арабский",
        "Турецкий", "Китайский", "Японский", "Казахский"
    )

    data class Options(
        val preserveFormatting: Boolean = true,
        val formality: String = "neutral", // formal | neutral | informal
        val glossary: String = ""
    )

    fun validateInput(text: String): String? = when {
        text.isBlank() -> "Пустой текст."
        text.length > 60000 -> "Слишком длинный текст (> 60k символов)."
        else -> null
    }

    fun formalityLine(f: String): String = when (f.lowercase()) {
        "formal" -> "Стиль: формальный."
        "informal" -> "Стиль: неформальный."
        else -> "Стиль: нейтральный."
    }

    fun buildChunkPrompt(
        chunk: String,
        src: String,
        dst: String,
        opts: Options,
        idx: Int, // 0-based
        total: Int
    ): String {
        val sb = StringBuilder()
        sb.append("Переведи текст с языка «${src.ifBlank { "авто" }}» на «${dst.ifBlank { "русский" }}». ")
        sb.append("Верни ТОЛЬКО перевод, без пояснений, без кавычек-обёрток. ")
        if (opts.preserveFormatting) sb.append("Сохрани параграфы, списки и Markdown-разметку. ")
        sb.append(formalityLine(opts.formality)).append(" ")
        if (opts.glossary.isNotBlank()) {
            sb.append("Глоссарий (используй эти соответствия терминов): ${opts.glossary.take(500)}. ")
        }
        if (total > 1) {
            sb.append("Это часть ${idx + 1} из $total. Переводи консистентно с соседними частями, не добавляй нумерацию частей. ")
        }
        sb.append("\n\n").append(chunk)
        return sb.toString()
    }
}

// ---------------------------------------------------------------------------
// Summarizer
// ---------------------------------------------------------------------------

object SummarizerWorkflow {
    // short | detailed | keypoints | actions | timeline
    val MODES = listOf("short", "detailed", "keypoints", "actions", "timeline")

    fun modeTitle(m: String): String = when (m) {
        "detailed" -> "Подробное"
        "keypoints" -> "Ключевые тезисы"
        "actions" -> "Action items"
        "timeline" -> "Timeline"
        else -> "Краткое"
    }

    fun validateInput(text: String): String? = when {
        text.isBlank() -> "Пустой текст."
        text.trim().length < 40 -> "Текст слишком короткий для саммари."
        text.length > 80000 -> "Слишком длинный текст (> 80k символов)."
        else -> null
    }

    fun buildChunkPrompt(chunk: String, idx: Int, total: Int): String =
        "Сделай локальное саммари части ${idx + 1} из $total (3–5 коротких пунктов, только суть, без воды):\n\n$chunk"

    fun buildSynthPrompt(localSummaries: String, mode: String): String {
        val instruction = when (mode) {
            "detailed" -> "Собери подробное связное саммари: суть, контекст, детали, вывод."
            "keypoints" -> "Верни структурированный список ключевых тезисов (маркированный список) + вывод одной строкой."
            "actions" -> "Верни список action items (что сделать, чеклист) + вывод одной строкой."
            "timeline" -> "Верни события в хронологическом порядке (timeline) + вывод одной строкой."
            else -> "Верни саммари: 3–5 пунктов + вывод одной строкой."
        }
        return "Ниже — локальные саммари частей длинного текста. $instruction " +
            "Не выдумывай фактов, которых нет в частях.\n\n$localSummaries"
    }

    fun buildSinglePrompt(text: String, mode: String): String {
        val instruction = when (mode) {
            "detailed" -> "Подробное связное саммари: суть, контекст, детали, вывод."
            "keypoints" -> "Ключевые тезисы маркированным списком + вывод одной строкой."
            "actions" -> "Список action items (чеклист) + вывод одной строкой."
            "timeline" -> "События в хронологическом порядке + вывод одной строкой."
            else -> "Суть в 3–5 пунктах + вывод одной строкой."
        }
        return "Саммари на русском. $instruction Текст:\n\n$text"
    }
}

// ---------------------------------------------------------------------------
// Improver
// ---------------------------------------------------------------------------

object ImproverWorkflow {
    // grammar | natural | professional | concise | expand | clearer | tone
    val MODES = listOf("grammar", "natural", "professional", "concise", "expand", "clearer", "tone")

    fun modeTitle(m: String): String = when (m) {
        "grammar" -> "Только грамматика"
        "professional" -> "Профессиональный"
        "concise" -> "Короче"
        "expand" -> "Развернуть"
        "clearer" -> "Понятнее"
        "tone" -> "Сохранить тон"
        else -> "Естественно"
    }

    fun validateInput(text: String): String? = when {
        text.isBlank() -> "Пустой текст."
        text.length > 30000 -> "Слишком длинный текст для улучшения за раз (> 30k)."
        else -> null
    }

    fun buildPrompt(text: String, mode: String): String {
        val instruction = when (mode) {
            "grammar" -> "Исправь только грамматику, орфографию и пунктуацию. Не меняй стиль и длину."
            "professional" -> "Сделай текст профессиональным и деловым. Сохрани смысл."
            "concise" -> "Сократи текст, убери воду, сохрани смысл и ключевые факты."
            "expand" -> "Слегка разверни текст: добавь ясности и связок, не выдумывая новых фактов."
            "clearer" -> "Сделай текст понятнее: простые предложения, логика, структура."
            "tone" -> "Улучши текст, строго сохраняя авторский тон и голос."
            else -> "Сделай текст естественным и чистым: грамматика, стиль, сила."
        }
        return "$instruction Сохрани исходный язык (не переводи без запроса). " +
            "Верни ТОЛЬКО улучшенный вариант, без пояснений. " +
            "После разделителя «---ИЗМЕНЕНИЯ---» одной строкой перечисли 1–5 главных правок (кратко).\n\n$text"
    }

    /** Разобрать ответ улучшателя на (improved, changes). */
    fun splitImproved(raw: String): Pair<String, String> {
        val sep = "---ИЗМЕНЕНИЯ---"
        val i = raw.indexOf(sep)
        if (i < 0) return raw.trim() to ""
        return raw.substring(0, i).trim() to raw.substring(i + sep.length).trim().take(600)
    }
}

// ---------------------------------------------------------------------------
// Analyzer (structured, без выдуманного AI-detector)
// ---------------------------------------------------------------------------

object AnalyzerWorkflow {
    data class AnalyzerResult(
        val language: String = "",
        val tone: String = "",
        val sentiment: String = "",
        val intent: String = "",
        val readability: String = "",
        val issues: String = "",
        val confidence: String = "",
        val notes: String = "",
        val raw: String = ""
    )

    fun validateInput(text: String): String? = when {
        text.isBlank() -> "Пустой текст."
        text.trim().length < 3 -> "Слишком короткий текст."
        text.length > 30000 -> "Слишком длинный текст (> 30k)."
        else -> null
    }

    fun buildPrompt(text: String): String =
        "Разбери текст и верни СТРОГО 7 строк в формате «Ключ: значение», без лишнего текста:\n" +
            "Язык: <язык>\nТон: <тон>\nНастроение: <позитивное/нейтральное/негативное/смешанное>\n" +
            "Намерение: <информировать/убедить/развлечь/попросить/иное>\n" +
            "Читаемость: <легко/средне/сложно + коротко почему>\n" +
            "Проблемы: <грамматика/стиль/логика — кратко или «нет»>\n" +
            "Уверенность: <низкая/средняя/высокая>\n" +
            "НЕ оценивай вероятность авторства ИИ — такого замера нет. " +
            "Текст:\n\n$text"

    fun buildRepairPrompt(text: String, badOutput: String): String =
        "Предыдущий разбор нарушил формат. Верни СТРОГО 7 строк «Ключ: значение» " +
            "(Язык/Тон/Настроение/Намерение/Читаемость/Проблемы/Уверенность), без вступлений. " +
            "Текст:\n\n${text.take(4000)}\n\nБыл такой неверный ответ (исправь формат):\n${badOutput.take(1000)}"

    /** Строгий парсер: ждём минимум 5 из 7 ключей. */
    fun parse(raw: String): AnalyzerResult? {
        val map = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val t = line.trim().trimStart('-', '*', '•', ' ').trim()
            val ci = t.indexOf(':')
            if (ci <= 0) continue
            val k = t.substring(0, ci).trim().lowercase()
            val v = t.substring(ci + 1).trim()
            if (v.isEmpty()) continue
            when {
                k.startsWith("язык") || k.startsWith("language") -> map["language"] = v.take(120)
                k.startsWith("тон") || k.startsWith("tone") -> map["tone"] = v.take(200)
                k.startsWith("настроен") || k.startsWith("sentiment") || k.startsWith("mood") -> map["sentiment"] = v.take(120)
                k.startsWith("намерен") || k.startsWith("intent") -> map["intent"] = v.take(200)
                k.startsWith("читаем") || k.startsWith("readab") -> map["readability"] = v.take(250)
                k.startsWith("проблем") || k.startsWith("issues") || k.startsWith("проблемы") -> map["issues"] = v.take(400)
                k.startsWith("уверен") || k.startsWith("confid") -> map["confidence"] = v.take(60)
                k.startsWith("замет") || k.startsWith("notes") || k.startsWith("примеч") -> map["notes"] = v.take(300)
            }
        }
        // старый pipe-формат "Язык: .. | Тон: .. | Проблемы: .. | Оценка .." — тоже понимаем
        if (map.size < 3 && raw.contains("|")) {
            for (part in raw.split("|")) {
                val ci = part.indexOf(':')
                if (ci <= 0) continue
                val k = part.substring(0, ci).trim().lowercase()
                val v = part.substring(ci + 1).trim()
                if (v.isEmpty()) continue
                when {
                    k.contains("язык") -> map["language"] = v.take(120)
                    k.contains("тон") -> map["tone"] = v.take(200)
                    k.contains("проблем") -> map["issues"] = v.take(400)
                    k.contains("оцен") -> map["confidence"] = v.take(60)
                }
            }
        }
        // нужно минимум 5 полей (confidence можно вывести)
        val required = listOf("language", "tone", "sentiment", "intent", "readability", "issues")
        val hit = required.count { map.containsKey(it) }
        if (hit < 5) return null
        return AnalyzerResult(
            language = map["language"] ?: "",
            tone = map["tone"] ?: "",
            sentiment = map["sentiment"] ?: "",
            intent = map["intent"] ?: "",
            readability = map["readability"] ?: "",
            issues = map["issues"] ?: "",
            confidence = map["confidence"] ?: "средняя",
            notes = map["notes"] ?: "",
            raw = raw.take(2000)
        )
    }

    fun needsRepair(raw: String): Boolean = parse(raw) == null
}

// ---------------------------------------------------------------------------
// VibeCode workspace
// ---------------------------------------------------------------------------

object VibeCodeWorkflow {
    data class VibeFile(val name: String, val language: String, val code: String)
    data class VibeResult(
        val files: List<VibeFile>,
        val explanation: String,
        val runInstructions: String,
        val warnings: String,
        val notExecuted: Boolean = true
    )

    const val NOT_EXECUTED = "Сгенерировано / на устройстве не выполнялось."

    fun validateInput(task: String): String? = when {
        task.isBlank() -> "Пустая задача."
        task.trim().length < 5 -> "Опиши задачу чуть подробнее."
        task.length > 20000 -> "Слишком длинное описание (> 20k)."
        else -> null
    }

    fun buildPrompt(task: String, language: String, framework: String, context: String): String {
        val sb = StringBuilder()
        sb.append("Ты senior-разработчик. Дай минимальное рабочее решение. ")
        sb.append("Формат СТРОГО:\n")
        sb.append("1) Файлы кода в блоках ```язык filename=\"ИмяФайла\" ... ``` (1+ файлов).\n")
        sb.append("2) Раздел «Объяснение:» — 3–7 предложений.\n")
        sb.append("3) Раздел «Запуск:» — шаги запуска.\n")
        sb.append("4) Раздел «Риски:» — подводные камни.\n")
        sb.append("НИКОГДА не пиши «проверено/протестировано/работает», если код не запускался. ")
        sb.append("Пиши честно: «Сгенерировано / на устройстве не выполнялось».\n")
        if (language.isNotBlank()) sb.append("Язык: $language. ")
        if (framework.isNotBlank()) sb.append("Фреймворк: $framework. ")
        sb.append("\nЗадача: $task\n")
        if (context.isNotBlank()) sb.append("Контекст/существующий код:\n${context.take(6000)}\n")
        return sb.toString()
    }

    /** Парсинг ответа: ```блоки -> файлы, остальное -> секции. */
    fun parse(raw: String): VibeResult {
        val files = mutableListOf<VibeFile>()
        val fence = Regex("```(\\w*)\\s*(?:filename\\s*=\\s*\"([^\"]+)\"|([^\\n]*))?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        for (m in fence.findAll(raw)) {
            val lang = m.groupValues[1].ifBlank { "code" }.take(30)
            val fname = (m.groupValues[2].ifBlank { m.groupValues[3].trim() }).trim().take(120)
            val code = m.groupValues[4].trim().take(20000)
            if (code.length < 2) continue
            val name = when {
                fname.isNotBlank() && !fname.startsWith("```") -> fname
                else -> "snippet-${files.size + 1}.${
                    when (lang.lowercase()) {
                        "kotlin" -> "kt"; "java" -> "java"; "python", "py" -> "py"
                        "javascript", "js" -> "js"; "typescript", "ts" -> "ts"
                        "xml" -> "xml"; "gradle" -> "kts"; "sql" -> "sql"
                        else -> "txt"
                    }
                }"
            }
            files.add(VibeFile(name = name, language = lang, code = code))
        }
        var textNoCode = fence.replace(raw, "\n").trim()
        fun section(vararg keys: String): String {
            for (k in keys) {
                val idx = textNoCode.indexOf(k, ignoreCase = true)
                if (idx >= 0) {
                    val rest = textNoCode.substring(idx + k.length).trimStart(':', '-', ' ').trim()
                    // до следующего заголовка-секции
                    val next = listOf("Запуск:", "Риски:", "Объяснение:", "Предупрежден").mapNotNull { h ->
                        val j = rest.indexOf(h, ignoreCase = true).takeIf { it > 0 }
                        j
                    }.minOrNull() ?: rest.length
                    return rest.substring(0, next).trim().take(3000)
                }
            }
            return ""
        }
        var explanation = section("Объяснение:").ifBlank { textNoCode.take(1500) }
        val run = section("Запуск:").take(1500)
        val risks = section("Риски:", "Риски ", "Предупрежден").take(1200)
        // честность: если модель написала tested/works — помечаем, но UI всегда показывает notExecuted
        if (explanation.isBlank()) explanation = "См. код выше."
        return VibeResult(files, explanation, run, risks, notExecuted = true)
    }
}

// ---------------------------------------------------------------------------
// Disclosure: честные пометки Mock/template.
// ---------------------------------------------------------------------------

object CapabilityDisclosure {
    private val MOCK_MARKERS = listOf(
        "[перевод-заготовка]", "[улучшено-заготовка]", "заготовка v1",
        "подключи gguf", "подключи модель", "llama.cpp слот готов",
        "локальный ответ-заготовка"
    )

    fun isMockOutput(text: String): Boolean {
        val low = text.lowercase()
        return MOCK_MARKERS.any { low.contains(it) }
    }

    /** Короткая честная метка движка для UI инструментов/агента. */
    fun engineBadge(engineName: String, isLocalReal: Boolean, output: String): String? {
        if (!isLocalReal) return "Mock / template fallback"
        if (isMockOutput(output)) return "Mock / template fallback"
        return null
    }
}

// ---------------------------------------------------------------------------
// Registry: границы инструментов (для ViewModel + тестов).
// ---------------------------------------------------------------------------

object ToolWorkflowRegistry {
    const val TRANSLATOR = "translator"
    const val SUMMARIZER = "summarizer"
    const val IMPROVER = "improver"
    const val ANALYZER = "detector"
    const val VIBECODE = "vibecode"

    val ALL = listOf(TRANSLATOR, SUMMARIZER, IMPROVER, ANALYZER, VIBECODE)

    fun validate(toolId: String, input: String): String? = when (toolId) {
        TRANSLATOR -> TranslatorWorkflow.validateInput(input)
        SUMMARIZER -> SummarizerWorkflow.validateInput(input)
        IMPROVER -> ImproverWorkflow.validateInput(input)
        ANALYZER -> AnalyzerWorkflow.validateInput(input)
        VIBECODE -> VibeCodeWorkflow.validateInput(input)
        else -> if (input.isBlank()) "Пустой ввод." else null
    }
}
