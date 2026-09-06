package com.neuropocket.app.core

/**
 * Lead-review #2 п.4: terminal semantics загрузок.
 * failed/done id больше НЕ query каждую секунду (пропуск), но failed-строка
 * остаётся в UI для сообщения/dismiss. Status/error выставляются один раз
 * при переходе, а не перезаписываются каждую секунду.
 */
object DlPollPolicy {
    /** Метки engine-файлов, чьё исчезновение из DownloadManager — terminal engine-событие. */
    const val VOICE_LABEL = "voice-engine-arm64.zip"
    const val SD_LABEL = "libnpsd.so"

    /** true если id ещё нужно опрашивать. */
    fun shouldQuery(done: Boolean, failed: Boolean, terminal: Boolean): Boolean =
        !done && !failed && !terminal

    /** Добавить terminal id (чистая операция над множеством). */
    fun markTerminal(current: Set<Long>, id: Long): Set<Long> = current + id

    /** Убрать id из terminal (dismiss/cancel). */
    fun unmarkTerminal(current: Set<Long>, id: Long): Set<Long> = current - id

    /**
     * Исчезновение DownloadManager-row для tracked id = terminal failure.
     * Возвращает engine-событие для voice/sd, иначе null (обычные модели —
     * только failed-строка в UI).
     */
    fun engineEventForMissingRow(fileName: String): EngineEvent? = when (fileName) {
        VOICE_LABEL, SD_LABEL -> EngineEvent.DOWNLOAD_FAILED
        else -> null
    }
}
