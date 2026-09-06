package com.neuropocket.app.core

/**
 * Red-team A: политика legacy-migration для executable native code.
 * Size-only verification исполняемого кода ЗАПРЕЩЕНА.
 *
 * - SD legacy .so: копировать ТОЛЬКО при совпадении trusted SHA-256
 *   (AssetManifest.SD_ENGINE); иначе quarantine (удалить) + MISSING/ERROR.
 * - Voice legacy extracted .so: НЕ доверяем вообще (PREFERRED вариант) —
 *   всегда quarantine + trusted redownload pinned ZIP.
 *   (Даже exact-size extracted .so не доказывает происхождение: хэши
 *   есть только для ZIP, а не для отдельных извлечённых файлов.)
 */
object NativeInstall {
    enum class LegacyDecision { COPY_TRUSTED, QUARANTINE }

    /**
     * @param legacyShaLower вычисленный SHA-256 legacy-файла (lowercase hex) или null
     */
    fun decideLegacySd(legacyExists: Boolean, legacyShaLower: String?): LegacyDecision {
        if (!legacyExists) return LegacyDecision.QUARANTINE
        return if (legacyShaLower?.lowercase() == AssetManifest.SD_ENGINE.sha256Hex.lowercase()) {
            LegacyDecision.COPY_TRUSTED
        } else {
            LegacyDecision.QUARANTINE
        }
    }

    /** Voice legacy всегда quarantine (см. kdoc): redownload pinned ZIP. */
    fun decideLegacyVoice(): LegacyDecision = LegacyDecision.QUARANTINE
}
