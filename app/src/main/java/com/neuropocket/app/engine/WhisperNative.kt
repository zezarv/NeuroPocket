package com.neuropocket.app.engine

object WhisperNative {
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("npwhisper")
            true
        } catch (_: UnsatisfiedLinkError) { false } catch (_: Exception) { false }
    }

    external fun isLoaded(): Boolean
    external fun loadModel(path: String): Int
    external fun unload()
    external fun transcribe(wavPath: String, lang: String, nThreads: Int): String
    /** Строки "t0_ms|t1_ms|текст". */
    external fun transcribeDetailed(wavPath: String, lang: String, nThreads: Int): String
}
