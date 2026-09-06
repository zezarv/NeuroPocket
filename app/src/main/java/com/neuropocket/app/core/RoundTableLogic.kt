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

    /**
     * Контекст предыдущих turns для новых участников (seed для промпта).
     * Red-team I: bounded context предпочитает СВЕЖУЮ историю (takeLast),
     * чтобы encore реагировал на последние turns длинной дискуссии.
     */
    fun buildSeedContext(turns: List<RoundTurn>, limitChars: Int = 2000): String =
        tail(turns.takeLast(20).joinToString("\n") { it.name + ": " + it.text }, limitChars)

    /**
     * ЕДИНАЯ truncation для живой дискуссии (п.1 lead-review #2):
     * production каждый turn берёт СВЕЖИЙ tail, а не первые N chars.
     * buildSeedContext и per-turn prompt используют только его.
     */
    fun tail(text: String, limitChars: Int = 2000): String =
        if (limitChars <= 0) "" else text.takeLast(limitChars)
}
