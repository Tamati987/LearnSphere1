package com.jarvis.assistant

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler

/**
 * Mode coach : une séance de fractionné (préparation, puis effort et repos enchaînés) qui tourne
 * sur le téléphone, sans appeler Claude. Les changements de phase sont signalés par des bips ;
 * Jarvis ne parle qu'au début, à la fin, et (si [announceExercises]) pour nommer l'exercice.
 */
class Coach(
    private val main: Handler,
    private val say: (String) -> Unit,
    private val onFinished: () -> Unit,
) {
    data class Plan(
        val rounds: Int,
        val workSeconds: Int,
        val restSeconds: Int,
        val exercises: List<String>,
        val announceExercises: Boolean,
    )

    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
    private var plan: Plan? = null
    private var round = 0
    private var working = false
    private var remaining = 0
    var running = false
        private set

    fun start(plan: Plan) {
        stop(silent = true)
        this.plan = plan
        running = true
        round = 0
        working = false
        // Dix secondes pour se mettre en place.
        remaining = PREPARATION_SECONDS
        main.post(tick)
    }

    fun stop(silent: Boolean = false) {
        if (!running) return
        running = false
        main.removeCallbacks(tick)
        if (!silent) JarvisBus.line(false, "Séance du coach arrêtée.")
    }

    fun status(): String {
        val p = plan ?: return "Aucune séance en cours."
        if (!running) return "Aucune séance en cours."
        val phase = when {
            round == 0 -> "préparation"
            working -> "effort${exerciseFor(round)?.let { " ($it)" }.orEmpty()}"
            else -> "repos"
        }
        return "Séance en cours : tour $round sur ${p.rounds}, $phase, encore $remaining secondes."
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val p = plan ?: return
            if (remaining in 1..3) beep(ToneGenerator.TONE_PROP_BEEP, 120)
            if (remaining <= 0) {
                nextPhase(p)
                if (!running) return
            }
            remaining--
            main.postDelayed(this, 1000)
        }
    }

    private fun nextPhase(p: Plan) {
        if (working) {
            // Fin d'un effort : repos, ou fin de séance au dernier tour.
            if (round >= p.rounds) {
                running = false
                beep(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING, 900)
                JarvisBus.line(false, "Séance du coach terminée : ${p.rounds} tours.")
                say("Séance terminée, Monsieur. ${p.rounds} tours bouclés. Bravo.")
                onFinished()
                return
            }
            working = false
            remaining = p.restSeconds
            // Repos : un bip long et grave.
            beep(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
        } else {
            round++
            working = true
            remaining = p.workSeconds
            // Effort : deux bips aigus, et le nom de l'exercice si demandé.
            beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            if (p.announceExercises) exerciseFor(round)?.let { say(it) }
        }
    }

    private fun exerciseFor(round: Int): String? {
        val list = plan?.exercises.orEmpty()
        return if (list.isEmpty()) null else list[(round - 1).coerceAtLeast(0) % list.size]
    }

    private fun beep(type: Int, ms: Int) {
        runCatching { tone?.startTone(type, ms) }
    }

    fun release() {
        stop(silent = true)
        tone?.release()
    }

    companion object {
        const val PREPARATION_SECONDS = 10
    }
}
