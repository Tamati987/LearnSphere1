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
        val announceSms = findViewById<android.widget.CheckBox>(R.id.announce_sms)
        announceSms.isChecked = prefs.announceSms

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.anthropicKey = anthropic.text.toString()
            prefs.elevenLabsKey = elevenLabs.text.toString()
            prefs.announceSms = announceSms.isChecked
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
        findViewById<Button>(R.id.notifications).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.battery).setOnClickListener {
            // Sans cette exemption, Android coupe l'écoute quand l'écran est éteint.
            @android.annotation.SuppressLint("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }
                .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
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

        // Réveils de départ (6 h travail, midi déjeuner), installés une seule fois.
        runCatching { JarvisAlarm.installDefaults(this) }
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
        val boot = BootSequenceView(
            this,
            onGreet = {
                // La voix de Jarvis souhaite la bienvenue pendant l'apparition des cercles.
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    JarvisService.start(this, JarvisService.ACTION_GREET)
                }
            },
            onFinished = {
                root.findViewWithTag<View>(BOOT_TAG)?.let(root::removeView)
                bootTrack?.release()
                bootTrack = null
            },
        )
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
        // Position mémorisée pour la météo (la position n'est lisible que quand Jarvis est à l'écran).
        runCatching { Weather.rememberLocation(this) }
        // Réaffiche tout le journal, y compris ce qui s'est passé appli fermée, avec un diagnostic en tête.
        log.text = ""
        log.append("DIAGNOSTIC  " + diagnostic())
        JarvisBus.lines().forEach { (fromUser, text) -> onLine(fromUser, text) }
        findViewById<Button>(R.id.overlay).visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        val listening = MessageListener.isEnabled(this)
        findViewById<Button>(R.id.notifications).visibility = if (listening) View.GONE else View.VISIBLE
        // Après une mise à jour, Android peut oublier de reconnecter l'accès aux notifications.
        if (listening) {
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, MessageListener::class.java)
                )
            }
        }
        val power = getSystemService(android.os.PowerManager::class.java)
        findViewById<Button>(R.id.battery).visibility =
            if (power.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
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
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
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

    /** État de ce dont les annonces ont besoin, pour repérer d'un coup d'œil ce qui bloque. */
    private fun diagnostic(): String {
        fun ok(b: Boolean) = if (b) "✓" else "✗"
        fun perm(p: String) = checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val ringer = getSystemService(android.media.AudioManager::class.java).ringerMode ==
            android.media.AudioManager.RINGER_MODE_NORMAL
        val power = getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        return listOf(
            "service ${ok(JarvisService.instance != null)}",
            "accès notifications ${ok(MessageListener.isEnabled(this))}",
            "téléphone ${ok(perm(Manifest.permission.READ_PHONE_STATE))}",
            "journal d'appels ${ok(perm(Manifest.permission.READ_CALL_LOG))}",
            "SMS ${ok(perm(Manifest.permission.RECEIVE_SMS))}",
            "annonces ${ok(prefs.announceSms)}",
            "sonnerie ${ok(ringer)}",
            "batterie sans restriction ${ok(power)}",
        ).joinToString(" · ")
    }

    override fun onLine(fromUser: Boolean, text: String) {
        log.append((if (log.text.isEmpty()) "" else "\n\n") + (if (fromUser) "VOUS  " else "JARVIS  ") + text)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
