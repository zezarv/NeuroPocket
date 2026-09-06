package com.neuropocket.app.core

import com.neuropocket.app.engine.RoundTurn
import org.junit.Assert.*
import org.junit.Test

/**
 * Red-team H: полный production-цикл "Ещё круг" как pure test.
 * Трасса: UI onClick append=true -> initialTurns(existing, true) ->
 * seed содержит старые -> генерация -> merge(existing, fresh, true).
 */
class RoundTableCycleTest {
    private fun turn(name: String, text: String) = RoundTurn("id-$name", name, text)

    @Test fun `full encore cycle keeps old visible and appends new`() {
        // первый стол: 2 круга x 2 участника
        val firstRun = listOf(
            turn("A", "тезис один"),
            turn("B", "ответ один"),
            turn("A", "тезис два"),
            turn("B", "ответ два")
        )
        // UI: "Ещё круг (+2)" -> startRoundTable(topic, 2, append = true)
        val seedTurns = RoundTableLogic.initialTurns(firstRun, append = true)
        // старые остаются видимыми (VM не чистит rtTurns при append)
        assertEquals(firstRun, seedTurns)
        // seed для новых участников содержит предыдущий контекст
        val seed = RoundTableLogic.buildSeedContext(seedTurns)
        assertTrue(seed.contains("тезис один"))
        assertTrue(seed.contains("ответ два"))
        // новые turns append, а не replace
        val fresh = listOf(turn("A", "тезис три"), turn("B", "ответ три"))
        val merged = RoundTableLogic.mergeTurns(seedTurns, fresh, append = true)
        assertEquals(6, merged.size)
        assertEquals(firstRun + fresh, merged)
        // а новый стол без append — чистый
        assertEquals(fresh, RoundTableLogic.mergeTurns(seedTurns, fresh, append = false))
    }

    @Test fun `long discussion seed prefers fresh turns`() {
        // Red-team I: encore длинной дискуссии реагирует на последние turns
        val many = (1..30).map { i -> turn(if (i % 2 == 0) "B" else "A", "реплика номер $i") }
        val seed = RoundTableLogic.buildSeedContext(many, limitChars = 300)
        assertTrue(seed.contains("реплика номер 30"))
        assertTrue(seed.contains("реплика номер 29"))
        // старые вытеснены лимитом
        assertFalse(seed.contains("реплика номер 1\n"))
    }

    @Test fun `per-turn tail keeps fresh answer for next participant`() {
        // Lead-review #2 п.1: oldSeed >= limit + freshTurnA -> контекст для B
        // ОБЯЗАН содержать freshTurnA, oldest — вытеснен.
        val oldSeed = "x".repeat(2000)
        val acc = StringBuilder(oldSeed)
        acc.append("\nA: свежий ответ A\n")
        val contextForB = RoundTableLogic.tail(acc.toString(), 2000)
        assertTrue(contextForB.contains("свежий ответ A"))
        assertEquals(2000, contextForB.length)
        // начало старого seed вытеснено
        assertFalse(contextForB.startsWith("x".repeat(2000)))
    }

    @Test fun `tail single implementation`() {
        assertEquals("", RoundTableLogic.tail("abc", 0))
        assertEquals("abc", RoundTableLogic.tail("abc", 2000))
        assertEquals("bc", RoundTableLogic.tail("abc", 2))
    }
}
