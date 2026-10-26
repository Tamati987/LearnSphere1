package com.jarvis.assistant

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bande-son du démarrage, synthétisée sur le téléphone (aucun fichier audio) :
 * montée en puissance, tics de chargement, allumage du cœur, données qui défilent,
 * puis carillon final. Calée sur les phases de [BootSequenceView].
 */
object BootSound {
    const val SAMPLE_RATE = 22050
    const val DURATION_MS = 5600

    fun render(): ShortArray {
        val n = SAMPLE_RATE * DURATION_MS / 1000
        val mix = FloatArray(n)
        val rnd = Random(7)

        // 0 – 1,8 s : bourdonnement grave qui monte, avec un souffle filtré.
        var lp = 0f
        sweep(mix, 0.0, 1.8, 70.0, 180.0, 0.22f) { p -> (p * p).toFloat() }
        for (i in 0 until (1.8 * SAMPLE_RATE).toInt()) {
            lp += 0.04f * (rnd.nextFloat() * 2 - 1 - lp)
            mix[i] += lp * 0.5f * (i / (1.8f * SAMPLE_RATE))
        }
        // Tics de la barre de chargement, tous les 10 %.
        for (k in 0..10) blip(mix, 0.1 + k * 0.165, 2200.0, 0.025, 0.18f)

        // 1,8 – 2,6 s : allumage du cœur, glissando vers l'aigu.
        sweep(mix, 1.8, 0.8, 260.0, 1300.0, 0.28f) { p -> sin(p * PI).toFloat() }
        sweep(mix, 1.8, 1.6, 55.0, 55.0, 0.30f) { p -> exp(-3.0 * p).toFloat() }

        // 3,6 – 5 s : données qui défilent pendant l'écriture du texte.
        var t = 3.6
        while (t < 5.0) {
            blip(mix, t, 1400.0 + rnd.nextInt(5) * 300.0, 0.018, 0.10f)
            t += 0.07 + rnd.nextDouble() * 0.05
        }

        // 5 s : carillon « systèmes en ligne ».
        bell(mix, 5.0, 880.0, 0.20f)
        bell(mix, 5.12, 1320.0, 0.16f)

        // Normalise le volume : le son le plus fort atteint 90 % du maximum.
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-3f)
        for (i in 0 until n) mix[i] *= 0.9f / peak

        val out = ShortArray(n)
        val fade = SAMPLE_RATE / 12 // fondu final pour éviter un clic
        for (i in 0 until n) {
            val g = ((n - i).toFloat() / fade).coerceAtMost(1f)
            out[i] = (mix[i].coerceIn(-1f, 1f) * g * 32767).toInt().toShort()
        }
        return out
    }

    private inline fun sweep(
        mix: FloatArray, start: Double, length: Double, f0: Double, f1: Double, gain: Float,
        envelope: (Double) -> Float,
    ) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val p = i.toDouble() / count
            phase += 2 * PI * (f0 + (f1 - f0) * p) / SAMPLE_RATE
            mix[idx] += (sin(phase) * gain * envelope(p)).toFloat()
        }
    }

    private fun blip(mix: FloatArray, start: Double, freq: Double, length: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val env = exp(-6.0 * i / count)
            mix[idx] += (sin(2 * PI * freq * i / SAMPLE_RATE) * env * gain).toFloat()
        }
    }

    private fun bell(mix: FloatArray, start: Double, freq: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        for (idx in from until mix.size) {
            val s = (idx - from).toDouble() / SAMPLE_RATE
            val env = exp(-4.0 * s)
            val tone = sin(2 * PI * freq * s) + 0.4 * sin(2 * PI * freq * 2.01 * s)
            mix[idx] += (tone * env * gain).toFloat()
        }
    }
}
