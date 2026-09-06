package com.neuropocket.app.core

/**
 * Red-team F: pinned release contract для executable assets.
 * AssetManifest.releaseTag — source of truth; качаем строго из
 * releases/tags/{releaseTag} с EXACT совпадением имени (не startsWith,
 * не latest), иначе manifest-хэш вечно бы расходился с новым asset.
 */
object ReleaseAssets {
    fun tagUrl(tag: String): String =
        "https://api.github.com/repos/zezarv/NeuroPocket/releases/tags/" + tag.trim()

    /**
     * EXACT выбор asset по имени из списка (name -> browser_download_url).
     * Возвращает URL или null. Никаких префиксных совпадений для executable.
     */
    fun findExactUrl(assets: List<Pair<String, String>>, exactName: String): String? {
        val want = exactName.trim()
        if (want.isEmpty()) return null
        return assets.firstOrNull { it.first == want }?.second?.ifBlank { null }
    }
}
