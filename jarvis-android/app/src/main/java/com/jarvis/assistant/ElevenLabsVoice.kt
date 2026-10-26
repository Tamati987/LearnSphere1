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
    fun synthesize(text: String): File =
        // La voix Jarvis n'existe que dans le compte qui l'a créée : sinon, Daniel prend le relais.
        runCatching { synthesize(text, VOICE_ID, MODEL) }.getOrElse { error ->
            JarvisBus.line(false, "Voix Jarvis indisponible (${error.message}). Voix Daniel utilisée.")
            synthesize(text, FALLBACK_VOICE_ID, FALLBACK_MODEL)
        }

    private fun synthesize(text: String, voiceId: String, model: String): File {
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
            val body = JSONObject()
                .put("text", text)
                .put("model_id", model)
                // eleven_v3 n'accepte que 0, 0,5 ou 1 pour la stabilité.
                .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.8))
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
    fun play(file: File, onDone: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
