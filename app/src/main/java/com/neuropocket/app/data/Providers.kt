package com.neuropocket.app.data

import kotlinx.serialization.Serializable

/**
 * Внешний провайдер ИИ: сервер на ПК (LM Studio, Ollama, llama.cpp server…)
 * или облачный API. Всё OpenAI-совместимое + особый тип pollinations (GET без ключа).
 * Ключи хранятся только на устройстве.
 */
@Serializable
data class AiProvider(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Мой сервер",
    val kind: String = "openai", // openai | pollinations
    val baseUrl: String = "http://192.168.1.100:1234/v1",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = true
)

object ProviderPresets {
    data class Preset(
        val name: String,
        val kind: String,
        val baseUrl: String,
        val model: String,
        val needKey: Boolean,
        val descRu: String
    )

    // ПК по локальной сети — без ключей, подставь IP своего компьютера.
    val local = listOf(
        Preset(
            "LM Studio (ПК)", "openai", "http://192.168.1.100:1234/v1", "",
            false,
            "В LM Studio: вкладка Server → Start Server. Ключ не нужен. Телефон и ПК — в одном Wi-Fi."
        ),
        Preset(
            "Ollama (ПК)", "openai", "http://192.168.1.100:11434/v1", "llama3.2",
            false,
            "На ПК запусти: OLLAMA_HOST=0.0.0.0 OLLAMA_ORIGINS=* ollama serve. Модель — любая из ollama list."
        ),
        Preset(
            "llama.cpp server (ПК)", "openai", "http://192.168.1.100:8080/v1", "",
            false,
            "llama-server -m model.gguf --port 8080 --host 0.0.0.0. Ключ не нужен."
        ),
        Preset(
            "text-generation-webui (ПК)", "openai", "http://192.168.1.100:5000/v1", "",
            false,
            "Oobabooga: запусти с флагом --api и включи OpenAI-совместимый API."
        )
    )

    // Облака. Честно: truly-free без ключа — только Pollinations.
    // Остальные — бесплатные лимиты/триалы, ключ получаешь сам за минуту.
    val cloud = listOf(
        Preset(
            "Pollinations (бесплатно, без ключа)", "pollinations", "", "openai",
            false,
            "Реально бесплатный API без регистрации. Лимиты и очередь возможны. Модель: openai (по умолч.)."
        ),
        Preset(
            "Google Gemini (free tier)", "openai",
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            "gemini-2.0-flash", true,
            "Бесплатный лимит щедрый. Ключ: aistudio.google.com → Get API key. Вставь ниже."
        ),
        Preset(
            "OpenRouter (есть :free модели)", "openai", "https://openrouter.ai/api/v1",
            "deepseek/deepseek-chat-v3-0324:free", true,
            "Регистрация бесплатна, много моделей с суффиксом :free. Ключ: openrouter.ai/keys. Список :free меняется — жми «Модели с сервера»."
        ),
        Preset(
            "Groq (free tier)", "openai", "https://api.groq.com/openai/v1",
            "llama-3.3-70b-versatile", true,
            "Очень быстрый инференс, бесплатные лимиты. Ключ: console.groq.com/keys."
        ),
        Preset(
            "Mistral (trial)", "openai", "https://api.mistral.ai/v1",
            "mistral-small-latest", true,
            "Триал-кредит новичкам. Ключ: console.mistral.ai."
        ),
        Preset(
            "xAI Grok (платный)", "openai", "https://api.x.ai/v1",
            "grok-3-mini", true,
            "Платный API, новичкам иногда дают кредит. Ключ: console.x.ai. Добавили для полноты."
        )
    )
}
