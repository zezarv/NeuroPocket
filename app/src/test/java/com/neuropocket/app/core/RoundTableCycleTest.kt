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
}
