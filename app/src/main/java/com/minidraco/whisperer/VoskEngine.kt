package com.minidraco.whisperer

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Vosk (Kaldi) on-device backend. Offline, streaming, no network. The model is
 * shipped in assets/model and unpacked to internal storage on first run.
 */
class VoskEngine(private val context: Context) : SpeechEngine {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    override var isListening: Boolean = false
        private set

    override fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        if (model != null) {
            onReady()
            return
        }
        StorageService.unpack(
            context, "model", "vosk-model",
            { m ->
                model = m
                onReady()
            },
            { e -> onError("model load failed: ${e.message}") }
        )
    }

    override fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val m = model ?: run { onError("model not ready"); return }
        if (isListening) return
        try {
            val recognizer = Recognizer(m, 16000.0f)
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    onPartial(extract(hypothesis, "partial"))
                }

                override fun onResult(hypothesis: String?) {
                    val text = extract(hypothesis, "text")
                    if (text.isNotBlank()) onFinal(text)
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = extract(hypothesis, "text")
                    if (text.isNotBlank()) onFinal(text)
                }

                override fun onError(e: Exception?) {
                    onError("recognition error: ${e?.message}")
                }

                override fun onTimeout() {}
            }
            speechService = SpeechService(recognizer, 16000.0f).also {
                it.startListening(listener)
            }
            isListening = true
        } catch (e: Exception) {
            onError("could not start mic: ${e.message}")
        }
    }

    override fun stop() {
        speechService?.stop()
        speechService = null
        isListening = false
    }

    override fun release() {
        stop()
        model?.close()
        model = null
    }

    private fun extract(hypothesis: String?, key: String): String =
        try {
            JSONObject(hypothesis ?: "{}").optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }
}
