package com.neuropocket.app.data

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String = "user", // user | assistant | system
    val text: String = "",
    val ts: Long = System.currentTimeMillis(),
    val personaId: String? = null
)

@Serializable
data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "Новый чат",
    val personaId: String? = null,
    val created: Long = System.currentTimeMillis(),
    var updated: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val folder: String = ""
)

@Serializable
data class Persona(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Ассистент",
    val systemPrompt: String = "Ты полезный локальный ассистент. Отвечай кратко и по-русски.",
    val avatarEmoji: String = "\uD83E\uDD16",
    val temperature: Float = 0.7f,
    val nsfwAllowed: Boolean = true,
    val desc: String = "",
    val tags: List<String> = emptyList(),
    val voice: String = "", // имя папки голоса, "" = общий,
    val avatarPath: String = "", // файл в avatars/, "" = эмодзи,
    val engine: String = ""
)

@Serializable
data class ToolRun(
    val id: String = java.util.UUID.randomUUID().toString(),
    val input: String = "",
    val output: String = "",
    val ts: Long = System.currentTimeMillis(),
    // Phase B: workflow-контекст (все с дефолтами — старые записи читаются).
    val toolId: String = "",
    val sourceLang: String = "",
    val targetLang: String = "",
    val mode: String = "",
    val options: String = "",
    val engine: String = "",
    val mockFallback: Boolean = false
)

@Serializable
data class AiModelInfo(
    val id: String,
    val name: String,
    val sizeLabel: String, // e.g. 0.5B, 1B, 3B, 7-8B
    val ramNeedGb: Double,
    val fileName: String,
    val url: String,
    val format: String = "GGUF",
    val kind: String = "text", // text | whisper | tts | image-stub | music-stub
    val nsfw: Boolean = false,
    val descRu: String = ""
)

@Serializable
data class SocialCharacter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Алекс",
    val handle: String = "@alex",
    val bio: String = "люблю кофе и код",
    val emoji: String = "\uD83D\uDE0E"
)

@Serializable
data class PostComment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val ts: Long = System.currentTimeMillis(),
    val aiMade: Boolean = false
)

@Serializable
data class SocialPost(
    val id: String = java.util.UUID.randomUUID().toString(),
    val authorId: String,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    // Personal edition: новый пост стартует с 0 (никаких random likes).
    val likes: Int = 0,
    val liked: Boolean = false,
    val aiMade: Boolean = false,
    // Phase B: честная связь репоста + пометка template-fallback.
    val repostOfId: String? = null,
    val template: Boolean = false
)
