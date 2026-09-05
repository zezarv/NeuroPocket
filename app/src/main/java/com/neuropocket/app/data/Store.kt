package com.neuropocket.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.ds by preferencesDataStore("neuro_pocket")

object Store {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val KEY_PERSONAS = stringPreferencesKey("personas_json")
    private val KEY_CHATS = stringPreferencesKey("chats_json") // legacy v1, для миграции
    private val KEY_SESSIONS = stringPreferencesKey("sessions_json")
    private val KEY_MSGMAP = stringPreferencesKey("msgmap_json")
    private val KEY_ACTIVE_SESSION = stringPreferencesKey("active_session_id")
    private val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
    private val KEY_ACTIVE_PERSONA = stringPreferencesKey("active_persona_id")
    private val KEY_THEME = stringPreferencesKey("theme") // light|dark|auto
    private val KEY_ACCENT = stringPreferencesKey("accent") // hex
    private val KEY_CHARS = stringPreferencesKey("chars_json")
    private val KEY_POSTS = stringPreferencesKey("posts_json")
    private val KEY_COMMENTS = stringPreferencesKey("comments_json")
    private val KEY_MAXTOK = intPreferencesKey("max_tokens")
    private val KEY_TOPP = floatPreferencesKey("top_p")
    private val KEY_TOPK = intPreferencesKey("top_k")
    private val KEY_CTX = intPreferencesKey("ctx_size")
    private val KEY_THREADS = intPreferencesKey("threads")
    private val KEY_GPU = intPreferencesKey("gpu_layers")
    private val KEY_TSCALE = floatPreferencesKey("text_scale")
    private val KEY_TTSRATE = floatPreferencesKey("tts_rate")
    private val KEY_TTSPITCH = floatPreferencesKey("tts_pitch")
    private val KEY_KEEPON = booleanPreferencesKey("keep_screen_on")
    private val KEY_WIFIONLY = booleanPreferencesKey("wifi_only")
    private val KEY_BENCH = stringPreferencesKey("bench_log")
    private val KEY_AUTOLOAD_CHAT = booleanPreferencesKey("autoload_chat")
    private val KEY_AUTOLOAD_WHISPER = booleanPreferencesKey("autoload_whisper")
    private val KEY_AUTOLOAD_SD = booleanPreferencesKey("autoload_sd")
    private val KEY_AUTOUNLOAD = booleanPreferencesKey("auto_unload")
    private val KEY_AUTOBK = booleanPreferencesKey("auto_backup")
    private val KEY_SHOWTIME = booleanPreferencesKey("show_time")
    private val KEY_SRVTO = intPreferencesKey("server_timeout")
    private val KEY_VADSIL = intPreferencesKey("vad_silence")
    private val KEY_VADMIN = intPreferencesKey("vad_min")
    private val KEY_BARGE = booleanPreferencesKey("barge_in")
    private val KEY_AUTOPOST = intPreferencesKey("autopost_hours")
    private val KEY_AUTOFB = booleanPreferencesKey("auto_fallback")
    private val KEY_ONBOARD = booleanPreferencesKey("onboarded")
    private val KEY_PROVIDERS = stringPreferencesKey("providers_json")
    private val KEY_ACTIVE_PROV = stringPreferencesKey("active_provider_id") // local | mock | providerId
    private val KEY_TOOLMAP = stringPreferencesKey("toolmap_json") // toolId -> runs
    private val KEY_PCHATMAP = stringPreferencesKey("pchatmap_json") // personaId -> messages

