package com.jarvis.assistant

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Détecteur du mot d'activation « Hey Jarvis » (modèles openWakeWord, sur le téléphone).
 *
 * Reprend le traitement en continu d'openWakeWord : l'audio 16 kHz arrive par blocs
 * de 1280 échantillons (80 ms), devient un spectrogramme mel, puis un vecteur de
 * 96 valeurs ; les 16 derniers vecteurs donnent le score du mot.
 */
class WakeWordDetector(melModel: ByteArray, embeddingModel: ByteArray, wakeModel: ByteArray) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val mel = env.createSession(melModel)
    private val embedding = env.createSession(embeddingModel)
    private val wake = env.createSession(wakeModel)

    // Les 1280 nouveaux échantillons plus 480 de contexte, comme openWakeWord.
    private val raw = FloatArray(CHUNK + 3 * 160)
    private val melFrames = ArrayDeque<FloatArray>()
    private val features = ArrayDeque<FloatArray>()
    private var chunks = 0

    init { reset() }

    fun reset() {
        raw.fill(0f)
        melFrames.clear()
        repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }
        features.clear()
        chunks = 0
    }

    /** Traite 1280 échantillons PCM 16 bits et renvoie le score de « Hey Jarvis », de 0 à 1. */
    fun process(chunk: ShortArray): Float {
        require(chunk.size == CHUNK) { "Il faut des blocs de $CHUNK échantillons." }
        System.arraycopy(raw, CHUNK, raw, 0, raw.size - CHUNK)
        for (i in 0 until CHUNK) raw[raw.size - CHUNK + i] = chunk[i].toFloat()
        chunks++

        // 1. Spectrogramme mel des nouveaux échantillons.
        run(mel, raw, longArrayOf(1, raw.size.toLong())) { out ->
            val values = out.floatBuffer
            val frames = values.remaining() / MEL_BINS
            repeat(frames) {
                melFrames.addLast(FloatArray(MEL_BINS) { values.get() / 10f + 2f })
            }
        }
        while (melFrames.size > MEL_WINDOW) melFrames.removeFirst()

        // 2. Vecteur de 96 valeurs à partir des 76 dernières trames.
        val window = FloatArray(MEL_WINDOW * MEL_BINS)
        melFrames.forEachIndexed { i, frame -> frame.copyInto(window, i * MEL_BINS) }
        run(embedding, window, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1)) { out ->
            val v = FloatArray(EMBEDDING)
            out.floatBuffer.get(v)
            features.addLast(v)
        }
        while (features.size > FEATURE_WINDOW) features.removeFirst()

        // 3. Score du mot sur les 16 derniers vecteurs ; rien avant d'avoir assez de contexte.
        if (features.size < FEATURE_WINDOW || chunks < WARMUP_CHUNKS) return 0f
        val input = FloatArray(FEATURE_WINDOW * EMBEDDING)
        features.forEachIndexed { i, f -> f.copyInto(input, i * EMBEDDING) }
        var score = 0f
        run(wake, input, longArrayOf(1, FEATURE_WINDOW.toLong(), EMBEDDING.toLong())) { out ->
            score = out.floatBuffer.get(0)
        }
        return score
    }

    private inline fun run(session: OrtSession, data: FloatArray, shape: LongArray, read: (OnnxTensor) -> Unit) {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                read(result.get(0) as OnnxTensor)
            }
        }
    }

    override fun close() {
        mel.close(); embedding.close(); wake.close()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK = 1280
        const val THRESHOLD = 0.5f
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val EMBEDDING = 96
        private const val FEATURE_WINDOW = 16
        private const val WARMUP_CHUNKS = 20
    }
}
