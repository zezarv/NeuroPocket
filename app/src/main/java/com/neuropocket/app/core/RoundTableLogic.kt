package com.neuropocket.app.core

import com.neuropocket.app.engine.RoundTurn

/**
 * Pure RoundTable logic — вынесено из AppViewModel для тестируемости.
 *
 * P0.1 regression: startRoundTable(append=true) раньше делал:
 *   if (!append) rtTurns = emptyList()
 *   ...
 *   rtTurns = emptyList()   // безусловный сброс — ломал "Ещё круг"
 * Правильная семантика зафиксирована здесь и покрыта тестами.
 */
object RoundTableLogic {
    /** Начальное состояние при старте: новый стол чистит, append сохраняет. */
    fun initialTurns(existing: List<RoundTurn>, append: Boolean): List<RoundTurn> =
        if (append) existing.toList() else emptyList()

    /** Слить старые + новые turns для отображения (старые + новые). */
    fun mergeTurns(existing: List<RoundTurn>, fresh: List<RoundTurn>, append: Boolean): List<RoundTurn> =
        if (append) existing + fresh else fresh.toList()

    /** Контекст предыдущих turns для новых участников (seed для промпта). */
    fun buildSeedContext(turns: List<RoundTurn>, limitChars: Int = 2000): String =
        turns.joinToString("\n") { it.name + ": " + it.text }.take(limitChars)
}
