package com.neuropocket.app.engine

import com.neuropocket.app.data.ChatMessage
import com.neuropocket.app.data.Persona

data class AgentStep(
    val text: String,
    var status: String = "wait", // wait | run | done | fail
    var result: String = ""
)

data class RoundTurn(
    val personaId: String,
    val name: String,
    val text: String
)

/**
 * Простой локальный агент: план из N шагов + последовательное выполнение
 * тем же движком (llama native или mock). Всё на устройстве.
 */
object AgentRunner {
    private val workerPersona = Persona(
        name = "Агент",
        systemPrompt = "Ты исполнительный агент. Отвечай коротко, по делу, без воды.",
        temperature = 0.5f
    )

    suspend fun run(
        task: String,
        call: suspend (prompt: String) -> String,
        onUpdate: () -> Unit = {},
        shouldStop: () -> Boolean = { false }
    ): Pair<List<AgentStep>, String> {
        val planRaw = call(
            "Разбей задачу на пронумерованные шаги (максимум 5, каждый — одно короткое предложение). " +
                "Только список, без вступлений. Задача: $task"
        )
        if (shouldStop()) return emptyList<AgentStep>() to "[Остановлено.]"
        val steps = planRaw.lines()
            .map { it.trim().trimStart('-', '*', '•').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val m = Regex("""^(\d+)[).:]\s*(.+)""").find(line)
                if (m != null) AgentStep(m.groupValues[2].take(300)) else null
            }
            .take(5)
            .ifEmpty { listOf(AgentStep(task.take(300))) }

        val acc = StringBuilder()
        for ((i, s) in steps.withIndex()) {
            if (shouldStop()) {
                s.status = "fail"; onUpdate()
                return steps to "[Остановлено на шаге ${i + 1}.]\n$acc"
            }
            s.status = "run"; onUpdate()
            val prev = if (acc.isEmpty()) "нет" else acc.toString().take(1500)
            val out = try {
                call(
                    "Задача: ${task.take(500)}\nУже сделано: $prev\n" +
                        "Текущий шаг ${i + 1}/${steps.size}: ${s.text}\n" +
                        "Выполни шаг коротко (до 5 предложений)."
                )
            } catch (e: Exception) { "[ошибка: ${e.message}]" }
            s.result = out.take(1200)
            s.status = "done"
            acc.append("\n[Шаг ${i + 1}] ${s.text}\n$out\n")
            onUpdate()
        }
        if (shouldStop()) return steps to "[Остановлено.]\n$acc"
        val final = try {
            call(
                "Задача: ${task.take(500)}\nРезультаты шагов:$acc\n" +
                    "Собери короткий итог: что сделано и что дальше (до 8 предложений)."
            )
        } catch (e: Exception) { acc.toString() }
        return steps to final.take(2500)
    }

    /** Адаптер под AiEngine. */
    suspend fun runWithEngine(
        task: String,
        engine: AiEngine,
        persona: Persona,
        onUpdate: () -> Unit,
        shouldStop: () -> Boolean = { false }
    ): Pair<List<AgentStep>, String> {
        val hist = listOf(ChatMessage(role = "user", text = task))
        return run(task, { prompt -> engine.generate(hist, workerPersona.copy(name = persona.name), prompt) }, onUpdate, shouldStop)
    }
}
