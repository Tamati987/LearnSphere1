package com.jarvis.assistant

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale
import java.util.concurrent.Executors

/** Relie le service à l'écran principal, quand il est ouvert. */
object JarvisBus {
    enum class State { OFF, WAITING, LISTENING, THINKING, SPEAKING }

    interface Listener {
        fun onState(state: State)
        fun onLine(fromUser: Boolean, text: String)
    }

    @Volatile var state = State.OFF
        private set
    var listener: Listener? = null

    private val main = Handler(Looper.getMainLooper())

    fun state(s: State) { state = s; main.post { listener?.onState(s) } }
    fun line(fromUser: Boolean, text: String) = main.post { listener?.onLine(fromUser, text) }
}

/**
 * Service au premier plan : écoute le mot « Jarvis », transcrit la demande,
 * la confie à Claude puis lit la réponse à voix haute.
 */
class JarvisService : Service(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var porcupine: PorcupineManager? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var brain: JarvisBrain
    // Certains téléphones refusent le générateur de bips : Jarvis s'en passe alors.
    private val beep = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    private var followUp = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        brain = JarvisBrain(prefs.anthropicKey, PhoneActions(this))
        tts = TextToSpeech(this, this)
        startInForeground()
        startWakeWord(prefs.picovoiceKey)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> main.post { onWakeWord() }
            ACTION_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { handle(it) }
            ACTION_RESET -> brain.reset()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        porcupine?.let { runCatching { it.stop() }; it.delete() }
        recognizer?.destroy()
        tts.shutdown()
        beep?.release()
        worker.shutdownNow()
        JarvisBus.state(JarvisBus.State.OFF)
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.FRANCE
            tts.setPitch(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { afterSpeaking() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { afterSpeaking() } }
            })
        }
    }

    // --- Mot d'activation -------------------------------------------------

    private fun startWakeWord(accessKey: String) {
        try {
            porcupine = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.6f)
                .setErrorCallback { e -> JarvisBus.line(false, "Erreur du mot d'activation : ${e.message}") }
                .build(applicationContext) { main.post { onWakeWord() } }
            resumeWakeWord()
        } catch (e: PorcupineException) {
            // Sans clé Picovoice valide, Jarvis reste utilisable avec le bouton micro.
            porcupine = null
            JarvisBus.line(false, "Mot « Jarvis » indisponible : ${e.message}. Utilisez le bouton micro.")
            JarvisBus.state(JarvisBus.State.WAITING)
        }
    }

    private fun pauseWakeWord() { runCatching { porcupine?.stop() } }

    private fun resumeWakeWord() {
        followUp = false
        runCatching { porcupine?.start() }
        JarvisBus.state(JarvisBus.State.WAITING)
    }

    private fun onWakeWord() {
        if (JarvisBus.state == JarvisBus.State.LISTENING || JarvisBus.state == JarvisBus.State.THINKING) return
        tts.stop()
        pauseWakeWord()
        beep?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        // Laisse le bip finir avant d'ouvrir le micro.
        main.postDelayed({ listen() }, 250)
    }

    // --- Reconnaissance vocale -------------------------------------------

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            JarvisBus.line(false, "La reconnaissance vocale n'est pas disponible. Installez l'appli Google.")
            resumeWakeWord()
            return
        }
        JarvisBus.state(JarvisBus.State.LISTENING)
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) resumeWakeWord() else handle(text)
                }
                override fun onError(error: Int) = resumeWakeWord()
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            )
        }
    }

    // --- Réflexion et réponse --------------------------------------------

    private fun handle(text: String) {
        pauseWakeWord()
        JarvisBus.line(true, text)
        JarvisBus.state(JarvisBus.State.THINKING)
        worker.execute {
            val answer = brain.ask(text)
            main.post { speak(answer) }
        }
    }

    private fun speak(answer: String) {
        JarvisBus.line(false, answer)
        JarvisBus.state(JarvisBus.State.SPEAKING)
        // Une question appelle une réponse : on rouvre le micro sans attendre « Jarvis ».
        followUp = answer.trimEnd().endsWith("?")
        tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun afterSpeaking() {
        if (JarvisBus.state != JarvisBus.State.SPEAKING) return
        if (followUp) { followUp = false; listen() } else resumeWakeWord()
    }

    // --- Notification ----------------------------------------------------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jarvis à l'écoute", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getForegroundService(
            this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_LISTEN), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jarvis est à l'écoute")
            .setContentText("Dites « Jarvis » ou touchez Parler.")
            .setContentIntent(open)
            .addAction(0, "Parler", talk)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    companion object {
        private const val CHANNEL = "jarvis"
        const val ACTION_LISTEN = "com.jarvis.assistant.LISTEN"
        const val ACTION_TEXT = "com.jarvis.assistant.TEXT"
        const val ACTION_RESET = "com.jarvis.assistant.RESET"
        const val EXTRA_TEXT = "text"

        fun start(context: Context, action: String? = null, text: String? = null) {
            val intent = Intent(context, JarvisService::class.java).setAction(action)
            text?.let { intent.putExtra(EXTRA_TEXT, it) }
            context.startForegroundService(intent)
        }
    }
}
