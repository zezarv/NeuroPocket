package com.neuropocket.app.engine

import com.neuropocket.app.data.ChatMessage
import com.neuropocket.app.data.Persona
import kotlinx.coroutines.delay
import kotlin.random.Random

interface AiEngine {
    val engineName: String
    val isLocalReal: Boolean
    suspend fun generate(
        history: List<ChatMessage>,
        persona: Persona,
        userText: String,
        onToken: (String) -> Unit = {}
    ): String
}

object PromptTools {
    fun translator(text: String, target: String = "английский"): String =
        "Переведи на $target, сохрани смысл и стиль, без пояснений:\n\n$text"

    fun improver(text: String): String =
        "Улучши текст: исправь грамматику, сделай чище и сильнее, сохрани смысл. Верни только улучшенный вариант:\n\n$text"

    fun summarizer(text: String): String =
        "Сделай краткое саммари на русском (3-5 пунктов + вывод):\n\n$text"

    fun vibeCode(task: String): String =
        "Ты senior mobile-разработчик. Дай минимальный рабочий код + шаги (Kotlin/Compose). Задача: $task"

    fun detector(text: String): String =
        "Определи язык, тональность и возможные проблемы текста (кратко):\n\n$text"

    fun socialPost(characterName: String, bio: String, mood: String = "нейтральное"): String =
        "Придумай короткий пост для соцсети от лица $characterName ($bio), настроение: $mood. До 240 символов, живо, с 1 эмодзи."
}

/**
 * v1 движок-заглушка, полностью офлайн.
 * Честно помечен как Mock: генерирует локально без модели,
 * чтобы весь UI, персоны, лента, инструменты работали до подключения llama.cpp.
 * Не фильтрует контент — пользователь отвечает за свои персонажи/тексты (18+ разрешены локально).
 */
class MockEngine : AiEngine {
    override val engineName = "Mock-Local v1 (llama.cpp слот готов)"
    override val isLocalReal = false

    override suspend fun generate(
        history: List<ChatMessage>,
        persona: Persona,
        userText: String,
        onToken: (String) -> Unit
    ): String {
        val low = userText.lowercase()
        val base = when {
            low.startsWith("переведи") || low.contains("перевод") ->
                "[Перевод-заготовка] ${userText.take(300)}\n(Подключи GGUF-модель во вкладке Модели для полного качества)"
            low.contains("улучши") ->
                "[Улучшено-заготовка] ${userText.take(300)}"
            low.contains("код") || low.contains("вайбкод") || low.contains("vibe") ->
                "```kotlin\n// v1 заготовка: опиши задачу подробнее\nfun hello() = println(\"NeuroPocket v1\")\n```\nПодключи модель 3B+ для полного вайбкода."
            low.contains("пост") || low.contains("соцсет") ->
                randomPost()
            low.contains("привет") ->
                "Привет! Я ${persona.name}. Работаю полностью офлайн в v1. Загрузи GGUF во вкладке «Модели» — и я стану умнее."
            else ->
                "Понял: «${userText.take(200)}». Это локальный ответ-заготовка v1 (${persona.name}). " +
                    "Скачай модель 1B–3B и выбери её — движок llama.cpp уже подготовлен под слот models/."
        }
        // стриминг по словам для живости
        val parts = base.split(" ")
        val sb = StringBuilder()
        for (p in parts) {
            sb.append(p).append(" ")
            onToken(p + " ")
            delay(18)
        }
        return sb.toString().trim()
    }

    fun randomPost(): String {
        val variants = listOf(
            "кофе + ночной код = идеальное утро ☕ сегодня допиливаю своего бота",
            "тестил новую локальную модель прямо на телефоне — летает ⚡",
            "кто ещё пишет посты без интернета? я да 😎",
            "настроение: собрать плейлист из звуков клавиатуры 🎹",
            "мини-победа дня: разобрал 100500 заметок в порядок ✨",
            "вайбкод выходного дня: приложение за вечер, баги за неделю 😅"
        )
        return variants[Random.nextInt(variants.size)]
    }
}
