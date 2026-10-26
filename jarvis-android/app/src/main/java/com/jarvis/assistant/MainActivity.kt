package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), JarvisBus.Listener {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var setup: View
    private lateinit var console: View
    private lateinit var toggle: Button
    private var bootTrack: AudioTrack? = null

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) JarvisService.start(this)
            else status.text = "Jarvis a besoin du micro pour vous entendre."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)
        scroll = findViewById(R.id.scroll)
        setup = findViewById(R.id.setup)
        console = findViewById(R.id.console)
        toggle = findViewById(R.id.toggle)

        val anthropic = findViewById<EditText>(R.id.anthropic_key)
        val elevenLabs = findViewById<EditText>(R.id.elevenlabs_key)
        anthropic.setText(prefs.anthropicKey)
        elevenLabs.setText(prefs.elevenLabsKey)

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.anthropicKey = anthropic.text.toString()
            prefs.elevenLabsKey = elevenLabs.text.toString()
            if (prefs.isConfigured) {
                stopService(Intent(this, JarvisService::class.java))
                showConsole()
                startJarvis()
            } else {
                anthropic.error = "Clé requise"
            }
        }
        findViewById<Button>(R.id.settings).setOnClickListener { showSetup() }
        findViewById<Button>(R.id.overlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        toggle.setOnClickListener {
            if (JarvisBus.state == JarvisBus.State.OFF) startJarvis()
            else stopService(Intent(this, JarvisService::class.java))
        }
        findViewById<Button>(R.id.talk).setOnClickListener {
            JarvisService.start(this, JarvisService.ACTION_LISTEN)
        }
        val input = findViewById<EditText>(R.id.input)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && input.text.isNotBlank()) {
                JarvisService.start(this, JarvisService.ACTION_TEXT, input.text.toString())
                input.text.clear()
                true
            } else false
        }

        if (prefs.isConfigured) {
            showConsole()
            startJarvis()
            // La séquence de démarrage ne joue qu'à l'ouverture, pas après une rotation.
            if (savedInstanceState == null) playBootSequence()
        } else {
            showSetup()
        }
    }

    override fun onDestroy() {
        bootTrack?.release()
        bootTrack = null
        super.onDestroy()
    }

    /** Animation et sons de démarrage, puis message de bienvenue de Jarvis. */
    private fun playBootSequence() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val boot = BootSequenceView(this) {
            root.findViewWithTag<View>(BOOT_TAG)?.let(root::removeView)
            bootTrack?.release()
            bootTrack = null
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                JarvisService.start(this, JarvisService.ACTION_GREET)
            }
        }
        boot.tag = BOOT_TAG
        root.addView(boot, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        bootTrack = runCatching { bootSoundTrack() }.getOrNull()
        bootTrack?.play()
        boot.start()
    }

    private fun bootSoundTrack(): AudioTrack {
        val pcm = BootSound.render()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(BootSound.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    override fun onResume() {
        super.onResume()
        JarvisBus.listener = this
        onState(JarvisBus.state)
        findViewById<Button>(R.id.overlay).visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        JarvisBus.listener = null
        super.onPause()
    }

    private fun startJarvis() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        askPermissions.launch(wanted.toTypedArray())
    }

    private fun showSetup() { setup.visibility = View.VISIBLE; console.visibility = View.GONE }
    private fun showConsole() { setup.visibility = View.GONE; console.visibility = View.VISIBLE }

    override fun onState(state: JarvisBus.State) {
        status.text = when (state) {
            JarvisBus.State.OFF -> "Hors ligne"
            JarvisBus.State.WAITING -> "En attente · dites « Hey Jarvis »"
            JarvisBus.State.LISTENING -> "Je vous écoute…"
            JarvisBus.State.THINKING -> "Analyse en cours…"
            JarvisBus.State.SPEAKING -> "Réponse"
        }
        toggle.text = if (state == JarvisBus.State.OFF) "Activer" else "Pause"
    }

    companion object {
        private const val BOOT_TAG = "boot"
    }

    override fun onLine(fromUser: Boolean, text: String) {
        log.append((if (log.text.isEmpty()) "" else "\n\n") + (if (fromUser) "VOUS  " else "JARVIS  ") + text)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
