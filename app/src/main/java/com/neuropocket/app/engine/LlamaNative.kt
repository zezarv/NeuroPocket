package com.neuropocket.app.engine

/** Приёмник токенов из native (вызывается на том же потоке, что и generateStream). */
interface TokenSink {
    fun emit(piece: String)
}

object LlamaNative {
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("neuropocket")
            true
        } catch (_: UnsatisfiedLinkError) { false } catch (_: Exception) { false }
    }

    external fun systemInfo(): String
    external fun isLoaded(): Boolean
    external fun supportsGpu(): Boolean
    external fun cancel()
    external fun loadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Int
    external fun unload()
    external fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int, seed: Int): String
    external fun generateStream(prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int, seed: Int, sink: TokenSink): String
    /** "pp_tok pp_ms gen_tok gen_ms" или "__ERR:...". */
    external fun runBench(): String
    external fun isVisionLoaded(): Boolean
    external fun loadVision(mmprojPath: String, nThreads: Int): Int
    external fun unloadVision()
    external fun describeImage(img: ByteArray, prompt: String, maxTokens: Int, temperature: Float): String
    external fun isEmbedLoaded(): Boolean
    external fun embedDim(): Int
    external fun loadEmbed(path: String, nThreads: Int): Int
    external fun unloadEmbed()
    external fun embedBatch(texts: Array<String>): FloatArray?
}
