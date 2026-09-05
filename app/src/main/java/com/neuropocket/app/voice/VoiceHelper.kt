package com.neuropocket.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceHelper(private val ctx: Context) {
    private var tts: TextToSpeech? = null

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f, onDone: () -> Unit = {}) {
        if (tts == null) {
            tts = TextToSpeech(ctx) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    applyVoice(rate, pitch)
                    tts?.speak(text.take(1000), TextToSpeech.QUEUE_FLUSH, null, "np1")
                    onDone()
                }
            }
        } else {
            applyVoice(rate, pitch)
            tts?.speak(text.take(1000), TextToSpeech.QUEUE_FLUSH, null, "np1")
            onDone()
        }
    }

    private fun applyVoice(rate: Float, pitch: Float) {
        tts?.language = Locale("ru", "RU")
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun stop() { tts?.stop() }
    fun destroy() { tts?.shutdown(); tts = null }

    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onError("Распознавание недоступно"); return }
        val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                sr.destroy()
                if (!list.isNullOrEmpty()) onResult(list[0]) else onError("Пусто")
            }
            override fun onError(e: Int) { sr.destroy(); onError("Ошибка $e") }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        try { sr.startListening(intent) } catch (e: Exception) { onError(e.message ?: "err") }
    }
}
