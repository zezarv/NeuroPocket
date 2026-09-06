package com.neuropocket.app.core

import java.net.URI

/**
 * Red-team L: редиректы user-configured endpoint проверяются той же
 * NetworkPolicy, что и исходный URL. LAN http -> public http запрещён.
 * Обычные HTTPS-редиректы облаков не затрагиваются (https всегда allowed).
 */
object RedirectPolicy {
    sealed interface Decision {
        data class Allow(val url: String) : Decision
        data class Block(val reason: String) : Decision
    }

    /**
     * @param baseUrl исходный URL запроса (уже проверен NetworkPolicy)
     * @param location значение заголовка Location (абсолютное или относительное)
     */
    fun check(baseUrl: String, location: String?): Decision {
        if (location.isNullOrBlank()) return Decision.Block("пустой redirect Location")
        val target = try {
            URI(baseUrl).resolve(location.trim()).toString()
        } catch (_: Exception) {
            return Decision.Block("битый redirect Location")
        }
        return if (NetworkPolicy.isUrlAllowed(target)) Decision.Allow(target)
        else Decision.Block(NetworkPolicy.blockedReason(target))
    }

    /** Коды, которые RemoteEngine обрабатывает вручную (авто-follow выключен). */
    fun isRedirect(code: Int): Boolean =
        code == 301 || code == 302 || code == 303 || code == 307 || code == 308
}
