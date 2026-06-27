package com.minidraco.whisperer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Whisperer — offline dictation notes. Tap record, speak, and the transcript
 * fills the editable notes field. Copy / share / clear. 100% on-device.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var engine: SpeechEngine
    private lateinit var status: TextView
    private lateinit var partial: TextView
    private lateinit var notes: EditText
    private lateinit var recordBtn: Button

    private var modelReady = false

    private val askMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) prepareEngine()
            else status.text = getString(R.string.need_mic)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        partial = findViewById(R.id.partial)
        notes = findViewById(R.id.notes)
        recordBtn = findViewById(R.id.recordBtn)
        engine = VoskEngine(applicationContext)

        recordBtn.setOnClickListener { toggle() }
        findViewById<Button>(R.id.copyBtn).setOnClickListener { copyNotes() }
        findViewById<Button>(R.id.shareBtn).setOnClickListener { shareNotes() }
        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            notes.setText("")
            partial.text = ""
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

    private fun toggle() {
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

    private fun copyNotes() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("notes", notes.text.toString()))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareNotes() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, notes.text.toString())
        }
        startActivity(android.content.Intent.createChooser(send, getString(R.string.share)))
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
