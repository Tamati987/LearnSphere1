package com.jarvis.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Écoute le micro en continu et appelle [onWake] quand « Hey Jarvis » est détecté.
 * [stop] libère le micro, pour laisser la reconnaissance vocale l'utiliser.
 */
class WakeWordListener(context: Context, private val onWake: () -> Unit) {

    private val detector = WakeWordDetector(
        context.assets.open("wakeword/melspectrogram.onnx").use { it.readBytes() },
        context.assets.open("wakeword/embedding_model.onnx").use { it.readBytes() },
        context.assets.open("wakeword/hey_jarvis_v0.1.onnx").use { it.readBytes() },
    )
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // Le service ne démarre qu'une fois le micro autorisé.
    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val minBuffer = AudioRecord.getMinBufferSize(
                WakeWordDetector.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                WakeWordDetector.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, WakeWordDetector.CHUNK * 2 * 4),
            )
            val chunk = ShortArray(WakeWordDetector.CHUNK)
            synchronized(detector) { detector.reset() }
            try {
                record.startRecording()
                while (running) {
                    var filled = 0
                    while (filled < chunk.size && running) {
                        val n = record.read(chunk, filled, chunk.size - filled)
                        if (n < 0) throw IllegalStateException("micro indisponible ($n)")
                        filled += n
                    }
                    if (!running) break
                    val score = synchronized(detector) { detector.process(chunk) }
                    if (score >= WakeWordDetector.THRESHOLD) {
                        running = false
                        onWake()
                    }
                }
            } catch (e: Exception) {
                running = false
                JarvisBus.line(false, "Écoute de « Hey Jarvis » interrompue : ${e.message}. Utilisez le bouton Parler.")
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }, "hey-jarvis").apply { start() }
    }

    /** Arrête l'écoute et attend que le micro soit libéré. */
    @Synchronized
    fun stop() {
        running = false
        thread?.takeIf { it != Thread.currentThread() }?.join(500)
        thread = null
    }

    fun release() {
        stop()
        synchronized(detector) { detector.close() }
    }
}
