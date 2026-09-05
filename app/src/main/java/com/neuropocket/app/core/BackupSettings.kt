package com.neuropocket.app.core

import org.json.JSONObject

/**
 * Чистая работа с settings бэкапа. P0.4.
 *
 * Было: Backup.make писал data.put("settings", st.toString()) — т.е. STRING,
 * а parse делал optJSONObject("settings")?.let { JSONObject(it.optString(...)) } —
 * всегда пусто. Настройки терялись при restore.
 *
 * Новый формат: settings как JSONObject. Backward compat: принимаем
 * и старый String(JSON), и новый JSONObject.
 */
object BackupSettings {
    /** Сериализация для нового формата (JSONObject, не String). */
    fun encode(settings: Map<String, Any?>): JSONObject {
        val st = JSONObject()
        settings.forEach { (k, v) ->
            when (v) {
                null -> st.put(k, JSONObject.NULL)
                is Number, is Boolean, is String -> st.put(k, v)
                else -> st.put(k, v.toString())
            }
        }
        return st
    }

    /**
     * Декодирование settings из data-объекта бэкапа.
     * Принимает: отсутствующее поле, JSONObject, String(JSON), String(пусто/битое).
     */
    fun decode(data: JSONObject): Map<String, String> {
        if (!data.has("settings")) return emptyMap()
        val raw = data.opt("settings") ?: return emptyMap()
        val jo: JSONObject? = when (raw) {
            is JSONObject -> raw
            is String -> {
                val t = raw.trim()
                if (t.isEmpty()) null
                else try { JSONObject(t) } catch (_: Exception) { null }
            }
            else -> null
        }
        if (jo == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (k in jo.keys()) {
            if (jo.isNull(k)) continue
            out[k] = jo.opt(k)?.toString() ?: continue
        }
        return out
    }
}
