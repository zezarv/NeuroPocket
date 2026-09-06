package com.neuropocket.app.core

import java.io.File

/**
 * Pinned manifest для downloadable EXECUTABLE assets (red-team C).
 *
 * Значения измерены 2026-09-05 из релиза v1.24.0 (не из недоверенного metadata):
 * - voice-engine-arm64.zip: 9642281 bytes, содержимое и ELF-зависимости проверены
 *   локально через llvm-readelf (NDK r26d):
 *   - libonnxruntime.so (21684872): NEEDED только system (dl/log/m/c), ELF64 AArch64
 *   - libsherpa-onnx-jni.so (4761536): NEEDED system + libonnxruntime.so, ELF64 AArch64
 *   - c-api/cxx-api .so в архиве НЕТ и НЕ нужны (проверено, не предположено).
 *     Порядок загрузки: libonnxruntime.so -> libsherpa-onnx-jni.so.
 * - libnpsd-arm64-v8a.so: 53672384 bytes, ELF64 AArch64, SONAME libnpsd.so,
 *   NEEDED system + c++_shared + omp (остальное статически слинковано).
 *
 * Fail-closed: несовпадение size/sha -> ERROR, файл карантинируется (удаляется),
 * System.load НЕ вызывается. При обновлении asset в релизе manifest нужно
 * обновить в коде (bump + новые хэши), иначе загрузка будет отклоняться —
 * это осознанный trade-off текущей стадии.
 */
object AssetManifest {
    data class PinnedAsset(
        val assetName: String,
        val sizeBytes: Long,
        val sha256Hex: String,
        val releaseTag: String
    )

    val VOICE_ENGINE = PinnedAsset(
        assetName = "voice-engine-arm64.zip",
        sizeBytes = 9642281L,
        sha256Hex = "85015b943d5ee7623bb4c86030ebb9ed080345baea4d6cf4c1f02af17fad7a46",
        releaseTag = "v1.24.0"
    )

    val SD_ENGINE = PinnedAsset(
        assetName = "libnpsd-arm64-v8a.so",
        sizeBytes = 53672384L,
        sha256Hex = "376222c195b41fe9342819fc2a4ea17b724d7cbea373410c63d82cae513fe8d4",
        releaseTag = "v1.24.0"
    )

    /** Ожидаемое содержимое voice-engine ZIP (имя -> размер). Whitelist распаковки. */
    val VOICE_ZIP_FILES: Map<String, Long> = mapOf(
        "libonnxruntime.so" to 21684872L,
        "libsherpa-onnx-jni.so" to 4761536L
    )

    /** Проверка скачанного файла против пина: exact size + SHA-256. */
    fun verifyFile(f: File, pinned: PinnedAsset): Boolean {
        try {
            if (!f.isFile || f.length() != pinned.sizeBytes) return false
            if (pinned.sha256Hex.length != 64) return false
            return NativeVerify.sha256Hex(f)?.equals(pinned.sha256Hex, ignoreCase = true) == true
        } catch (_: Exception) {
            return false
        }
    }
}
