package com.neuropocket.app.core

/**
 * Состояния voice engine — ЕДИНСТВЕННЫЙ источник истины для UI (red-team C/D).
 * Никаких magic strings в VM/UI: AppViewModel хранит этот enum.
 *
 * MISSING — ничего нет, доступна загрузка.
 * DOWNLOADING — DownloadManager активен (прогресс через DlRow).
 * VERIFYING — проверка size/SHA-256 против AssetManifest.
 * INSTALLING — распаковка/атомарная установка в internal storage.
 * READY — .so загружается через System.load из internal storage.
 * ERROR — понятная ошибка + Retry/Repair (перекачать/удалить движок).
 *
 * Тупикового FILE_READY нет: файлы есть, но load неуспешен => ERROR
 * (D: files exist + System.load failed — не dead end, а ремонт).
 */
enum class VoiceEngineState {
    MISSING,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    READY,
    ERROR
}

/** События жизненного цикла native-движка (voice и SD). */
enum class EngineEvent {
    START_DOWNLOAD,
    DOWNLOAD_OK,
    DOWNLOAD_FAILED,
    DOWNLOAD_CANCELLED,
    DOWNLOAD_DISMISSED,
    VERIFY_OK,
    VERIFY_FAIL,
    INSTALL_OK,
    INSTALL_FAIL,
    LOAD_OK,
    LOAD_FAIL,
    FILES_ABSENT,
    RETRY
}

object VoiceEngine {
    /** Реальный размер voice-engine-arm64.zip релиза v1.24 ~9.6 MB. */
    const val EXPECTED_SIZE_MB = 9.6
    const val SIZE_LABEL = "~10 МБ"
    const val ARCHIVE_NAME = "voice-engine-arm64.zip"

    /**
     * Чистый переход состояний (red-team C: terminal semantics).
     * FAILED во время DOWNLOADING -> ERROR (не вечное "скачиваю…").
     * CANCEL/DISMISS -> MISSING (можно скачать снова), кроме ERROR,
     * который остаётся до RETRY.
     */
    fun next(state: VoiceEngineState, event: EngineEvent): VoiceEngineState {
        return when (event) {
            EngineEvent.START_DOWNLOAD -> VoiceEngineState.DOWNLOADING
            EngineEvent.DOWNLOAD_OK -> VoiceEngineState.VERIFYING
            EngineEvent.DOWNLOAD_FAILED -> VoiceEngineState.ERROR
            EngineEvent.DOWNLOAD_CANCELLED -> VoiceEngineState.MISSING
            EngineEvent.DOWNLOAD_DISMISSED ->
                if (state == VoiceEngineState.ERROR) VoiceEngineState.ERROR
                else VoiceEngineState.MISSING
            EngineEvent.VERIFY_OK -> VoiceEngineState.INSTALLING
            EngineEvent.VERIFY_FAIL -> VoiceEngineState.ERROR
            EngineEvent.INSTALL_OK ->
                // дальше refresh пробует LOAD; оптимистично остаёмся в INSTALLING,
                // финальный READY/ERROR ставит refreshVoiceEngineState()
                VoiceEngineState.INSTALLING
            EngineEvent.INSTALL_FAIL -> VoiceEngineState.ERROR
            EngineEvent.LOAD_OK -> VoiceEngineState.READY
            EngineEvent.LOAD_FAIL -> VoiceEngineState.ERROR
            EngineEvent.FILES_ABSENT -> VoiceEngineState.MISSING
            EngineEvent.RETRY -> VoiceEngineState.MISSING
        }
    }

    /** Маппинг легаси-строк (до enum-миграции) — для тестов совместимости. */
    fun fromLegacy(raw: String?): VoiceEngineState = when (raw?.trim()?.lowercase()) {
        "ok", "ready" -> VoiceEngineState.READY
        "downloading" -> VoiceEngineState.DOWNLOADING
        "verifying" -> VoiceEngineState.VERIFYING
        "installing" -> VoiceEngineState.INSTALLING
        "error", "failed" -> VoiceEngineState.ERROR
        // "file"/""/unknown: тупикового FILE_READY больше нет — честно MISSING,
        // refresh при наличии файлов сам дойдёт до READY или ERROR
        else -> VoiceEngineState.MISSING
    }

    fun statusRu(s: VoiceEngineState): String = when (s) {
        VoiceEngineState.MISSING -> "нужно скачать ($SIZE_LABEL)"
        VoiceEngineState.DOWNLOADING -> "скачиваю…"
        VoiceEngineState.VERIFYING -> "проверяю (size/SHA-256)…"
        VoiceEngineState.INSTALLING -> "устанавливаю…"
        VoiceEngineState.READY -> "готов"
        VoiceEngineState.ERROR -> "ошибка — можно повторить"
    }
}
