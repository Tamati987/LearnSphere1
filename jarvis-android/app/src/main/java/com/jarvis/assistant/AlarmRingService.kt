package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Fait sonner un réveil de Jarvis : carillon puis sa voix, en boucle jusqu'à « Arrêter » ou
 * « Encore 10 min ». Tout se passe dans ce service au premier plan, lancé par l'alarme :
 * le réveil sonne même si le téléphone (HyperOS, MIUI…) empêche l'écran de réveil de s'ouvrir.
 * L'écran [WakeUpActivity] s'affiche par la notification plein écran, comme une appli d'alarme.
 */
class AlarmRingService : Service(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var voice: ElevenLabsVoice? = null
    @Volatile private var voiceFile: File? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var chime: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing = false
    @Volatile private var voiceReady = false
    private var waitedForVoice = 0
    private var savedAlarmVolume = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android exige la notification au premier plan, même pour un simple « Arrêter ».
        if (!ringing && intent?.action != null && intent.action != ACTION_RING) startInForeground()
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> {
                runCatching { JarvisAlarm.snooze(this, text) }
                    .onFailure { JarvisBus.line(false, "Impossible de reprogrammer le réveil dans 10 minutes : ${it.message}") }
                JarvisBus.line(false, "Réveil repoussé de 10 minutes.")
                stopRinging()
            }
            else -> ring(intent?.getIntExtra(JarvisAlarm.EXTRA_ID, -1) ?: -1)
        }
        return START_NOT_STICKY
    }

    private fun ring(alarmId: Int) {
        val alarm = JarvisAlarm.get(this, alarmId)
        // Reprogramme tout de suite la prochaine fois (ou efface un réveil unique).
        runCatching { JarvisAlarm.fired(this, alarmId) }
            .onFailure { JarvisBus.line(false, "Réveil : prochaine sonnerie non programmée (${it.message}).") }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        time = String.format(Locale.FRANCE, "%02d:%02d", hour, minute)
        text = alarm?.text ?: run {
            val hello = if (hour in 5..17) "Bonjour, Monsieur." else "Bonsoir, Monsieur."
            val spoken = if (minute == 0) "$hour heures" else "$hour heures $minute"
            "$hello Il est $spoken. C'est l'heure."
        }
        startInForeground()
        if (ringing) return
        ringing = true
        active = true
        JarvisBus.line(false, "Réveil de $time : ${text.take(60)}…")
        listener?.invoke()

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:alarm")
            .apply { acquire(11 * 60_000L) }
        raiseAlarmVolume()
        // Sur certains téléphones l'écran ne s'ouvre que par la notification ; on essaie aussi directement.
        runCatching { startActivity(screenIntent()) }

        voice = Prefs(this).elevenLabsKey.takeIf { it.isNotBlank() }?.let { ElevenLabsVoice(this, it) }
        tts = TextToSpeech(this, this)
        // Le programme du jour (lu dans l'agenda) puis la voix se préparent pendant que le carillon sonne.
        val withAgenda = alarm?.agenda == true
        worker.execute {
            if (withAgenda) {
                runCatching { PhoneActions(this).todaySummary() }.getOrNull()?.let { summary ->
                    text = "$text $summary"
                    main.post { listener?.invoke() }
                }
            }
            voice?.let { v -> voiceFile = runCatching { v.synthesize(text) }.getOrNull() }
            voiceReady = true
        }
        vibrate()
        cycle()
        // Sécurité : le réveil s'arrête seul au bout de 10 minutes.
        main.postDelayed({ stopRinging() }, 10 * 60_000L)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        engine.language = Locale.FRANCE
        engine.setPitch(0.9f)
        engine.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { scheduleNextCycle() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { scheduleNextCycle() } }
        })
        ttsReady = true
    }

    /** Un tour : carillon, puis la voix de Jarvis, puis une pause avant de recommencer. */
    private fun cycle() {
        if (!ringing) return
        playChime()
        main.postDelayed({ speak() }, 1600)
    }

    private fun speak() {
        if (!ringing) return
        // Attend un peu la voix de Jarvis (jusqu'à 8 s) plutôt que de prendre la voix Android.
        if (voice != null && !voiceReady && waitedForVoice < 16) {
            waitedForVoice++
            main.postDelayed({ speak() }, 500)
            return
        }
        val v = voice
        val file = voiceFile
        if (v != null && file != null) {
            // Copie : le fichier joué est supprimé après la lecture, l'original sert au tour suivant.
            val copy = File.createTempFile("jarvis-wake", ".mp3", cacheDir)
            file.copyTo(copy, overwrite = true)
            runCatching { v.play(copy, alarm = true) { scheduleNextCycle() } }
                .onSuccess { return }
        }
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wake")
        else scheduleNextCycle()
    }

    private fun scheduleNextCycle() {
        if (ringing) main.postDelayed({ cycle() }, 12_000)
    }

    private fun stopRinging() {
        ringing = false
        active = false
        main.removeCallbacksAndMessages(null)
        chime?.release()
        chime = null
        voice?.stop()
        tts?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
        restoreAlarmVolume()
        listener?.invoke()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (ringing) stopRinging()
        tts?.shutdown()
        voiceFile?.delete()
        worker.shutdownNow()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    /** Un réveil au volume d'alarme coupé ne réveille personne : on le monte au moins à mi-course. */
    private fun raiseAlarmVolume() {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current < max / 2) {
            savedAlarmVolume = current
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0) }
        }
    }

    private fun restoreAlarmVolume() {
        if (savedAlarmVolume < 0) return
        runCatching {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
        }
        savedAlarmVolume = -1
    }

    private fun screenIntent() = Intent(this, WakeUpActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Réveils de Jarvis", NotificationManager.IMPORTANCE_HIGH).apply {
                // Le son vient du service (carillon et voix), pas de la notification.
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val screen = PendingIntent.getActivity(this, 10, screenIntent(), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Réveil · $time")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .addAction(0, "Arrêter", command(this, ACTION_STOP, 11))
            .addAction(0, "Encore 10 min", command(this, ACTION_SNOOZE, 12))
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun playChime() {
        chime?.release()
        val pcm = chimePcm()
        chime = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(CHIME_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .apply { write(pcm, 0, pcm.size); play() }
        }.getOrNull()
    }

    /** Carillon montant en trois notes, synthétisé (aucun fichier audio). */
    private fun chimePcm(): ShortArray {
        val n = (CHIME_RATE * 1.5).toInt()
        val out = FloatArray(n)
        listOf(0.0 to 659.3, 0.18 to 880.0, 0.36 to 1318.5).forEach { (start, freq) ->
            val from = (start * CHIME_RATE).toInt()
            for (i in from until n) {
                val s = (i - from).toDouble() / CHIME_RATE
                out[i] += ((sin(2 * PI * freq * s) + 0.35 * sin(4 * PI * freq * s)) * exp(-3.5 * s) * 0.3).toFloat()
            }
        }
        return ShortArray(n) { (out[it].coerceIn(-1f, 1f) * 32000).toInt().toShort() }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0))
    }

    companion object {
        private const val CHANNEL = "jarvis_alarm"
        private const val NOTIFICATION_ID = 7
        private const val CHIME_RATE = 22050
        const val ACTION_RING = "com.jarvis.assistant.ALARM_RING"
        const val ACTION_STOP = "com.jarvis.assistant.ALARM_STOP"
        const val ACTION_SNOOZE = "com.jarvis.assistant.ALARM_SNOOZE"

        /** État affiché par [WakeUpActivity]. */
        @Volatile var active = false
            private set
        @Volatile var time = ""
            private set
        @Volatile var text = ""
            private set
        /** Prévenu (sur le fil principal) quand le texte change ou que le réveil s'arrête. */
        var listener: (() -> Unit)? = null

        fun send(context: Context, action: String, alarmId: Int = -1) {
            context.startForegroundService(
                Intent(context, AlarmRingService::class.java).setAction(action).putExtra(JarvisAlarm.EXTRA_ID, alarmId)
            )
        }

        private fun command(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
            context, code, Intent(context, AlarmRingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** L'alarme d'Android est arrivée : on lance la sonnerie. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(JarvisAlarm.EXTRA_ID, -1)
        runCatching { AlarmRingService.send(context, AlarmRingService.ACTION_RING, id) }
            .onFailure {
                // Dernier recours : l'écran de réveil, qui relance la sonnerie de lui-même.
                JarvisBus.line(false, "Réveil : service de sonnerie refusé (${it.message}), ouverture de l'écran.")
                runCatching {
                    context.startActivity(
                        Intent(context, WakeUpActivity::class.java).putExtra(JarvisAlarm.EXTRA_ID, id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }
}
