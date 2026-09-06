package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class ToolWorkflowsTest {

    @Test fun `chunk split short returns single`() {
        val parts = ToolChunking.splitSmart("привет мир", 2500)
        assertEquals(1, parts.size)
        assertEquals("привет мир", parts[0])
    }

    @Test fun `chunk split long preserves order and joins`() {
        val p1 = "Параграф один. ".repeat(100) // ~1500
        val p2 = "Параграф два. ".repeat(100)
        val p3 = "Параграф три. ".repeat(100)
        val text = "$p1\n\n$p2\n\n$p3"
        val parts = ToolChunking.splitSmart(text, 2000)
        assertTrue(parts.size >= 2)
        // каждый чанк в лимите
        parts.forEach { assertTrue(it.length <= 2000) }
        // join собирает всё (порядок)
        val joined = ToolChunking.joinOrdered(parts)
        assertTrue(joined.contains("Параграф один"))
        assertTrue(joined.contains("Параграф три"))
        // порядок: один раньше трёх
        assertTrue(joined.indexOf("Параграф один") < joined.indexOf("Параграф три"))
    }

    @Test fun `chunk split oversize paragraph by sentences`() {
        val long = (1..50).joinToString(" ") { "Предложение номер $it с текстом." }
        val parts = ToolChunking.splitSmart(long, 200)
        assertTrue(parts.size > 1)
        parts.forEach { assertTrue(it.length <= 200) }
    }

    @Test fun `translator prompt has no-explanation and glossary`() {
        val p = TranslatorWorkflow.buildChunkPrompt(
            "Hello", "Английский", "Русский",
            TranslatorWorkflow.Options(true, "formal", "bot = бот"),
            0, 1
        )
        assertTrue(p.contains("ТОЛЬКО перевод"))
        assertTrue(p.contains("бот"))
        assertTrue(p.contains("формальный"))
    }

    @Test fun `translator prompt multi-chunk mentions consistency`() {
        val p = TranslatorWorkflow.buildChunkPrompt(
            "Hi", "авто", "русский",
            TranslatorWorkflow.Options(), 1, 3
        )
        assertTrue(p.contains("часть 2 из 3"))
    }

    @Test fun `summarizer chunk then synth prompts`() {
        val c = SummarizerWorkflow.buildChunkPrompt("текст", 0, 2)
        assertTrue(c.contains("1 из 2"))
        val s = SummarizerWorkflow.buildSynthPrompt("п1\nп2", "keypoints")
        assertTrue(s.contains("тезисов") || s.contains("тезис"))
        val single = SummarizerWorkflow.buildSinglePrompt("длинный текст", "actions")
        assertTrue(single.contains("action", ignoreCase = true) || single.contains("чеклист", ignoreCase = true))
    }

    @Test fun `improver prompt keeps language and changes section`() {
        val p = ImproverWorkflow.buildPrompt("текст", "concise")
        assertTrue(p.contains("исходный язык") || p.contains("не переводи"))
        assertTrue(p.contains("ИЗМЕНЕНИЯ"))
        val (imp, ch) = ImproverWorkflow.splitImproved("Новый текст\n---ИЗМЕНЕНИЯ---\nУбрал воду")
        assertEquals("Новый текст", imp)
        assertTrue(ch.contains("воду"))
    }

    @Test fun `analyzer parse valid seven lines`() {
        val raw = "Язык: русский\nТон: нейтральный\nНастроение: нейтральное\n" +
            "Намерение: информировать\nЧитаемость: легко\nПроблемы: нет\nУверенность: высокая"
        val r = AnalyzerWorkflow.parse(raw)
        assertNotNull(r)
        assertEquals("русский", r!!.language)
        assertEquals("нейтральный", r.tone)
        assertEquals("высокая", r.confidence)
        assertFalse(AnalyzerWorkflow.needsRepair(raw))
    }

    @Test fun `analyzer parse legacy pipe format`() {
        val raw = "Язык: русский | Тон: деловой | Проблемы: нет | Оценка 5"
        // pipe: только 3-4 поля -> needsRepair true (нужно минимум 5)
        assertTrue(AnalyzerWorkflow.needsRepair(raw))
    }

    @Test fun `analyzer invalid output needs repair`() {
        assertTrue(AnalyzerWorkflow.needsRepair("Язык | Тон | Проблемы"))
        assertTrue(AnalyzerWorkflow.needsRepair(""))
        assertTrue(AnalyzerWorkflow.needsRepair("просто какой-то текст без ключей"))
        val repair = AnalyzerWorkflow.buildRepairPrompt("текст", "мусор")
        assertTrue(repair.contains("СТРОГО 7 строк") || repair.contains("7 строк"))
    }

    @Test fun `vibecode parse extracts files and keeps honesty`() {
        val raw = "Вот решение:\n```kotlin filename=\"Main.kt\"\nfun main() {}\n```\n" +
            "Объяснение: простой пример.\nЗапуск: kotlinc Main.kt\nРиски: нет."
        val v = VibeCodeWorkflow.parse(raw)
        assertEquals(1, v.files.size)
        assertEquals("Main.kt", v.files[0].name)
        assertTrue(v.files[0].code.contains("fun main"))
        assertTrue(v.notExecuted)
        assertEquals(VibeCodeWorkflow.NOT_EXECUTED, "Сгенерировано / на устройстве не выполнялось.")
        assertFalse(v.explanation.contains("протестировано", ignoreCase = true))
    }

    @Test fun `vibecode prompt forbids fake tested claims`() {
        val p = VibeCodeWorkflow.buildPrompt("сделай кнопку", "Kotlin", "Compose", "")
        assertTrue(p.contains("НИКОГДА"))
        assertTrue(p.contains("не выполнялось"))
    }

    @Test fun `mock disclosure detects mock outputs`() {
        assertTrue(CapabilityDisclosure.isMockOutput("[Перевод-заготовка] hello"))
        assertTrue(CapabilityDisclosure.isMockOutput("Это локальный ответ-заготовка v1"))
        assertFalse(CapabilityDisclosure.isMockOutput("Обычный перевод: привет"))
        assertEquals(
            "Mock / template fallback",
            CapabilityDisclosure.engineBadge("Mock-Local v1", false, "что угодно")
        )
        assertNull(CapabilityDisclosure.engineBadge("llama.cpp native", true, "Обычный перевод"))
    }

    @Test fun `registry validates inputs`() {
        assertNotNull(ToolWorkflowRegistry.validate("translator", ""))
        assertNotNull(ToolWorkflowRegistry.validate("summarizer", "коротко"))
        assertNull(ToolWorkflowRegistry.validate("translator", "нормальный текст для перевода"))
        assertNull(ToolWorkflowRegistry.validate("vibecode", "сделай экран на Compose"))
    }
}
