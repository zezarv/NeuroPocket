package com.neuropocket.app.core

/**
 * Чистая политика обновлений (red-team D, тестируемая часть).
 * Android-зависимая часть (PackageManager: packageName/versionCode/signature)
 * живёт в AppViewModel.verifyUpdateApk(); здесь только сравнение версий.
 */
object UpdatePolicy {
    enum class Decision { UP_TO_DATE, AVAILABLE, NEWER_LOCAL, UNKNOWN }

    /** Решение по тегам: latest (релиз) vs current (установлено). */
    fun decide(latestTag: String?, current: String?): Decision {
        if (latestTag.isNullOrBlank() || current.isNullOrBlank()) return Decision.UNKNOWN
        // Red-team G: malformed — UNKNOWN, а не молчаливое "разумное" сравнение.
        if (!SemVer.isValid(latestTag) || !SemVer.isValid(current)) return Decision.UNKNOWN
        return try {
            when {
                SemVer.compare(latestTag, current) > 0 -> Decision.AVAILABLE
                SemVer.compare(latestTag, current) < 0 -> Decision.NEWER_LOCAL
                else -> Decision.UP_TO_DATE
            }
        } catch (_: Exception) {
            Decision.UNKNOWN
        }
    }

    /** true если архив можно предлагать: строго новее, не downgrade/переустановка. */
    fun shouldOffer(latestTag: String?, current: String?): Boolean =
        decide(latestTag, current) == Decision.AVAILABLE
}
