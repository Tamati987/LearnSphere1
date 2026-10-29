package com.jarvis.assistant

import android.content.Context

/** Clés d'accès, gardées dans les préférences privées de l'appli. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", "").orEmpty()
        set(value) = prefs.edit().putString("anthropic_key", value.trim()).apply()

    var elevenLabsKey: String
        get() = prefs.getString("elevenlabs_key", "").orEmpty()
        set(value) = prefs.edit().putString("elevenlabs_key", value.trim()).apply()

    var announceSms: Boolean
        get() = prefs.getBoolean("announce_sms", true)
        set(value) = prefs.edit().putBoolean("announce_sms", value).apply()

    /** Sensibilité du mot d'activation : 0 normale, 1 élevée, 2 très élevée. */
    var wakeSensitivity: Int
        get() = prefs.getInt("wake_sensitivity", 1)
        set(value) = prefs.edit().putInt("wake_sensitivity", value.coerceIn(0, 2)).apply()

    /** Score à partir duquel « Hey Jarvis » est reconnu : plus bas, plus sensible. */
    val wakeThreshold get() = when (wakeSensitivity) { 0 -> 0.5f; 2 -> 0.15f; else -> 0.3f }

    /** Après chaque réponse, le micro se rouvre une fois pour continuer la conversation. */
    var conversationMode: Boolean
        get() = prefs.getBoolean("conversation_mode", true)
        set(value) = prefs.edit().putBoolean("conversation_mode", value).apply()

    /** Voix réaliste ElevenLabs (true) ou voix d'Android (false). */
    var realisticVoice: Boolean
        get() = prefs.getBoolean("realistic_voice", true)
        set(value) = prefs.edit().putBoolean("realistic_voice", value).apply()

    /** Débit de la voix, de 0,7 (lent) à 1,2 (rapide). */
    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1f)
        set(value) = prefs.edit().putFloat("speech_rate", value.coerceIn(0.7f, 1.2f)).apply()

    /** SIM qui envoie les SMS : 1 ou 2. */
    var smsSim: Int
        get() = prefs.getInt("sms_sim", 1)
        set(value) = prefs.edit().putInt("sms_sim", value.coerceIn(1, 2)).apply()

    /** Qualité de la voix : « economique » (modèle rapide, moitié moins de crédits) ou « premium » (eleven_v3). */
    var voiceQuality: String
        get() = prefs.getString("voice_quality", "economique").orEmpty()
        set(value) = prefs.edit().putString("voice_quality", value).apply()

    /** Longueur des réponses : courtes, normales ou détaillées. */
    var replyLength: String
        get() = prefs.getString("reply_length", "normales").orEmpty()
        set(value) = prefs.edit().putString("reply_length", value).apply()

    val isConfigured get() = anthropicKey.isNotBlank()
}
