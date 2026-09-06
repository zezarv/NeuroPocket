package com.neuropocket.app.core

/**
 * Состояния SD native engine — тот же контракт, что VoiceEngineState (red-team D).
 * MISSING — нет файла, доступна загрузка. READY — .so грузится из internal.
 * ERROR — файлы есть, но load/verify неуспешен: Repair (перекачать/удалить),
 * а не тупиковое "файл есть" без кнопок.
 */
enum class SdEngineState {
    MISSING,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    READY,
    ERROR
}

object SdEngine {
    const val SIZE_LABEL = "51 МБ"
    const val ASSET_NAME = "libnpsd.so"

    /** Чистый переход состояний (аналог VoiceEngine.next). */
    fun next(state: SdEngineState, event: EngineEvent): SdEngineState {
        return when (event) {
            EngineEvent.START_DOWNLOAD -> SdEngineState.DOWNLOADING
            EngineEvent.DOWNLOAD_OK -> SdEngineState.VERIFYING
            EngineEvent.DOWNLOAD_FAILED -> SdEngineState.ERROR
            EngineEvent.DOWNLOAD_CANCELLED -> SdEngineState.MISSING
            EngineEvent.DOWNLOAD_DISMISSED ->
                if (state == SdEngineState.ERROR) SdEngineState.ERROR
                else SdEngineState.MISSING
            EngineEvent.VERIFY_OK -> SdEngineState.INSTALLING
            EngineEvent.VERIFY_FAIL -> SdEngineState.ERROR
            EngineEvent.INSTALL_OK -> SdEngineState.INSTALLING
            EngineEvent.INSTALL_FAIL -> SdEngineState.ERROR
            EngineEvent.LOAD_OK -> SdEngineState.READY
            EngineEvent.LOAD_FAIL -> SdEngineState.ERROR
            EngineEvent.FILES_ABSENT -> SdEngineState.MISSING
            EngineEvent.RETRY -> SdEngineState.MISSING
        }
    }

    fun statusRu(s: SdEngineState): String = when (s) {
        SdEngineState.MISSING -> "нужно скачать ($SIZE_LABEL)"
        SdEngineState.DOWNLOADING -> "скачиваю…"
        SdEngineState.VERIFYING -> "проверяю (size/SHA-256)…"
        SdEngineState.INSTALLING -> "устанавливаю…"
        SdEngineState.READY -> "встроен/готов"
        SdEngineState.ERROR -> "ошибка — можно повторить"
    }
}
