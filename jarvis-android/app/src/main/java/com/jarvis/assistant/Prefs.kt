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

    val isConfigured get() = anthropicKey.isNotBlank()
}
