package com.jarvis.assistant

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bande-son du démarrage, synthétisée sur le téléphone (aucun fichier audio),
 * calée sur les phases de [BootSequenceView] :
 *  0 – 3 s      mise sous tension, tics rapides du compteur, montée de tonalité
 *  3 – 5,5 s    rotation du cœur : ronronnement, glissandos aigus, sifflement fin
 *  5,5 – 8,5 s  alerte rouge : sirène à deux tons, pulsation grave
 *  8,5 – 11,5 s données qui défilent, blocs qui se remplissent, confirmation
 *  11,5 – 15 s  souffle, impact grave et nappe lumineuse des cercles
 */
object BootSound {
    const val SAMPLE_RATE = 22050
    const val DURATION_MS = 15_000

    fun render(): ShortArray {
        val n = SAMPLE_RATE * DURATION_MS / 1000
        val mix = FloatArray(n)
        val rnd = Random(42)

        // Mise sous tension.
        thump(mix, 0.05, 55.0, 0.9f)
        noise(mix, rnd, 0.05, 0.25, 0.35f, 0.25f)

        // Compteur 0 → 100 : tics rapides et tonalité qui monte.
        var t = 0.4
        while (t < 2.7) {
            blip(mix, t, 3200.0 + rnd.nextInt(6) * 250.0, 0.02, 0.22f)
            t += 0.045
        }
        sweep(mix, 0.4, 2.3, 400.0, 1600.0, 0.10f) { p -> (0.3 + 0.7 * p).toFloat() }
        blip(mix, 2.75, 2500.0, 0.07, 0.4f)
        blip(mix, 2.88, 3300.0, 0.09, 0.4f)

        // Rotation du cœur blanc.
        whirr(mix, 3.0, 2.5, 110.0, 0.25f)
        for (k in 0 until 5) sweep(mix, 3.1 + k * 0.48, 0.35, 2000.0, 11000.0, 0.09f) { p -> sin(p * PI).toFloat() }
        sweep(mix, 3.0, 5.5, 6600.0, 6600.0, 0.025f) { p -> sin(p * PI).toFloat() }
        for (k in 0 until 8) blip(mix, 3.2 + k * 0.3, 1800.0 + (k % 3) * 600.0, 0.03, 0.18f)

        // Alerte rouge : sirène à deux tons et pulsation grave.
        for (k in 0 until 6) {
            val start = 5.6 + k * 0.48
            sweep(mix, start, 0.22, 880.0, 880.0, 0.22f) { p -> envelope(p) }
            sweep(mix, start + 0.24, 0.22, 660.0, 660.0, 0.22f) { p -> envelope(p) }
            thump(mix, start, 48.0, 0.45f)
        }
        sweep(mix, 5.5, 3.0, 5000.0, 5000.0, 0.03f) { p -> sin(p * PI).toFloat() }

        // Panneau d'initialisation : données, blocs, confirmation.
        noise(mix, rnd, 8.5, 0.5, 0.25f, 0.6f)
        t = 8.6
        while (t < 10.6) {
            blip(mix, t, 1200.0 + rnd.nextInt(8) * 350.0, 0.015, 0.12f)
            t += 0.05 + rnd.nextDouble() * 0.04
        }
        for (k in 0 until 14) blip(mix, 9.1 + k * 0.1, 900.0 + k * 90.0, 0.05, 0.25f)
        bell(mix, 10.5, 1046.0, 0.8, 0.22f)
        bell(mix, 10.62, 1568.0, 0.8, 0.18f)

        // Cercles cyan : souffle, impact, nappe.
        noise(mix, rnd, 11.2, 0.6, 0.35f, 0.15f)
        thump(mix, 11.5, 40.0, 1.0f)
        for ((freq, gain) in listOf(440.0 to 0.09f, 659.3 to 0.07f, 880.0 to 0.06f, 1318.5 to 0.04f)) {
            sweep(mix, 11.6, 3.4, freq, freq, gain) { p -> (minOf(p * 4, 1.0) * (1 - p)).toFloat() }
        }
        for (k in 0 until 6) blip(mix, 11.8 + k * 0.18, 4000.0 + k * 300.0, 0.04, 0.10f)

        // Normalise le volume : le son le plus fort atteint 90 % du maximum.
        val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-3f)
        val out = ShortArray(n)
        val fade = SAMPLE_RATE / 4 // fondu final
        for (i in 0 until n) {
            val g = ((n - i).toFloat() / fade).coerceAtMost(1f)
            out[i] = (mix[i] * 0.9f / peak * g * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun envelope(p: Double) = (minOf(p * 10, 1.0) * minOf((1 - p) * 10, 1.0)).toFloat()

    private inline fun sweep(
        mix: FloatArray, start: Double, length: Double, f0: Double, f1: Double, gain: Float,
        shape: (Double) -> Float,
    ) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val p = i.toDouble() / count
            phase += 2 * PI * (f0 + (f1 - f0) * p) / SAMPLE_RATE
            mix[idx] += (sin(phase) * gain * shape(p)).toFloat()
        }
    }

    private fun blip(mix: FloatArray, start: Double, freq: Double, length: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val env = exp(-5.0 * i / count)
            mix[idx] += (sin(2 * PI * freq * i / SAMPLE_RATE) * env * gain).toFloat()
        }
    }

    /** Coup grave : sinus dont la hauteur et le volume chutent vite. */
    private fun thump(mix: FloatArray, start: Double, freq: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (0.6 * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val s = i.toDouble() / SAMPLE_RATE
            phase += 2 * PI * freq * (1 + 1.5 * exp(-25 * s)) / SAMPLE_RATE
            mix[idx] += (sin(phase) * exp(-7 * s) * gain).toFloat()
        }
    }

    /** Ronronnement mécanique : grave modulé en amplitude. */
    private fun whirr(mix: FloatArray, start: Double, length: Double, freq: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val s = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count
            val am = 0.6 + 0.4 * sin(2 * PI * 9 * s)
            val tone = sin(2 * PI * freq * s) + 0.5 * sin(2 * PI * freq * 2 * s) + 0.25 * sin(2 * PI * freq * 3 * s)
            mix[idx] += (tone * am * gain * sin(p * PI)).toFloat()
        }
    }

    /** Souffle filtré ; [brightness] près de 1 = aigu, près de 0 = sourd. */
    private fun noise(mix: FloatArray, rnd: Random, start: Double, length: Double, gain: Float, brightness: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        var lp = 0f
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            lp += brightness * (rnd.nextFloat() * 2 - 1 - lp)
            val p = i.toFloat() / count
            mix[idx] += lp * gain * sin(p * PI.toFloat())
        }
    }

    private fun bell(mix: FloatArray, start: Double, freq: Double, length: Double, gain: Float) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (length * SAMPLE_RATE).toInt()
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val s = i.toDouble() / SAMPLE_RATE
            val tone = sin(2 * PI * freq * s) + 0.4 * sin(2 * PI * freq * 2.01 * s)
            mix[idx] += (tone * exp(-4.0 * s) * gain).toFloat()
        }
    }
}
