package com.neuropocket.app.core

/**
 * Red-team E: корректная rotation-aware проверка подписи обновления.
 *
 * Android SigningInfo semantics:
 * - один подписант: lineage = signingCertificateHistory + текущий подписант.
 *   Легитимный update old->new (с proof-of-rotation) проходит, если lineage
 *   архива и установленной пересекаются (разделяют хотя бы один сертификат).
 * - несколько подписантов: identity = ПОЛНЫЙ набор. Требуется exact match
 *   множеств подписантов (частичное пересечение — FAIL).
 *
 * Всё — чистые множества строк (Signature.toCharsString()), без Android-зависимостей.
 */
object ApkSigVerify {
    data class SigSets(
        val archiveSigners: Set<String>,
        val archiveHistory: Set<String>,
        val installedSigners: Set<String>,
        val installedHistory: Set<String>
    ) {
        val archiveMulti: Boolean get() = archiveSigners.size > 1
        val installedMulti: Boolean get() = installedSigners.size > 1
    }

    /**
     * true если архив допустим как обновление установленного.
     * Пустые множества подписантов — всегда false (нет подписи = нет доверия).
     */
    fun isValidUpdate(s: SigSets): Boolean {
        if (s.archiveSigners.isEmpty() || s.installedSigners.isEmpty()) return false
        if (s.archiveMulti || s.installedMulti) {
            // multiple signers: exact signer-set semantics, не any-intersection
            return s.archiveSigners == s.installedSigners
        }
        // single signer: lineage intersection в любую сторону
        val archiveLineage = s.archiveHistory + s.archiveSigners
        val installedLineage = s.installedHistory + s.installedSigners
        return archiveLineage.intersect(installedLineage).isNotEmpty()
    }
}
