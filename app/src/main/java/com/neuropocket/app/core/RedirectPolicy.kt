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

    /**
     * Lead-review #2 п.3: credential policy редиректов. Никогда не несём
     * чужие секреты: cross-origin теряет Authorization/Proxy-Authorization/Cookie.
     */
    val SENSITIVE_HEADERS: Set<String> = setOf("authorization", "proxy-authorization", "cookie")

    fun isSensitiveHeader(name: String): Boolean = name.trim().lowercase() in SENSITIVE_HEADERS

    /** Origin: scheme + host + effective port (lead п.3). */
    fun originOf(rawUrl: String): String? {
        return try {
            val u = URI(rawUrl.trim())
            val scheme = u.scheme?.lowercase() ?: return null
            val host = u.host?.lowercase() ?: return null
            val port = if (u.port == -1) {
                when (scheme) {
                    "https" -> 443
                    "http" -> 80
                    else -> return null
                }
            } else u.port
            "$scheme://$host:$port"
        } catch (_: Exception) { null }
    }

    fun sameOrigin(a: String, b: String): Boolean {
        val oa = originOf(a) ?: return false
        val ob = originOf(b) ?: return false
        return oa == ob
    }

    sealed interface Follow {
        /** Тот же origin — headers сохраняем. */
        data class KeepHeaders(val url: String) : Follow
        /** Чужой origin, безопасный GET — идём БЕЗ sensitive headers. */
        data class Stripped(val url: String) : Follow
        data class Block(val reason: String) : Follow
    }

    /**
     * @param isPost true для POST /chat/completions (body + bearer).
     * Правила:
     * - target не allowed политикой -> Block;
     * - https -> http downgrade -> Block всегда (даже в LAN);
     * - same-origin -> KeepHeaders;
     * - cross-origin POST -> Block (PREFERRED: "configure direct API URL");
     * - cross-origin GET -> Stripped (идём без Authorization/Cookie).
     * API-ключ никогда не попадает в сообщения — только reason без headers.
     */
    fun decideFollow(currentUrl: String, location: String?, isPost: Boolean): Follow {
        val target = when (val d = check(currentUrl, location)) {
            is Decision.Block -> return Follow.Block(d.reason)
            is Decision.Allow -> d.url
        }
        val fromScheme = try { URI(currentUrl).scheme?.lowercase() } catch (_: Exception) { null }
        val toScheme = try { URI(target).scheme?.lowercase() } catch (_: Exception) { null }
        if (fromScheme == "https" && toScheme == "http") {
            return Follow.Block("redirect https -> http запрещён: $target".take(160))
        }
        if (sameOrigin(currentUrl, target)) return Follow.KeepHeaders(target)
        if (isPost) {
            return Follow.Block(
                "provider redirected to another host; configure direct API URL"
            )
        }
        return Follow.Stripped(target)
    }
}
