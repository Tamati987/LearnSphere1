package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sons de l'interface, synthétisés sur le téléphone (aucun fichier audio) :
 * cloches FM aux harmoniques légèrement désaccordées, souffles filtrés et réverbération
 * de pièce, pour un rendu proche d'une interface holographique de cinéma.
 */
object HudSound {
    enum class Cue { ACTIVATE, PROMPT, CLOSE }

    private const val SAMPLE_RATE = 44100
    private val tracks = mutableMapOf<Cue, AudioTrack>()

    /** Joue un son ; sans effet si le téléphone refuse la sortie audio. */
    @Synchronized
    fun play(cue: Cue) {
        runCatching {
            val track = tracks.getOrPut(cue) { track(render(cue)) }
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    @Synchronized
    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
    }

    private fun render(cue: Cue): ShortArray {
        val rnd = Random(7)
        val dry = when (cue) {
            // « Hey Jarvis » : souffle qui monte, impact sourd, deux notes cristallines.
            Cue.ACTIVATE -> FloatArray(seconds(0.45)).also { mix ->
                whoosh(mix, rnd, 0.0, 0.2, rising = true, gain = 0.35f)
                thump(mix, 0.12, 70.0, 0.5f)
                bell(mix, 0.12, 1760.0, 0.33, 0.30f)
                bell(mix, 0.19, 2637.0, 0.26, 0.24f)
            }
            // Le micro se rouvre pour une réponse : deux tics brefs qui montent.
            Cue.PROMPT -> FloatArray(seconds(0.3)).also { mix ->
                bell(mix, 0.0, 1568.0, 0.12, 0.22f)
                bell(mix, 0.07, 2093.0, 0.18, 0.22f)
            }
            // Fin de l'échange : glissement descendant, souffle qui retombe.
            Cue.CLOSE -> FloatArray(seconds(0.45)).also { mix ->
                whoosh(mix, rnd, 0.0, 0.25, rising = false, gain = 0.18f)
                bell(mix, 0.0, 2093.0, 0.15, 0.14f)
                bell(mix, 0.08, 1397.0, 0.3, 0.14f)
            }
        }
        return toPcm(reverb(dry, SAMPLE_RATE, wet = 0.32f), peakLevel = 0.6f)
    }

    /**
     * Réverbération de Schroeder : quatre filtres en peigne amortis en parallèle, puis deux
     * passe-tout. Ajoute une queue de [tail] secondes après le son.
     */
    fun reverb(input: FloatArray, sampleRate: Int, wet: Float, tail: Double = 0.6): FloatArray {
        val out = FloatArray(input.size + (tail * sampleRate).toInt())
        val combs = listOf(29.7, 37.1, 41.1, 43.7).map { (it * sampleRate / 1000).toInt() }
        val sum = FloatArray(out.size)
        for (delay in combs) {
            val buf = FloatArray(delay)
            var pos = 0
            var damp = 0f
            for (i in out.indices) {
                val x = if (i < input.size) input[i] else 0f
                val y = buf[pos]
                damp += 0.35f * (y - damp) // les aigus s'éteignent plus vite, comme dans une vraie pièce
                buf[pos] = x + damp * 0.8f
                sum[i] += y / combs.size
                pos = (pos + 1) % delay
            }
        }
        var wetSignal = sum
        for (ms in listOf(5.0, 1.7)) wetSignal = allpass(wetSignal, (ms * sampleRate / 1000).toInt(), 0.7f)
        for (i in out.indices) out[i] = (if (i < input.size) input[i] else 0f) + wetSignal[i] * wet
        return out
    }

    private fun allpass(input: FloatArray, delay: Int, g: Float): FloatArray {
        val buf = FloatArray(delay)
        var pos = 0
        return FloatArray(input.size) { i ->
            val delayed = buf[pos]
            val y = -g * input[i] + delayed
            buf[pos] = input[i] + g * y
            pos = (pos + 1) % delay
            y
        }
    }

    private fun seconds(s: Double) = (s * SAMPLE_RATE).toInt()

    /** Cloche FM : partiel inharmonique qui s'éteint plus vite que la fondamentale, comme du verre. */
    private fun bell(mix: FloatArray, start: Double, freq: Double, length: Double, gain: Float) {
        val from = seconds(start)
        val count = seconds(length)
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val s = i.toDouble() / SAMPLE_RATE
            val attack = minOf(s / 0.003, 1.0)
            val index = 2.2 * exp(-18 * s)
            val mod = sin(2 * PI * freq * 1.414 * s) * index
            val tone = sin(2 * PI * freq * s + mod) + 0.3 * sin(2 * PI * freq * 2.76 * s) * exp(-30 * s) +
                0.15 * sin(2 * PI * (freq * 1.003) * s)
            mix[idx] += (tone * attack * exp(-6.5 * s / length) * gain).toFloat()
        }
    }

    /** Souffle filtré dont la brillance monte ou descend. */
    private fun whoosh(mix: FloatArray, rnd: Random, start: Double, length: Double, rising: Boolean, gain: Float) {
        val from = seconds(start)
        val count = seconds(length)
        var lp = 0f
        var lp2 = 0f
        for (i in 0 until count) {
            val idx = from + i
            if (idx >= mix.size) break
            val p = i.toFloat() / count
            val bright = 0.02f + 0.4f * (if (rising) p else 1 - p)
            lp += bright * (rnd.nextFloat() * 2 - 1 - lp)
            lp2 += bright * (lp - lp2)
            val env = if (rising) p * p * (1 - p) * 6 else (1 - p) * (1 - p)
            mix[idx] += lp2 * gain * env * 3
        }
    }

    /** Coup grave et court, dont la hauteur chute. */
    private fun thump(mix: FloatArray, start: Double, freq: Double, gain: Float) {
        val from = seconds(start)
        var phase = 0.0
        for (i in 0 until seconds(0.25)) {
            val idx = from + i
            if (idx >= mix.size) break
            val s = i.toDouble() / SAMPLE_RATE
            phase += 2 * PI * freq * (1 + 1.2 * exp(-40 * s)) / SAMPLE_RATE
            mix[idx] += (sin(phase) * exp(-14 * s) * gain).toFloat()
        }
    }

    fun toPcm(mix: FloatArray, peakLevel: Float): ShortArray {
        val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-3f)
        val fade = SAMPLE_RATE / 50
        return ShortArray(mix.size) { i ->
            val g = ((mix.size - i).toFloat() / fade).coerceAtMost(1f)
            (mix[i] * peakLevel / peak * g * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun track(pcm: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
        .also { it.write(pcm, 0, pcm.size) }
}
