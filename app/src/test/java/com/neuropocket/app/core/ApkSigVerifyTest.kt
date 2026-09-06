package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class ApkSigVerifyTest {
    private val OLD = "cert-old"
    private val NEW = "cert-new"
    private val EVIL = "cert-evil"

    @Test fun `same signer passes`() {
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf(OLD), archiveHistory = emptySet(),
            installedSigners = setOf(OLD), installedHistory = emptySet()
        )
        assertTrue(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `foreign signer fails`() {
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf(EVIL), archiveHistory = emptySet(),
            installedSigners = setOf(OLD), installedHistory = emptySet()
        )
        assertFalse(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `valid old to new lineage passes`() {
        // установлен old; архив подписан new с proof-of-rotation [old, new]
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf(NEW), archiveHistory = setOf(OLD),
            installedSigners = setOf(OLD), installedHistory = emptySet()
        )
        assertTrue(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `reverse lineage installed newer also passes`() {
        // архив содержит lineage, разделяющую сертификат с установленной
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf(OLD), archiveHistory = emptySet(),
            installedSigners = setOf(NEW), installedHistory = setOf(OLD)
        )
        assertTrue(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `unsigned archive fails`() {
        val s = ApkSigVerify.SigSets(
            archiveSigners = emptySet(), archiveHistory = emptySet(),
            installedSigners = setOf(OLD), installedHistory = emptySet()
        )
        assertFalse(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `multiple signers exact match passes`() {
        val pair = setOf("A", "B")
        val s = ApkSigVerify.SigSets(
            archiveSigners = pair, archiveHistory = emptySet(),
            installedSigners = pair, installedHistory = emptySet()
        )
        assertTrue(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `partial multi-signer match fails`() {
        // пересечение есть, но наборы разные — FAIL (не any-intersection)
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf("A", "B"), archiveHistory = emptySet(),
            installedSigners = setOf("A", "C"), installedHistory = emptySet()
        )
        assertFalse(ApkSigVerify.isValidUpdate(s))
    }
    @Test fun `single vs multi fails`() {
        val s = ApkSigVerify.SigSets(
            archiveSigners = setOf("A"), archiveHistory = emptySet(),
            installedSigners = setOf("A", "B"), installedHistory = emptySet()
        )
        assertFalse(ApkSigVerify.isValidUpdate(s))
    }
}
