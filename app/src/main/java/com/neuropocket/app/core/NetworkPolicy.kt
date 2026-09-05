package com.neuropocket.app.core

import java.net.URI

/**
 * P0.10: cleartext (http://) разрешён ТОЛЬКО для локальной сети.
 * OS-уровень (Network Security Config) не умеет CIDR для произвольных LAN-IP,
 * поэтому дополнительно стоит app-layer guard: публичные http:// блокируем в коде.
 * Все облака/GitHub/HF используют https и не затрагиваются.
 */
object NetworkPolicy {
    /** Приватные хосты: localhost, RFC1918, link-local, .local/.lan/.home/.internal */
    fun isPrivateHost(rawHost: String?): Boolean {
        val h = rawHost?.trim()?.lowercase()?.trim('[', ']') ?: return false
        if (h.isEmpty()) return false
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0.0.0.0") return true
        if (h == "10.0.2.2") return true // Android emulator -> host
        if (h.endsWith(".local") || h.endsWith(".localhost") || h.endsWith(".lan") ||
            h.endsWith(".home") || h.endsWith(".internal") || h.endsWith(".localdomain")
        ) return true
        // IPv4
        val v4 = h.split('.')
        if (v4.size == 4 && v4.all { it.toIntOrNull() in 0..255 }) {
            val a = v4[0].toInt()
            val b = v4[1].toInt()
            if (a == 10) return true
            if (a == 172 && b in 16..31) return true
            if (a == 192 && b == 168) return true
            if (a == 127) return true
            if (a == 169 && b == 254) return true // link-local
            return false
        }
        // IPv6 ULA/link-local/loopback
        if (':' in h) {
            if (h == "::1") return true
            if (h.startsWith("fc") || h.startsWith("fd")) return true
            if (h.startsWith("fe80")) return true
            return false
        }
        // обычные DNS-имена считаем публичными (https обязателен)
        return false
    }

    /** true если URL можно открывать: https всегда, http только для приватных хостов. */
    fun isUrlAllowed(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        return try {
            val u = URI(rawUrl.trim())
            when (u.scheme?.lowercase()) {
                "https" -> true
                "http" -> isPrivateHost(u.host)
                else -> false
            }
        } catch (_: Exception) { false }
    }

    /** Человекочитаемая причина блокировки (для UI). */
    fun blockedReason(rawUrl: String?): String =
        "HTTP разрешён только для локальной сети (127.0.0.1, 10.x, 172.16-31.x, 192.168.x, .local). " +
            "Для публичных серверов используй https. URL: ${(rawUrl ?: "").take(120)}"
}
