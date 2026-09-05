package com.neuropocket.app.core

import com.neuropocket.app.engine.RoundTurn
import org.junit.Assert.*
import org.junit.Test

class RoundTableLogicTest {
    private fun turn(name: String, text: String) = RoundTurn("id-$name", name, text)

    @Test fun `new table clears history`() {
        val existing = listOf(turn("A", "hi"), turn("B", "yo"))
        assertTrue(RoundTableLogic.initialTurns(existing, append = false).isEmpty())
    }

    @Test fun `append keeps history`() {
        val existing = listOf(turn("A", "hi"), turn("B", "yo"))
        assertEquals(existing, RoundTableLogic.initialTurns(existing, append = true))
    }

    @Test fun `merge appends fresh after existing for UI`() {
        val old = listOf(turn("A", "1"))
        val fresh = listOf(turn("B", "2"), turn("A", "3"))
        assertEquals(old + fresh, RoundTableLogic.mergeTurns(old, fresh, append = true))
        assertEquals(fresh, RoundTableLogic.mergeTurns(old, fresh, append = false))
    }

    @Test fun `seed context contains previous turns`() {
        val turns = listOf(turn("Mira", "hello world"), turn("Kai", "second line"))
        val seed = RoundTableLogic.buildSeedContext(turns)
        assertTrue(seed.contains("Mira: hello world"))
        assertTrue(seed.contains("Kai: second line"))
    }

    @Test fun `seed context empty for fresh start`() {
        assertEquals("", RoundTableLogic.buildSeedContext(emptyList()))
    }

    @Test fun `P0_1 regression - append must not lose seed`() {
        // Симуляция бага: старый код делал rtTurns=emptyList() после условного сохранения.
        val existing = listOf(turn("A", "first"), turn("B", "second"))
        val seedTurns = RoundTableLogic.initialTurns(existing, append = true)
        val seed = RoundTableLogic.buildSeedContext(seedTurns)
        assertTrue("seed обязан содержать предыдущие turns", seed.contains("first"))
        assertTrue(seed.contains("second"))
    }
}
