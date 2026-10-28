package com.jarvis.assistant

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Mode jeu : ne pas déranger (seuls les appels prioritaires et les alarmes passent),
 * luminosité au maximum, son multimédia monté, et Jarvis n'annonce plus les messages.
 * Les réglages d'avant sont gardés pour être remis à la sortie du mode jeu.
 */
object GameMode {
    private const val PREFS = "jarvis_game"
    private const val GAME_VOLUME_PERCENT = 80

    fun isOn(context: Context) = prefs(context).getBoolean("on", false)

    /** Active le mode jeu et renvoie ce qui a été fait, pour Claude. */
    fun start(context: Context): String {
        val saved = prefs(context)
        val first = !saved.getBoolean("on", false)
        val edit = saved.edit().putBoolean("on", true)
        val done = mutableListOf<String>()
        val missing = mutableListOf<String>()

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            if (first) edit.putInt("filter", nm.currentInterruptionFilter)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            done += "ne pas déranger activé (appels prioritaires et alarmes passent encore)"
        } else {
            missing += "ne pas déranger (accès aux notifications non accordé)"
        }

        val resolver = context.contentResolver
        if (Settings.System.canWrite(context)) {
            if (first) {
                edit.putInt("brightness_mode", Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
                edit.putInt("brightness", Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 255)
            done += "luminosité au maximum"
        } else {
            missing += "luminosité (autorisation « Modifier les paramètres système » non accordée)"
        }

        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (first) edit.putInt("volume", am.getStreamVolume(AudioManager.STREAM_MUSIC))
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (GAME_VOLUME_PERCENT / 100f * max).roundToInt(), 0)
        done += "son multimédia à $GAME_VOLUME_PERCENT %"
        done += "annonces des messages suspendues"

        edit.apply()
        JarvisBus.line(false, "Mode jeu activé.")
        return "Mode jeu activé : ${done.joinToString(", ")}." +
            if (missing.isEmpty()) "" else " Non fait : ${missing.joinToString(", ")} ; l'utilisateur peut l'autoriser dans l'écran des clés."
    }

    /** Remet les réglages d'avant le mode jeu. */
    fun stop(context: Context): String {
        val saved = prefs(context)
        if (!saved.getBoolean("on", false)) return "Le mode jeu n'était pas activé."

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(saved.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL))
        }
        val resolver = context.contentResolver
        if (Settings.System.canWrite(context)) {
            saved.getInt("brightness", -1).takeIf { it >= 0 }
                ?.let { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, it) }
            saved.getInt("brightness_mode", -1).takeIf { it >= 0 }
                ?.let { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it) }
        }
        saved.getInt("volume", -1).takeIf { it >= 0 }?.let {
            context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
        }

        saved.edit().clear().apply()
        JarvisBus.line(false, "Mode jeu désactivé.")
        return "Mode jeu désactivé : ne pas déranger, luminosité et son remis comme avant, annonces des messages reprises."
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
