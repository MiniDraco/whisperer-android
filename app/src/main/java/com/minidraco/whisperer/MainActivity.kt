package com.minidraco.whisperer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.RangeSlider

/**
 * Whisperer — offline dictation + an A-B loop audio player.
 *
 * The audio player and the speech recognizer run independently, so you can loop
 * a passage of a recording (between the two timeline handles, on repeat) AND
 * dictate at the same time. Headphones recommended so the loop doesn't bleed
 * into the mic.
 */
class MainActivity : AppCompatActivity() {

    // dictation
    private lateinit var engine: SpeechEngine
    private lateinit var status: TextView
    private lateinit var partial: TextView
    private lateinit var notes: EditText
    private lateinit var recordBtn: Button
    private var modelReady = false

    // audio loop player
    private lateinit var audio: AudioLooper
    private lateinit var rangeSlider: RangeSlider
    private lateinit var playBtn: Button
    private lateinit var audioTime: TextView

    private val askMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) prepareEngine()
            else status.text = getString(R.string.need_mic)
        }

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { onAudioPicked(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        partial = findViewById(R.id.partial)
        notes = findViewById(R.id.notes)
        recordBtn = findViewById(R.id.recordBtn)
        engine = VoskEngine(applicationContext)

        rangeSlider = findViewById(R.id.rangeSlider)
        playBtn = findViewById(R.id.playBtn)
        audioTime = findViewById(R.id.audioTime)
        audio = AudioLooper(applicationContext)
        audio.setOnTick { pos -> updateAudioLabel(pos) }

        recordBtn.setOnClickListener { toggleRecord() }
        findViewById<Button>(R.id.copyBtn).setOnClickListener { copyNotes() }
        findViewById<Button>(R.id.shareBtn).setOnClickListener { shareNotes() }
        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            notes.setText("")
            partial.text = ""
        }

        // --- audio loop controls ---
        findViewById<Button>(R.id.loadAudioBtn).setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }
        playBtn.setOnClickListener {
            val playing = audio.toggle()
            playBtn.setText(if (playing) R.string.pause else R.string.play)
        }
        rangeSlider.addOnChangeListener { slider, _, _ ->
            val v = slider.values
            if (v.size == 2) {
                val a = (v[0] * 1000).toInt()
                val b = (v[1] * 1000).toInt()
                if (b - a >= 300) audio.setLoop(a, b)   // ignore degenerate loops
            }
        }

        recordBtn.isEnabled = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            prepareEngine()
        } else {
            status.text = getString(R.string.requesting_mic)
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---------- dictation ----------
    private fun prepareEngine() {
        status.text = getString(R.string.loading_model)
        engine.prepare(
            onReady = {
                runOnUiThread {
                    modelReady = true
                    recordBtn.isEnabled = true
                    status.text = getString(R.string.ready)
                }
            },
            onError = { msg -> runOnUiThread { status.text = msg } }
        )
    }

    private fun toggleRecord() {
        if (!modelReady) return
        if (engine.isListening) {
            engine.stop()
            recordBtn.setText(R.string.record)
            status.text = getString(R.string.ready)
            commitPartial()
        } else {
            engine.start(
                onPartial = { text -> runOnUiThread { partial.text = text } },
                onFinal = { text -> runOnUiThread { appendFinal(text) } },
                onError = { msg -> runOnUiThread { status.text = msg } }
            )
            recordBtn.setText(R.string.stop)
            status.text = getString(R.string.listening)
        }
    }

    private fun appendFinal(text: String) {
        partial.text = ""
        val cur = notes.text.toString()
        val sep = if (cur.isEmpty() || cur.endsWith(" ") || cur.endsWith("\n")) "" else " "
        notes.append("$sep$text")
    }

    private fun commitPartial() {
        val p = partial.text.toString().trim()
        if (p.isNotEmpty()) appendFinal(p)
    }

    // ---------- audio loop ----------
    private fun onAudioPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        audioTime.text = getString(R.string.loading_model)
        audio.load(
            uri,
            onReady = { dur -> runOnUiThread { configureSlider(dur) } },
            onError = { msg -> runOnUiThread { audioTime.text = msg } }
        )
    }

    private fun configureSlider(durationMs: Int) {
        if (durationMs <= 0) {
            audioTime.text = getString(R.string.no_audio)
            return
        }
        val durSec = durationMs / 1000f
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = durSec
        rangeSlider.values = listOf(0f, durSec)
        rangeSlider.isEnabled = true
        playBtn.isEnabled = true
        audio.setLoop(0, durationMs)
        updateAudioLabel(0)
    }

    private fun updateAudioLabel(posMs: Int) {
        val v = if (rangeSlider.isEnabled && rangeSlider.values.size == 2)
            rangeSlider.values else listOf(0f, 0f)
        audioTime.text = "${fmt(posMs)}  ·  loop ${fmt((v[0] * 1000).toInt())}–${fmt((v[1] * 1000).toInt())}"
    }

    private fun fmt(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ---------- notes actions ----------
    private fun copyNotes() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("notes", notes.text.toString()))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareNotes() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, notes.text.toString())
        }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    override fun onDestroy() {
        audio.release()
        engine.release()
        super.onDestroy()
    }
}
