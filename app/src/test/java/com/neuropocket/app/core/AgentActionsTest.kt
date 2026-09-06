package com.neuropocket.app.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AgentActionsTest {

    private class FakeHandler(
        private val available: Set<String> = AgentActionTypes.ALL.toSet(),
        private val fail: Set<String> = emptySet()
    ) : AgentActionHandler {
        val executed = mutableListOf<AgentAction>()
        override fun isAvailable(type: String): Boolean = type in available
        override suspend fun execute(action: AgentAction): AgentExecResult {
            executed.add(action)
            if (action.type in fail) return AgentExecResult.Error("boom")
            return AgentExecResult.Success("real:${action.type}:${action.args.values.firstOrNull() ?: ""}")
        }
    }

    @Test fun `parse single action line`() {
        val p = AgentActionParser.parsePlan("ACTION: SEARCH_NOTES | query=рецепт")
        assertEquals(1, p.size)
        val v = p[0] as ParsedAction.Valid
        assertEquals("SEARCH_NOTES", v.action.type)
        assertEquals("рецепт", v.action.args["query"])
    }

    @Test fun `parse numbered list with noise`() {
        val raw = "Вот план:\n1. ACTION: READ_NOTE | name=todo.md\n2. ACTION: SUMMARIZE_TEXT | text=hello world"
        val p = AgentActionParser.parsePlan(raw)
        assertEquals(2, p.size)
        assertTrue(p.all { it is ParsedAction.Valid })
    }

    @Test fun `parse invalid action unknown type`() {
        val p = AgentActionParser.parsePlan("ACTION: DELETE_SYSTEM | target=all")
        assertEquals(1, p.size)
        val inv = p[0] as ParsedAction.Invalid
        assertTrue(inv.reason.contains("Неизвестное"))
    }

    @Test fun `parse missing required args is invalid`() {
        val p = AgentActionParser.parsePlan("ACTION: WRITE_NOTE | name=a.md")
        assertEquals(1, p.size)
        assertTrue(p[0] is ParsedAction.Invalid)
    }

    @Test fun `parse json array`() {
        val raw = """[{"action":"READ_NOTE","args":{"name":"a.md"}},{"action":"SEARCH_NOTES","query":"x"}]"""
        val p = AgentActionParser.parsePlan(raw)
        assertEquals(2, p.size)
        assertTrue(p.all { it is ParsedAction.Valid })
    }

    @Test fun `parse caps at five`() {
        val raw = (1..8).joinToString("\n") { "ACTION: SEARCH_NOTES | query=q$it" }
        val p = AgentActionParser.parsePlan(raw)
        assertEquals(5, p.size)
    }

    @Test fun `executor runs real handler and summarizes honestly`() = runBlocking {
        val h = FakeHandler()
        val (steps, final) = AgentExecutor.run(
            "ACTION: SEARCH_NOTES | query=кот\nACTION: READ_NOTE | name=a.md", h
        )
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.status == AgentExecutor.ST_DONE })
        assertEquals(2, h.executed.size)
        assertTrue(final.contains("Выполнено 2 из 2"))
        assertTrue(final.contains("real:SEARCH_NOTES"))
    }

    @Test fun `executor marks unavailable without executing`() = runBlocking {
        val h = FakeHandler(available = setOf(AgentActionTypes.READ_NOTE))
        val (steps, final) = AgentExecutor.run(
            "ACTION: TRANSCRIBE_AUDIO | file=a.wav", h
        )
        assertEquals(1, steps.size)
        assertEquals(AgentExecutor.ST_UNAVAILABLE, steps[0].status)
        assertTrue(h.executed.isEmpty()) // НЕ вызывали недоступное
        assertTrue(final.contains("UNAVAILABLE"))
        assertFalse(final.contains("Выполнено 1 из 1 действий. Честный разбор:\n\n1. [OK]"))
    }

    @Test fun `executor marks error and never claims success`() = runBlocking {
        val h = FakeHandler(fail = setOf(AgentActionTypes.READ_NOTE))
        val (steps, final) = AgentExecutor.run("ACTION: READ_NOTE | name=a.md", h)
        assertEquals(AgentExecutor.ST_ERROR, steps[0].status)
        assertTrue(final.contains("[ERROR]"))
        assertTrue(final.contains("Ни одно действие не выполнено успешно"))
        assertFalse(final.contains("[OK]"))
    }

    @Test fun `executor invalid plan line is invalid not success`() = runBlocking {
        val h = FakeHandler()
        val (steps, final) = AgentExecutor.run("ACTION: FROBNICATE | x=1", h)
        assertEquals(1, steps.size)
        assertEquals(AgentExecutor.ST_INVALID, steps[0].status)
        assertTrue(h.executed.isEmpty())
        assertTrue(final.contains("INVALID"))
    }

    @Test fun `executor cancellation skips remaining`() = runBlocking {
        val h = FakeHandler()
        var calls = 0
        val (steps, _) = AgentExecutor.run(
            "ACTION: SEARCH_NOTES | query=a\nACTION: SEARCH_NOTES | query=b\nACTION: SEARCH_NOTES | query=c",
            h,
            // первое действие успевает выполниться, дальше — остановка
            shouldStop = { ++calls > 2 }
        )
        // первый шаг выполнен, остальные — skipped (либо цикл прерван раньше)
        assertTrue(steps.any { it.status == AgentExecutor.ST_DONE })
        assertTrue(steps.any { it.status == AgentExecutor.ST_SKIPPED } || steps.size < 3)
    }

    @Test fun `no hallucinated success when everything fails`() = runBlocking {
        val h = FakeHandler(available = emptySet())
        val (_, final) = AgentExecutor.run(
            "ACTION: SEARCH_NOTES | query=a\nACTION: READ_NOTE | name=b", h
        )
        assertFalse(final.contains("[OK]"))
        assertTrue(final.contains("Ни одно действие не выполнено успешно"))
    }

    @Test fun `empty plan is honest`() = runBlocking {
        val h = FakeHandler()
        val (steps, final) = AgentExecutor.run("просто поболтай со мной", h)
        assertTrue(steps.isEmpty())
        assertTrue(final.contains("Нет исполнимых действий"))
        assertTrue(h.executed.isEmpty())
    }
}
