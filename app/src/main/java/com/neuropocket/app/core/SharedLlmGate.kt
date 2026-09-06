package com.neuropocket.app.core

/**
 * Lead-review #2 п.2: единый guard shared llama runtime.
 * deviceBusy() покрывает не всё: send/sendPersona/runAgent используют свои
 * ручные flag checks и обходили rtRunning. Этот helper — единственное место
 * с формулой; VM entrypoints только вызывают его.
 *
 * Ownership: RoundTable держит rtRunning; hands-free НЕ получает исключений —
 * его внутренний send() проходит ту же проверку (во время стола HF-запросы
 * отклоняются, цикл HF при этом не ломается: пустой ответ = пропуск витка).
 */
object SharedLlmGate {
    /** Чат (main): требует свободный llama + отсутствие стола. */
    fun canSend(
        busy: Boolean,
        agentRunning: Boolean,
        pBusy: Boolean,
        sdBusy: Boolean,
        visionBusy: Boolean,
        rtRunning: Boolean
    ): Boolean = !(busy || agentRunning || pBusy || sdBusy || visionBusy || rtRunning)

    /** Чат персоны: та же формула, что send. */
    fun canSendPersona(
        busy: Boolean,
        agentRunning: Boolean,
        pBusy: Boolean,
        sdBusy: Boolean,
        visionBusy: Boolean,
        rtRunning: Boolean
    ): Boolean = canSend(busy, agentRunning, pBusy, sdBusy, visionBusy, rtRunning)

    /** Агент: свои флаги + стол. */
    fun canRunAgent(
        taskBlank: Boolean,
        agentRunning: Boolean,
        busy: Boolean,
        sdBusy: Boolean,
        rtRunning: Boolean
    ): Boolean = !taskBlank && !(agentRunning || busy || sdBusy || rtRunning)

    /** RAG/вопрос по заметкам: embed+llama делят рантайм со столом. */
    fun canAskNotes(queryBlank: Boolean, ragBusy: Boolean, rtRunning: Boolean): Boolean =
        !queryBlank && !ragBusy && !rtRunning

    /** Загрузка текстовой модели: финальный execution gate (закрывает TOCTOU
     * между confirm-диалогом ambiguous и фактической загрузкой). */
    fun canLoadTextModel(busy: Boolean, agentRunning: Boolean, sdBusy: Boolean, rtRunning: Boolean): Boolean =
        !(busy || agentRunning || sdBusy || rtRunning)

    /** Старт hands-free: столу нельзя мешать захватом pipeline. */
    fun canStartHandsFree(rtRunning: Boolean): Boolean = !rtRunning
}
