package com.jarvis.assistant

import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Écran du réveil : s'allume par-dessus l'écran verrouillé, fait sonner un carillon
 * puis Jarvis annonce l'heure de sa voix, en boucle jusqu'à « Arrêter » ou « Encore 10 min ».
 */
class WakeUpActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var voice: ElevenLabsVoice? = null
    @Volatile private var voiceFile: File? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var chime: AudioTrack? = null
    private var stopped = false
    private lateinit var wakeText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val alarmId = intent.getIntExtra(JarvisAlarm.EXTRA_ID, -1)
        val alarm = JarvisAlarm.get(this, alarmId)
        // Reprogramme tout de suite la prochaine fois (ou efface un réveil unique).
        runCatching { JarvisAlarm.fired(this, alarmId) }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        wakeText = alarm?.text ?: run {
            val hello = if (hour in 5..17) "Bonjour, Monsieur." else "Bonsoir, Monsieur."
            val time = if (minute == 0) "$hour heures" else "$hour heures $minute"
            "$hello Il est $time. C'est l'heure."
        }

        setContentView(layout(String.format(Locale.FRANCE, "%02d:%02d", hour, minute)))

        voice = Prefs(this).elevenLabsKey.takeIf { it.isNotBlank() }?.let { ElevenLabsVoice(this, it) }
        tts = TextToSpeech(this, this)
        // La voix se génère pendant que le carillon sonne.
        voice?.let { v -> worker.execute { voiceFile = runCatching { v.synthesize(wakeText) }.getOrNull() } }
        vibrate()
        cycle()
        // Sécurité : le réveil s'arrête seul au bout de 10 minutes.
        main.postDelayed({ stopAlarm() }, 10 * 60_000L)
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
        if (stopped) return
        playChime()
        main.postDelayed({ speak() }, 1600)
    }

    private fun speak() {
        if (stopped) return
        val v = voice
        val file = voiceFile
        if (v != null && file != null) {
            // Copie : le fichier joué est supprimé après la lecture, l'original sert au tour suivant.
            val copy = File.createTempFile("jarvis-wake", ".mp3", cacheDir)
            file.copyTo(copy, overwrite = true)
            runCatching { v.play(copy, alarm = true) { scheduleNextCycle() } }
                .onSuccess { return }
        }
        if (ttsReady) tts?.speak(wakeText, TextToSpeech.QUEUE_FLUSH, null, "wake")
        else scheduleNextCycle()
    }

    private fun scheduleNextCycle() {
        if (!stopped) main.postDelayed({ cycle() }, 12_000)
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        main.removeCallbacksAndMessages(null)
        chime?.release()
        chime = null
        voice?.stop()
        tts?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
        finish()
    }

    override fun onDestroy() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        chime?.release()
        voice?.stop()
        tts?.shutdown()
        getSystemService(Vibrator::class.java)?.cancel()
        voiceFile?.delete()
        worker.shutdownNow()
        super.onDestroy()
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

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun layout(time: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(getColor(R.color.bg))
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        addView(TextView(context).apply {
            text = "JARVIS · RÉVEIL"
            setTextColor(getColor(R.color.dim))
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = time
            setTextColor(getColor(R.color.core))
            textSize = 84f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 0f, getColor(R.color.core))
        })
        addView(TextView(context).apply {
            text = wakeText
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad * 2)
        })
        addView(Button(context).apply {
            text = "Arrêter"
            isAllCaps = false
            textSize = 18f
            setTextColor(getColor(R.color.on_core))
            setBackgroundColor(getColor(R.color.core))
            setOnClickListener { stopAlarm() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()))
        addView(Button(context).apply {
            text = "Encore 10 min"
            isAllCaps = false
            textSize = 16f
            setTextColor(getColor(R.color.core))
            setBackgroundColor(getColor(R.color.panel))
            setOnClickListener {
                runCatching { JarvisAlarm.snooze(context, wakeText) }
                stopAlarm()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()).apply {
            topMargin = pad / 2
        })
    }

    companion object {
        private const val CHIME_RATE = 22050
    }
}
