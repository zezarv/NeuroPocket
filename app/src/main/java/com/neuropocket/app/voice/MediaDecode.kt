package com.neuropocket.app.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Декодирует сжатое аудио (mp3/m4a/aac/ogg/opus/flac) в PCM через системный
 * MediaCodec — полностью на устройстве. Лимит 10 минут.
 */
object MediaDecode {
    fun toMono16k(f: File): FloatArray? {
        if (!f.exists() || f.length() > 120 * 1024 * 1024) return null
        val ex = MediaExtractor()
        try {
            ex.setDataSource(f.absolutePath)
            var track = -1
            var mime = ""
            var rate = 0
            var chans = 0
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val m = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    track = i; mime = m
                    rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    chans = try { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 2 }
                    break
                }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(ex.getTrackFormat(track), null, null, 0)
            dec.start()
            val shorts = mutableListOf<Short>()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var outCount = 0
            val maxOut = 16000 * 60 * 10 // с запасом, обрежем позже
            var guard = 0
            while (guard++ < 20000) {
                if (!sawInputEos) {
                    val bi = dec.dequeueInputBuffer(10_000)
                    if (bi >= 0) {
                        val buf = dec.getInputBuffer(bi)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(bi, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            dec.queueInputBuffer(bi, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi >= 0 -> {
                        if (info.size > 0 && outCount < maxOut) {
                            val buf = dec.getOutputBuffer(oi)!!
                            val bb = buf.duplicate().order(ByteOrder.nativeOrder())
                            // почти всегда PCM 16-bit
                            val n = info.size / 2
                            var k = 0
                            while (k < n && outCount < maxOut) {
                                shorts.add(bb.short)
                                k++; outCount++
                            }
                        }
                        dec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    oi == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (sawInputEos) break
                    }
                }
                if (sawInputEos && oi == MediaCodec.INFO_TRY_AGAIN_LATER) break
            }
            try { dec.stop(); dec.release() } catch (_: Exception) {}
            if (shorts.isEmpty()) return null
            val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
            return WavUtils.toMono16k(WavUtils.Pcm(floats, if (rate > 0) rate else 44100, if (chans > 0) chans else 2))
        } catch (_: Exception) {
            return null
        } finally {
            try { ex.release() } catch (_: Exception) {}
        }
    }
}
