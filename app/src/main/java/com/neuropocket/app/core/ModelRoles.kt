package com.neuropocket.app.core

/**
 * Явные роли файлов моделей. P0.6.
 * Нельзя классифицировать все .gguf как один тип и нельзя грузить
 * mmproj/embed как текстовый LLM.
 */
enum class ModelRole {
    TEXT_LLM,
    VISION_LLM,
    MM_PROJECTOR,
    EMBEDDING,
    WHISPER,
    SD,
    TTS,
    VAD,
    UNKNOWN
}

object ModelRoles {
    /** Классификация по имени файла (расширение + ключевые подстроки). */
    fun classify(fileName: String): ModelRole {
        val n = fileName.lowercase()
        // порядок важен: специфичные совпадения раньше общих
        if (n.endsWith(".onnx") && ("vad" in n || "silero" in n)) return ModelRole.VAD
        if (n.endsWith(".onnx")) return ModelRole.VAD
        if ("mmproj" in n && n.endsWith(".gguf")) return ModelRole.MM_PROJECTOR
        if (n.endsWith(".bin") && ("ggml" in n || "whisper" in n || n.startsWith("ggml-"))) return ModelRole.WHISPER
        if (n.endsWith(".safetensors") || n.endsWith(".ckpt")) {
            if ("taesd" in n) return ModelRole.SD // tiny decoder — тоже SD-рантайм
            return ModelRole.SD
        }
        if (n.endsWith(".tar.bz2") || n.endsWith(".tar.gz") || ("piper" in n) || ("vits" in n)) return ModelRole.TTS
        if (n.endsWith(".gguf")) {
            if ("e5" in n || "embed" in n || "bge" in n || "nomic" in n) return ModelRole.EMBEDDING
            // vision-текстовая часть: содержит vl/vision/llava/qwen2-vl и НЕ mmproj
            if ("qwen2-vl" in n || "qwen2_vl" in n || "llava" in n || "vision" in n || "-vl-" in n) return ModelRole.VISION_LLM
            return ModelRole.TEXT_LLM
        }
        return ModelRole.UNKNOWN
    }

    fun labelRu(role: ModelRole): String = when (role) {
        ModelRole.TEXT_LLM -> "текстовая LLM"
        ModelRole.VISION_LLM -> "vision LLM (текстовая часть)"
        ModelRole.MM_PROJECTOR -> "mmproj (зрение)"
        ModelRole.EMBEDDING -> "эмбеддинги (RAG)"
        ModelRole.WHISPER -> "whisper (STT)"
        ModelRole.SD -> "SD (картинки)"
        ModelRole.TTS -> "голос (TTS)"
        ModelRole.VAD -> "VAD"
        ModelRole.UNKNOWN -> "файл"
    }

    /**
     * Red-team F: filename heuristic — НЕ абсолютная истина.
     * Classification с уверенностью: generic "model.gguf" без маркеров
     * классифицируется как TEXT_LLM, но НЕ уверенно (ambiguous).
     * Опасное направление (mmproj/embed -> запуск как текст) блокируется
     * в loadFileToRam по уверенным маркерам; переименованный mmproj без
     * маркеров даст лишь ошибку native-загрузки, а не порчу состояния.
     */
    data class Classification(val role: ModelRole, val confident: Boolean)

    fun classifyC(fileName: String): Classification {
        val n = fileName.lowercase()
        // уверенные маркеры
        if (n.endsWith(".onnx")) return Classification(ModelRole.VAD, true)
        if ("mmproj" in n && n.endsWith(".gguf")) return Classification(ModelRole.MM_PROJECTOR, true)
        if (n.endsWith(".bin") && ("ggml" in n || "whisper" in n)) return Classification(ModelRole.WHISPER, true)
        if (n.endsWith(".safetensors") || n.endsWith(".ckpt")) return Classification(ModelRole.SD, true)
        if (n.endsWith(".tar.bz2") || n.endsWith(".tar.gz") || "piper" in n || "vits" in n) {
            return Classification(ModelRole.TTS, true)
        }
        if (n.endsWith(".gguf")) {
            if ("e5" in n || "embed" in n || "bge" in n || "nomic" in n) return Classification(ModelRole.EMBEDDING, true)
            if ("qwen2-vl" in n || "qwen2_vl" in n || "llava" in n || "vision" in n || "-vl-" in n) {
                return Classification(ModelRole.VISION_LLM, true)
            }
            // generic GGUF (напр. импортированный "model.gguf"): тип НЕ проверен
            return Classification(ModelRole.TEXT_LLM, false)
        }
        return Classification(ModelRole.UNKNOWN, true)
    }

    /** true если имя не содержит маркеров типа (импортированный/переименованный файл). */
    fun isAmbiguous(fileName: String): Boolean {
        val c = classifyC(fileName)
        return c.role == ModelRole.TEXT_LLM && !c.confident
    }

    /** Человекочитаемое описание файла для диалогов (тип + честность про эвристику). */
    fun describeFile(fileName: String): String {
        val c = classifyC(fileName)
        return if (c.confident) labelRu(c.role) else labelRu(c.role) + " (по имени, не проверено)"
    }

    /**
     * Authoritative mapping каталога (ModelCatalog kind -> role).
     * Каталог — источник истины для своих записей; эвристика — только для
     * импортированных пользователем файлов.
     */
    fun roleForCatalogKind(kind: String): ModelRole = when (kind.trim().lowercase()) {
        "text" -> ModelRole.TEXT_LLM
        "vision-text" -> ModelRole.VISION_LLM
        "mmproj" -> ModelRole.MM_PROJECTOR
        "embed" -> ModelRole.EMBEDDING
        "whisper" -> ModelRole.WHISPER
        "image", "taesd" -> ModelRole.SD
        "voice" -> ModelRole.TTS
        "vad" -> ModelRole.VAD
        else -> ModelRole.UNKNOWN
    }

    /** Какой рантайм отвечает за роль (для точного unload при удалении). */
    fun runtimeFor(role: ModelRole): String = when (role) {
        ModelRole.TEXT_LLM, ModelRole.VISION_LLM -> "llama-text"
        ModelRole.MM_PROJECTOR -> "llama-vision"
        ModelRole.EMBEDDING -> "llama-embed"
        ModelRole.WHISPER -> "whisper"
        ModelRole.SD -> "sd"
        ModelRole.TTS -> "tts"
        ModelRole.VAD -> "vad"
        ModelRole.UNKNOWN -> "none"
    }
}
