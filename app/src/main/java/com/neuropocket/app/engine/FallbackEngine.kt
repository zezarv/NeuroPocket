package com.neuropocket.app.engine

import com.neuropocket.app.data.ChatMessage
import com.neuropocket.app.data.Persona

/**
 * Обёртка: если primary вернул ошибку "[...]", один раз пробуем fallback.
 * Для нескримминговых вызовов (агент, инструменты).
 */
class FallbackEngine(
    private val primary: AiEngine,
    private val fallback: AiEngine?,
    private val onFallback: () -> Unit = {}
) : AiEngine {
    override val engineName: String get() = primary.engineName
    override val isLocalReal: Boolean get() = primary.isLocalReal

    override suspend fun generate(
        history: List<ChatMessage>,
        persona: Persona,
        userText: String,
        onToken: (String) -> Unit
    ): String {
        val r = primary.generate(history, persona, userText, onToken)
        if (fallback != null && r.startsWith("[")) {
            try { onFallback() } catch (_: Exception) {}
            return fallback.generate(history, persona, userText, onToken)
        }
        return r
    }
}
