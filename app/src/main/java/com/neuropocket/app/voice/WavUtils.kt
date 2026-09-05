package com.neuropocket.app.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * WAV-утилиты: чтение PCM (16-bit / float32, моно/стерео, любая частота)
 * и конверсия в 16 кГц моно 16-bit для whisper-моста. Только локально.
 */
object WavUtils {
    data class Pcm(val samples: FloatArray, val rate: Int, val channels: Int)

    fun read(path: String): Pcm? {
        return try {
            val b = File(path).readBytes()
            if (b.size < 44 || b[0] != 'R'.code.toByte()) return null
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            // найти чанки fmt и data
            var pos = 12
            var fmt = 0; var ch = 0; var rate = 0; var bits = 0
            var dataOff = -1; var dataLen = 0
            while (pos + 8 <= b.size) {
                val id = String(b, pos, 4, Charsets.US_ASCII)
                val sz = bb.getInt(pos + 4)
                if (id == "fmt ") {
                    fmt = bb.getShort(pos + 8).toInt()
                    ch = bb.getShort(pos + 10).toInt()
                    rate = bb.getInt(pos + 12)
                    bits = bb.getShort(pos + 22).toInt()
                } else if (id == "data") { dataOff = pos + 8; dataLen = sz; break }
                pos += 8 + sz + (sz and 1)
            }
            if (dataOff < 0 || ch <= 0 || rate <= 0) return null
            if (fmt != 1 && fmt != 3) return null
            if (fmt == 1 && bits != 16) return null
            if (fmt == 3 && bits != 32) return null
            val frames = dataLen / (ch * bits / 8)
            val out = FloatArray(frames * ch)
            if (fmt == 1) {
                for (i in out.indices) out[i] = bb.getShort(dataOff + i * 2) / 32768f
            } else {
                for (i in out.indices) out[i] = bb.getFloat(dataOff + i * 4)
            }
            Pcm(out, rate, ch)
        } catch (_: Exception) { null }
    }

    /** Линейный ресемплинг + микс в моно. */
    fun toMono16k(p: Pcm): FloatArray {
        val frames = p.samples.size / p.channels
        // микс в моно
        val mono = FloatArray(frames) { i ->
            var s = 0f
            for (c in 0 until p.channels) s += p.samples[i * p.channels + c]
            s / p.channels
        }
        if (p.rate == 16000) return mono
        val ratio = 16000.0 / p.rate
        val n = (frames * ratio).roundToInt().coerceAtLeast(1)
        return FloatArray(n) { i ->
            val src = i / ratio
            val i0 = src.toInt().coerceIn(0, frames - 1)
            val i1 = (i0 + 1).coerceIn(0, frames - 1)
            val f = (src - i0).toFloat()
            mono[i0] * (1 - f) + mono[i1] * f
        }
    }

    fun writeMono16k(file: File, mono: FloatArray) {
        file.parentFile?.mkdirs()
        val data = ByteArray(mono.size * 2)
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (s in mono) bb.putShort(((s.coerceIn(-1f, 1f)) * 32767).toInt().toShort())
        file.outputStream().use {
            it.write(AudioRec.wavHeader(data.size, 16000))
            it.write(data)
        }
    }

    /** true если файл уже 16 кГц моно — конверсия не нужна. */
    fun isReady16kMono(path: String): Boolean {
        val p = read(path) ?: return false
        return p.rate == 16000 && p.channels == 1
    }
}
