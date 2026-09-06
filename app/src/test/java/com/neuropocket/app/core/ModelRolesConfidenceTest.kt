package com.neuropocket.app.core

import com.neuropocket.app.data.ModelCatalog
import org.junit.Assert.*
import org.junit.Test

class ModelRolesConfidenceTest {
    @Test fun `normal text gguf confident-ish ambiguous`() {
        // обычный текстовый GGUF без маркеров: роль TEXT_LLM, но НЕ уверенно
        val c = ModelRoles.classifyC("Llama-3.2-3B-Instruct-Q4_K_M.gguf")
        assertEquals(ModelRole.TEXT_LLM, c.role)
    }
    @Test fun `mmproj confident`() {
        val c = ModelRoles.classifyC("mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf")
        assertEquals(ModelRole.MM_PROJECTOR, c.role)
        assertTrue(c.confident)
    }
    @Test fun `embeddings confident`() {
        val c = ModelRoles.classifyC("multilingual-e5-small-q8_0.gguf")
        assertEquals(ModelRole.EMBEDDING, c.role)
        assertTrue(c.confident)
    }
    @Test fun `ambiguous generic model`() {
        // импортированный файл без маркеров — честно ambiguous
        assertTrue(ModelRoles.isAmbiguous("model.gguf"))
        assertTrue(ModelRoles.isAmbiguous("my-llama.gguf"))
    }
    @Test fun `renamed imported model stays usable as text`() {
        // переименованный текстовый GGUF: грузится как текст (native даст
        // ошибку при реальном несоответствии, состояние не портится)
        assertFalse(ModelRoles.isAmbiguous("mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf"))
        assertEquals(ModelRole.TEXT_LLM, ModelRoles.classify("custom-model.gguf"))
    }
    @Test fun `describeFile honest about heuristic`() {
        assertTrue(ModelRoles.describeFile("model.gguf").contains("не проверено"))
        assertFalse(ModelRoles.describeFile("ggml-base.bin").contains("не проверено"))
    }
    @Test fun `catalog kinds all authoritative`() {
        // каждая запись каталога обязана маппиться в известную роль
        val all = ModelCatalog.models + ModelCatalog.whisperModels +
            ModelCatalog.embedModels + ModelCatalog.mmprojModels +
            ModelCatalog.voiceModels + ModelCatalog.taesdModels + ModelCatalog.sdModels
        assertTrue("каталог пуст?", all.isNotEmpty())
        for (m in all) {
            val role = ModelRoles.roleForCatalogKind(m.kind)
            assertNotEquals("kind=${m.kind} id=${m.id}", ModelRole.UNKNOWN, role)
        }
    }
    @Test fun `catalog kind mapping spot checks`() {
        assertEquals(ModelRole.TEXT_LLM, ModelRoles.roleForCatalogKind("text"))
        assertEquals(ModelRole.VISION_LLM, ModelRoles.roleForCatalogKind("vision-text"))
        assertEquals(ModelRole.MM_PROJECTOR, ModelRoles.roleForCatalogKind("mmproj"))
        assertEquals(ModelRole.EMBEDDING, ModelRoles.roleForCatalogKind("embed"))
        assertEquals(ModelRole.WHISPER, ModelRoles.roleForCatalogKind("whisper"))
        assertEquals(ModelRole.SD, ModelRoles.roleForCatalogKind("image"))
        assertEquals(ModelRole.TTS, ModelRoles.roleForCatalogKind("voice"))
        assertEquals(ModelRole.VAD, ModelRoles.roleForCatalogKind("vad"))
    }
}
