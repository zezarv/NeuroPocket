package com.neuropocket.app.engine

import com.neuropocket.app.data.ChatMessage
import com.neuropocket.app.data.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Реальный локальный движок через llama.cpp (JNI, CPU arm64).
 * Собирает промпт из system + истории и вызывает blocking generate в IO.
 */
class LlamaEngine : AiEngine {
    override val engineName: String
        get() = if (LlamaNative.available) "llama.cpp native" else "llama.cpp (native нет)"
    override val isLocalReal = true

    var maxTokens: Int = 256
    var topP: Float = 0.9f
    var topK: Int = 40

    fun load(path: String, nCtx: Int = 2048, threads: Int = 6, gpuLayers: Int = 0): Int =
        if (!LlamaNative.available) -99 else LlamaNative.loadModel(path, nCtx, threads, gpuLayers)

    fun gpuSupported(): Boolean = try { LlamaNative.available && LlamaNative.supportsGpu() } catch (_: Exception) { false }

    fun loaded(): Boolean = try { LlamaNative.available && LlamaNative.isLoaded() } catch (_: Exception) { false }

    fun unload() = try { LlamaNative.unload() } catch (_: Exception) {}

    override suspend fun generate(
        history: List<ChatMessage>,
        persona: Persona,
        userText: String,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (!loaded()) return@withContext "[Модель не загружена] Выбери .gguf во вкладке Модели и нажми Загрузить в RAM."
        val prompt = buildPrompt(history, persona, userText)
        val seed = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val raw = try {
            if (LlamaNative.available) {
                LlamaNative.generateStream(prompt, maxTokens.coerceIn(8, 1024), persona.temperature, topP, topK, seed,
                    object : TokenSink { override fun emit(piece: String) { onToken(piece) } })
            } else "[Модель не загружена]"
        } catch (e: Exception) { return@withContext "[Ошибка native: ${e.message}]" }
        if (raw.startsWith("__ERR:")) return@withContext "[Ошибка движка $raw] Попробуй модель меньше или уменьши контекст."
        raw.trim()
    }

    private fun buildPrompt(history: List<ChatMessage>, persona: Persona, userText: String): String {
        val sb = StringBuilder()
        sb.append("<system>\n").append(persona.systemPrompt).append("\n</system>\n")
        val tail = history.takeLast(12)
        for (m in tail) {
            when (m.role) {
                "user" -> sb.append("<user>\n").append(m.text.take(1200)).append("\n</user>\n")
                "assistant" -> sb.append("<assistant>\n").append(m.text.take(1200)).append("\n</assistant>\n")
            }
        }
        sb.append("<user>\n").append(userText.take(1500)).append("\n</user>\n<assistant>\n")
        return sb.toString()
    }
}
