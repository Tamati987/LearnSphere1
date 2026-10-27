package com.jarvis.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Écoute le micro en continu et appelle [onWake] quand le score de « Hey Jarvis » atteint [threshold].
 * [stop] libère le micro, pour laisser la reconnaissance vocale l'utiliser.
 */
class WakeWordListener(context: Context, private val threshold: Float, private val onWake: () -> Unit) {

    private val detector = WakeWordDetector(
        context.assets.open("wakeword/melspectrogram.onnx").use { it.readBytes() },
        context.assets.open("wakeword/embedding_model.onnx").use { it.readBytes() },
        context.assets.open("wakeword/hey_jarvis_v0.1.onnx").use { it.readBytes() },
    )
    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastNearMiss = 0L

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
                    if (score >= threshold) {
                        running = false
                        onWake()
                    } else if (score >= threshold / 3) {
                        nearMiss(score)
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

    /**
     * « Hey Jarvis » presque reconnu : noté dans le journal (au plus toutes les 20 s) pour
     * savoir s'il faut augmenter la sensibilité.
     */
    private fun nearMiss(score: Float) {
        val now = System.currentTimeMillis()
        if (now - lastNearMiss < 20_000) return
        lastNearMiss = now
        JarvisBus.line(false, "« Hey Jarvis » presque reconnu (score %.2f, seuil %.2f).".format(score, threshold))
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
