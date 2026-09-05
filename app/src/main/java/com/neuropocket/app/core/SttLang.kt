package com.neuropocket.app.core

/** STT language: persisted setting, одна для Whisper и hands-free. P0.3. */
object SttLang {
    const val RU = "ru"
    const val EN = "en"
    const val AUTO = "auto"

    val ALLOWED = setOf(RU, EN, AUTO)

    /** Нормализация сырого значения (из DataStore / UI / бэкапа). */
    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase() ?: return RU
        return when (v) {
            RU, EN, AUTO -> v
            // whisper.cpp понимает "auto" как автовыбор; пустое/битое -> ru
            "" -> RU
            else -> RU
        }
    }

    /** Язык для whisper native: "auto" отдаём как есть, остальное как есть. */
    fun forWhisper(persisted: String): String = normalize(persisted)
}
