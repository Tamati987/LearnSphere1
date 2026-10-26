package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.File
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
 * Service au premier plan : écoute « Hey Jarvis », transcrit la demande,
 * la confie à Claude puis lit la réponse à voix haute.
 */
class JarvisService : Service(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var wakeWord: WakeWordListener? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var brain: JarvisBrain
    private lateinit var actions: PhoneActions
    private var smsReceiverRegistered = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    /** Nouveaux SMS : Jarvis les annonce à voix haute. */
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            // Un long SMS arrive en plusieurs morceaux : on les recolle par expéditeur.
            parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, pieces) ->
                announceSms(address, pieces.joinToString("") { it.messageBody.orEmpty() })
            }
        }
    }
    private var voice: ElevenLabsVoice? = null
    // Certains téléphones refusent le générateur de bips : Jarvis s'en passe alors.
    private val beep = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    private var followUp = false

    // Lecture phrase par phrase : la voix de chaque phrase est générée dans l'ordre
    // pendant que Claude écrit la suite, puis jouée dès qu'elle est prête.
    private val synth = Executors.newSingleThreadExecutor()
    private val playQueue = ArrayDeque<Pair<String, File?>>()
    private var playing = false
    private var replyDone = false
    @Volatile private var speechGeneration = 0
    private var ttsState = TTS_PENDING
    private var utterance = 0
    private var currentUtterance: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        actions = PhoneActions(this)
        brain = JarvisBrain(prefs.anthropicKey, actions)
        if (prefs.announceSms &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.registerReceiver(
                this, smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            smsReceiverRegistered = true
        }
        voice = prefs.elevenLabsKey.takeIf { it.isNotBlank() }?.let { ElevenLabsVoice(this, it) }
        tts = TextToSpeech(this, this)
        startInForeground()
        // Garde le processeur éveillé écran éteint : sinon le micro s'arrête et « Hey Jarvis » n'est plus entendu.
        wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen")
            .apply { setReferenceCounted(false); acquire() }
        startWakeWord()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> main.post { onWakeWord() }
            ACTION_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { handle(it) }
            ACTION_RESET -> brain.reset()
            ACTION_GREET -> greet()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.release()
        wakeWord?.release()
        if (smsReceiverRegistered) unregisterReceiver(smsReceiver)
        recognizer?.destroy()
        tts.shutdown()
        voice?.stop()
        beep?.release()
        worker.shutdownNow()
        synth.shutdownNow()
        JarvisBus.state(JarvisBus.State.OFF)
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsState = TTS_READY
            tts.language = Locale.FRANCE
            tts.setPitch(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { onUtteranceDone(utteranceId) } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { onUtteranceDone(utteranceId) } }
            })
        } else {
            ttsState = TTS_FAILED
        }
        // Des phrases attendaient peut-être que la voix Android soit prête.
        main.post { playNext() }
    }

    // --- Mot d'activation -------------------------------------------------

    private fun startWakeWord() {
        try {
            wakeWord = WakeWordListener(this) { main.post { onWakeWord() } }
        } catch (e: Exception) {
            // Sans détecteur, Jarvis reste utilisable avec le bouton Parler.
            JarvisBus.line(false, "« Hey Jarvis » indisponible : ${e.message}. Utilisez le bouton Parler.")
        }
        resumeWakeWord()
    }

    private fun pauseWakeWord() { wakeWord?.stop() }

    private fun resumeWakeWord() {
        followUp = false
        wakeWord?.start()
        JarvisBus.state(JarvisBus.State.WAITING)
    }

    private fun onWakeWord() {
        if (JarvisBus.state == JarvisBus.State.LISTENING || JarvisBus.state == JarvisBus.State.THINKING) return
        stopSpeech()
        pauseWakeWord()
        beep?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        // Laisse le bip finir avant d'ouvrir le micro.
        main.postDelayed({ listen() }, 150)
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
        val gen = startReply()
        worker.execute {
            var queued = 0
            // Chaque phrase part vers la voix dès que Claude l'a écrite.
            val answer = brain.ask(text) { sentence -> queued++; queueSentence(gen, sentence) }
            if (queued == 0) queueSentence(gen, answer)
            finishReply(gen, answer)
        }
    }

    private fun announceSms(address: String, body: String) {
        worker.execute {
            val name = actions.contactName(address)
            val from = name ?: "le $address"
            val spoken = "Monsieur, nouveau message de $from : « ${body.take(400)} ». Souhaitez-vous répondre ?"
            brain.recordEvent("Nouveau SMS reçu de ${name ?: "numéro inconnu"} ($address) : $body", spoken)
            main.post {
                // Occupé à écouter ou à répondre : le message reste affiché sans interrompre.
                if (JarvisBus.state != JarvisBus.State.WAITING) {
                    JarvisBus.line(false, spoken)
                    return@post
                }
                say(spoken)
            }
        }
    }

    /** Message de bienvenue après la séquence de démarrage. */
    private fun greet() {
        if (JarvisBus.state == JarvisBus.State.THINKING || JarvisBus.state == JarvisBus.State.LISTENING) return
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (hour) {
            in 5..11 -> "Bonjour, Monsieur."
            in 12..17 -> "Bon après-midi, Monsieur."
            else -> "Bonsoir, Monsieur."
        }
        say("$hello Initialisation terminée. Tous les systèmes sont opérationnels. Je suis à votre service.")
    }

    /** Dit un texte prêt à l'avance, découpé en phrases pour démarrer plus vite. */
    private fun say(text: String) {
        pauseWakeWord()
        JarvisBus.state(JarvisBus.State.THINKING)
        val gen = startReply()
        SentenceSplitter { queueSentence(gen, it) }.apply { add(text); flush() }
        finishReply(gen, text)
    }

    // --- Lecture phrase par phrase ----------------------------------------

    /** Coupe ce qui se dit et prépare une nouvelle réponse. Renvoie son numéro. */
    private fun startReply(): Int {
        stopSpeech()
        return speechGeneration
    }

    private fun stopSpeech() {
        speechGeneration++
        playQueue.forEach { it.second?.delete() }
        playQueue.clear()
        playing = false
        replyDone = false
        currentUtterance = null
        tts.stop()
        voice?.stop()
    }

    /** Génère la voix d'une phrase (dans l'ordre d'arrivée) puis la met en file de lecture. */
    private fun queueSentence(gen: Int, sentence: String) {
        synth.execute {
            if (gen != speechGeneration) return@execute
            val audio = voice?.let { v ->
                runCatching { v.synthesize(sentence) }
                    .onFailure { JarvisBus.line(false, "Voix ElevenLabs indisponible (${it.message}), voix Android utilisée.") }
                    .getOrNull()
            }
            main.post {
                if (gen != speechGeneration) { audio?.delete(); return@post }
                playQueue.addLast(sentence to audio)
                playNext()
            }
        }
    }

    /** Signale la fin de la réponse, une fois toutes ses phrases passées en file. */
    private fun finishReply(gen: Int, answer: String) {
        synth.execute {
            main.post {
                if (gen != speechGeneration) return@post
                JarvisBus.line(false, answer)
                // Une question appelle une réponse : on rouvre le micro sans attendre « Hey Jarvis ».
                followUp = answer.trimEnd().endsWith("?")
                replyDone = true
                playNext()
            }
        }
    }

    private fun playNext() {
        if (playing) return
        val next = playQueue.removeFirstOrNull()
        if (next == null) {
            if (replyDone) { replyDone = false; afterSpeaking() }
            return
        }
        val (text, audio) = next
        // Voix Android pas encore prête : on attend son initialisation.
        if (audio == null && ttsState == TTS_PENDING) { playQueue.addFirst(next); return }
        playing = true
        JarvisBus.state(JarvisBus.State.SPEAKING)
        val v = voice
        if (audio != null && v != null) {
            try {
                v.play(audio) { onUtteranceDone() }
                return
            } catch (e: Exception) {
                audio.delete()
            }
        }
        if (ttsState == TTS_READY) {
            currentUtterance = "jarvis-${utterance++}"
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, currentUtterance)
        } else {
            onUtteranceDone()
        }
    }

    /** Fin d'une phrase ; une fin signalée pour une phrase déjà coupée est ignorée. */
    private fun onUtteranceDone(utteranceId: String? = null) {
        if (utteranceId != null && utteranceId != currentUtterance) return
        playing = false
        playNext()
    }

    private fun afterSpeaking() {
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
            .setContentText("Dites « Hey Jarvis » ou touchez Parler.")
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
        const val ACTION_GREET = "com.jarvis.assistant.GREET"
        private const val TTS_PENDING = 0
        private const val TTS_READY = 1
        private const val TTS_FAILED = 2
        const val EXTRA_TEXT = "text"

        fun start(context: Context, action: String? = null, text: String? = null) {
            val intent = Intent(context, JarvisService::class.java).setAction(action)
            text?.let { intent.putExtra(EXTRA_TEXT, it) }
            context.startForegroundService(intent)
        }
    }
}