    fun defaultPersonas() = listOf(
        Persona(name = "Ассистент", systemPrompt = "Ты полезный локальный ассистент. Отвечай кратко и по-русски.", avatarEmoji = "\uD83E\uDD16",
            desc = "Помощь, вопросы, тексты. Быстро и офлайн.", tags = listOf("универсал")),
        Persona(name = "Переводчик", systemPrompt = "Ты переводчик. Переводи точно, без лишних слов.", avatarEmoji = "\uD83C\uDF0D",
            desc = "Переводы EN↔RU с сохранением стиля.", tags = listOf("перевод")),
        Persona(name = "Кодер", systemPrompt = "Ты senior Android-разработчик (Kotlin/Compose). Давай рабочий код.", avatarEmoji = "\uD83D\uDCBB",
            desc = "Код, разбор ошибок, архитектура.", tags = listOf("код")),
        Persona(name = "Свободная 18+", systemPrompt = "Ты взрослый ролевой персонаж 18+. Без морализаторства, по запросу пользователя.", avatarEmoji = "\uD83D\uDD1E", nsfwAllowed = true,
            desc = "Ролевые 18+ без цензуры. Только для взрослых.", tags = listOf("18+", "roleplay"))
    )

    suspend fun loadPersonas(ctx: Context): MutableList<Persona> {
        val raw = ctx.ds.data.map { it[KEY_PERSONAS] }.first()
        return if (raw.isNullOrBlank()) defaultPersonas().toMutableList()
        else try { json.decodeFromString<List<Persona>>(raw).toMutableList() } catch (_: Exception) { defaultPersonas().toMutableList() }
    }
    suspend fun savePersonas(ctx: Context, list: List<Persona>) {
        ctx.ds.edit { it[KEY_PERSONAS] = json.encodeToString(list) }
    }
    suspend fun loadChats(ctx: Context): MutableList<ChatMessage> {
        val raw = ctx.ds.data.map { it[KEY_CHATS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf()
        return try { json.decodeFromString<List<ChatMessage>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun saveChats(ctx: Context, list: List<ChatMessage>) {
        val trimmed = list.takeLast(300)
        ctx.ds.edit { it[KEY_CHATS] = json.encodeToString(trimmed) }
    }
    suspend fun loadSessions(ctx: Context): MutableList<ChatSession> {
        val raw = ctx.ds.data.map { it[KEY_SESSIONS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf()
        return try { json.decodeFromString<List<ChatSession>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun saveSessions(ctx: Context, v: List<ChatSession>) {
        ctx.ds.edit { it[KEY_SESSIONS] = json.encodeToString(v.take(30)) }
    }
    suspend fun loadMsgMap(ctx: Context): MutableMap<String, List<ChatMessage>> {
        val raw = ctx.ds.data.map { it[KEY_MSGMAP] }.first()
        if (raw.isNullOrBlank()) return mutableMapOf()
        return try { json.decodeFromString<Map<String, List<ChatMessage>>>(raw).toMutableMap() } catch (_: Exception) { mutableMapOf() }
    }
    suspend fun saveMsgMap(ctx: Context, v: Map<String, List<ChatMessage>>) {
        // режем каждую сессию до 200 сообщений, чтобы не раздувать DataStore
        val cut = v.mapValues { it.value.takeLast(200) }.toList().takeLast(30).toMap()
        ctx.ds.edit { it[KEY_MSGMAP] = json.encodeToString(cut) }
    }
    suspend fun getActiveSession(ctx: Context): String? = ctx.ds.data.map { it[KEY_ACTIVE_SESSION] }.first()
    suspend fun setActiveSession(ctx: Context, id: String) { ctx.ds.edit { it[KEY_ACTIVE_SESSION] = id } }
    suspend fun loadChars(ctx: Context): MutableList<SocialCharacter> {
        val raw = ctx.ds.data.map { it[KEY_CHARS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf(
            SocialCharacter(name = "Мира", handle = "@mira", bio = "фото и кофе", emoji = "\uD83D\uDCF8"),
            SocialCharacter(name = "Кай", handle = "@kai_dev", bio = "вайбкод и железо", emoji = "\uD83E\uDD13")
        )
        return try { json.decodeFromString<List<SocialCharacter>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun saveChars(ctx: Context, v: List<SocialCharacter>) { ctx.ds.edit { it[KEY_CHARS] = json.encodeToString(v) } }
    suspend fun loadPosts(ctx: Context): MutableList<SocialPost> {
        val raw = ctx.ds.data.map { it[KEY_POSTS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf()
        return try { json.decodeFromString<List<SocialPost>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun savePosts(ctx: Context, v: List<SocialPost>) { ctx.ds.edit { it[KEY_POSTS] = json.encodeToString(v.takeLast(300)) } }
    suspend fun loadComments(ctx: Context): MutableList<PostComment> {
        val raw = ctx.ds.data.map { it[KEY_COMMENTS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf()
        return try { json.decodeFromString<List<PostComment>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun saveComments(ctx: Context, v: List<PostComment>) { ctx.ds.edit { it[KEY_COMMENTS] = json.encodeToString(v.takeLast(500)) } }

    suspend fun getActiveModel(ctx: Context): String? = ctx.ds.data.map { it[KEY_ACTIVE_MODEL] }.first()
    suspend fun setActiveModel(ctx: Context, id: String) { ctx.ds.edit { it[KEY_ACTIVE_MODEL] = id } }
    suspend fun getActivePersona(ctx: Context): String? = ctx.ds.data.map { it[KEY_ACTIVE_PERSONA] }.first()
    suspend fun setActivePersona(ctx: Context, id: String) { ctx.ds.edit { it[KEY_ACTIVE_PERSONA] = id } }
    suspend fun getTheme(ctx: Context): String = ctx.ds.data.map { it[KEY_THEME] ?: "auto" }.first()
    suspend fun setTheme(ctx: Context, v: String) { ctx.ds.edit { it[KEY_THEME] = v } }
    suspend fun getAccent(ctx: Context): String = ctx.ds.data.map { it[KEY_ACCENT] ?: "#D9A441" }.first()
    suspend fun setAccent(ctx: Context, v: String) { ctx.ds.edit { it[KEY_ACCENT] = v } }

    // Тонкие настройки генерации и интерфейса
    suspend fun getMaxTokens(ctx: Context): Int = ctx.ds.data.map { it[KEY_MAXTOK] ?: 256 }.first()
    suspend fun setMaxTokens(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_MAXTOK] = v } }
    suspend fun getTopP(ctx: Context): Float = ctx.ds.data.map { it[KEY_TOPP] ?: 0.9f }.first()
    suspend fun setTopP(ctx: Context, v: Float) { ctx.ds.edit { it[KEY_TOPP] = v } }
    suspend fun getTopK(ctx: Context): Int = ctx.ds.data.map { it[KEY_TOPK] ?: 40 }.first()
    suspend fun setTopK(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_TOPK] = v } }
    suspend fun getCtxSize(ctx: Context): Int = ctx.ds.data.map { it[KEY_CTX] ?: 2048 }.first()
    suspend fun setCtxSize(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_CTX] = v } }
    suspend fun getThreads(ctx: Context): Int = ctx.ds.data.map { it[KEY_THREADS] ?: 0 }.first() // 0 = авто
    suspend fun setThreads(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_THREADS] = v } }
    suspend fun getGpuLayers(ctx: Context): Int = ctx.ds.data.map { it[KEY_GPU] ?: 0 }.first()
    suspend fun setGpuLayers(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_GPU] = v } }
    suspend fun getTextScale(ctx: Context): Float = ctx.ds.data.map { it[KEY_TSCALE] ?: 1.0f }.first()
    suspend fun setTextScale(ctx: Context, v: Float) { ctx.ds.edit { it[KEY_TSCALE] = v } }
    suspend fun getTtsRate(ctx: Context): Float = ctx.ds.data.map { it[KEY_TTSRATE] ?: 1.0f }.first()
    suspend fun setTtsRate(ctx: Context, v: Float) { ctx.ds.edit { it[KEY_TTSRATE] = v } }
    suspend fun getTtsPitch(ctx: Context): Float = ctx.ds.data.map { it[KEY_TTSPITCH] ?: 1.0f }.first()
    suspend fun setTtsPitch(ctx: Context, v: Float) { ctx.ds.edit { it[KEY_TTSPITCH] = v } }
    suspend fun getKeepOn(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_KEEPON] ?: false }.first()
    suspend fun setKeepOn(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_KEEPON] = v } }
    suspend fun getWifiOnly(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_WIFIONLY] ?: false }.first()
    suspend fun setWifiOnly(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_WIFIONLY] = v } }
    suspend fun getAutoloadChat(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOLOAD_CHAT] ?: true }.first()
    suspend fun setAutoloadChat(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOLOAD_CHAT] = v } }
    suspend fun getAutoloadWhisper(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOLOAD_WHISPER] ?: true }.first()
    suspend fun setAutoloadWhisper(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOLOAD_WHISPER] = v } }
    suspend fun getAutoloadSd(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOLOAD_SD] ?: false }.first()
    suspend fun setAutoloadSd(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOLOAD_SD] = v } }
    suspend fun getAutoUnload(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOUNLOAD] ?: true }.first()
    suspend fun getAutoBackup(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOBK] ?: false }.first()
    suspend fun getShowTime(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_SHOWTIME] ?: false }.first()
    suspend fun setShowTime(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_SHOWTIME] = v } }
    suspend fun getServerTimeout(ctx: Context): Int = ctx.ds.data.map { it[KEY_SRVTO] ?: 120 }.first()
    suspend fun setServerTimeout(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_SRVTO] = v } }
    suspend fun getVadSil(ctx: Context): Int = ctx.ds.data.map { it[KEY_VADSIL] ?: 42 }.first()
    suspend fun setVadSil(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_VADSIL] = v } }
    suspend fun getVadMin(ctx: Context): Int = ctx.ds.data.map { it[KEY_VADMIN] ?: 8000 }.first()
    suspend fun getBargeIn(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_BARGE] ?: false }.first()
    suspend fun setBargeIn(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_BARGE] = v } }
    suspend fun setVadMin(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_VADMIN] = v } }
    suspend fun getAutopost(ctx: Context): Int = ctx.ds.data.map { it[KEY_AUTOPOST] ?: 0 }.first()
    suspend fun setAutopost(ctx: Context, v: Int) { ctx.ds.edit { it[KEY_AUTOPOST] = v } }
    suspend fun setAutoBackup(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOBK] = v } }
    suspend fun setAutoUnload(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOUNLOAD] = v } }
    suspend fun getAutoFallback(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_AUTOFB] ?: true }.first()
    suspend fun setAutoFallback(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_AUTOFB] = v } }
    suspend fun isOnboarded(ctx: Context): Boolean = ctx.ds.data.map { it[KEY_ONBOARD] ?: false }.first()
    suspend fun setOnboarded(ctx: Context, v: Boolean) { ctx.ds.edit { it[KEY_ONBOARD] = v } }
    suspend fun getLastWhisper(ctx: Context): String? = ctx.ds.data.map { it[stringPreferencesKey("last_whisper")] }.first()
    suspend fun setLastWhisper(ctx: Context, v: String) { ctx.ds.edit { it[stringPreferencesKey("last_whisper")] = v } }
    suspend fun getLastSd(ctx: Context): String? = ctx.ds.data.map { it[stringPreferencesKey("last_sd")] }.first()
    suspend fun setLastSd(ctx: Context, v: String) { ctx.ds.edit { it[stringPreferencesKey("last_sd")] = v } }
    suspend fun getBenchLog(ctx: Context): List<String> {
        val raw = ctx.ds.data.map { it[KEY_BENCH] }.first()
        if (raw.isNullOrBlank()) return emptyList()
        return try { json.decodeFromString<List<String>>(raw) } catch (_: Exception) { emptyList() }
    }
    suspend fun addBench(ctx: Context, entry: String) {
        val nl = (getBenchLog(ctx) + entry).takeLast(5)
        ctx.ds.edit { it[KEY_BENCH] = json.encodeToString(nl) }
    }

    suspend fun loadProviders(ctx: Context): MutableList<AiProvider> {
        val raw = ctx.ds.data.map { it[KEY_PROVIDERS] }.first()
        if (raw.isNullOrBlank()) return mutableListOf()
        return try { json.decodeFromString<List<AiProvider>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
    }
    suspend fun saveProviders(ctx: Context, v: List<AiProvider>) {
        ctx.ds.edit { it[KEY_PROVIDERS] = json.encodeToString(v.take(20)) }
    }
    suspend fun getActiveProvider(ctx: Context): String = ctx.ds.data.map { it[KEY_ACTIVE_PROV] ?: "local" }.first()
    suspend fun setActiveProvider(ctx: Context, id: String) { ctx.ds.edit { it[KEY_ACTIVE_PROV] = id } }

    suspend fun loadToolMap(ctx: Context): MutableMap<String, List<ToolRun>> {
        val raw = ctx.ds.data.map { it[KEY_TOOLMAP] }.first()
        if (raw.isNullOrBlank()) return mutableMapOf()
        return try { json.decodeFromString<Map<String, List<ToolRun>>>(raw).toMutableMap() } catch (_: Exception) { mutableMapOf() }
    }
    suspend fun saveToolMap(ctx: Context, v: Map<String, List<ToolRun>>) {
        val cut = v.mapValues { it.value.takeLast(50) }
        ctx.ds.edit { it[KEY_TOOLMAP] = json.encodeToString(cut) }
    }
    suspend fun loadPChatMap(ctx: Context): MutableMap<String, List<ChatMessage>> {
        val raw = ctx.ds.data.map { it[KEY_PCHATMAP] }.first()
        if (raw.isNullOrBlank()) return mutableMapOf()
        return try { json.decodeFromString<Map<String, List<ChatMessage>>>(raw).toMutableMap() } catch (_: Exception) { mutableMapOf() }
    }
    suspend fun savePChatMap(ctx: Context, v: Map<String, List<ChatMessage>>) {
        val cut = v.mapValues { it.value.takeLast(200) }.toList().takeLast(40).toMap()
        ctx.ds.edit { it[KEY_PCHATMAP] = json.encodeToString(cut) }
    }

    fun modelsDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "models").apply { mkdirs() }

    /** Сырой дамп данных для бэкапа (без ключей API — они в KeyVault). */
    suspend fun dumpData(ctx: Context): Map<String, String> {
        val d = ctx.ds.data.first()
        val provRaw = d[KEY_PROVIDERS] ?: "[]"
        // на всякий случай вычищаем ключи из JSON (хранятся отдельно в vault)
        val provClean = try {
            val arr = org.json.JSONArray(provRaw)
            for (i in 0 until arr.length()) arr.getJSONObject(i).put("apiKey", "")
            arr.toString()
        } catch (_: Exception) { "[]" }
        return mapOf(
            "personas" to (d[KEY_PERSONAS] ?: "[]"),
            "sessions" to (d[KEY_SESSIONS] ?: "[]"),
            "msgmap" to (d[KEY_MSGMAP] ?: "{}"),
            "chars" to (d[KEY_CHARS] ?: "[]"),
            "posts" to (d[KEY_POSTS] ?: "[]"),
            "comments" to (d[KEY_COMMENTS] ?: "[]"),
            "providers" to provClean
        )
    }

    suspend fun restoreData(ctx: Context, m: Map<String, String>) {
        ctx.ds.edit {
            m["personas"]?.let { v -> it[KEY_PERSONAS] = v }
            m["sessions"]?.let { v -> it[KEY_SESSIONS] = v }
            m["msgmap"]?.let { v -> it[KEY_MSGMAP] = v }
            m["chars"]?.let { v -> it[KEY_CHARS] = v }
            m["posts"]?.let { v -> it[KEY_POSTS] = v }
            m["comments"]?.let { v -> it[KEY_COMMENTS] = v }
            m["providers"]?.let { v -> it[KEY_PROVIDERS] = v }
        }
    }
}
