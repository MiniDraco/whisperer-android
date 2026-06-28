package com.minidraco.whisperer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Plays an audio file and loops a region [A, B] defined by the two timeline
 * thumbs. Repeats forever by default. Runs independently of the speech
 * recognizer, so it can play while dictation is active at the same time.
 */
class AudioLooper(context: Context) {

    private val appContext = context.applicationContext
    private var mp: MediaPlayer? = null

    private var loopA = 0          // ms
    private var loopB = 0          // ms
    var durationMs = 0
        private set

    val isPlaying: Boolean get() = try { mp?.isPlaying == true } catch (_: Exception) { false }

    private val handler = Handler(Looper.getMainLooper())
    private var onTick: ((posMs: Int) -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null) {
                try {
                    val pos = p.currentPosition
                    if (p.isPlaying && loopB > loopA && pos >= loopB) {
                        p.seekTo(loopA.toLong(), MediaPlayer.SEEK_CLOSEST)
                        onTick?.invoke(loopA)
                    } else {
                        onTick?.invoke(pos)
                    }
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 50)
        }
    }

    fun setOnTick(cb: (posMs: Int) -> Unit) { onTick = cb }

    fun load(uri: Uri, onReady: (durationMs: Int) -> Unit, onError: (String) -> Unit) {
        release()
        mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener {
                durationMs = duration
                loopA = 0
                loopB = duration
                onReady(durationMs)
            }
            setOnErrorListener { _, what, extra ->
                onError("audio error $what/$extra")
                true
            }
            try {
                setDataSource(appContext, uri)
                prepareAsync()
            } catch (e: Exception) {
                onError("could not open audio: ${e.message}")
            }
        }
    }

    /** Constrain playback to the slider region; jump inside if currently out. */
    fun setLoop(aMs: Int, bMs: Int) {
        loopA = aMs
        loopB = bMs
        val p = mp ?: return
        try {
            val pos = p.currentPosition
            if (pos < aMs || pos > bMs) p.seekTo(aMs.toLong(), MediaPlayer.SEEK_CLOSEST)
        } catch (_: Exception) {
        }
    }

    fun play() {
        val p = mp ?: return
        try {
            val pos = p.currentPosition
            if (pos < loopA || pos >= loopB) p.seekTo(loopA.toLong(), MediaPlayer.SEEK_CLOSEST)
            p.start()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        } catch (_: Exception) {
        }
    }

    fun pause() {
        try { mp?.pause() } catch (_: Exception) {}
    }

    /** @return true if now playing. */
    fun toggle(): Boolean {
        if (isPlaying) pause() else play()
        return isPlaying
    }

    fun release() {
        handler.removeCallbacks(ticker)
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        durationMs = 0
    }
}
