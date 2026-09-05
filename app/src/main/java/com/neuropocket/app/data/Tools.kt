package com.neuropocket.app.data

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
}

/** Хэштеги поста: #слово (кириллица/латиница/цифры). */
fun extractTags(text: String): List<String> {
    return Regex("#[\\p{L}\\p{N}_]+").findAll(text)
        .map { it.value.lowercase().take(30) }
        .distinct().take(8).toList()
}
