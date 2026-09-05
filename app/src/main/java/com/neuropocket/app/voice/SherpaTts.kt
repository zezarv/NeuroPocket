package com.neuropocket.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Локальный TTS через sherpa-onnx (Piper/VITS голоса).
 * Генерирует по предложениям — первые звуки почти сразу, без ожидания всего текста.
 */
class SherpaTts(modelOnnx: File, tokens: File, dataDir: File, threads: Int = 2) {
    private val tts: OfflineTts
    val sampleRateGuess = 22050

    init {
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelOnnx.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = dataDir.absolutePath
                ),
                numThreads = threads.coerceIn(1, 4)
            )
        )
        tts = OfflineTts(config = cfg)
    }

    fun release() { try { tts.release() } catch (_: Exception) {} }

    fun synth(text: String, speed: Float = 1.0f): GeneratedAudio =
        tts.generate(text = text, sid = 0, speed = speed)

    companion object {
        /** Поиск файлов голоса в папке: *.onnx + tokens.txt + espeak-ng-data. */
        fun findVoice(dir: File): Triple<File, File, File>? {
            if (!dir.isDirectory) return null
            val onnx = dir.listFiles { f -> f.extension.lowercase() == "onnx" }?.firstOrNull() ?: return null
            val tokens = File(dir, "tokens.txt").takeIf { it.exists() } ?: return null
            val data = File(dir, "espeak-ng-data").takeIf { it.exists() && it.isDirectory } ?: return null
            return Triple(onnx, tokens, data)
        }
    }
}

/** Проигрыватель float-PCM через AudioTrack, с кнопкой Стоп. */
class TtsPlayer {
    private var track: AudioTrack? = null
    private val stopped = AtomicBoolean(false)

    fun stop() {
        stopped.set(true)
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null
    }

    fun play(audio: GeneratedAudio) {
        stopped.set(false)
        val sr = audio.sampleRate
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        val chunk = ShortArray(2048)
        var i = 0
        val n = audio.samples.size
        while (i < n && !stopped.get()) {
            val m = minOf(chunk.size, n - i)
            for (k in 0 until m) {
                chunk[k] = (audio.samples[i + k].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            }
            t.write(chunk, 0, m)
            i += m
        }
        try { t.stop(); t.release() } catch (_: Exception) {}
        if (track === t) track = null
    }
}

/** Делит текст на предложения для потоковой озвучки. */
fun splitSentences(text: String): List<String> {
    return text.replace("\n", " ")
        .split(Regex("(?<=[.!?…])\\s+"))
        .map { it.trim() }
        .filter { it.length > 1 }
        .take(40)
}
