package com.neuropocket.app.engine

object SdNative {
    var available: Boolean = false
        private set
    var fromFile: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("npsd")
            true
        } catch (_: UnsatisfiedLinkError) { false } catch (_: Exception) { false }
    }

    /** Догрузка движка из файла (лёгкий APK). */
    fun loadFromFile(f: java.io.File): Boolean {
        if (available) return true
        return try {
            System.load(f.absolutePath)
            fromFile = true
            available = true
            true
        } catch (_: UnsatisfiedLinkError) { false } catch (_: Exception) { false }
    }

    external fun isLoaded(): Boolean
    external fun loadModel(modelPath: String, vaePath: String, taesdPath: String, nThreads: Int): Int
    external fun unload()
    external fun cancel()

    /** Возвращает RGB-байты (w*h*3) или null. */
    external fun renderImg(
        prompt: String, negative: String, argb: IntArray, iw: Int, ih: Int,
        w: Int, h: Int, steps: Int, cfg: Float, seed: Long, sampler: String, strength: Float
    ): ByteArray?

    external fun render(
        prompt: String, negative: String,
        w: Int, h: Int, steps: Int, cfg: Float, seed: Long, sampler: String
    ): ByteArray?
}
