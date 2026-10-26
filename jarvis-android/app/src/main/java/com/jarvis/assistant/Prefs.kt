package com.jarvis.assistant

import android.content.Context

/** Clé d'accès, gardée dans les préférences privées de l'appli. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", "").orEmpty()
        set(value) = prefs.edit().putString("anthropic_key", value.trim()).apply()

    val isConfigured get() = anthropicKey.isNotBlank()
}
