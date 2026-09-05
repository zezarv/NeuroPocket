package com.neuropocket.app.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Запись речи в WAV 16 кГц моно 16-bit — формат, который ест whisper-мост без конверсии.
 * Все файлы — только на устройстве.
 */
class AudioRec(private val outFile: File) {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    var seconds: Int = 0
        private set

    fun start(): Boolean {
        val sr = 16000
        val ch = AudioFormat.CHANNEL_IN_MONO
        val enc = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sr, ch, enc)
        if (minBuf <= 0) return false
        return try {
            outFile.parentFile?.mkdirs()
            val ar = AudioRecord(MediaRecorder.AudioSource.MIC, sr, ch, enc, minBuf * 2)
            if (ar.state != AudioRecord.STATE_INITIALIZED) return false
            // WAV-заголовок с запасом, размер допишем в stop()
            FileOutputStream(outFile).use { it.write(wavHeader(0, sr)) }
            rec = ar
            running.set(true)
            seconds = 0
            thread = Thread {
                val fos = FileOutputStream(outFile, true)
                val buf = ShortArray(2048)
                var total = 0
                ar.startRecording()
                while (running.get()) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n > 0) {
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        fos.write(bytes)
                        total += n
                        seconds = total / sr
                        if (total >= sr * 60 * 10) break // лимит 10 минут
                    }
                }
                try { ar.stop() } catch (_: Exception) {}
                ar.release()
                fos.close()
                // дописать размеры в заголовок
                try {
                    val raf = java.io.RandomAccessFile(outFile, "rw")
                    raf.write(wavHeader(total * 2, sr))
                    raf.close()
                } catch (_: Exception) {}
            }
            thread?.start()
            true
        } catch (_: Exception) { false }
    }

    fun stop() {
        running.set(false)
        try { thread?.join(2000) } catch (_: Exception) {}
        thread = null
        rec = null
    }

    companion object {
        fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray {
            val h = ByteArray(44)
            fun str(o: Int, s: String) { s.toByteArray().copyInto(h, o, 0, 4) }
            fun i32(o: Int, v: Int) {
                h[o] = (v and 0xFF).toByte(); h[o + 1] = ((v shr 8) and 0xFF).toByte()
                h[o + 2] = ((v shr 16) and 0xFF).toByte(); h[o + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun i16(o: Int, v: Int) { h[o] = (v and 0xFF).toByte(); h[o + 1] = ((v shr 8) and 0xFF).toByte() }
            str(0, "RIFF"); i32(4, 36 + dataLen); str(8, "WAVE"); str(12, "fmt ")
            i32(16, 16); i16(20, 1); i16(22, 1); i32(24, sampleRate)
            i32(28, sampleRate * 2); i16(32, 2); i16(34, 16); str(36, "data"); i32(40, dataLen)
            return h
        }
    }
}
