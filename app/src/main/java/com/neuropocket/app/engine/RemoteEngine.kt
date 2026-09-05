package com.neuropocket.app.engine

import com.neuropocket.app.data.AiProvider
import com.neuropocket.app.data.ChatMessage
import com.neuropocket.app.data.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

/** OpenAI-совместимый провайдер (LM Studio, Ollama, vLLM, облака) + Pollinations. */
class RemoteEngine(
    val provider: AiProvider,
    var maxTokens: Int = 512,
    var topP: Float = 0.9f
) : AiEngine {
    override val engineName: String get() = "${provider.name} (${provider.model.ifBlank { "?" }})"
    override val isLocalReal = false

    override suspend fun generate(
        history: List<ChatMessage>,
        persona: Persona,
        userText: String,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (provider.kind == "pollinations") return@withContext pollinations(history, persona, userText, onToken)
        val base = provider.baseUrl.trim().trimEnd('/')
        if (base.isEmpty() || provider.model.isBlank()) {
            return@withContext "[Провайдер ${provider.name}: не заполнены URL или модель. Открой Провайдеры.]"
        }
        // сначала стрим, при неудаче — обычный запрос
        try {
            streamChat(base, history, persona, userText, onToken)
        } catch (e: Exception) {
            try {
                val full = plainChat(base, history, persona, userText)
                onToken(full)
                full
            } catch (e2: Exception) {
                "[${provider.name}: ${shortErr(e2)}]"
            }
        }
    }

    private fun msgs(history: List<ChatMessage>, persona: Persona, userText: String): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", persona.systemPrompt))
        for (m in history.takeLast(12)) {
            if (m.role == "user" || m.role == "assistant") {
                arr.put(JSONObject().put("role", m.role).put("content", m.text.take(1500)))
            }
        }
        arr.put(JSONObject().put("role", "user").put("content", userText.take(2000)))
        return arr
    }

    private fun body(stream: Boolean, history: List<ChatMessage>, persona: Persona, userText: String): JSONObject {
        return JSONObject()
            .put("model", provider.model)
            .put("messages", msgs(history, persona, userText))
            .put("temperature", persona.temperature.coerceIn(0f, 2f).toDouble())
            .put("top_p", topP.coerceIn(0f, 1f).toDouble())
            .put("max_tokens", maxTokens.coerceIn(16, 8192))
            .put("stream", stream)
    }

    private fun post(base: String, json: JSONObject, acceptSse: Boolean): okhttp3.Response {
        val rb = json.toString().toRequestBody("application/json".toMediaType())
        val b = Request.Builder().url("$base/chat/completions").post(rb)
        if (provider.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${provider.apiKey}")
        if (acceptSse) b.header("Accept", "text/event-stream")
        val call = http.newCall(b.build())
        activeCall = call
        try {
            return call.execute()
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    private fun streamChat(base: String, history: List<ChatMessage>, persona: Persona, userText: String, onToken: (String) -> Unit): String {
        post(base, body(true, history, persona, userText), true).use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val src = resp.body?.source() ?: throw Exception("пустое тело")
            val sb = StringBuilder()
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                val t = line.trim()
                if (!t.startsWith("data:")) continue
                val payload = t.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                try {
                    val delta = JSONObject(payload)
                        .getJSONArray("choices").getJSONObject(0)
                        .optJSONObject("delta")?.optString("content")
                        ?: JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                            .optJSONObject("message")?.optString("content")
                    if (!delta.isNullOrEmpty()) {
                        sb.append(delta)
                        onToken(delta)
                    }
                } catch (_: Exception) { /* heartbeat/comments пропускаем */ }
            }
            val out = sb.toString()
            if (out.isBlank()) throw Exception("сервер вернул пустой стрим")
            return out.trim()
        }
    }

    private fun plainChat(base: String, history: List<ChatMessage>, persona: Persona, userText: String): String {
        post(base, body(false, history, persona, userText), false).use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val txt = resp.body?.string() ?: throw Exception("пустое тело")
            return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        }
    }

    private fun pollinations(history: List<ChatMessage>, persona: Persona, userText: String, onToken: (String) -> Unit): String {
        val prompt = buildString {
            append(persona.systemPrompt.take(500)).append("\n")
            for (m in history.takeLast(6)) {
                if (m.role == "user") append("User: ").append(m.text.take(500)).append("\n")
                if (m.role == "assistant") append("AI: ").append(m.text.take(500)).append("\n")
            }
            append("User: ").append(userText.take(1000))
        }.take(3500)
        val model = provider.model.ifBlank { "openai" }
        val url = "https://text.pollinations.ai/${URLEncoder.encode(prompt, "UTF-8")}?model=$model"
        val req = Request.Builder().url(url).get().build()
        val call = http.newCall(req)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val out = (resp.body?.string() ?: "").trim()
                if (out.isEmpty()) throw Exception("пустой ответ")
                onToken(out)
                return out
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    companion object {
        @Volatile private var activeCall: okhttp3.Call? = null

        fun cancelCurrent() {
            try { activeCall?.cancel() } catch (_: Exception) {}
            activeCall = null
        }

        fun shortErr(e: Exception): String {
            val m = e.message ?: e.javaClass.simpleName
            return when {
                m.contains("Failed to connect", true) || m.contains("ConnectException") ->
                    "нет соединения. Проверь IP/порт и один Wi-Fi."
                m.contains("401", true) || m.contains("Unauthorized", true) ->
                    "401 — неверный API-ключ."
                m.contains("404", true) -> "404 — проверь URL и имя модели."
                m.contains("timeout", true) || m.contains("Timeout", true) ->
                    "таймаут. Сервер долго думает или недоступен."
                m.contains("CLEARTEXT", true) -> "HTTP заблокирован."
                else -> m.take(160)
            }
        }

        /** Список моделей сервера. Бросает исключение с понятным текстом. */
        fun fetchModels(p: AiProvider): List<String> {
            if (p.kind == "pollinations") return listOf("openai")
            try {
                val base = p.baseUrl.trim().trimEnd('/')
                val b = Request.Builder().url("$base/models").get()
                if (p.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${p.apiKey}")
                http.newCall(b.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string() ?: "").optJSONArray("data") ?: JSONArray()
                    return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }.sorted()
                }
            } catch (e: Exception) {
                throw Exception(shortErr(e as? Exception ?: Exception(e.toString())))
            }
        }
    }
}
