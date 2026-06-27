package com.minidraco.whisperer

/**
 * A swappable on-device speech-to-text backend.
 *
 * The app talks only to this interface, so the engine (Vosk today, whisper.cpp
 * or another later) can be replaced without touching the UI — same idea as the
 * desktop version's pluggable model layer.
 */
interface SpeechEngine {

    /** Called as soon as the engine has loaded its model and is usable. */
    fun prepare(onReady: () -> Unit, onError: (String) -> Unit)

    /**
     * Begin streaming recognition from the microphone.
     * @param onPartial in-progress (unstable) text for the current utterance
     * @param onFinal   a finalized chunk of transcript
     */
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    )

    /** Stop streaming (keeps the model loaded so start() is cheap next time). */
    fun stop()

    /** Whether recognition is currently running. */
    val isListening: Boolean

    /** Free all native resources. */
    fun release()
}
