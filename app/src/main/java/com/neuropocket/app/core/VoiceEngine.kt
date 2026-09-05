package com.neuropocket.app.core

/**
 * Состояния voice engine для однозначного UI. P0.2.
 * Было: voiceEngineState = "" на старте, refresh нигде не вызывался при reload,
 * UI показывал "движок нужен", но кнопки нет; размер врал (25 МБ вместо ~9.6).
 */
enum class VoiceEngineState {
    MISSING,      // ничего нет -> кнопка Download
    DOWNLOADING,  // идёт DownloadManager -> progress
    VERIFYING,    // проверка size/sha после скачивания
    INSTALLING,   // распаковка/атомарная установка
    READY,        // .so загружается (ok)
    FILE_READY,   // файлы есть, но load ещё не проверен
    ERROR         // понятная ошибка + Retry
}

object VoiceEngine {
    /** Реальный размер voice-engine-arm64.zip релиза v1.24 ~9.6 MB. */
    const val EXPECTED_SIZE_MB = 9.6
    const val SIZE_LABEL = "~10 МБ"
    const val ARCHIVE_NAME = "voice-engine-arm64.zip"

    /** Маппинг легаси-строк ("", ok/file/missing) в enum. */
    fun fromLegacy(raw: String?): VoiceEngineState = when (raw?.trim()?.lowercase()) {
        "ok", "ready" -> VoiceEngineState.READY
        "file", "file_ready" -> VoiceEngineState.FILE_READY
        "missing", "", null -> VoiceEngineState.MISSING
        "downloading" -> VoiceEngineState.DOWNLOADING
        "verifying" -> VoiceEngineState.VERIFYING
        "installing" -> VoiceEngineState.INSTALLING
        "error", "failed" -> VoiceEngineState.ERROR
        else -> VoiceEngineState.MISSING
    }

    fun statusRu(s: VoiceEngineState): String = when (s) {
        VoiceEngineState.MISSING -> "нужно скачать ($SIZE_LABEL)"
        VoiceEngineState.DOWNLOADING -> "скачиваю…"
        VoiceEngineState.VERIFYING -> "проверяю…"
        VoiceEngineState.INSTALLING -> "устанавливаю…"
        VoiceEngineState.READY -> "готов"
        VoiceEngineState.FILE_READY -> "файл есть"
        VoiceEngineState.ERROR -> "ошибка"
    }
}
