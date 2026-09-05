package com.neuropocket.app

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neuropocket.app.data.*
import com.neuropocket.app.engine.LlamaEngine
import com.neuropocket.app.engine.LlamaNative
import com.neuropocket.app.engine.MockEngine
import com.neuropocket.app.engine.RemoteEngine
import com.neuropocket.app.engine.FallbackEngine
import com.neuropocket.app.engine.WhisperNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var messages by mutableStateOf(listOf<ChatMessage>()); private set
    var sessions by mutableStateOf(listOf<ChatSession>()); private set
    var activeSessionId by mutableStateOf<String?>(null); private set
    var toolRuns by mutableStateOf(mapOf<String, List<ToolRun>>()); private set
    var toolBusyId by mutableStateOf<String?>(null); private set
    var pchats by mutableStateOf(mapOf<String, List<ChatMessage>>()); private set
    var pBusy by mutableStateOf(false); private set
    var personas by mutableStateOf(listOf<Persona>()); private set
    var activePersona by mutableStateOf<Persona?>(null); private set
    var activeModelId by mutableStateOf<String?>(null); private set
    var posts by mutableStateOf(listOf<SocialPost>()); private set
    var comments by mutableStateOf(listOf<PostComment>()); private set
    var commentsOpen by mutableStateOf<String?>(null); private set
    fun toggleComments(postId: String) { commentsOpen = if (commentsOpen == postId) null else postId }
    var theme by mutableStateOf("auto"); private set
    var accent by mutableStateOf("#D9A441"); private set
    var maxTokens by mutableStateOf(256); private set
    var topP by mutableStateOf(0.9f); private set
    var topK by mutableStateOf(40); private set
    var ctxSize by mutableStateOf(2048); private set
    var threads by mutableStateOf(0); private set // 0 = авто
    var gpuLayers by mutableStateOf(0); private set // 0 = CPU, 999 = все слои на GPU
    var textScale by mutableStateOf(1.0f); private set
    var ttsRate by mutableStateOf(1.0f); private set
    var ttsPitch by mutableStateOf(1.0f); private set
    var keepScreenOn by mutableStateOf(false); private set
    var wifiOnly by mutableStateOf(false); private set
    var autoloadChat by mutableStateOf(true); private set
    var autoloadWhisper by mutableStateOf(true); private set
    var autoloadSd by mutableStateOf(false); private set
    var autoUnload by mutableStateOf(true); private set
    var autoBackup by mutableStateOf(false); private set
    var autopostHours by mutableStateOf(0); private set
    var showTime by mutableStateOf(false); private set
    var serverTimeout by mutableStateOf(120); private set
    var vadSil by mutableStateOf(42); private set
    var bargeIn by mutableStateOf(false); private set
    private var vadInst: com.k2fsa.sherpa.onnx.Vad? = null
    var vadMin by mutableStateOf(8000); private set
    var autoFallback by mutableStateOf(true); private set
    var onboarded by mutableStateOf(true); private set
    var benchHistory by mutableStateOf(listOf<String>()); private set

    fun threadsEffective(): Int {
        if (threads in 1..8) return threads
        // оставляем 2 ядра интерфейсу, иначе UI голодает и приложение "виснет"
        return (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
    }
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Готов. Офлайн."); private set
    var modelFiles by mutableStateOf(listOf<File>()); private set
    var whisperFiles by mutableStateOf(listOf<File>()); private set
    var wavFiles by mutableStateOf(listOf<File>()); private set

    private val mock = MockEngine()
    private val llama = LlamaEngine()
    var useNative by mutableStateOf(true); private set // legacy-переключатель local/mock
    var activeProviderId by mutableStateOf("local"); private set // local | mock | providerId
    var providers by mutableStateOf(listOf<AiProvider>()); private set
    var provModels by mutableStateOf(mapOf<String, List<String>>()); private set
    var provStatus by mutableStateOf(mapOf<String, String>()); private set
    var provBusy by mutableStateOf(false); private set
    var nativeLoaded by mutableStateOf(false); private set
    var nativeInfo by mutableStateOf("native: нет"); private set
    var whisperLoaded by mutableStateOf(false); private set
    var whisperInfo by mutableStateOf("whisper: нет"); private set
    var whisperResult by mutableStateOf(""); private set
    data class TimedLine(val from: Long, val to: Long, val text: String)
    var whisperTimed by mutableStateOf(listOf<TimedLine>()); private set
    var showTimed by mutableStateOf(false); private set
    fun toggleTimed() { showTimed = !showTimed }
    var agentSteps by mutableStateOf(listOf<com.neuropocket.app.engine.AgentStep>()); private set
    var agentRunning by mutableStateOf(false); private set
    var agentResult by mutableStateOf(""); private set
    private var agentCancel = false
    var sdLoaded by mutableStateOf(false); private set
    var sdInfo by mutableStateOf("sd: нет"); private set
    var sdBusy by mutableStateOf(false); private set
    var sdFiles by mutableStateOf(listOf<File>()); private set
    var gallery by mutableStateOf(listOf<File>()); private set
    var benchResult by mutableStateOf(""); private set
    var benchRunning by mutableStateOf(false); private set
    var visionLoaded by mutableStateOf(false); private set
    var visionInfo by mutableStateOf("зрение: нет"); private set
    var visionResult by mutableStateOf(""); private set
    var visionBusy by mutableStateOf(false); private set
    var mmprojFiles by mutableStateOf(listOf<File>()); private set
    var taesdFiles by mutableStateOf(listOf<File>()); private set
    var voiceDirs by mutableStateOf(listOf<String>()); private set
    var activeVoice by mutableStateOf<String?>(null); private set
    var ttsInfo by mutableStateOf("голос: нет"); private set
    var speaking by mutableStateOf(false); private set
    var hfRunning by mutableStateOf(false); private set
    var hfStatus by mutableStateOf(""); private set
    var sttLang by mutableStateOf("ru"); private set
    fun applySttLang(v: String) {
        val n = com.neuropocket.app.core.SttLang.normalize(v)
        sttLang = n
        viewModelScope.launch(Dispatchers.IO) { Store.setSttLang(ctx, n) }
    }
    var hfLog by mutableStateOf(listOf<String>()); private set
    private fun hfSay(s: String) {
        hfLog = (hfLog + s).takeLast(6)
    }
    var noteFiles by mutableStateOf(listOf<String>()); private set
    var ragBusy by mutableStateOf(false); private set
    var ragResult by mutableStateOf(""); private set
    var embedLoaded by mutableStateOf(false); private set
    var embedInfo by mutableStateOf("вектора: нет"); private set
    var ragIndexed by mutableStateOf(0); private set
    private var sherpa: com.neuropocket.app.voice.SherpaTts? = null
    private var player = com.neuropocket.app.voice.TtsPlayer()
    private var speakStop = false

    fun engineLabel(): String = when {
        activeProviderId == "mock" -> "Mock-Local v1"
        activeProviderId == "local" && llama.loaded() -> "llama.cpp native"
        activeProviderId == "local" -> "llama.cpp (модель не в RAM)"
        else -> providers.find { it.id == activeProviderId }
            ?.let { "${it.name} • ${it.model.ifBlank { "?" }}" } ?: "Mock-Local v1"
    }

    /** Движок для чата/агента/ленты по текущему выбору. */
    fun chatEngine(): com.neuropocket.app.engine.AiEngine = resolveEngine(activeProviderId)

    /** Движок с учётом привязки персоны ("" = как везде). */
    fun chatEngineFor(p: Persona?): com.neuropocket.app.engine.AiEngine {
        val id = p?.engine?.ifBlank { null } ?: activeProviderId
        return resolveEngine(id)
    }

    private fun resolveEngine(id: String): com.neuropocket.app.engine.AiEngine {
        if (id == "mock") return mock
        if (id == "local") {
            return if (useNative && llama.loaded()) llama else mock
        }
        val pr = providers.find { it.id == id && it.enabled } ?: return mock
        return com.neuropocket.app.engine.RemoteEngine(pr, maxTokens, topP, serverTimeout)
    }

    /**
     * Генерация с автозапасным движком: если primary вернул ошибку "[...]",
     * один раз пробуем локальную модель, затем Mock. Возвращает текст.
     */
    suspend fun genWithFallback(
        primary: com.neuropocket.app.engine.AiEngine,
        persona: Persona,
        history: List<ChatMessage>,
        text: String,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val r = primary.generate(history, persona, text, onToken)
        if (!autoFallback || !r.startsWith("[")) return@withContext r
        val fb = fallbackFor(primary) ?: return@withContext r
        withContext(Dispatchers.Main) { status = "Основной движок недоступен — отвечаю запасным." }
        fb.generate(history, persona, text, onToken)
    }

    private fun fallbackFor(primary: com.neuropocket.app.engine.AiEngine): com.neuropocket.app.engine.AiEngine? = when {
        primary === mock -> null
        primary is LlamaEngine -> mock
        primary is RemoteEngine -> if (llama.loaded()) llama else mock
        primary is FallbackEngine -> null
        else -> mock
    }

    /** Обёртка для нескримминговых вызовов. */
    private fun withFallbackEngine(primary: com.neuropocket.app.engine.AiEngine): com.neuropocket.app.engine.AiEngine {
        if (!autoFallback) return primary
        val fb = fallbackFor(primary) ?: return primary
        return FallbackEngine(primary, fb) {
            viewModelScope.launch(Dispatchers.Main) { status = "Основной движок недоступен — отвечаю запасным." }
        }
    }

    fun engineNotice(): String? = when {
        activeProviderId == "local" && !llama.loaded() && useNative ->
            "Native нет модели в RAM — отвечаю через Mock. Загрузи GGUF ниже."
        activeProviderId != "local" && activeProviderId != "mock" &&
            providers.none { it.id == activeProviderId && it.enabled } ->
            "Провайдер недоступен — отвечаю через Mock."
        else -> null
    }

    init { viewModelScope.launch { reload() } }

    suspend fun reload() = withContext(Dispatchers.IO) {
        var p = Store.loadPersonas(ctx)
        val c = Store.loadChars(ctx)
        var ps = Store.loadPosts(ctx)
        // миграция героев ленты в персоны (единая сущность)
        if (c.isNotEmpty()) {
            val pm = p.toMutableList()
            val posts = ps.toMutableList()
            for (ch in c) {
                var per = pm.find { it.name == ch.name }
                if (per == null) {
                    per = Persona(
                        name = ch.name,
                        systemPrompt = "Ты ${ch.name} (${ch.bio}). Отвечай живо и кратко, по-русски.",
                        avatarEmoji = ch.emoji.ifBlank { "\uD83D\uDE00" },
                        desc = ch.bio, tags = listOf("лента")
                    )
                    pm.add(per)
                }
                for (i in posts.indices) {
                    if (posts[i].authorId == ch.id) posts[i] = posts[i].copy(authorId = per.id)
                }
            }
            Store.savePersonas(ctx, pm)
            Store.savePosts(ctx, posts)
            Store.saveChars(ctx, emptyList())
            p = pm
            ps = posts
        }
        val toolMap = Store.loadToolMap(ctx)
        val pchatMap = Store.loadPChatMap(ctx)
        val am = Store.getActiveModel(ctx)
        val ap = Store.getActivePersona(ctx)
        val th = Store.getTheme(ctx)
        val ac = Store.getAccent(ctx)
        var ses = Store.loadSessions(ctx)
        var mmap = Store.loadMsgMap(ctx)
        // миграция со старого плоского чата v1
        if (ses.isEmpty()) {
            val old = Store.loadChats(ctx)
            if (old.isNotEmpty()) {
                val s = ChatSession(title = "Мой чат")
                ses = mutableListOf(s)
                mmap = mutableMapOf(s.id to old)
                Store.saveSessions(ctx, ses)
                Store.saveMsgMap(ctx, mmap)
            }
        }
        if (ses.isEmpty()) {
            val s = ChatSession(title = "Новый чат")
            ses = mutableListOf(s)
            Store.saveSessions(ctx, ses)
        }
        var asid = Store.getActiveSession(ctx)
        if (ses.none { it.id == asid }) asid = ses.maxByOrNull { it.updated }?.id ?: ses.first().id
        val cur = mmap[asid] ?: emptyList()
        withContext(Dispatchers.Main) {
            personas = p
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
            activeSessionId = asid
            messages = cur
            posts = ps.sortedByDescending { it.ts }
            comments = Store.loadComments(ctx).sortedBy { it.ts }
            comments = Store.loadComments(ctx).sortedBy { it.ts }
            toolRuns = toolMap
            pchats = pchatMap
            activeModelId = am
            activePersona = p.find { it.id == ap } ?: p.firstOrNull()
            theme = th; accent = ac
            maxTokens = Store.getMaxTokens(ctx); topP = Store.getTopP(ctx); topK = Store.getTopK(ctx)
            ctxSize = Store.getCtxSize(ctx); threads = Store.getThreads(ctx); gpuLayers = Store.getGpuLayers(ctx)
            textScale = Store.getTextScale(ctx); ttsRate = Store.getTtsRate(ctx); ttsPitch = Store.getTtsPitch(ctx)
            keepScreenOn = Store.getKeepOn(ctx); wifiOnly = Store.getWifiOnly(ctx)
            autoloadChat = Store.getAutoloadChat(ctx); autoloadWhisper = Store.getAutoloadWhisper(ctx)
            autoloadSd = Store.getAutoloadSd(ctx); autoUnload = Store.getAutoUnload(ctx)
            autoBackup = Store.getAutoBackup(ctx)
            showTime = Store.getShowTime(ctx); serverTimeout = Store.getServerTimeout(ctx)
            autopostHours = Store.getAutopost(ctx)
            vadSil = Store.getVadSil(ctx); vadMin = Store.getVadMin(ctx)
            bargeIn = Store.getBargeIn(ctx)
            applyAutopostWork(autopostHours)
            applyAutoBackupWork(autoBackup)
            autoFallback = Store.getAutoFallback(ctx); onboarded = Store.isOnboarded(ctx)
            lastWhisperName = Store.getLastWhisper(ctx); lastSdName = Store.getLastSd(ctx)
            benchHistory = Store.getBenchLog(ctx)
            providers = Store.loadProviders(ctx)
            // ключи — из vault; заодно мигрируем старые inline-ключи
            providers = providers.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: it.apiKey) }
            persistProvidersIO(providers)
            providers = providers.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: "") }
            activeProviderId = Store.getActiveProvider(ctx)
            sttLang = Store.getSttLang(ctx)
            loadAppVersion()
            refreshModelFiles()
            refreshNativeState()
            refreshWhisperState()
            refreshSdState()
            refreshVisionState()
            refreshEmbedState()
            // P0.2: lifecycle на чистом старте — однозначно MISSING/READY, не "".
            try { refreshVoiceEngineState() } catch (_: Exception) { voiceEngineState = "missing" }
        }
    }

    fun currentSession(): ChatSession? = sessions.find { it.id == activeSessionId }

    private suspend fun persistChatIO(ses: List<ChatSession>, map: Map<String, List<ChatMessage>>) {
        Store.saveSessions(ctx, ses)
        Store.saveMsgMap(ctx, map)
    }

    private fun deviceBusy(): Boolean = busy || agentRunning || sdBusy || visionBusy || pBusy || hfRunning || benchRunning || ragBusy || toolBusyId != null

    fun newChat() {
        if (deviceBusy()) { status = "Дождись конца генерации."; return }
        viewModelScope.launch(Dispatchers.IO) {
        val ses = Store.loadSessions(ctx).toMutableList()
        val mmap = Store.loadMsgMap(ctx).toMutableMap()
        val s = ChatSession(title = "Новый чат", personaId = activePersona?.id)
        ses.add(0, s)
        mmap[s.id] = emptyList()
        Store.setActiveSession(ctx, s.id)
        persistChatIO(ses, mmap)
        withContext(Dispatchers.Main) {
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
            activeSessionId = s.id
            messages = emptyList()
        }
    } }

    fun openSession(id: String) {
        if (id != activeSessionId && deviceBusy()) { status = "Дождись конца генерации."; return }
        viewModelScope.launch(Dispatchers.IO) {
        val mmap = Store.loadMsgMap(ctx)
        Store.setActiveSession(ctx, id)
        val cur = mmap[id] ?: emptyList()
        withContext(Dispatchers.Main) { activeSessionId = id; messages = cur }
    } }

    fun folders(): List<String> =
        sessions.mapNotNull { it.folder.ifBlank { null } }.distinct().sorted()

    fun moveSession(id: String, folder: String) { viewModelScope.launch(Dispatchers.IO) {
        val ses = Store.loadSessions(ctx).map {
            if (it.id == id) it.copy(folder = folder.trim().take(24)) else it
        }
        Store.saveSessions(ctx, ses)
        withContext(Dispatchers.Main) {
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
        }
    } }

    fun togglePin(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val ses = Store.loadSessions(ctx).map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        Store.saveSessions(ctx, ses)
        withContext(Dispatchers.Main) {
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
        }
    } }

    fun deleteSession(id: String) {
        if (deviceBusy()) { status = "Дождись конца генерации."; return }
        viewModelScope.launch(Dispatchers.IO) {
        var ses = Store.loadSessions(ctx).toMutableList()
        val mmap = Store.loadMsgMap(ctx).toMutableMap()
        ses.removeAll { it.id == id }
        mmap.remove(id)
        if (ses.isEmpty()) {
            val s = ChatSession(title = "Новый чат")
            ses.add(s)
            mmap[s.id] = emptyList()
        }
        var asid = if (ses.any { it.id == activeSessionId }) activeSessionId else ses.maxByOrNull { it.updated }!!.id
        Store.setActiveSession(ctx, asid!!)
        persistChatIO(ses, mmap)
        val cur = mmap[asid] ?: emptyList()
        withContext(Dispatchers.Main) {
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
            activeSessionId = asid
            messages = cur
        }
    } }

    fun voicesDir(): File = File(ctx.getExternalFilesDir(null), "models/voices").apply { mkdirs() }
    fun voiceEngineDir(): File = File(ctx.getExternalFilesDir(null), "voice_engine").apply { mkdirs() }
    // P0.2: однозначное начальное состояние, refresh при reload (см. reload()).
    var voiceEngineState by mutableStateOf("missing"); private set
    var voiceEngineUrl by mutableStateOf<String?>(null); private set
    var voiceEngineBusy by mutableStateOf(false); private set

    /** Правда ли, что sherpa/onnxruntime подгружены (встроены или файлом). */
    fun ensureVoiceEngine(): Boolean {
        // 1. встроенный в APK (старые сборки)
        try {
            System.loadLibrary("sherpa-onnx-jni")
            return true
        } catch (_: Throwable) { }
        // 2. скачанный файл
        return tryLoadVoiceEngineFiles()
    }

    private fun tryLoadVoiceEngineFiles(): Boolean {
        val dir = voiceEngineDir()
        val ort = java.io.File(dir, "libonnxruntime.so")
        val jni = java.io.File(dir, "libsherpa-onnx-jni.so")
        if (!ort.exists() || !jni.exists()) return false
        return try {
            System.load(ort.absolutePath)
            System.load(jni.absolutePath)
            true
        } catch (_: Throwable) { false }
    }

    fun refreshVoiceEngineState() {
        voiceEngineState = when {
            ensureVoiceEngine() -> "ok"
            voiceEngineFileReady() -> "file"
            else -> "missing"
        }
    }

    private fun voiceEngineFileReady(): Boolean {
        val dir = voiceEngineDir()
        return java.io.File(dir, "libonnxruntime.so").exists() &&
            java.io.File(dir, "libsherpa-onnx-jni.so").exists()
    }

    suspend fun fetchAssetUrl(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/zezarv/NeuroPocket/releases/latest")
                .header("Accept", "application/vnd.github+json").get().build()
            com.neuropocket.app.engine.NetHttp.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val arr = org.json.JSONObject(resp.body?.string() ?: "").optJSONArray("assets")
                    ?: return@withContext null
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name") == name) {
                        return@withContext o.optString("browser_download_url").ifBlank { null }
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    fun resolveVoiceEngineUrl() {
        if (voiceEngineBusy) return
        voiceEngineBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val u = fetchAssetUrl("voice-engine-arm64.zip")
            withContext(Dispatchers.Main) {
                voiceEngineUrl = u
                voiceEngineBusy = false
                if (u == null) status = "Движок не найден в релизах."
            }
        }
    }

    fun downloadVoiceEngine(url: String) {
        if (url.isBlank()) return
        try {
            downloads.values.find { it.fileName == "voice-engine-arm64.zip" && !it.done }?.let { return }
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("voice-engine-arm64.zip")
                setDescription("NeuroPocket: голосовой движок")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(ctx, "models", "voice-engine-arm64.zip")
                setAllowedOverMetered(!wifiOnly); setAllowedOverRoaming(false)
            }
            val id = dm.enqueue(req)
            downloads = downloads + (id to DlInfo("voice-engine-arm64.zip", "В очереди…", 0f, false, false))
            status = "Качаю голосовой движок…"
            startDlPoll()
        } catch (e: Exception) { status = "Ошибка загрузки: ${e.message}" }
    }

    fun extractVoiceEngine() { viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            voiceEngineState = "installing"
            status = "Распаковываю движок…"
        }
        // P0.7: whitelist + zip-slip + atomic temp dir + cleanup. System.load только после проверки.
        val allowed = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so")
        var tmpDir: java.io.File? = null
        try {
            val zip = java.io.File(Store.modelsDir(ctx), "voice-engine-arm64.zip")
            require(zip.exists() && zip.length() > 1024 * 1024) { "zip не найден или слишком мал" }
            val dir = voiceEngineDir()
            tmpDir = java.io.File(dir.parent, "voice_engine.tmp-${System.currentTimeMillis()}")
            tmpDir.mkdirs()
            org.apache.commons.compress.archivers.zip.ZipFile(zip).use { zf ->
                val it = zf.entries
                while (it.hasMoreElements()) {
                    val e = it.nextElement()
                    if (e.isDirectory) continue
                    // zip-slip: берём только basename из whitelist, отбрасываем пути
                    val name = java.io.File(e.name).name
                    if (name !in allowed) continue
                    if (e.size > 60 * 1024 * 1024) throw Exception("подозрительно большой $name")
                    val out = java.io.File(tmpDir, name)
                    // canonical check: out обязан лежать внутри tmpDir
                    require(out.canonicalPath.startsWith(tmpDir.canonicalPath + java.io.File.separator)) { "zip-slip" }
                    zf.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
                    require(out.length() > 100 * 1024) { "$name слишком мал, архив бит" }
                }
            }
            for (name in allowed) {
                val f = java.io.File(tmpDir, name)
                require(f.exists() && f.length() > 100 * 1024) { "в архиве нет $name" }
            }
            // atomic install: tmp -> final
            for (name in allowed) {
                val src = java.io.File(tmpDir, name)
                val dst = java.io.File(dir, name)
                if (dst.exists()) dst.delete()
                require(src.renameTo(dst)) {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                    dst.exists()
                }
            }
            try { tmpDir.deleteRecursively() } catch (_: Exception) { }
            tmpDir = null
            val ok = voiceEngineFileReady()
            withContext(Dispatchers.Main) {
                refreshVoiceEngineState()
                status = if (ok) "Голосовой движок готов." else "Не вышло распаковать."
                if (!ok) voiceEngineState = "error"
            }
        } catch (e: Exception) {
            try { tmpDir?.deleteRecursively() } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                voiceEngineState = "error"
                status = "Ошибка распаковки: ${e.message?.take(120)}"
            }
        }
    } }
    fun notesDir(): File = File(ctx.getExternalFilesDir(null), "notes").apply { mkdirs() }
    private fun ragIndexFile(): File = File(notesDir(), ".index.json")

    fun refreshModelFiles() {
        val all = Store.modelsDir(ctx).listFiles()?.toList() ?: emptyList()
        modelFiles = all.filter { it.extension.lowercase() == "gguf" }.sortedByDescending { it.lastModified() }
        whisperFiles = all.filter { it.extension.lowercase() == "bin" }.sortedByDescending { it.lastModified() }
        wavFiles = all.filter { it.extension.lowercase() in listOf("wav", "mp3", "m4a", "aac", "ogg", "opus", "flac") }.sortedByDescending { it.lastModified() }
        sdFiles = all.filter { it.extension.lowercase() in listOf("safetensors", "ckpt") }.sortedByDescending { it.lastModified() }
        mmprojFiles = all.filter { it.extension.lowercase() == "gguf" && "mmproj" in it.name.lowercase() }.sortedByDescending { it.lastModified() }
        taesdFiles = all.filter { "taesd" in it.name.lowercase() }.sortedByDescending { it.lastModified() }
        voiceDirs = voicesDir().listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
        noteFiles = notesDir().listFiles { f -> f.extension.lowercase() in listOf("md", "txt") }
            ?.map { it.name }?.sorted() ?: emptyList()
        if (activeVoice != null && activeVoice !in voiceDirs) {
            activeVoice = null; try { sherpa?.release() } catch (_: Exception) {}; sherpa = null
            ttsInfo = "голос: нет"
        }
        refreshGallery()
    }

    /** Распаковка скачанных голосов .tar.bz2 → voices/<имя>/. */
    fun extractVoices() { viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) { status = "Проверяю голоса…" }
        var n = 0
        try {
            val vd = voicesDir()
            // tar.bz2 могут лежать и в models/, и в models/voices/
            val arch1 = Store.modelsDir(ctx).listFiles { f -> f.extension.lowercase() == "bz2" }?.toList() ?: emptyList()
            val arch2 = vd.listFiles { f -> f.extension.lowercase() == "bz2" }?.toList() ?: emptyList()
            for (a in arch1 + arch2) {
                try {
                    val target = File(vd, a.nameWithoutExtension.substringBeforeLast('.'))
                    if (target.exists() && (target.listFiles()?.isNotEmpty() == true)) continue
                    target.mkdirs()
                    java.io.FileInputStream(a).use { fis ->
                        org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(fis).use { bz ->
                            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bz).use { tar ->
                                var e = tar.nextEntry
                                while (e != null) {
                                    if (!e.isDirectory) {
                                        val parts = e.name.trim('/').split('/')
                                        // espeak-ng-data кладём папкой целиком, остальное — плоскими именами
                                        val f2 = if (parts.contains("espeak-ng-data")) {
                                            val tail = parts.drop(parts.indexOf("espeak-ng-data") + 1)
                                            if (tail.isEmpty()) { e = tar.nextEntry; continue }
                                            File(File(target, "espeak-ng-data"), tail.joinToString(File.separator))
                                        } else {
                                            File(target, File(e.name).name)
                                        }
                                        f2.parentFile?.mkdirs()
                                        f2.outputStream().use { tar.copyTo(it) }
                                    }
                                    e = tar.nextEntry
                                }
                            }
                        }
                    }
                    // espeak-ng-data иногда вложен глубже — ищем
                    val hasData = target.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
                    if (hasData) n++ else { target.deleteRecursively() }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        refreshModelFiles()
        withContext(Dispatchers.Main) { status = if (n > 0) "Распаковано голосов: $n" else "Новых голосов нет" }
    } }

    fun loadVoice(dirName: String) { viewModelScope.launch(Dispatchers.IO) {
        if (!ensureVoiceEngine()) {
            withContext(Dispatchers.Main) { status = "Нет голосового движка: скачай его ниже." }
            return@launch
        }
        withContext(Dispatchers.Main) { status = "Загружаю голос $dirName…" }
        loadVoiceSync(dirName)
        withContext(Dispatchers.Main) {
            status = if (sherpa != null) "Голос $dirName готов." else "Голос не встал. Проверь папку."
        }
    } }

    fun stopSpeak() {
        speakStop = true
        try { player.stop() } catch (_: Exception) {}
        viewModelScope.launch(Dispatchers.Main) { speaking = false }
    }

    /** Озвучка с учётом голоса персоны (переключает голос при нужде). true = озвучено sherpa. */
    suspend fun speakOut(text: String, personaVoice: String = ""): Boolean = withContext(Dispatchers.IO) {
        val want = personaVoice.ifBlank { activeVoice ?: "" }
        if (want.isNotEmpty() && want != activeVoice && want in voiceDirs) {
            withContext(Dispatchers.Main) { status = "Переключаю голос…" }
            loadVoiceSync(want)
        }
        if (sherpa == null) return@withContext false
        speakSherpa(text)
    }

    private suspend fun loadVoiceSync(dirName: String) = withContext(Dispatchers.IO) {
        try { sherpa?.release() } catch (_: Exception) {}
        sherpa = null
        val dir = File(voicesDir(), dirName)
        val found = com.neuropocket.app.voice.SherpaTts.findVoice(dir)
            ?: dir.walkTopDown().firstOrNull { it.isDirectory && com.neuropocket.app.voice.SherpaTts.findVoice(it) != null }
                ?.let { com.neuropocket.app.voice.SherpaTts.findVoice(it) }
        withContext(Dispatchers.Main) {
            if (found != null) {
                try {
                    sherpa = com.neuropocket.app.voice.SherpaTts(found.first, found.second, found.third, threadsEffective())
                    activeVoice = dirName
                    ttsInfo = "голос: $dirName"
                } catch (_: Throwable) { ttsInfo = "голос: ошибка" }
            } else {
                ttsInfo = "голос: нет"
            }
        }
    }

    fun vadFile(): File = File(Store.modelsDir(ctx), "silero_vad.onnx")

    var rtSelected by mutableStateOf(setOf<String>()); private set
    var rtRunning by mutableStateOf(false); private set
    var rtTurns by mutableStateOf(listOf<com.neuropocket.app.engine.RoundTurn>()); private set
    var rtCancel = false

    fun rtToggle(id: String) {
        rtSelected = if (id in rtSelected) rtSelected - id else rtSelected + id
    }

    fun rtClear() { rtTurns = emptyList() }

    fun stopRoundTable() {
        rtCancel = true
        stopGen()
    }

    fun startRoundTable(topic: String, rounds: Int, append: Boolean = false) {
        val parts = personas.filter { it.id in rtSelected }.take(4)
        if (topic.isBlank() || parts.size < 2 || rtRunning || deviceBusy() || pBusy) return
        // P0.1: новый стол чистит, "Ещё круг" сохраняет предыдущие turns.
        val seedTurns = com.neuropocket.app.core.RoundTableLogic.initialTurns(rtTurns, append)
        if (!append) rtTurns = emptyList()
        stopSpeak()
        rtRunning = true
        rtCancel = false
        status = "Круглый стол идёт…"
        val seedAcc = com.neuropocket.app.core.RoundTableLogic.buildSeedContext(seedTurns)
        viewModelScope.launch(Dispatchers.IO) {
            llama.maxTokens = 160; llama.topP = topP; llama.topK = topK
            val acc = StringBuilder(seedAcc)
            outer@ for (r in 1..rounds.coerceIn(1, 5)) {
                for (per in parts) {
                    if (rtCancel) break@outer
                    val prompt = "Круглый стол на тему: ${topic.take(400)}\n" +
                        "Участники: ${parts.joinToString { it.name }}.\n" +
                        (if (acc.isEmpty()) "Ты начинаешь обсуждение." else "Ход дискуссии:\n${acc.toString().take(2000)}\n") +
                        "Ответь как ${per.name} (${per.desc.ifBlank { per.systemPrompt.take(150) }}): " +
                        "коротко (2–4 предложения), реагируй на сказанное, не повторяйся."
                    val eng = withFallbackEngine(chatEngineFor(per))
                    val out = try {
                        eng.generate(emptyList(), per, prompt)
                    } catch (e: Exception) { "[ошибка: ${e.message?.take(100)}]" }
                    if (rtCancel) break@outer
                    val turn = com.neuropocket.app.engine.RoundTurn(per.id, per.name, out.take(1200))
                    acc.append("\n${per.name}: ${turn.text}\n")
                    withContext(Dispatchers.Main) { rtTurns = rtTurns + turn }
                }
            }
            withContext(Dispatchers.Main) {
                rtRunning = false
                status = if (rtCancel) "Круглый стол остановлен." else "Круглый стол готов."
            }
        }
    }

    /** Сохранить стенограмму в общий чат. */
    fun saveRoundTable(topic: String) {
        if (rtTurns.isEmpty() || deviceBusy() || pBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            val ses = Store.loadSessions(ctx).toMutableList()
            val mmap = Store.loadMsgMap(ctx).toMutableMap()
            val s = ChatSession(title = "Стол: ${topic.take(28)}")
            ses.add(0, s)
            val um = ChatMessage(role = "user", text = "Круглый стол на тему «${topic.take(300)}». Участники: ${rtTurns.map { it.name }.distinct().joinToString()}.")
            val turns = rtTurns.map { t ->
                ChatMessage(role = "assistant", text = "${t.name}: ${t.text}", personaId = t.personaId)
            }
            mmap[s.id] = (listOf(um) + turns).takeLast(200)
            Store.setActiveSession(ctx, s.id)
            persistChatIO(ses, mmap)
            withContext(Dispatchers.Main) {
                sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
                activeSessionId = s.id
                messages = mmap[s.id] ?: emptyList()
                status = "Стенограмма в чатах."
            }
        }
    }

    fun stopHandsFree() {
        hfRunning = false
        stopSpeak()
        try { LlamaNative.cancel() } catch (_: Exception) {}
    }

    // ---------- RAG по заметкам ----------
    data class RagChunk(val file: String, val text: String, val vec: List<Float>)

    private fun ragCount(): Int = try {
        val f = ragIndexFile()
        if (!f.exists()) 0
        else org.json.JSONObject(f.readText()).length()
    } catch (_: Exception) { 0 }

    fun saveNote(name: String, text: String) { viewModelScope.launch(Dispatchers.IO) {
        var n = name.trim().ifBlank { "заметка" }.replace(Regex("[^A-Za-z0-9а-яА-ЯёЁ _\\-]"), "_").take(60)
        if (!n.endsWith(".md", true)) n += ".md"
        try { File(notesDir(), n).writeText(text) } catch (_: Exception) { }
        refreshModelFiles()
        withContext(Dispatchers.Main) { status = "Заметка сохранена." }
    } }

    fun deleteNote(name: String) { viewModelScope.launch(Dispatchers.IO) {
        try { File(notesDir(), name).delete() } catch (_: Exception) { }
        refreshModelFiles()
    } }

    fun readNote(name: String): String = try { File(notesDir(), name).readText().take(20000) } catch (_: Exception) { "" }

    fun refreshEmbedState() {
        embedLoaded = try { LlamaNative.available && LlamaNative.isEmbedLoaded() } catch (_: Exception) { false }
        embedInfo = when {
            !LlamaNative.available -> "вектора: .so нет"
            embedLoaded -> "вектора: модель в RAM"
            else -> "вектора: загрузи E5-small"
        }
        ragIndexed = ragCount()
    }

    fun loadEmbedToRam(f: File) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { status = "Загружаю вектора ${f.name}…" }
            val rc = try {
                if (!LlamaNative.available) -99 else LlamaNative.loadEmbed(f.absolutePath, threadsEffective())
            } catch (e: Exception) { -98 }
            if (rc == 0) loadedEmbedPath = f.absolutePath
            withContext(Dispatchers.Main) {
                refreshEmbedState()
                status = if (rc == 0) "Вектора в RAM." else "Ошибка векторов: $rc"
            }
        }
    }

    private fun chunkText(file: String, text: String): List<Pair<String, String>> {
        val paras = text.split(Regex("\n\\s*\n")).map { it.trim().filter { c -> !c.isISOControl() || c == '\n' } }
            .filter { it.length > 20 }
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        for (p in paras) {
            var rest = p
            while (rest.length > 600) {
                out.add("$file#$i" to rest.take(600)); i++
                rest = rest.drop(500)
            }
            out.add("$file#$i" to rest); i++
        }
        return out.take(400)
    }

    fun reindexNotes() {
        if (deviceBusy() && !embedLoaded) { status = "Дождись конца текущей задачи."; return }
        if (!embedLoaded) { status = "Сначала вектора в RAM."; return }
        ragBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { status = "Индексирую заметки…" }
            try {
                val all = mutableMapOf<String, RagChunk>()
                for (name in noteFiles) {
                    val chunks = chunkText(name, readNote(name))
                    val texts = chunks.map { "passage: " + it.second.take(800) }
                    var base = 0 // глобальный индекс чанка внутри файла
                    for (batch in texts.chunked(16)) {
                        val vecs = try { LlamaNative.embedBatch(batch.toTypedArray()) } catch (_: Exception) { null }
                        if (vecs == null) { base += batch.size; continue }
                        val dim = try { LlamaNative.embedDim() } catch (_: Exception) { 0 }
                        if (dim <= 0) { base += batch.size; continue }
                        // P0: маппинг без texts.indexOf (ломался на дубликатах текста).
                        val splits = com.neuropocket.app.core.RagUtils.splitBatch(vecs, batch.size, dim)
                        for ((bi, v) in splits) {
                            val (cid, txt) = chunks[base + bi]
                            all[cid] = RagChunk(name, txt, v.toList())
                        }
                        base += batch.size
                    }
                }
                val frozen: Map<String, RagChunk> = all.toMap()
                val jo = org.json.JSONObject()
                frozen.forEach { (cid, ch) ->
                    val rec = org.json.JSONObject()
                    rec.put("file", ch.file)
                    rec.put("text", ch.text)
                    val arr = org.json.JSONArray()
                    ch.vec.forEach { arr.put(it.toDouble()) }
                    rec.put("vec", arr)
                    jo.put(cid, rec)
                }
                ragIndexFile().writeText(jo.toString())
                withContext(Dispatchers.Main) {
                    ragIndexed = all.size
                    status = "Проиндексировано чанков: ${all.size}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Ошибка индекса: ${e.message?.take(120)}" }
            }
            withContext(Dispatchers.Main) { ragBusy = false }
        }
    }

    private fun cosine(a: List<Float>, b: FloatArray): Double =
        com.neuropocket.app.core.RagUtils.cosine(a, b)

    fun askNotes(q: String) {
        if (q.isBlank() || ragBusy) return
        if (!embedLoaded) { status = "Сначала вектора в RAM."; return }
        stopSpeak()
        ragBusy = true
        ragResult = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jo = org.json.JSONObject(ragIndexFile().readText())
                val idx = mutableListOf<RagChunk>()
                val keys = jo.keys()
                while (keys.hasNext()) {
                    val rec = jo.getJSONObject(keys.next())
                    val arr = rec.getJSONArray("vec")
                    val v = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
                    idx.add(RagChunk(rec.getString("file"), rec.getString("text"), v.toList()))
                }
                if (idx.isEmpty()) {
                    withContext(Dispatchers.Main) { status = "Индекс пуст. Сначала «Индексировать»."; ragBusy = false }
                    return@launch
                }
                val qv = try { LlamaNative.embedBatch(arrayOf("query: " + q.take(500))) } catch (_: Exception) { null }
                if (qv == null) {
                    withContext(Dispatchers.Main) { status = "Не вышел вектор вопроса."; ragBusy = false }
                    return@launch
                }
                // P0: порог релевантности + честное "не найдено", не слепой top-3.
                val scored = idx.map { com.neuropocket.app.core.RagUtils.Scored(it, cosine(it.vec, qv)) }
                val top = com.neuropocket.app.core.RagUtils.topK(scored, 3, minScore = 0.25)
                if (top.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        ragResult = "В заметках не найдено релевантного контекста. Попробуй переформулировать или добавь заметки."
                        ragBusy = false; status = "Релевантного контекста нет."
                    }
                    return@launch
                }
                val ctxText = top.joinToString("\n---\n") {
                    "[${it.item.file} • score=${"%.2f".format(it.score)}] ${it.item.text.take(600)}"
                }
                withContext(Dispatchers.Main) { status = "Спрашиваю движок…" }
                llama.maxTokens = maxTokens; llama.topP = topP; llama.topK = topK
                val p = activePersona
                val ans = withFallbackEngine(chatEngineFor(p)).generate(
                    emptyList(), p ?: com.neuropocket.app.data.Persona(),
                    "Контекст из моих заметок:\n$ctxText\n\nВопрос: ${q.take(800)}\n" +
                        "Отвечай по контексту, укажи из какого файла взял."
                )
                withContext(Dispatchers.Main) { ragResult = ans; ragBusy = false; status = "Готово." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Ошибка RAG: ${e.message?.take(140)}"; ragBusy = false }
            }
        }
    }

    /** Голосовой чат: слушаю (VAD) → whisper → движок → sherpa-голос, по кругу. */
    fun startHandsFree() {
        if (hfRunning) return
        val p = activePersona ?: return
        if (!WhisperNative.available || !whisperLoaded) { status = "Сначала whisper в RAM."; return }
        if (sherpa == null) { status = "Сначала голос в RAM (Модели → Голоса)."; return }
        val vadF = vadFile()
        if (!vadF.exists()) { status = "Скачай VAD Silero в каталоге голосов."; return }
        if (!ensureVoiceEngine()) { status = "Нет голосового движка: скачай его в Моделях."; return }
        hfRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            var vad: com.k2fsa.sherpa.onnx.Vad? = null
            var rec: android.media.AudioRecord? = null
            try {
                vad = com.k2fsa.sherpa.onnx.Vad(
                    config = com.k2fsa.sherpa.onnx.VadModelConfig(
                        sileroVadModelConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(model = vadF.absolutePath),
                        sampleRate = 16000
                    )
                )
                val minBuf = android.media.AudioRecord.getMinBufferSize(
                    16000, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT)
                rec = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC, 16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) throw Exception("mic")
                rec.startRecording()
                val shortBuf = ShortArray(512)
                val floatBuf = FloatArray(512)
                while (hfRunning) {
                    withContext(Dispatchers.Main) { hfStatus = "Слушаю… (Стоп — выход)" }
                    val turn = listenTurn(rec, vad, shortBuf, floatBuf, vadSil, vadMin) ?: break
                    if (turn.isEmpty()) continue
                    withContext(Dispatchers.Main) { hfStatus = "Распознаю…" }
                    val tmp = File(ctx.cacheDir, "hf-${System.currentTimeMillis()}.wav")
                    com.neuropocket.app.voice.WavUtils.writeMono16k(tmp, turn)
                    val text = try {
                        WhisperNative.transcribe(tmp.absolutePath, sttLang, threadsEffective())
                    } catch (_: Exception) { "" }
                    try { tmp.delete() } catch (_: Exception) {}
                    if (text.isBlank() || text.startsWith("__ERR")) {
                        withContext(Dispatchers.Main) { hfStatus = "Не расслышал, повтори." }
                        continue
                    }
                    withContext(Dispatchers.Main) { hfStatus = "Ты: ${text.take(120)}"; hfSay("Ты: " + text.take(120)) }
                    var ans = ""
                    var done = false
                    withContext(Dispatchers.Main) { send(text) { ans = it; done = true } }
                    val t0 = System.currentTimeMillis()
                    while (!done && hfRunning && System.currentTimeMillis() - t0 < 300000) {
                        kotlinx.coroutines.delay(300)
                    }
                    if (!hfRunning) break
                    if (ans.isBlank() || ans.startsWith("[")) continue
                    withContext(Dispatchers.Main) { hfStatus = "Отвечаю…"; hfSay("ИИ: " + ans.take(120)) }
                    speakSherpa(ans)
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) { hfStatus = "Ошибка голосового чата." }
            }
            try { rec?.stop(); rec?.release() } catch (_: Exception) {}
            try { vad?.release() } catch (_: Exception) {}
            withContext(Dispatchers.Main) { hfRunning = false; hfStatus = "" }
        }
    }

    /** Один речевой виток: ждём речь, пишем до паузы 1.4с. null = стоп. */
    private suspend fun listenTurn(
        rec: android.media.AudioRecord,
        vad: com.k2fsa.sherpa.onnx.Vad,
        shortBuf: ShortArray,
        floatBuf: FloatArray,
        silenceWin: Int = 42,
        minSamples: Int = 8000
    ): FloatArray? = withContext(Dispatchers.IO) {
        val out = mutableListOf<Float>()
        var started = false
        var silence = 0
        var total = 0
        vad.reset()
        while (hfRunning && total < 16000 * 30) {
            val n = try { rec.read(shortBuf, 0, shortBuf.size) } catch (_: Exception) { -1 }
            if (n <= 0) { kotlinx.coroutines.delay(50); continue }
            for (i in 0 until n) floatBuf[i] = shortBuf[i] / 32768f
            val window = floatBuf.copyOf(n)
            try { vad.acceptWaveform(window) } catch (_: Exception) { return@withContext null }
            val speech = try { vad.isSpeechDetected() } catch (_: Exception) { false }
            if (speech) {
                if (!started) started = true
                silence = 0
                for (i in 0 until n) out.add(window[i])
            } else if (started) {
                for (i in 0 until n) out.add(window[i])
                if (++silence > silenceWin) break
            }
            total += n
        }
        if (!hfRunning) return@withContext null
        if (!started) return@withContext FloatArray(0)
        val arr = out.toFloatArray()
        if (arr.size < minSamples) return@withContext FloatArray(0)
        arr
    }

    /** Озвучка через sherpa-голос. Возвращает false если голос не загружен. */
    suspend fun speakSherpa(text: String): Boolean = withContext(Dispatchers.IO) {
        val eng = sherpa ?: return@withContext false
        speakStop = false
        withContext(Dispatchers.Main) { speaking = true }
        // barge-in: слушаем микрофон поверх речи, при устойчивой речи — стоп
        val monitor = if (bargeIn) startBargeMonitor() else null
        try {
            for (s in com.neuropocket.app.voice.splitSentences(text)) {
                if (speakStop) break
                val audio = try { eng.synth(s, ttsRate) } catch (_: Throwable) { break }
                player.play(audio)
            }
        } catch (_: Exception) { }
        try { monitor?.cancel() } catch (_: Exception) { }
        withContext(Dispatchers.Main) { speaking = false }
        true
    }

    /** Фоновый слушатель для barge-in. Возвращает job или null. */
    private fun startBargeMonitor(): kotlinx.coroutines.Job? {
        val vadF = try { vadFile() } catch (_: Exception) { return null }
        if (!vadF.exists()) return null
        if (!ensureVoiceEngine()) return null
        return viewModelScope.launch(Dispatchers.IO) {
            var vad: com.k2fsa.sherpa.onnx.Vad? = null
            var rec: android.media.AudioRecord? = null
            try {
                vad = vadInst ?: com.k2fsa.sherpa.onnx.Vad(
                    config = com.k2fsa.sherpa.onnx.VadModelConfig(
                        sileroVadModelConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(model = vadF.absolutePath),
                        sampleRate = 16000
                    )
                ).also { vadInst = it }
                val minBuf = android.media.AudioRecord.getMinBufferSize(
                    16000, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) return@launch
                rec = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) return@launch
                rec.startRecording()
                val sb = ShortArray(512)
                val fb = FloatArray(512)
                var hits = 0
                // первые ~0.7с игнорируем (остатки прошлой фразы/эхо старта)
                var skip = 22
                while (!speakStop) {
                    val n = try { rec.read(sb, 0, sb.size) } catch (_: Exception) { -1 }
                    if (n <= 0) {
                        kotlinx.coroutines.delay(30)
                        continue
                    }
                    for (i in 0 until n) fb[i] = sb[i] / 32768f
                    try { vad.acceptWaveform(fb.copyOf(n)) } catch (_: Exception) { break }
                    val speech = try { vad.isSpeechDetected() } catch (_: Exception) { false }
                    if (skip > 0) {
                        skip--
                        try { vad.reset() } catch (_: Exception) { }
                        continue
                    }
                    if (speech && ++hits >= 8) {
                        speakStop = true
                        try { player.stop() } catch (_: Exception) { }
                        break
                    }
                    if (!speech) hits = 0
                }
            } catch (_: Throwable) { }
            try { rec?.stop(); rec?.release() } catch (_: Exception) { }
        }
    }

    var storageInfo by mutableStateOf(""); private set
    var appVersion by mutableStateOf(""); private set

    fun refreshFeed() { viewModelScope.launch(Dispatchers.IO) {
        val ps = Store.loadPosts(ctx).sortedByDescending { it.ts }
        val cm = Store.loadComments(ctx).sortedBy { it.ts }
        withContext(Dispatchers.Main) {
            posts = ps
            comments = cm
            status = "Лента обновлена."
        }
    } }

    fun computeStorage() { viewModelScope.launch(Dispatchers.IO) {
        fun dirSize(f: java.io.File): Long {
            var s = 0L
            try {
                f.walkTopDown().forEach { if (it.isFile) s += it.length() }
            } catch (_: Exception) { }
            return s
        }
        val m = dirSize(Store.modelsDir(ctx))
        val c = dirSize(ctx.cacheDir)
        val gb = { b: Long -> "%.1f ГБ".format(b / 1073741824.0) }
        withContext(Dispatchers.Main) { storageInfo = "Модели ${gb(m)} • Кэш ${gb(c)}" }
    } }

    fun clearCache() { viewModelScope.launch(Dispatchers.IO) {
        try {
            ctx.cacheDir.listFiles()?.forEach { if (it.name != "chat-export.md") try { it.deleteRecursively() } catch (_: Exception) {} }
        } catch (_: Exception) { }
        computeStorage()
        withContext(Dispatchers.Main) { status = "Кэш очищен." }
    } }

    fun loadAppVersion() {
        appVersion = try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (pi.versionName ?: "?") + " (" + pi.versionCode + ")"
        } catch (_: Exception) { "" }
    }

    fun refreshGallery() {
        val dir = File(ctx.getExternalFilesDir(null), "pictures").apply { mkdirs() }
        gallery = dir.listFiles()?.filter { it.extension.lowercase() in listOf("png", "jpg") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun selectPersona(id: String) { viewModelScope.launch {
        activePersona = personas.find { it.id == id } ?: activePersona
        activePersona?.let { Store.setActivePersona(ctx, it.id) }
    } }

    fun addPersona(name: String, prompt: String, emoji: String, desc: String = "", tags: String = "", temp: Float = 0.7f, voice: String = "") { viewModelScope.launch(Dispatchers.IO) {
        val tagList = tags.split(",", "،", ";").map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.take(6)
        val np = Persona(name = name.ifBlank { "Без имени" }, systemPrompt = prompt.ifBlank { "Ты полезный ассистент." }, avatarEmoji = emoji.ifBlank { "\uD83D\uDE00" }, desc = desc.trim().take(300), tags = tagList, temperature = temp.coerceIn(0f, 1.5f), voice = voice)
        val nl = personas + np
        Store.savePersonas(ctx, nl)
        Store.setActivePersona(ctx, np.id)
        withContext(Dispatchers.Main) { personas = nl; activePersona = np }
    } }

    fun send(userText: String, onStream: (String) -> Unit = {}) {
        val p = activePersona ?: return
        if (userText.isBlank() || busy || agentRunning || pBusy || sdBusy || visionBusy) {
            if (userText.isNotBlank()) status = "Дождись конца текущей задачи."
            onStream(""); return
        }
        stopSpeak()
        busy = true
        NpLog.d("chat", "send (" + chatEngine().engineName + "): " + userText.take(80))
        val um = ChatMessage(role = "user", text = userText, personaId = p.id)
        val aid = java.util.UUID.randomUUID().toString()
        messages = messages + um + ChatMessage(id = aid, role = "assistant", text = "", personaId = p.id)
        launchMainGen(p, userText, aid, onStream)
    }

    /** Перегенерировать последний ответ (вопрос не дублируется). */
    fun regenerate() {
        val p = activePersona ?: return
        if (deviceBusy() || pBusy) { status = "Дождись конца текущей задачи."; return }
        val list = messages
        val li = list.indexOfLast { it.role == "user" }
        if (li < 0) return
        stopSpeak()
        busy = true
        val aid = java.util.UUID.randomUUID().toString()
        messages = list.take(li + 1) + ChatMessage(id = aid, role = "assistant", text = "", personaId = p.id)
        launchMainGen(p, list[li].text, aid) {}
    }

    /** Перегенерировать ответ в чате персоны. */
    fun regeneratePersona(persona: Persona) {
        if (deviceBusy() || pBusy || busy || agentRunning) { status = "Дождись конца текущей задачи."; return }
        val list = pchats[persona.id] ?: emptyList()
        val li = list.indexOfLast { it.role == "user" }
        if (li < 0) return
        stopSpeak()
        pBusy = true
        val aid = java.util.UUID.randomUUID().toString()
        val base = list.take(li + 1) +
            ChatMessage(id = aid, role = "assistant", text = "", personaId = persona.id)
        viewModelScope.launch(Dispatchers.Main) {
            pchats = pchats + (persona.id to base)
        }
        launchPersonaGen(persona, list[li].text, aid, base) {}
    }

    fun renameSession(id: String, title: String) { viewModelScope.launch(Dispatchers.IO) {
        val t = title.trim().take(48)
        if (t.isBlank()) return@launch
        val ses = Store.loadSessions(ctx).map { if (it.id == id) it.copy(title = t) else it }
        Store.saveSessions(ctx, ses)
        withContext(Dispatchers.Main) {
            sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
        }
    } }

    private fun launchMainGen(p: Persona, userText: String, aid: String, onStream: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val final = runStream(chatEngineFor(p), p, messages, userText, aid, onUpdate = { snap ->
                viewModelScope.launch(Dispatchers.Main) {
                    messages = messages.map { if (it.id == aid) it.copy(text = snap) else it }
                }
            }, onFirstToken = { viewModelScope.launch(Dispatchers.Main) { status = "Печатает…" } })
            val nm = messages.map { if (it.id == aid) it.copy(text = final) else it }.takeLast(200)
            persistSession(nm)
            withContext(Dispatchers.Main) { messages = nm; busy = false; status = "Готов. Офлайн." }
            onStream(final)
        }
    }

    /**
     * Ядро стриминга: гонит токены движка через onUpdate (троттлинг 120мс),
     * возвращает финальный текст. Вызывается из IO.
     */
    private suspend fun runStream(
        engine: com.neuropocket.app.engine.AiEngine,
        persona: Persona,
        history: List<ChatMessage>,
        userText: String,
        aid: String,
        onUpdate: (String) -> Unit,
        onFirstToken: () -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { status = "Думаю…" }
        var first = true
        llama.maxTokens = maxTokens; llama.topP = topP; llama.topK = topK
        engineNotice()?.let { n -> withContext(Dispatchers.Main) { status = n } }
        val buf = StringBuilder()
        var lastPush = 0L
        val reply = genWithFallback(engine, persona, history, userText) { tok ->
            if (first) {
                first = false
                viewModelScope.launch(Dispatchers.Main) { onFirstToken() }
            }
            synchronized(buf) { buf.append(tok) }
            val now = System.currentTimeMillis()
            if (now - lastPush >= 120) {
                lastPush = now
                onUpdate(buf.toString())
            }
        }
        kotlinx.coroutines.delay(150)
        val final = reply.ifBlank { buf.toString() }
        onUpdate(final)
        final
    }

    private suspend fun persistSession(nm: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val sid = activeSessionId ?: return@withContext
        val mmap = Store.loadMsgMap(ctx).toMutableMap()
        mmap[sid] = nm
        val ses = Store.loadSessions(ctx).toMutableList()
        val idx = ses.indexOfFirst { it.id == sid }
        if (idx >= 0) {
            val s = ses[idx]
            if (s.title == "Новый чат" || s.title == "Мой чат" || s.title == "Разбор") {
                val first = nm.firstOrNull { it.role == "user" }?.text?.trim()?.take(36) ?: s.title
                ses[idx] = s.copy(title = first, updated = System.currentTimeMillis())
            } else {
                ses[idx] = s.copy(updated = System.currentTimeMillis())
            }
        }
        persistChatIO(ses, mmap)
        withContext(Dispatchers.Main) { sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated }) }
    }

    /** Продолжить в НОВОМ общем чате (мост из инструментов, не свалка). */
    fun discussInChat(seedText: String) {
        val p = activePersona ?: return
        if (seedText.isBlank() || deviceBusy() || pBusy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val ses = Store.loadSessions(ctx).toMutableList()
            val mmap = Store.loadMsgMap(ctx).toMutableMap()
            val s = ChatSession(title = "Разбор", personaId = p.id)
            ses.add(0, s); mmap[s.id] = emptyList()
            Store.setActiveSession(ctx, s.id)
            persistChatIO(ses, mmap)
            val um = ChatMessage(role = "user", text = seedText.take(1500), personaId = p.id)
            val aid = java.util.UUID.randomUUID().toString()
            withContext(Dispatchers.Main) {
                sessions = ses.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updated })
                activeSessionId = s.id
                messages = listOf(um, ChatMessage(id = aid, role = "assistant", text = "", personaId = p.id))
            }
            val final = runStream(chatEngineFor(p), p, listOf(um), seedText, aid, onUpdate = { snap ->
                viewModelScope.launch(Dispatchers.Main) {
                    messages = messages.map { if (it.id == aid) it.copy(text = snap) else it }
                }
            }, onFirstToken = { viewModelScope.launch(Dispatchers.Main) { status = "Печатает…" } })
            val nm = withContext(Dispatchers.Main) {
                messages.map { if (it.id == aid) it.copy(text = final) else it }.takeLast(200)
            }
            persistSession(nm)
            withContext(Dispatchers.Main) { messages = nm; busy = false; status = "Готов. Офлайн." }
        }
    }

    // ---------- Чаты персон (отдельная среда, не в общем списке) ----------
    fun personaMessages(id: String): List<ChatMessage> = pchats[id] ?: emptyList()

    fun sendPersona(persona: Persona, userText: String, onDone: (String) -> Unit = {}) {
        if (userText.isBlank() || pBusy || busy || agentRunning || sdBusy || visionBusy) { onDone(""); return }
        stopSpeak()
        pBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val cur = Store.loadPChatMap(ctx).toMutableMap()
            val um = ChatMessage(role = "user", text = userText, personaId = persona.id)
            val aid = java.util.UUID.randomUUID().toString()
            var list = (cur[persona.id] ?: emptyList()) + um +
                ChatMessage(id = aid, role = "assistant", text = "", personaId = persona.id)
            withContext(Dispatchers.Main) {
                pchats = pchats + (persona.id to list)
            }
            launchPersonaGen(persona, userText, aid, (pchats[persona.id] ?: emptyList()), onDone)
        }
    }

    private fun launchPersonaGen(
        persona: Persona, userText: String, aid: String,
        list: List<ChatMessage>, onDone: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val final = runStream(chatEngineFor(persona), persona, list, userText, aid, onUpdate = { snap ->
                viewModelScope.launch(Dispatchers.Main) {
                    val nl = (pchats[persona.id] ?: listOf())
                        .map { if (it.id == aid) it.copy(text = snap) else it }
                    pchats = pchats + (persona.id to nl)
                }
            }, onFirstToken = { viewModelScope.launch(Dispatchers.Main) { status = "Печатает…" } })
            kotlinx.coroutines.delay(120)
            val base = withContext(Dispatchers.Main) {
                (pchats[persona.id] ?: list).map { if (it.id == aid) it.copy(text = final) else it }.takeLast(200)
            }
            val cmap = Store.loadPChatMap(ctx).toMutableMap()
            cmap[persona.id] = base
            Store.savePChatMap(ctx, cmap)
            withContext(Dispatchers.Main) {
                pchats = pchats + (persona.id to base)
                pBusy = false
            }
            onDone(final)
        }
    }

    fun clearPersonaChat(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val cur = Store.loadPChatMap(ctx).toMutableMap()
        cur[id] = emptyList()
        Store.savePChatMap(ctx, cur)
        withContext(Dispatchers.Main) { pchats = pchats + (id to emptyList()) }
    } }

    fun stopPersona() {
        try { LlamaNative.cancel() } catch (_: Exception) {}
        try { com.neuropocket.app.engine.RemoteEngine.cancelCurrent() } catch (_: Exception) {}
    }

    // ---------- Среды инструментов (своя история у каждой) ----------
    fun toolHistory(id: String): List<ToolRun> = toolRuns[id] ?: emptyList()

    fun runTool(toolId: String, input: String, langFrom: String = "", langTo: String = "") {
        val def = com.neuropocket.app.data.ToolCatalog.byId(toolId) ?: return
        if (input.isBlank() || toolBusyId != null || deviceBusy() || pBusy) return
        stopSpeak()
        toolBusyId = toolId
        viewModelScope.launch(Dispatchers.IO) {
            llama.maxTokens = maxTokens; llama.topP = topP; llama.topK = topK
            engineNotice()?.let { n -> withContext(Dispatchers.Main) { status = n } }
            val p = activePersona ?: Persona()
            val prompt = com.neuropocket.app.data.ToolCatalog.buildPrompt(def, input, langFrom, langTo)
            val out = try {
                withFallbackEngine(chatEngineFor(p)).generate(emptyList(), p, prompt)
            } catch (e: Exception) { "[Ошибка: ${e.message?.take(120)}]" }
            val map = Store.loadToolMap(ctx).toMutableMap()
            val nl = (map[toolId] ?: emptyList()) + ToolRun(input = input.take(2000), output = out.take(4000))
            map[toolId] = nl.takeLast(50)
            Store.saveToolMap(ctx, map)
            withContext(Dispatchers.Main) {
                toolRuns = toolRuns + (toolId to (nl.takeLast(50)))
                toolBusyId = null
            }
        }
    }

    fun clearTool(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val map = Store.loadToolMap(ctx).toMutableMap()
        map[id] = emptyList()
        Store.saveToolMap(ctx, map)
        withContext(Dispatchers.Main) { toolRuns = toolRuns + (id to emptyList()) }
    } }

    fun deleteToolRun(toolId: String, runId: String) { viewModelScope.launch(Dispatchers.IO) {
        val map = Store.loadToolMap(ctx).toMutableMap()
        map[toolId] = (map[toolId] ?: emptyList()).filterNot { it.id == runId }
        Store.saveToolMap(ctx, map)
        withContext(Dispatchers.Main) { toolRuns = toolRuns + (toolId to (map[toolId] ?: emptyList())) }
    } }

    // ---------- Аватары персон ----------
    fun avatarsDir(): File = File(ctx.getExternalFilesDir(null), "avatars").apply { mkdirs() }

    fun setAvatarFromFile(personaId: String, src: File) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val dst = File(avatarsDir(), "$personaId.jpg")
            src.copyTo(dst, overwrite = true)
            updatePersonaField(personaId) { it.copy(avatarPath = dst.absolutePath) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "Не вышло: ${e.message?.take(100)}" }
        }
    } }

    fun clearAvatar(personaId: String) { viewModelScope.launch(Dispatchers.IO) {
        try { File(avatarsDir(), "$personaId.jpg").delete() } catch (_: Exception) {}
        updatePersonaField(personaId) { it.copy(avatarPath = "") }
    } }

    fun genAvatar(personaId: String) {
        if (sdBusy || deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        val per = personas.find { it.id == personaId } ?: return
        if (!sdLoaded) { status = "Сначала SD в RAM (Инструменты → Фото)."; return }
        sdBusy = true
        status = "Рисую аватар…"
        viewModelScope.launch(Dispatchers.IO) {
            val prompt = "portrait avatar, ${per.name}, ${per.desc.take(150)}, high quality, centered face"
            val rgb = try {
                com.neuropocket.app.engine.SdNative.render(prompt, "blurry, low quality", 512, 512, 6, 1.0f, System.currentTimeMillis(), "lcm", false)
            } catch (_: Exception) { null }
            if (rgb == null) {
                withContext(Dispatchers.Main) { sdBusy = false; status = "SD не смог." }
                return@launch
            }
            try {
                val px = IntArray(512 * 512) { i ->
                    val r = rgb[i * 3].toInt() and 0xFF
                    val g = rgb[i * 3 + 1].toInt() and 0xFF
                    val b = rgb[i * 3 + 2].toInt() and 0xFF
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                val bmp = android.graphics.Bitmap.createBitmap(px, 512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                val dst = File(avatarsDir(), "$personaId.jpg")
                dst.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                bmp.recycle()
                updatePersonaField(personaId) { it.copy(avatarPath = dst.absolutePath) }
                withContext(Dispatchers.Main) { sdBusy = false; status = "Аватар готов." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { sdBusy = false; status = "Ошибка PNG: ${e.message?.take(100)}" }
            }
        }
    }

    private suspend fun updatePersonaField(id: String, fn: (Persona) -> Persona) = withContext(Dispatchers.IO) {
        val nl = Store.loadPersonas(ctx).map { if (it.id == id) fn(it) else it }
        Store.savePersonas(ctx, nl)
        withContext(Dispatchers.Main) {
            personas = nl
            activePersona?.let { a -> activePersona = nl.find { it.id == a.id } }
        }
    }

    fun updatePersonaFull(
        id: String, name: String, prompt: String, emoji: String,
        desc: String, tags: String, temp: Float, voice: String,
        nsfw: Boolean, engine: String
    ) { viewModelScope.launch(Dispatchers.IO) {
        val tagList = tags.split(",", ";").map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.take(6)
        updatePersonaField(id) {
            it.copy(
                name = name.ifBlank { "Без имени" },
                systemPrompt = prompt.ifBlank { "Ты полезный ассистент." },
                avatarEmoji = emoji.ifBlank { "\uD83D\uDE00" },
                desc = desc.trim().take(300), tags = tagList,
                temperature = temp.coerceIn(0f, 1.5f), voice = voice,
                nsfwAllowed = nsfw, engine = engine
            )
        }
        withContext(Dispatchers.Main) { status = "Персона обновлена." }
    } }

    /** Экспорт персоны в JSON-файл (без чатов). Возвращает файл или null. */
    suspend fun exportPersonaFile(id: String): java.io.File? = withContext(Dispatchers.IO) {
        try {
            val per = Store.loadPersonas(ctx).find { it.id == id } ?: return@withContext null
            val jo = org.json.JSONObject()
            jo.put("app", "NeuroPocket-persona")
            jo.put("name", per.name)
            jo.put("systemPrompt", per.systemPrompt)
            jo.put("avatarEmoji", per.avatarEmoji)
            jo.put("temperature", per.temperature.toDouble())
            jo.put("nsfwAllowed", per.nsfwAllowed)
            jo.put("desc", per.desc)
            val arr = org.json.JSONArray()
            per.tags.forEach { arr.put(it) }
            jo.put("tags", arr)
            val safe = per.name.replace(Regex("[^A-Za-z0-9а-яА-ЯёЁ _\\-]"), "_").trim().take(40).ifBlank { "persona" }
            val f = java.io.File(ctx.getExternalFilesDir(null), "models/persona-$safe.json")
            f.writeText(jo.toString())
            // аватар рядом, если есть
            if (per.avatarPath.isNotBlank()) {
                try {
                    val src = java.io.File(per.avatarPath)
                    if (src.exists()) src.copyTo(java.io.File(f.parent, "persona-$safe.jpg"), overwrite = true)
                } catch (_: Exception) { }
            }
            f
        } catch (_: Exception) { null }
    }

    /** Импорт персоны из JSON (id всегда новый). */
    fun importPersonaFile(uri: android.net.Uri) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw Exception("не открылся файл")
            val jo = org.json.JSONObject(text)
            require(jo.optString("app") == "NeuroPocket-persona") { "не файл персоны" }
            val tags = mutableListOf<String>()
            val arr = jo.optJSONArray("tags")
            if (arr != null) for (i in 0 until arr.length()) tags.add(arr.getString(i))
            val np = Persona(
                name = jo.optString("name", "Без имени").take(60),
                systemPrompt = jo.optString("systemPrompt", "Ты полезный ассистент.").take(4000),
                avatarEmoji = jo.optString("avatarEmoji", "😀").take(8),
                temperature = jo.optDouble("temperature", 0.7).toFloat().coerceIn(0f, 1.5f),
                nsfwAllowed = jo.optBoolean("nsfwAllowed", false),
                desc = jo.optString("desc", "").take(300),
                tags = tags.take(6)
            )
            val nl = Store.loadPersonas(ctx).toMutableList().apply { add(np) }
            Store.savePersonas(ctx, nl)
            // аватар рядом с json? models/persona-<имя>.jpg -> avatars/<id>.jpg
            var finalList: List<Persona> = nl
            try {
                val safe = np.name.replace(Regex("[^A-Za-z0-9а-яА-ЯёЁ _-]"), "_").trim().take(40).ifBlank { "persona" }
                val cand = java.io.File(Store.modelsDir(ctx), "persona-$safe.jpg")
                if (cand.exists()) {
                    cand.copyTo(java.io.File(avatarsDir(), "${np.id}.jpg"), overwrite = true)
                    finalList = Store.loadPersonas(ctx).map {
                        if (it.id == np.id) it.copy(avatarPath = java.io.File(avatarsDir(), "${np.id}.jpg").absolutePath)
                        else it
                    }
                    Store.savePersonas(ctx, finalList)
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                personas = finalList
                status = "Импортирована: ${np.name}"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "Импорт не вышел: ${e.message?.take(120)}" }
        }
    } }

    fun deletePersona(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val nl = Store.loadPersonas(ctx).filterNot { it.id == id }
        if (nl.isEmpty()) {
            withContext(Dispatchers.Main) { status = "Последнюю персону удалять нельзя." }
            return@launch
        }
        Store.savePersonas(ctx, nl)
        val pm = Store.loadPChatMap(ctx).toMutableMap()
        pm.remove(id)
        Store.savePChatMap(ctx, pm)
        try { File(avatarsDir(), "$id.jpg").delete() } catch (_: Exception) {}
        if (activePersona?.id == id) Store.setActivePersona(ctx, nl.first().id)
        withContext(Dispatchers.Main) {
            personas = nl
            pchats = pchats - id
            if (activePersona?.id == id) activePersona = nl.first()
            status = "Персона удалена."
        }
    } }

    // ---------- Лента ----------
    fun addPost(personaId: String, text: String) { viewModelScope.launch(Dispatchers.IO) {
        if (text.isBlank()) return@launch
        val np = SocialPost(authorId = personaId, text = text.take(500), likes = 0)
        val all = (listOf(np) + Store.loadPosts(ctx)).sortedByDescending { it.ts }.take(200)
        Store.savePosts(ctx, all)
        withContext(Dispatchers.Main) { posts = all; status = "Опубликовано." }
    } }

    fun addComment(postId: String, personaId: String, text: String, ai: Boolean = false) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val nc = PostComment(postId = postId, authorId = personaId, text = text.take(400), aiMade = ai)
            val all = (Store.loadComments(ctx) + nc).takeLast(500)
            Store.saveComments(ctx, all)
            withContext(Dispatchers.Main) { comments = all.sortedBy { it.ts } }
        }
    }

    fun deleteComment(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val all = Store.loadComments(ctx).filterNot { it.id == id }
        Store.saveComments(ctx, all)
        withContext(Dispatchers.Main) { comments = all.sortedBy { it.ts } }
    } }

    fun aiComment(postId: String, personaId: String) {
        if (deviceBusy() || pBusy) { status = "Дождись конца генерации."; return }
        val per = personas.find { it.id == personaId } ?: return
        val post = posts.find { it.id == postId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { status = "Пишу комментарий…" }
            llama.maxTokens = 160; llama.topP = topP; llama.topK = topK
            val raw = try {
                withFallbackEngine(chatEngineFor(per)).generate(
                    emptyList(), activePersona ?: per,
                    "Пост в соцсети: «" + post.text.take(400) + "»\n" +
                        "Напиши 1 короткий живой комментарий от лица «" + per.name + "» " +
                        "(" + per.desc.ifBlank { per.systemPrompt.take(120) } + "). До 200 символов, без пояснений."
                )
            } catch (_: Exception) { "" }
            val line = raw.lines().map { it.trim() }.firstOrNull { it.length > 3 }?.take(300)
            if (!line.isNullOrBlank()) {
                val nc = PostComment(postId = postId, authorId = personaId, text = line, aiMade = true)
                val all = (Store.loadComments(ctx) + nc).takeLast(500)
                Store.saveComments(ctx, all)
                withContext(Dispatchers.Main) { comments = all.sortedBy { it.ts }; status = "Готово." }
            } else {
                withContext(Dispatchers.Main) { status = "Не вышло, попробуй ещё." }
            }
        }
    }

    fun updatePost(id: String, text: String) { viewModelScope.launch(Dispatchers.IO) {
        val t = text.trim().take(500)
        if (t.isBlank()) return@launch
        val all = Store.loadPosts(ctx).map { if (it.id == id) it.copy(text = t) else it }
        Store.savePosts(ctx, all)
        withContext(Dispatchers.Main) { posts = all.sortedByDescending { it.ts } }
    } }

    fun deletePost(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val all = Store.loadPosts(ctx).filterNot { it.id == id }
        Store.savePosts(ctx, all)
        val nc = Store.loadComments(ctx).filterNot { it.postId == id }
        Store.saveComments(ctx, nc)
        withContext(Dispatchers.Main) { posts = all; comments = nc.sortedBy { it.ts } }
    } }

    fun aiPost(personaId: String, count: Int = 2) {
        if (deviceBusy() || pBusy) { status = "Дождись конца генерации."; return }
        val per = personas.find { it.id == personaId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { status = "Пишу пост…" }
            llama.maxTokens = maxTokens; llama.topP = topP; llama.topK = topK
            val raw = try {
                withFallbackEngine(chatEngineFor(activePersona ?: per)).generate(
                    emptyList(), activePersona ?: per,
                    "Придумай $count коротких живых постов для соцсети от лица «${per.name}» (${per.desc.ifBlank { per.systemPrompt.take(120) }}). " +
                        "Каждый с новой строки, до 200 символов, добавь 1–2 хэштега (#тема). Без нумерации."
                )
            } catch (_: Exception) { "" }
            val lines = raw.lines().map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '.', ')', ' ').trim() }
                .filter { it.length > 8 }.take(count)
            val made = if (lines.isEmpty()) {
                (1..count).map { SocialPost(authorId = personaId, text = mock.randomPost()) }
            } else {
                lines.map { SocialPost(authorId = personaId, text = it.take(300), aiMade = true) }
            }
            val all = (made + Store.loadPosts(ctx)).sortedByDescending { it.ts }.take(200)
            Store.savePosts(ctx, all)
            withContext(Dispatchers.Main) { posts = all; status = "Готово." }
        }
    }

    fun stopGen() {
        try { LlamaNative.cancel() } catch (_: Exception) {}
        try { com.neuropocket.app.engine.RemoteEngine.cancelCurrent() } catch (_: Exception) {}
    }

    fun runAgent(task: String) {
        val p = activePersona ?: return
        if (task.isBlank() || agentRunning || busy || sdBusy) return
        agentRunning = true
        agentCancel = false
        agentSteps = emptyList()
        agentResult = ""
        status = "Агент работает…"
        viewModelScope.launch(Dispatchers.IO) {
            llama.maxTokens = maxTokens; llama.topP = topP; llama.topK = topK
            val active = withFallbackEngine(chatEngineFor(p))
            val (steps, final) = try {
                com.neuropocket.app.engine.AgentRunner.runWithEngine(task, active, p, {
                    viewModelScope.launch(Dispatchers.Main) { agentSteps = agentSteps.toList() }
                }, { agentCancel })
            } catch (e: Exception) {
                emptyList<com.neuropocket.app.engine.AgentStep>() to "[Агент упал: ${e.message}]"
            }
            withContext(Dispatchers.Main) {
                agentSteps = steps
                agentResult = final
                agentRunning = false
                status = "Агент готов. Офлайн."
            }
        }
    }

    fun stopAgent() {
        agentCancel = true
        stopGen()
    }

    fun clearChat() { viewModelScope.launch(Dispatchers.IO) {
        val sid = activeSessionId
        if (sid != null) {
            val mmap = Store.loadMsgMap(ctx).toMutableMap()
            mmap[sid] = emptyList()
            Store.saveMsgMap(ctx, mmap)
        } else {
            Store.saveChats(ctx, emptyList())
        }
        withContext(Dispatchers.Main) { messages = emptyList() }
    } }

    fun applyTheme(v: String) { viewModelScope.launch { Store.setTheme(ctx, v); theme = v } }
    fun applyAccent(v: String) { viewModelScope.launch { Store.setAccent(ctx, v); accent = v } }
    fun applyMaxTokens(v: Int) { viewModelScope.launch { Store.setMaxTokens(ctx, v); maxTokens = v } }
    fun applyTopP(v: Float) { viewModelScope.launch { Store.setTopP(ctx, v); topP = v } }
    fun applyTopK(v: Int) { viewModelScope.launch { Store.setTopK(ctx, v); topK = v } }
    fun applyCtxSize(v: Int) { viewModelScope.launch { Store.setCtxSize(ctx, v); ctxSize = v } }
    fun applyThreads(v: Int) { viewModelScope.launch { Store.setThreads(ctx, v); threads = v } }
    fun applyGpuLayers(v: Int) { viewModelScope.launch { Store.setGpuLayers(ctx, v); gpuLayers = v } }
    fun applyTextScale(v: Float) { viewModelScope.launch { Store.setTextScale(ctx, v); textScale = v } }
    fun applyTtsRate(v: Float) { viewModelScope.launch { Store.setTtsRate(ctx, v); ttsRate = v } }
    fun applyTtsPitch(v: Float) { viewModelScope.launch { Store.setTtsPitch(ctx, v); ttsPitch = v } }
    fun applyKeepScreenOn(v: Boolean) { viewModelScope.launch { Store.setKeepOn(ctx, v); keepScreenOn = v } }
    fun applyWifiOnly(v: Boolean) { viewModelScope.launch { Store.setWifiOnly(ctx, v); wifiOnly = v } }
    fun applyAutoloadChat(v: Boolean) { viewModelScope.launch { Store.setAutoloadChat(ctx, v); autoloadChat = v } }
    fun applyAutoloadWhisper(v: Boolean) { viewModelScope.launch { Store.setAutoloadWhisper(ctx, v); autoloadWhisper = v } }
    fun applyAutoloadSd(v: Boolean) { viewModelScope.launch { Store.setAutoloadSd(ctx, v); autoloadSd = v } }
    fun applyAutoUnload(v: Boolean) { viewModelScope.launch { Store.setAutoUnload(ctx, v); autoUnload = v } }
    fun applyShowTime(v: Boolean) { viewModelScope.launch { Store.setShowTime(ctx, v); showTime = v } }
    fun applyServerTimeout(v: Int) { viewModelScope.launch { Store.setServerTimeout(ctx, v); serverTimeout = v } }
    fun applyAutoBackup(v: Boolean) {
        viewModelScope.launch {
            Store.setAutoBackup(ctx, v); autoBackup = v
            applyAutoBackupWork(v)
            status = if (v) "Автобэкап включён (раз в неделю)." else "Автобэкап выключен."
        }
    }

    fun applyVadSil(v: Int) { viewModelScope.launch { Store.setVadSil(ctx, v); vadSil = v } }
    fun applyVadMin(v: Int) { viewModelScope.launch { Store.setVadMin(ctx, v); vadMin = v } }
    fun applyBargeIn(v: Boolean) { viewModelScope.launch { Store.setBargeIn(ctx, v); bargeIn = v } }

    fun applyAutopost(h: Int) {
        viewModelScope.launch {
            Store.setAutopost(ctx, h); autopostHours = h
            applyAutopostWork(h)
            status = if (h == 0) "Автопостинг выключен." else "Автопостинг каждые ${h}ч."
        }
    }

    private fun applyAutopostWork(h: Int) {
        try {
            val wm = androidx.work.WorkManager.getInstance(ctx)
            if (h <= 0) {
                wm.cancelUniqueWork("np-autopost")
            } else {
                val req = androidx.work.PeriodicWorkRequestBuilder<AutopostWorker>(h.toLong(), java.util.concurrent.TimeUnit.HOURS)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .addTag("np-autopost")
                    .build()
                wm.enqueueUniquePeriodicWork("np-autopost", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
            }
        } catch (_: Exception) { }
    }

    private fun applyAutoBackupWork(v: Boolean) {
        try {
            val wm = androidx.work.WorkManager.getInstance(ctx)
            if (v) {
                val req = androidx.work.PeriodicWorkRequestBuilder<BackupWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                    .addTag("np-autobackup")
                    .build()
                wm.enqueueUniquePeriodicWork("np-autobackup", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
            } else {
                wm.cancelUniqueWork("np-autobackup")
            }
        } catch (_: Exception) { }
    }
    fun applyAutoFallback(v: Boolean) { viewModelScope.launch { Store.setAutoFallback(ctx, v); autoFallback = v } }
    fun markOnboarded(v: Boolean = true) { viewModelScope.launch { Store.setOnboarded(ctx, v); onboarded = v } }
    fun setActiveModel(id: String) { viewModelScope.launch { Store.setActiveModel(ctx, id); activeModelId = id; status = "Выбрана модель: $id. Загрузится сама при входе в чат (если включено)." } }

    data class DlInfo(val fileName: String, val text: String, val progress: Float, val done: Boolean, val failed: Boolean)
    var downloads by mutableStateOf(mapOf<Long, DlInfo>()); private set
    private var dlPolling = false

    fun dlFor(fileName: String): DlInfo? = downloads.values.find { it.fileName == fileName && !it.done }

    fun cancelDownload(id: Long) {
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(id)
        } catch (_: Exception) { }
        downloads = downloads - id
    }

    fun dismissDownload(id: Long) { downloads = downloads - id }

    private fun startDlPoll() {
        if (dlPolling) return
        dlPolling = true
        viewModelScope.launch(Dispatchers.IO) {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (true) {
                kotlinx.coroutines.delay(1000)
                val ids = withContext(Dispatchers.Main) { downloads.keys.toList() }
                if (ids.isEmpty()) { dlPolling = false; return@launch }
                var needScan = false
                val upd = mutableMapOf<Long, DlInfo>()
                for (id in ids) {
                    val cur = withContext(Dispatchers.Main) { downloads[id] } ?: continue
                    if (cur.done) continue
                    try {
                        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                            if (!c.moveToFirst()) {
                                upd[id] = cur.copy(text = "нет в очереди", failed = true)
                                return@use
                            }
                            val stIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val dlIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val rsIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val st = c.getInt(stIdx)
                            val dl = c.getLong(dlIdx)
                            val tot = c.getLong(totIdx)
                            when (st) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    upd[id] = cur.copy(text = "Готово", progress = 1f, done = true)
                                    needScan = true
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val r = try { c.getInt(rsIdx) } catch (_: Exception) { -1 }
                                    upd[id] = cur.copy(text = "Ошибка ($r). Тап — убрать.", failed = true)
                                }
                                DownloadManager.STATUS_PAUSED -> {
                                    upd[id] = cur.copy(text = "Пауза • ${mb(dl)} / ${mb(tot)}")
                                }
                                else -> {
                                    val p = if (tot > 0) (dl.toFloat() / tot).coerceIn(0f, 1f) else -1f
                                    upd[id] = if (p >= 0)
                                        cur.copy(text = "${(p * 100).toInt()}% • ${mb(dl)} / ${mb(tot)}", progress = p)
                                    else cur.copy(text = "Старт… ${mb(dl)}")
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                withContext(Dispatchers.Main) {
                    if (upd.isNotEmpty()) downloads = downloads + upd
                    val doneIds = downloads.filter { it.value.done }.keys
                    if (doneIds.isNotEmpty()) {
                        downloads = downloads - doneIds
                        for (id in doneIds) {
                            val fn = upd[id]?.fileName ?: ""
                            if (fn == "libnpsd.so") {
                                try {
                                    val src = java.io.File(ctx.getExternalFilesDir(null), "models/libnpsd.so")
                                    val dst = sdEngineFile()
                                    if (src.exists()) {
                                        src.copyTo(dst, overwrite = true)
                                        src.delete()
                                        ensureSdEngine()
                                        refreshSdEngineState()
                                        status = "Движок SD готов."
                                    }
                                } catch (_: Exception) { }
                            }
                            if (fn == "NeuroPocket-update.apk") {
                                status = "Обновление скачано."
                                promptInstallUpdate()
                            }
                            if (fn == "voice-engine-arm64.zip") {
                                extractVoiceEngine()
                            }
                        }
                        scanModels()
                        if (status != "Движок SD готов." && !status.startsWith("Обновление")) {
                            status = "Загрузка завершена."
                        }
                    }
                }
                if (needScan) { /* скан уже вызван выше */ }
            }
        }
    }

    private fun mb(b: Long): String = if (b <= 0) "?" else "${b / 1048576} МБ"

    private fun fmtDur(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun toggleEngine() {
        useNative = !useNative
        status = "Движок: ${engineLabel()}"
    }

    // ---------- Провайдеры ----------
    fun selectProvider(id: String) { viewModelScope.launch {
        Store.setActiveProvider(ctx, id); activeProviderId = id
        status = "Движок: ${engineLabel()}"
    } }

    /** Ключи — в vault, в JSON только пустые. */
    private suspend fun persistProvidersIO(list: List<AiProvider>) {
        list.forEach { if (it.apiKey.isNotEmpty()) KeyVault.put(ctx, it.id, it.apiKey) }
        Store.saveProviders(ctx, list.map { it.copy(apiKey = "") })
    }

    fun addProvider(p: AiProvider) { viewModelScope.launch(Dispatchers.IO) {
        val nl = Store.loadProviders(ctx).toMutableList().apply { add(p) }
        persistProvidersIO(nl)
        val filled = nl.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: "") }
        withContext(Dispatchers.Main) { providers = filled; status = "Добавлен: ${p.name}" }
    } }

    fun addPreset(pr: ProviderPresets.Preset) {
        addProvider(AiProvider(name = pr.name, kind = pr.kind, baseUrl = pr.baseUrl, model = pr.model))
    }

    fun updateProvider(p: AiProvider) { viewModelScope.launch(Dispatchers.IO) {
        val cur = Store.loadProviders(ctx)
        // пустое поле ключа в редакторе = оставить прежний ключ
        val nl = cur.map {
            if (it.id == p.id) {
                val k = if (p.apiKey.isEmpty()) (KeyVault.get(ctx, it.id) ?: it.apiKey) else p.apiKey
                p.copy(apiKey = k)
            } else it
        }
        persistProvidersIO(nl)
        val filled = nl.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: "") }
        withContext(Dispatchers.Main) { providers = filled }
    } }

    fun deleteProvider(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val nl = Store.loadProviders(ctx).filterNot { it.id == id }
        Store.saveProviders(ctx, nl.map { it.copy(apiKey = "") })
        KeyVault.remove(ctx, id)
        if (activeProviderId == id) Store.setActiveProvider(ctx, "local")
        val filled = nl.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: "") }
        withContext(Dispatchers.Main) {
            providers = filled
            if (activeProviderId == id) activeProviderId = "local"
            status = "Провайдер удалён"
        }
    } }

    fun toggleProvider(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val cur = Store.loadProviders(ctx)
        val nl = cur.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        persistProvidersIO(nl)
        val filled = nl.map { it.copy(apiKey = KeyVault.get(ctx, it.id) ?: "") }
        withContext(Dispatchers.Main) { providers = filled }
    } }

    /** Проверка соединения + список моделей сервера. */
    fun testProvider(id: String) {
        val p = providers.find { it.id == id } ?: return
        if (provBusy) return
        provBusy = true
        provStatus = provStatus + (id to "Проверка…")
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                val models = com.neuropocket.app.engine.RemoteEngine.fetchModels(p)
                val ms = System.currentTimeMillis() - t0
                NpLog.i("net", "test ok " + p.name + " n=" + models.size)
                withContext(Dispatchers.Main) {
                    provModels = provModels + (id to models)
                    provStatus = provStatus + (id to "OK • моделей: ${models.size} • ${ms}мс")
                    provBusy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    provStatus = provStatus + (id to "Ошибка: ${e.message?.take(140)}")
                    provBusy = false
                }
            }
        }
    }

    fun refreshNativeState() {
        nativeLoaded = try { LlamaNative.available && LlamaNative.isLoaded() } catch (_: Exception) { false }
        nativeInfo = when {
            !LlamaNative.available -> "native: .so нет (пересобери с NDK)"
            nativeLoaded -> "native: модель в RAM"
            else -> "native: готов, модель не загружена"
        }
    }

    fun gpuSupported(): Boolean = llama.gpuSupported()

    fun diagText(): String = buildString {
        // Single version source: BuildConfig.VERSION_NAME (не хардкод "1.17").
        append("NeuroPocket ").append(try { com.neuropocket.app.BuildConfig.VERSION_NAME } catch (_: Exception) { appVersion }).append("\n")
        append("engine=").append(engineLabel()).append("\n")
        append("llamaLoaded=").append(nativeLoaded).append(" whisper=").append(whisperLoaded)
        append(" sd=").append(sdLoaded).append(" vision=").append(visionLoaded)
        append(" embed=").append(embedLoaded).append("\n")
        append("sessions=").append(sessions.size).append(" personas=").append(personas.size)
        append(" posts=").append(posts.size).append(" providers=").append(providers.size).append("\n")
        append("models=").append(modelFiles.size).append(" whisperFiles=").append(whisperFiles.size)
        append(" sdFiles=").append(sdFiles.size).append(" voices=").append(voiceDirs).append("\n")
        append("settings: ctx=").append(ctxSize).append(" thr=").append(threadsEffective())
        append(" gpu=").append(gpuLayers).append(" tok=").append(maxTokens).append("\n")
    }

    fun loadFileToRam(f: File, nCtx: Int = -1) {
        if (busy || agentRunning || sdBusy) { status = "Дождись конца текущей задачи."; return }
        // P0.6: не грузить mmproj/embed как текстовый LLM.
        val role = com.neuropocket.app.core.ModelRoles.classify(f.name)
        if (role == com.neuropocket.app.core.ModelRole.MM_PROJECTOR) {
            status = "Это mmproj — грузи кнопкой «Зрение в RAM», а не как текст."
            return
        }
        if (role == com.neuropocket.app.core.ModelRole.EMBEDDING) {
            status = "Это эмбеддинги — грузи кнопкой «Вектора в RAM»."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
        val ctxN = if (nCtx > 0) nCtx else ctxSize
        withContext(Dispatchers.Main) { status = "Загружаю ${f.name} в RAM… (ctx $ctxN, gpu $gpuLayers)" }
        val rc = try { llama.load(f.absolutePath, ctxN, threadsEffective(), gpuLayers) } catch (e: Exception) { -99 }
        if (rc == 0) loadedTextPath = f.absolutePath
        withContext(Dispatchers.Main) {
            refreshNativeState()
            NpLog.i("llm", "load rc=$rc file=" + f.name)
            status = when (rc) {
                0 -> "Модель в RAM: ${f.name}. Движок: llama.cpp native."
                -99 -> "Native недоступен (нет .so). Нужна сборка с NDK."
                -2 -> "Не открылся файл модели. Проверь GGUF."
                -3 -> "Не хватило RAM/контекста. Попробуй модель меньше или nCtx 1024."
                else -> "Ошибка загрузки native: $rc"
            }
        }
    } }

    fun unloadNative() { viewModelScope.launch(Dispatchers.IO) {
        try { llama.unload() } catch (_: Exception) {}
        loadedTextPath = null
        withContext(Dispatchers.Main) { refreshNativeState(); status = "Модель выгружена из RAM." }
    } }

    fun unloadWhisper() { viewModelScope.launch(Dispatchers.IO) {
        try { WhisperNative.unload() } catch (_: Exception) {}
        loadedWhisperPath = null
        withContext(Dispatchers.Main) { refreshWhisperState() }
    } }

    fun unloadSd() { viewModelScope.launch(Dispatchers.IO) {
        try { com.neuropocket.app.engine.SdNative.unload() } catch (_: Exception) {}
        loadedSdPath = null
        withContext(Dispatchers.Main) { refreshSdState() }
    } }

    fun unloadVision() { viewModelScope.launch(Dispatchers.IO) {
        try { LlamaNative.unloadVision() } catch (_: Exception) {}
        loadedVisionPath = null
        withContext(Dispatchers.Main) { refreshVisionState() }
    } }

    fun unloadEmbed() { viewModelScope.launch(Dispatchers.IO) {
        try { LlamaNative.unloadEmbed() } catch (_: Exception) {}
        loadedEmbedPath = null
        withContext(Dispatchers.Main) { refreshEmbedState() }
    } }

    private fun allIdle(): Boolean =
        !busy && !agentRunning && !sdBusy && !visionBusy && !pBusy &&
            toolBusyId == null && !benchRunning && !ragBusy && !hfRunning

    private fun ggufForActive(): File? {
        val id = activeModelId ?: return null
        val info = (ModelCatalog.models).find { it.id == id } ?: return null
        return modelFiles.find { it.name == info.fileName }
    }

    /** Вход в зону: автозагрузка выбранного, если включена и тихо. */
    fun onEnterScreen(zone: String) {
        if (zone == "chat" && autoloadChat && activeProviderId == "local" && !nativeLoaded && allIdle()) {
            val f = ggufForActive()
            if (f != null) loadFileToRam(f)
            else if (activeModelId == null) status = "Выбери модель: Модели → В RAM (или включится сама)."
            else status = "Файл модели не найден — скачай его в Моделях."
        }
        if (zone == "tools") {
            if (autoloadWhisper && !whisperLoaded && allIdle()) {
                val f = lastWhisperName?.let { n -> whisperFiles.find { it.name == n } }
                    ?: whisperFiles.maxByOrNull { it.length() }
                if (f != null) loadWhisperToRam(f)
            }
            if (autoloadSd && !sdLoaded && allIdle()) {
                val f = lastSdName?.let { n -> sdFiles.find { it.name == n } }
                if (f != null) loadSdToRam(f)
            }
        }
    }

    /** Выход из зоны: выгрузка, если включена и тихо. */
    fun onLeaveScreen(zone: String) {
        if (!autoUnload || !allIdle()) return
        when (zone) {
            "chat" -> if (nativeLoaded) unloadNative()
            "tools" -> {
                if (whisperLoaded) unloadWhisper()
                if (sdLoaded) unloadSd()
            }
        }
    }

    private var lastWhisperName: String? = null
    private var lastSdName: String? = null
    // P0.6: exact loaded paths — runtime знает ЧТО загружено (не по расширению).
    var loadedTextPath: String? = null; private set
    var loadedWhisperPath: String? = null; private set
    var loadedSdPath: String? = null; private set
    var loadedVisionPath: String? = null; private set
    var loadedEmbedPath: String? = null; private set

    fun refreshWhisperState() {
        whisperLoaded = try { WhisperNative.available && WhisperNative.isLoaded() } catch (_: Exception) { false }
        whisperInfo = when {
            !WhisperNative.available -> "whisper: .so нет"
            whisperLoaded -> "whisper: модель в RAM"
            else -> "whisper: готов, модель не загружена"
        }
    }

    fun loadWhisperToRam(f: File) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) { status = "Загружаю whisper ${f.name}…" }
        val rc = try { if (!WhisperNative.available) -99 else WhisperNative.loadModel(f.absolutePath) } catch (e: Exception) { -98 }
        if (rc == 0) { Store.setLastWhisper(ctx, f.name); lastWhisperName = f.name; loadedWhisperPath = f.absolutePath }
        withContext(Dispatchers.Main) {
            refreshWhisperState()
            status = when (rc) {
                0 -> "Whisper в RAM: ${f.name}."
                -99 -> "Нет libnpwhisper.so — пересобери с NDK."
                else -> "Ошибка whisper: $rc"
            }
        }
    } }

    fun transcribeWav(f: File, lang: String = "ru") {
        if (busy || agentRunning) { status = "Дождись конца генерации."; return }
        stopSpeak()
        viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) { status = "Транскрибирую ${f.name}…"; whisperResult = "" }
        // WAV: авто-конверсия в 16 кГц моно. Сжатые форматы — через MediaCodec.
        var wavPath = f.absolutePath
        try {
            val ext = f.extension.lowercase()
            if (ext != "wav") {
                withContext(Dispatchers.Main) { status = "Декодирую $ext…" }
                val mono = com.neuropocket.app.voice.MediaDecode.toMono16k(f)
                if (mono == null) {
                    withContext(Dispatchers.Main) { status = "Не смог декодировать $ext." }
                    return@launch
                }
                if (mono.size > 16000 * 60 * 10) {
                    withContext(Dispatchers.Main) { status = "Файл длиннее 10 минут." }
                    return@launch
                }
                val tmp = File(ctx.cacheDir, "np16k-${System.currentTimeMillis()}.wav")
                com.neuropocket.app.voice.WavUtils.writeMono16k(tmp, mono)
                wavPath = tmp.absolutePath
            } else if (!com.neuropocket.app.voice.WavUtils.isReady16kMono(f.absolutePath)) {
                withContext(Dispatchers.Main) { status = "Конвертирую аудио в 16 кГц моно…" }
                val p = com.neuropocket.app.voice.WavUtils.read(f.absolutePath)
                if (p == null) {
                    withContext(Dispatchers.Main) { status = "Не смог прочитать WAV (нужен PCM 16-bit / float32)." }
                    return@launch
                }
                val mono = com.neuropocket.app.voice.WavUtils.toMono16k(p)
                if (mono.size > 16000 * 60 * 10) {
                    withContext(Dispatchers.Main) { status = "Файл длиннее 10 минут." }
                    return@launch
                }
                val tmp = File(ctx.cacheDir, "np16k-${System.currentTimeMillis()}.wav")
                com.neuropocket.app.voice.WavUtils.writeMono16k(tmp, mono)
                wavPath = tmp.absolutePath
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "Ошибка конверсии: ${e.message}" }
            return@launch
        }
        withContext(Dispatchers.Main) { status = "Распознаю…" }
        val t0 = System.currentTimeMillis()
        val out = try {
            if (!WhisperNative.available) "__ERR:NO_SO"
            else WhisperNative.transcribeDetailed(wavPath, lang, threadsEffective())
        } catch (e: Exception) { "__ERR:${e.message}" }
        withContext(Dispatchers.Main) {
            if (out.startsWith("__ERR")) {
                whisperResult = out
                whisperTimed = emptyList()
            } else {
                val lines = out.lines().mapNotNull { ln ->
                    val parts = ln.split("|", limit = 3)
                    if (parts.size < 3 || parts[2].isBlank()) null
                    else TimedLine(parts[0].toLongOrNull() ?: 0L, parts[1].toLongOrNull() ?: 0L, parts[2].trim())
                }
                whisperTimed = lines
                whisperResult = lines.joinToString("\n") { it.text }.ifBlank { "(пусто)" }
            }
            status = when {
                out.startsWith("__ERR:NO_MODEL") -> "Сначала загрузи whisper-модель в RAM."
                out.startsWith("__ERR:") -> "Ошибка транскрибации: $out"
                else -> "Готово за " + fmtDur(System.currentTimeMillis() - t0) + ". Текст ниже."
            }
        }
    } }

    fun sdEngineDir(): java.io.File = java.io.File(ctx.getExternalFilesDir(null), "sd_engine").apply { mkdirs() }
    fun sdEngineFile(): java.io.File = java.io.File(sdEngineDir(), "libnpsd.so")

    /** Правда ли, что нативный движок SD доступен (встроен или подгружен). */
    fun ensureSdEngine(): Boolean {
        val nat = com.neuropocket.app.engine.SdNative
        if (nat.available) return true
        val f = sdEngineFile()
        if (f.exists() && f.length() > 10 * 1024 * 1024) {
            return try { nat.loadFromFile(f) } catch (_: Exception) { false }
        }
        return false
    }

    fun refreshSdEngineState() {
        sdEngineState = when {
            !com.neuropocket.app.engine.SdNative.available && !sdEngineFile().exists() -> "missing"
            com.neuropocket.app.engine.SdNative.available -> "ok"
            else -> "file"
        }
    }

    fun refreshSdState() {
        try { ensureSdEngine() } catch (_: Exception) { }
        refreshSdEngineState()
        sdLoaded = try { com.neuropocket.app.engine.SdNative.available && com.neuropocket.app.engine.SdNative.isLoaded() } catch (_: Exception) { false }
        sdInfo = when {
            !com.neuropocket.app.engine.SdNative.available -> "sd: .so нет"
            sdLoaded -> "sd: модель в RAM"
            else -> "sd: готов, модель не загружена"
        }
    }

    fun loadSdToRam(f: File) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        viewModelScope.launch(Dispatchers.IO) {
        if (!ensureSdEngine()) {
            withContext(Dispatchers.Main) { status = "Нет движка SD: скачай его ниже (51 МБ)." }
            return@launch
        }
        withContext(Dispatchers.Main) { status = "Загружаю SD ${f.name}… (долго, файл большой)" }
        val taesd = taesdFiles.firstOrNull()?.absolutePath ?: ""
        val rc = try {
            if (!com.neuropocket.app.engine.SdNative.available) -99
            else com.neuropocket.app.engine.SdNative.loadModel(f.absolutePath, "", taesd, threadsEffective())
        } catch (e: Exception) { -98 }
        if (rc == 0) { Store.setLastSd(ctx, f.name); lastSdName = f.name; loadedSdPath = f.absolutePath }
        withContext(Dispatchers.Main) {
            refreshSdState()
            status = when (rc) {
                0 -> "SD в RAM: ${f.name}. Можно генерировать 512px."
                -99 -> "Нет libnpsd.so — пересобери с NDK."
                else -> "Ошибка SD: $rc (нужно 4+ ГБ свободно)"
            }
        }
    } }

    private suspend fun failSd(msg: String) {
        withContext(Dispatchers.Main) { sdBusy = false; status = msg }
    }

    fun renderPhoto(
        prompt: String, neg: String, size: Int = 512, steps: Int = 6,
        cfg: Float = 1.0f, sampler: String = "lcm", seed: Long = 0L,
        initFile: java.io.File? = null, strength: Float = 0.6f, hires: Boolean = false
    ) {
        if (prompt.isBlank() || sdBusy || busy || agentRunning) return
        stopSpeak()
        sdBusy = true
        val w = size.coerceIn(256, 768); val h = size.coerceIn(256, 768)
        status = "Генерирую ${w}px… на CPU это минуты. Не сворачивай."
        viewModelScope.launch(Dispatchers.IO) {
            val useSeed = if (seed == 0L) System.currentTimeMillis() else seed
            val st0 = System.currentTimeMillis()
            val rgb = try {
                if (initFile != null && initFile.exists()) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(initFile.absolutePath, opts)
                    var s = 1
                    while (maxOf(opts.outWidth, opts.outHeight) / (s * 2) >= 768) s *= 2
                    val bmp = android.graphics.BitmapFactory.decodeFile(
                        initFile.absolutePath,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
                    ) ?: return@launch failSd("Не открылось фото.")
                    val iw = bmp.width; val ih = bmp.height
                    val px = IntArray(iw * ih)
                    bmp.getPixels(px, 0, iw, 0, 0, iw, ih)
                    bmp.recycle()
                    com.neuropocket.app.engine.SdNative.renderImg(
                        prompt, neg, px, iw, ih, w, h, steps, cfg, useSeed, sampler, strength, hires)
                } else {
                    com.neuropocket.app.engine.SdNative.render(prompt, neg, w, h, steps, cfg, useSeed, sampler, hires)
                }
            } catch (e: Exception) { null }
            if (rgb == null) {
                withContext(Dispatchers.Main) { sdBusy = false; status = "SD не смог: нет модели в RAM или ошибка." }
                return@launch
            }
            try {
                val px = IntArray(w * h) { i ->
                    val r = rgb[i * 3].toInt() and 0xFF
                    val g = rgb[i * 3 + 1].toInt() and 0xFF
                    val b = rgb[i * 3 + 2].toInt() and 0xFF
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                val bmp = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val dir = File(ctx.getExternalFilesDir(null), "pictures").apply { mkdirs() }
                val out = File(dir, "sd-${useSeed}.png")
                out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                withContext(Dispatchers.Main) { sdBusy = false; refreshGallery(); status = "Готово: ${out.name} (" + fmtDur(System.currentTimeMillis() - st0) + ")" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { sdBusy = false; status = "Ошибка сохранения PNG: ${e.message}" }
            }
        }
    }

    fun cancelSd() { try { com.neuropocket.app.engine.SdNative.cancel() } catch (_: Exception) {} }

    fun downloadSdEngine(url: String) {
        if (url.isBlank()) return
        try {
            downloads.values.find { it.fileName == "libnpsd.so" && !it.done }?.let { return }
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("libnpsd.so")
                setDescription("NeuroPocket: движок фото")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(ctx, "models", "libnpsd.so")
                setAllowedOverMetered(!wifiOnly); setAllowedOverRoaming(false)
            }
            val id = dm.enqueue(req)
            downloads = downloads + (id to DlInfo("libnpsd.so", "В очереди…", 0f, false, false))
            status = "Качаю движок SD (51 МБ)…"
            startDlPoll()
        } catch (e: Exception) { status = "Ошибка загрузки: ${e.message}" }
    }

    /** URL движка из последнего релиза GitHub. null = не найден/репо приватно. */
    suspend fun fetchSdEngineUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/zezarv/NeuroPocket/releases/latest")
                .header("Accept", "application/vnd.github+json").get().build()
            com.neuropocket.app.engine.NetHttp.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val js = org.json.JSONObject(resp.body?.string() ?: "")
                val arr = js.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name").startsWith("libnpsd")) {
                        return@withContext o.optString("browser_download_url").ifBlank { null }
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    // ---------- Обновления приложения ----------
    fun checkUpdates() {
        if (updateBusy) return
        updateBusy = true
        updateInfo = null
        updateUrl = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/zezarv/NeuroPocket/releases/latest")
                    .header("Accept", "application/vnd.github+json").get().build()
                com.neuropocket.app.engine.NetHttp.client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) throw Exception("релиз не найден (репо приватный?)")
                    if (!resp.isSuccessful) throw Exception("HTTP " + resp.code)
                    val js = org.json.JSONObject(resp.body?.string() ?: "")
                    val tag = js.optString("tag_name", "").trim()
                    val body = js.optString("body", "").take(600)
                    var apkUrl: String? = null
                    val arr = js.optJSONArray("assets")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("name").endsWith(".apk")) { apkUrl = o.optString("browser_download_url"); break }
                    }
                    val cur = try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                    } catch (_: Exception) { "" }
                    // P0.8: semantic comparison вместо equality строк.
                    // "1.24.0-plan5" vs "v1.24.0" корректно сравниваются.
                    val cmp = try { com.neuropocket.app.core.SemVer.compare(tag, cur) } catch (_: Exception) { 0 }
                    withContext(Dispatchers.Main) {
                        if (tag.isBlank() || apkUrl.isNullOrBlank()) {
                            updateInfo = "В релизе нет APK."
                        } else if (cmp == 0) {
                            updateInfo = "У тебя свежая версия ($cur)."
                        } else if (cmp < 0) {
                            updateInfo = "У тебя новее ($cur), чем в релизе ($tag). Обновление не нужно."
                        } else {
                            updateInfo = "Доступно $tag (у тебя $cur).\n$body"
                            updateUrl = apkUrl
                        }
                        updateBusy = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateInfo = "Не вышло проверить: ${e.message?.take(140)}"
                    updateBusy = false
                }
            }
        }
    }

    fun downloadUpdate() {
        val url = updateUrl ?: return
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("NeuroPocket-update.apk")
                setDescription("NeuroPocket: обновление")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(ctx, "updates", "NeuroPocket-update.apk")
                setAllowedOverMetered(!wifiOnly); setAllowedOverRoaming(false)
            }
            val id = dm.enqueue(req)
            downloads = downloads + (id to DlInfo("NeuroPocket-update.apk", "В очереди…", 0f, false, false))
            status = "Качаю обновление…"
            startDlPoll()
        } catch (e: Exception) { status = "Ошибка загрузки: ${e.message}" }
    }

    fun promptInstallUpdate() {
        try {
            val f = java.io.File(ctx.getExternalFilesDir(null), "updates/NeuroPocket-update.apk")
            if (!f.exists()) { status = "Файл обновления не найден."; return }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, ctx.packageName + ".fileprovider", f)
            val it = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(it)
        } catch (e: Exception) {
            status = "Не открылось: разреши «установку из неизвестных источников»."
        }
    }

    fun refreshVisionState() {
        visionLoaded = try { LlamaNative.available && LlamaNative.isVisionLoaded() } catch (_: Exception) { false }
        visionInfo = when {
            !LlamaNative.available -> "зрение: .so нет"
            visionLoaded -> "зрение: mmproj в RAM"
            else -> "зрение: нужен vision-GGUF в RAM + mmproj"
        }
    }

    fun loadVisionToRam(f: File) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { status = "Загружаю зрение ${f.name}…" }
            val rc = try {
                if (!LlamaNative.available) -99 else LlamaNative.loadVision(f.absolutePath, threadsEffective())
            } catch (e: Exception) { -98 }
            if (rc == 0) loadedVisionPath = f.absolutePath
            withContext(Dispatchers.Main) {
                refreshVisionState()
                status = when (rc) {
                    0 -> "Зрение в RAM. Спроси про фото ниже."
                    -99 -> "Нет libneuropocket.so."
                    -2 -> "Сначала загрузи vision-GGUF (Qwen2-VL) кнопкой «В RAM»."
                    -4 -> "mmproj не подошёл к модели."
                    else -> "Ошибка зрения: $rc"
                }
            }
        }
    }

    fun describePhoto(f: File, prompt: String) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        stopSpeak()
        visionBusy = true
        visionResult = ""
        status = "Смотрю на фото…"
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                val raw = f.readBytes()
                if (raw.size > 15 * 1024 * 1024) null else raw
            } catch (_: Exception) { null }
            if (bytes == null) {
                withContext(Dispatchers.Main) { visionBusy = false; status = "Не смог прочитать фото (лимит 15 МБ)." }
                return@launch
            }
            val out = try {
                LlamaNative.describeImage(bytes, prompt.ifBlank { "Опиши подробно, что на этом изображении." }, 256, activePersona?.temperature ?: 0.7f)
            } catch (e: Exception) { "__ERR:${e.message}" }
            withContext(Dispatchers.Main) {
                visionBusy = false
                visionResult = out
                status = when {
                    out.startsWith("__ERR:NO_VISION") -> "Нет зрения в RAM: vision-GGUF + mmproj."
                    out.startsWith("__ERR:") -> "Ошибка: $out"
                    else -> "Готово."
                }
            }
        }
    }

    fun runBench() {
        if (benchRunning || !llama.loaded()) return
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        benchRunning = true
        benchResult = ""
        viewModelScope.launch(Dispatchers.IO) {
            val raw = try { LlamaNative.runBench() } catch (e: Exception) { "__ERR:${e.message}" }
            val text = if (raw.startsWith("__ERR")) {
                "Не получилось: $raw"
            } else try {
                val p = raw.trim().split(Regex("\\s+"))
                val ppT = p[0].toDouble(); val ppMs = p[1].toDouble()
                val gT = p[2].toDouble(); val gMs = p[3].toDouble()
                val ppS = if (ppMs > 0) ppT / (ppMs / 1000.0) else 0.0
                val gS = if (gMs > 0) gT / (gMs / 1000.0) else 0.0
                "Промпт: ${ppT.toInt()} т за ${ppMs.toInt()} мс (${"%.1f".format(ppS)} т/с)\n" +
                    "Генерация: ${gT.toInt()} т за ${gMs.toInt()} мс (${"%.1f".format(gS)} т/с)"
            } catch (_: Exception) { "Сырой результат: $raw" }
            withContext(Dispatchers.Main) { benchResult = text; benchRunning = false }
            if (!raw.startsWith("__ERR")) {
                val stamp = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                Store.addBench(ctx, "$stamp • ${activeModelId ?: "?"} • $text".replace("\n", " / "))
                withContext(Dispatchers.Main) { benchHistory = Store.getBenchLog(ctx) }
            }
        }
    }

    fun downloadModel(info: AiModelInfo) {
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(info.url)).apply {
                setTitle(info.fileName)
                setDescription("NeuroPocket: загрузка ${info.name}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(ctx, "models", info.fileName)
                setAllowedOverMetered(!wifiOnly); setAllowedOverRoaming(false)
            }
            val id = dm.enqueue(req)
            downloads = downloads + (id to DlInfo(info.fileName, "В очереди…", 0f, false, false))
            status = "Загрузка ${info.name} началась…"
            startDlPoll()
        } catch (e: Exception) { status = "Ошибка загрузки: ${e.message}" }
    }

    fun scanModels() { refreshModelFiles(); refreshGallery(); status = if (modelFiles.isEmpty()) "GGUF не найдены. Скачай из каталога." else "Найдено моделей: ${modelFiles.size}" }

    /** Удалить файл модели/голоса с диска. P0.6: unload только ИМЕННО этого файла. */
    fun deleteModelFile(f: java.io.File) {
        if (deviceBusy()) { status = "Дождись конца текущей задачи."; return }
        viewModelScope.launch(Dispatchers.IO) {
            val name = f.name
            val path = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
            val role = com.neuropocket.app.core.ModelRoles.classify(name)
            // P0.6: определить, загружен ли ИМЕННО этот файл — до удаления.
            val isLoadedText = loadedTextPath != null && java.io.File(loadedTextPath!!).let {
                try { it.canonicalPath == path } catch (_: Exception) { it.absolutePath == f.absolutePath }
            }
            val isLoadedWhisper = loadedWhisperPath != null && java.io.File(loadedWhisperPath!!).let {
                try { it.canonicalPath == path } catch (_: Exception) { it.absolutePath == f.absolutePath }
            }
            val isLoadedSd = loadedSdPath != null && java.io.File(loadedSdPath!!).let {
                try { it.canonicalPath == path } catch (_: Exception) { it.absolutePath == f.absolutePath }
            }
            val isLoadedVision = loadedVisionPath != null && java.io.File(loadedVisionPath!!).let {
                try { it.canonicalPath == path } catch (_: Exception) { it.absolutePath == f.absolutePath }
            }
            val isLoadedEmbed = loadedEmbedPath != null && java.io.File(loadedEmbedPath!!).let {
                try { it.canonicalPath == path } catch (_: Exception) { it.absolutePath == f.absolutePath }
            }
            // Корректный unload соответствующего рантайма — до удаления файла.
            // Не ломаем vision/embed при удалении обычного текстового GGUF и наоборот.
            try {
                when (role) {
                    com.neuropocket.app.core.ModelRole.TEXT_LLM,
                    com.neuropocket.app.core.ModelRole.VISION_LLM -> if (isLoadedText) { llama.unload(); loadedTextPath = null }
                    com.neuropocket.app.core.ModelRole.MM_PROJECTOR -> if (isLoadedVision) { LlamaNative.unloadVision(); loadedVisionPath = null }
                    com.neuropocket.app.core.ModelRole.EMBEDDING -> if (isLoadedEmbed) { LlamaNative.unloadEmbed(); loadedEmbedPath = null }
                    com.neuropocket.app.core.ModelRole.WHISPER -> if (isLoadedWhisper) { WhisperNative.unload(); loadedWhisperPath = null }
                    com.neuropocket.app.core.ModelRole.SD -> if (isLoadedSd) { com.neuropocket.app.engine.SdNative.unload(); loadedSdPath = null }
                    com.neuropocket.app.core.ModelRole.TTS, com.neuropocket.app.core.ModelRole.VAD,
                    com.neuropocket.app.core.ModelRole.UNKNOWN -> { /* ниже — общая ветка голосов */ }
                }
            } catch (_: Exception) { }
            try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (_: Exception) { }
            if (activeVoice != null && f.absolutePath.contains("voices")) {
                try { sherpa?.release() } catch (_: Exception) { }
                sherpa = null
                if (loadedTextPath?.contains("voices") == true) loadedTextPath = null
            }
            refreshModelFiles()
            refreshNativeState(); refreshWhisperState(); refreshSdState(); refreshVisionState(); refreshEmbedState()
            // Если runtime считал файл загруженным, но unload не сработал — сбросить флаг.
            if (isLoadedText && !nativeLoaded) loadedTextPath = null
            if (isLoadedWhisper && !whisperLoaded) loadedWhisperPath = null
            if (isLoadedSd && !sdLoaded) loadedSdPath = null
            if (isLoadedVision && !visionLoaded) loadedVisionPath = null
            if (isLoadedEmbed && !embedLoaded) loadedEmbedPath = null
            withContext(Dispatchers.Main) { status = "Удалено: $name" }
        }
    }

    private var resetArmed = false

    /** P0.5: сброс по явному подтверждению диалога (один вызов). */
    fun factoryResetConfirmed() { viewModelScope.launch(Dispatchers.IO) {
        resetArmed = false
        doFactoryReset()
    } }

    /** Сброс всех данных (двухшаговый legacy). Модели на диске не трогает. */
    fun factoryReset() { viewModelScope.launch(Dispatchers.IO) {
        if (!resetArmed) {
            withContext(Dispatchers.Main) {
                resetArmed = true
                status = "Точно сбросить ВСЁ? Нажми ещё раз."
            }
            kotlinx.coroutines.delay(6000)
            resetArmed = false
            return@launch
        }
        resetArmed = false
        doFactoryReset()
    } }

    private suspend fun doFactoryReset() = withContext(Dispatchers.IO) {
        try {
            try { llama.unload() } catch (_: Exception) { }
            try { WhisperNative.unload() } catch (_: Exception) { }
            try { LlamaNative.unloadVision() } catch (_: Exception) { }
            try { LlamaNative.unloadEmbed() } catch (_: Exception) { }
            try { com.neuropocket.app.engine.SdNative.unload() } catch (_: Exception) { }
            try { sherpa?.release() } catch (_: Exception) { }
            sherpa = null
            loadedTextPath = null; loadedWhisperPath = null; loadedSdPath = null
            loadedVisionPath = null; loadedEmbedPath = null
            // P0.5: корректный сброс через DataStore edit{clear()}, не удалением файла.
            Store.clearAll(ctx)
            KeyVault.clear(ctx)
            try { ragIndexFile().delete() } catch (_: Exception) { }
        } catch (_: Exception) { }
        reload()
        // P0.5: честно — модели/картинки/заметки/аватары на диске остаются.
        withContext(Dispatchers.Main) { status = "Настройки, чаты и лента сброшены. Модели, заметки, картинки и голоса на диске сохранены." }
    }

    var backupMsg by mutableStateOf(""); private set
    var sdEngineState by mutableStateOf(""); private set // builtin | file | missing | error
    var updateInfo by mutableStateOf<String?>(null); private set
    var updateUrl by mutableStateOf<String?>(null); private set
    var updateBusy by mutableStateOf(false); private set
    var sdEngineUrl by mutableStateOf<String?>(null); private set
    var sdEngineBusy by mutableStateOf(false); private set

    fun resolveSdEngineUrl() {
        if (sdEngineBusy) return
        sdEngineBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val u = fetchSdEngineUrl()
            withContext(Dispatchers.Main) {
                sdEngineUrl = u
                sdEngineBusy = false
                if (u == null) status = "Движок не найден в релизах (нужен публичный репо)."
            }
        }
    }
    var chatDraft by mutableStateOf<String?>(null); private set
    var pendingVision by mutableStateOf<File?>(null); private set
    var shareTarget by mutableStateOf<String?>(null); private set

    fun setDraft(t: String) { chatDraft = t }
    fun clearDraft() { chatDraft = null }
    fun consumeVision(): File? {
        val f = pendingVision
        pendingVision = null
        return f
    }
    fun fireRoute(route: String) { shareTarget = route }

    fun consumeShareTarget(): String? {
        val s = shareTarget
        shareTarget = null
        return s
    }

    /** Входящий шаринг текста из других приложений. */
    fun handleSharedText(t: String) {
        if (t.isBlank()) return
        chatDraft = t.take(4000)
        shareTarget = "chat"
        status = "Текст из шаринга — в поле ввода."
    }

    /** Входящий шаринг картинки: копия в pictures + открыть фото-вопрос. */
    fun handleSharedImage(uri: android.net.Uri) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val out = File(ctx.getExternalFilesDir(null), "pictures/shared-${System.currentTimeMillis()}.jpg")
            out.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            withContext(Dispatchers.Main) {
                pendingVision = out
                shareTarget = "tools:vision"
                refreshGallery()
                status = "Фото из шаринга — задай вопрос."
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "Не вышло принять фото: ${e.message?.take(100)}" }
        }
    } }

    fun makeBackup(withKeys: Boolean, password: String) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val dump = Store.dumpData(ctx)
            val settings: Map<String, Any> = mapOf(
                "theme" to theme, "accent" to accent,
                "maxTokens" to maxTokens, "topP" to topP, "topK" to topK,
                "ctxSize" to ctxSize, "threads" to threads, "gpuLayers" to gpuLayers,
                "textScale" to textScale, "ttsRate" to ttsRate, "ttsPitch" to ttsPitch,
                "keepOn" to keepScreenOn, "wifiOnly" to wifiOnly,
                "activeProvider" to activeProviderId,
                "activePersona" to (Store.getActivePersona(ctx) ?: ""),
                "activeModel" to (activeModelId ?: ""),
                "activeSession" to (activeSessionId ?: "")
            )
            val f = Backup.make(
                ctx, dump["personas"] ?: "[]", dump["sessions"] ?: "[]", dump["msgmap"] ?: "{}",
                dump["chars"] ?: "[]", dump["posts"] ?: "[]", dump["comments"] ?: "[]",
                dump["providers"] ?: "[]",
                settings, withKeys, password,
                fullPassword = if (withKeys && password.length >= 4) password else ""
            )
            NpLog.i("backup", "saved " + f.name)
            withContext(Dispatchers.Main) {
                backupMsg = "Сохранено: ${f.name}"; status = "Бэкап готов: ${f.name}"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { backupMsg = "Ошибка: ${e.message?.take(140)}" }
        }
    } }

    fun restoreBackup(uri: android.net.Uri, password: String) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw Exception("не открылся файл")
            val p = Backup.parse(text, password)
            Store.restoreData(ctx, mapOf(
                "personas" to p.personas, "sessions" to p.sessions, "msgmap" to p.msgmap,
                "chars" to p.chars, "posts" to p.posts, "comments" to p.comments,
                "providers" to p.providers
            ))
            p.keys.forEach { (id, k) -> KeyVault.put(ctx, id, k) }
            val s = p.settings
            s["theme"]?.let { Store.setTheme(ctx, it) }
            s["accent"]?.let { Store.setAccent(ctx, it) }
            s["maxTokens"]?.toIntOrNull()?.let { Store.setMaxTokens(ctx, it) }
            s["topP"]?.toFloatOrNull()?.let { Store.setTopP(ctx, it) }
            s["topK"]?.toIntOrNull()?.let { Store.setTopK(ctx, it) }
            s["ctxSize"]?.toIntOrNull()?.let { Store.setCtxSize(ctx, it) }
            s["threads"]?.toIntOrNull()?.let { Store.setThreads(ctx, it) }
            s["gpuLayers"]?.toIntOrNull()?.let { Store.setGpuLayers(ctx, it) }
            s["textScale"]?.toFloatOrNull()?.let { Store.setTextScale(ctx, it) }
            s["ttsRate"]?.toFloatOrNull()?.let { Store.setTtsRate(ctx, it) }
            s["ttsPitch"]?.toFloatOrNull()?.let { Store.setTtsPitch(ctx, it) }
            s["keepOn"]?.toBooleanStrictOrNull()?.let { Store.setKeepOn(ctx, it) }
            s["wifiOnly"]?.toBooleanStrictOrNull()?.let { Store.setWifiOnly(ctx, it) }
            s["activeProvider"]?.let { Store.setActiveProvider(ctx, it) }
            s["activePersona"]?.let { if (it.isNotEmpty()) Store.setActivePersona(ctx, it) }
            s["activeModel"]?.let { if (it.isNotEmpty()) Store.setActiveModel(ctx, it) }
            s["activeSession"]?.let { if (it.isNotEmpty()) Store.setActiveSession(ctx, it) }
            reload()
            withContext(Dispatchers.Main) { backupMsg = "Восстановлено. Ключей: ${p.keys.size}"; status = "Бэкап восстановлен." }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { backupMsg = "Ошибка: ${e.message?.take(160)}" }
        }
    } }

    fun toggleLike(id: String) { viewModelScope.launch(Dispatchers.IO) {
        val nl = posts.map { p ->
            if (p.id == id) p.copy(liked = !p.liked, likes = (p.likes + if (p.liked) -1 else 1).coerceAtLeast(0))
            else p
        }
        Store.savePosts(ctx, nl)
        withContext(Dispatchers.Main) { posts = nl }
    } }

    fun clearPosts() { viewModelScope.launch(Dispatchers.IO) {
        Store.savePosts(ctx, emptyList())
        withContext(Dispatchers.Main) { posts = emptyList() }
    } }
}
