package com.neuropocket.app.data

import com.neuropocket.app.core.AnalyzerWorkflow
import com.neuropocket.app.core.ImproverWorkflow
import com.neuropocket.app.core.SummarizerWorkflow
import com.neuropocket.app.core.ToolChunking
import com.neuropocket.app.core.TranslatorWorkflow
import com.neuropocket.app.core.VibeCodeWorkflow

/**
 * Текстовые инструменты — у каждого своя среда и своя история.
 * История хранится отдельно от общего чата: Map toolId -> runs.
 */
data class ToolDef(
    val id: String,
    val title: String,
    val inputLabel: String,
    val hint: String,
    val withLangs: Boolean = false,
    val chatty: Boolean = false // vibecode/detector — ответы подлиннее
)

object ToolCatalog {
    val textTools = listOf(
        ToolDef(
            id = "translator",
            title = "Переводчик",
            inputLabel = "Текст для перевода…",
            hint = "Пара языков + история переводов. Отдельно от чата.",
            withLangs = true
        ),
        ToolDef(
            id = "vibecode",
            title = "VibeCode",
            inputLabel = "Что закодить…",
            hint = "Код + объяснение. История сниппетов.",
            chatty = true
        ),
        ToolDef(
            id = "improver",
            title = "Улучшение текста",
            inputLabel = "Черновик…",
            hint = "Грамматика, стиль, сила текста."
        ),
        ToolDef(
            id = "summarizer",
            title = "Саммари",
            inputLabel = "Длинный текст…",
            hint = "Суть в 3–5 пунктах + вывод."
        ),
        ToolDef(
            id = "detector",
            title = "Детектор",
            inputLabel = "Текст для разбора…",
            hint = "Язык, тон, проблемы текста.",
            chatty = true
        )
    )

    fun byId(id: String): ToolDef? = textTools.find { it.id == id }

    // Legacy single-shot prompt (оставлен для совместимости истории/старых вызовов).
    fun buildPrompt(def: ToolDef, input: String, langFrom: String, langTo: String): String {
        val t = input.take(3000)
        return when (def.id) {
            "translator" -> "Переведи с языка «${langFrom.ifBlank { "авто" }}» на «${langTo.ifBlank { "русский" }}». " +
                "Сохрани смысл, стиль и форматирование. Верни только перевод, без пояснений:\n\n$t"
            "vibecode" -> "Ты senior-разработчик. Задача: $t\nДай: 1) минимальный рабочий код, 2) как запустить, 3) подводные камни. Кратко."
            "improver" -> "Улучши текст: грамматика, стиль, сила. Сохрани смысл и язык. Верни только улучшенный вариант:\n\n$t"
            "summarizer" -> "Саммари на русском: 3–5 пунктов + вывод одной строкой:\n\n$t"
            "detector" -> "Разбери текст. Верни строго: Язык: … | Тон: … | Проблемы: … | Оценка 1–5:\n\n$t"
            else -> t
        }
    }

    // ---- Phase B: workflow-промпты (чанки, режимы, structured output) ----

    fun translatorChunk(
        chunk: String, src: String, dst: String,
        preserveFormatting: Boolean = true, formality: String = "neutral",
        glossary: String = "", idx: Int = 0, total: Int = 1
    ): String = TranslatorWorkflow.buildChunkPrompt(
        chunk, src, dst,
        TranslatorWorkflow.Options(preserveFormatting, formality, glossary), idx, total
    )

    fun summarizerSingle(text: String, mode: String = "short"): String =
        SummarizerWorkflow.buildSinglePrompt(text.take(12000), mode)

    fun summarizerChunk(chunk: String, idx: Int, total: Int): String =
        SummarizerWorkflow.buildChunkPrompt(chunk, idx, total)

    fun summarizerSynth(locals: String, mode: String = "short"): String =
        SummarizerWorkflow.buildSynthPrompt(locals.take(12000), mode)

    fun improver(text: String, mode: String = "natural"): String =
        ImproverWorkflow.buildPrompt(text.take(12000), mode)

    fun analyzer(text: String): String =
        AnalyzerWorkflow.buildPrompt(text.take(12000))

    fun analyzerRepair(text: String, bad: String): String =
        AnalyzerWorkflow.buildRepairPrompt(text, bad)

    fun vibecode(task: String, language: String = "", framework: String = "", context: String = ""): String =
        VibeCodeWorkflow.buildPrompt(task.take(8000), language.take(60), framework.take(80), context)

    fun splitForTool(toolId: String, text: String): List<String> {
        val max = when (toolId) {
            "summarizer" -> 3000
            "translator" -> 2500
            else -> 2500
        }
        return ToolChunking.splitSmart(text, max)
    }
}

/** Хэштеги поста: #слово (кириллица/латиница/цифры). */
fun extractTags(text: String): List<String> {
    return Regex("#[\\p{L}\\p{N}_]+").findAll(text)
        .map { it.value.lowercase().take(30) }
        .distinct().take(8).toList()
}
