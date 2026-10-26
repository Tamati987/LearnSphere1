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

    val isConfigured get() = anthropicKey.isNotBlank()
}
