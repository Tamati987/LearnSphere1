package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Voix réaliste ElevenLabs : un majordome britannique posé, qui parle français. */
class ElevenLabsVoice(private val context: Context, private val apiKey: String) {

    private var player: MediaPlayer? = null

    /** Génère l'audio de [text]. Appel réseau : à faire hors du fil principal. */
    /**
     * [fast] : annonce urgente (appel entrant). [premium] : moment important (réveil), toujours
     * avec le modèle eleven_v3. Sinon le réglage « qualité de la voix » choisit le modèle.
     */
    fun synthesize(text: String, fast: Boolean = false, premium: Boolean = false): File {
        // Crédits épuisés : inutile de redemander à chaque phrase, la voix Android répond tout de suite.
        if (System.currentTimeMillis() < quotaBlockedUntil) throw IllegalStateException("crédits ElevenLabs épuisés")
        // La voix Jarvis n'existe que dans le compte qui l'a créée : sinon, Daniel prend le relais.
        // [fast] : modèle express, pour une annonce qui ne peut pas attendre (appel entrant).
        val model = when {
            fast -> FALLBACK_MODEL
            premium || Prefs(context).voiceQuality == "premium" -> MODEL
            // Réponses ordinaires : modèle rapide, la même voix pour environ moitié moins de crédits.
            else -> FALLBACK_MODEL
        }
        return runCatching { jarvisVoice(text, model) }.getOrElse { error ->
            if (isQuota(error)) {
                quotaBlockedUntil = System.currentTimeMillis() + QUOTA_RETRY_MS
                JarvisBus.line(
                    false,
                    "Crédits ElevenLabs épuisés : Jarvis parle avec la voix d'Android. Sa voix reviendra quand " +
                        "l'abonnement ElevenLabs sera renouvelé ou rechargé (nouvel essai toutes les 30 minutes).",
                )
                throw error
            }
            JarvisBus.line(false, "Voix Jarvis indisponible (${error.message}). Voix Daniel utilisée.")
            synthesize(text, FALLBACK_VOICE_ID, FALLBACK_MODEL, withSpeed = true)
        }
    }

    private fun isQuota(error: Throwable) = error.message.orEmpty().let { it.contains("quota_exceeded") || it.contains("credits") }

    /**
     * La voix Jarvis, au débit choisi. Si ElevenLabs refuse le réglage de débit pour ce modèle,
     * on garde la voix Jarvis au débit normal plutôt que de passer à Daniel.
     */
    private fun jarvisVoice(text: String, model: String): File =
        runCatching { synthesize(text, VOICE_ID, model, withSpeed = true) }.getOrElse { error ->
            if (speed == 1.0 || isQuota(error)) throw error
            synthesize(text, VOICE_ID, model, withSpeed = false)
        }

    // Débit choisi dans les réglages de Jarvis (0,7 à 1,2).
    private val speed get() = Prefs(context).speechRate.toDouble()

    private fun synthesize(text: String, voiceId: String, model: String, withSpeed: Boolean): File {
        val connection = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")
            // Les indications d'intonation ([laughs]…) ne sont comprises que par eleven_v3.
            val body = JSONObject()
                .put("text", if (model == MODEL) text else Speech.clean(text))
                .put("model_id", model)
                // eleven_v3 n'accepte que 0, 0,5 ou 1 pour la stabilité.
                .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.8).apply {
                    // Débit envoyé seulement s'il a été changé : au réglage normal, la requête reste celle d'avant.
                    if (withSpeed && speed != 1.0) put("speed", speed)
                })
            // eleven_v3 détecte la langue seul ; on ne la précise qu'au modèle rapide.
            if (model == FALLBACK_MODEL) body.put("language_code", "fr")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("ElevenLabs a répondu $code ${error.take(200)}")
            }
            // Un fichier par phrase : la suivante se prépare pendant que celle-ci est lue.
            val file = File.createTempFile("jarvis-voice", ".mp3", context.cacheDir)
            connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            return file
        } finally {
            connection.disconnect()
        }
    }

    /** Lit le fichier puis appelle [onDone], sur le fil principal. */
    fun play(file: File, alarm: Boolean = false, onDone: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    // Le réveil passe par le volume d'alarme, audible même si le média est coupé.
                    .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop(); file.delete(); onDone() }
            setOnErrorListener { _, _, _ -> stop(); file.delete(); onDone(); true }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    companion object {
        // Les crédits sont ceux du compte : un blocage vaut pour toutes les voix de l'appli.
        @Volatile private var quotaBlockedUntil = 0L
        private const val QUOTA_RETRY_MS = 30 * 60_000L
        // « Jarvis (voix originale) », créée pour l'appli : homme britannique, grave, calme.
        const val VOICE_ID = "53CXgEMfXvQXipNADfUR"
        // « Daniel », voix ElevenLabs par défaut : britannique, posée, formelle.
        const val FALLBACK_VOICE_ID = "onwK4e9ZLuTAKqWW03F9"
        // La voix Jarvis a été créée avec eleven_v3 : ce modèle la restitue fidèlement.
        const val MODEL = "eleven_v3"
        // Modèle multilingue rapide pour la voix de secours.
        const val FALLBACK_MODEL = "eleven_flash_v2_5"
    }
}
