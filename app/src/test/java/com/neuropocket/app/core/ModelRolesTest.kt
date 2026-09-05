package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class ModelRolesTest {
    @Test fun `text llm`() {
        assertEquals(ModelRole.TEXT_LLM, ModelRoles.classify("Llama-3.2-3B-Instruct-Q4_K_M.gguf"))
        assertEquals(ModelRole.TEXT_LLM, ModelRoles.classify("qwen2.5-3b-instruct-q4_k_m.gguf"))
    }
    @Test fun `vision split`() {
        assertEquals(ModelRole.VISION_LLM, ModelRoles.classify("Qwen2-VL-2B-Instruct-Q4_K_M.gguf"))
        assertEquals(ModelRole.MM_PROJECTOR, ModelRoles.classify("mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf"))
    }
    @Test fun `embed not confused with mmproj`() {
        assertEquals(ModelRole.EMBEDDING, ModelRoles.classify("multilingual-e5-small-q8_0.gguf"))
        assertEquals(ModelRole.EMBEDDING, ModelRoles.classify("bge-small-en.gguf"))
        // mmproj с embed-подстрокой? mmproj приоритетнее
        assertEquals(ModelRole.MM_PROJECTOR, ModelRoles.classify("mmproj-e5-test.gguf"))
    }
    @Test fun `whisper sd tts vad`() {
        assertEquals(ModelRole.WHISPER, ModelRoles.classify("ggml-base.bin"))
        assertEquals(ModelRole.WHISPER, ModelRoles.classify("ggml-large-v3-turbo.bin"))
        assertEquals(ModelRole.SD, ModelRoles.classify("v1-5-pruned-emaonly.safetensors"))
        assertEquals(ModelRole.SD, ModelRoles.classify("model.ckpt"))
        assertEquals(ModelRole.TTS, ModelRoles.classify("vits-piper-ru_RU-denis-medium.tar.bz2"))
        assertEquals(ModelRole.VAD, ModelRoles.classify("silero_vad.onnx"))
    }
    @Test fun `unknown`() {
        assertEquals(ModelRole.UNKNOWN, ModelRoles.classify("notes.txt"))
        assertEquals(ModelRole.UNKNOWN, ModelRoles.classify("photo.png"))
    }
    @Test fun `runtime mapping keeps vision embed separate`() {
        assertEquals("llama-text", ModelRoles.runtimeFor(ModelRole.TEXT_LLM))
        assertEquals("llama-vision", ModelRoles.runtimeFor(ModelRole.MM_PROJECTOR))
        assertEquals("llama-embed", ModelRoles.runtimeFor(ModelRole.EMBEDDING))
        assertNotEquals(
            ModelRoles.runtimeFor(ModelRole.TEXT_LLM),
            ModelRoles.runtimeFor(ModelRole.MM_PROJECTOR)
        )
    }
}
