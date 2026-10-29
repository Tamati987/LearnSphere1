package com.jarvis.assistant

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Texte prêt à afficher ou à faire lire par la voix d'Android : sans les indications d'intonation. */
object Speech {
    private val TAGS = Regex("\\[(?:laughs|chuckles|sighs|whispers|excited|curious|sarcastic|thoughtful|warmly)[^\\]]*]\\s*", RegexOption.IGNORE_CASE)
    fun clean(text: String) = TAGS.replace(text, "").trim()
}

/**
 * Réglages que Jarvis lit et change lui-même, à la voix : les siens (sensibilité, voix,
 * mode conversation…) et ceux du téléphone qu'Android laisse modifier à une appli
 * (sonnerie, Ne pas déranger, luminosité, mise en veille, rotation). Pour le Wi-Fi, le
 * Bluetooth, les données ou le mode avion, Android impose que l'utilisateur touche
 * lui-même l'interrupteur : Jarvis ouvre alors le bon panneau.
 */
class JarvisSettings(private val context: Context) {

    private val prefs = Prefs(context)

    /** Réglages de Jarvis. [name] absent ou action « lire » : renvoie la liste. */
    fun jarvis(action: String?, name: String?, value: String?): String {
        if (action != "modifier" || name.isNullOrBlank()) return describe()
        val v = value?.trim()?.lowercase().orEmpty()
        val result = when (name) {
            "sensibilite" -> {
                prefs.wakeSensitivity = when { v.startsWith("tres") || v.startsWith("très") -> 2; v.startsWith("norm") -> 0; else -> 1 }
                "Sensibilité de « Hey Jarvis » : ${SENSITIVITY[prefs.wakeSensitivity]}."
            }
            "annonces" -> { prefs.announceSms = yes(v); "Annonces des appels et messages ${onOff(prefs.announceSms)}." }
            "mode_conversation" -> { prefs.conversationMode = yes(v); "Mode conversation ${onOff(prefs.conversationMode)}." }
            "voix" -> {
                prefs.realisticVoice = !v.contains("android")
                if (prefs.realisticVoice && prefs.elevenLabsKey.isBlank()) "Échec : aucune clé ElevenLabs enregistrée, la voix d'Android reste utilisée."
                else "Voix : ${if (prefs.realisticVoice) "Jarvis (ElevenLabs)" else "Android"}."
            }
            "vitesse_voix" -> {
                val rate = v.replace(',', '.').toFloatOrNull() ?: when {
                    v.contains("lent") -> prefs.speechRate - 0.1f
                    v.contains("rapide") || v.contains("vite") -> prefs.speechRate + 0.1f
                    else -> 1f
                }
                prefs.speechRate = rate
                "Vitesse de la voix : %.1f.".format(prefs.speechRate)
            }
            "sim_sms" -> { prefs.smsSim = if (v.contains("2")) 2 else 1; "Les SMS partiront de la SIM ${prefs.smsSim}." }
            "longueur_reponses" -> {
                prefs.replyLength = when { v.startsWith("court") -> "courtes"; v.startsWith("d") -> "détaillées"; else -> "normales" }
                "Réponses ${prefs.replyLength}."
            }
            else -> return "Échec : réglage « $name » inconnu. ${describe()}"
        }
        // Le service applique tout de suite le changement (micro, voix…).
        JarvisService.instance?.applySettings()
        return result
    }

    fun describe() = "Réglages de Jarvis : sensibilité ${SENSITIVITY[prefs.wakeSensitivity]} ; " +
        "annonces ${onOff(prefs.announceSms)} ; mode conversation ${onOff(prefs.conversationMode)} ; " +
        "voix ${if (prefs.realisticVoice && prefs.elevenLabsKey.isNotBlank()) "Jarvis (ElevenLabs)" else "Android"} ; " +
        "vitesse de la voix %.1f ; SMS par la SIM ${prefs.smsSim} ; réponses ${prefs.replyLength}.".format(prefs.speechRate)

    /** Réglage du téléphone. Renvoie un compte rendu, ou ce que l'utilisateur doit autoriser. */
    fun phone(name: String, value: String?, open: (Intent) -> Unit): String {
        val v = value?.trim()?.lowercase().orEmpty()
        return when (name) {
            "sonnerie" -> ringer(v, open)
            "ne_pas_deranger" -> {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) return needPolicy(open)
                nm.setInterruptionFilter(if (yes(v)) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                "Ne pas déranger ${onOff(yes(v))}."
            }
            "luminosite" -> writeSystem(open) {
                if (v.startsWith("auto")) {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    "Luminosité automatique."
                } else {
                    val percent = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 100) ?: return@writeSystem "Échec : donnez un pourcentage ou « auto »."
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100)
                    "Luminosité à $percent %."
                }
            }
            "mise_en_veille" -> writeSystem(open) {
                val seconds = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(15, 1800) ?: return@writeSystem "Échec : donnez une durée en secondes."
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
                "L'écran se mettra en veille après $seconds secondes."
            }
            "rotation_auto" -> writeSystem(open) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (yes(v)) 1 else 0)
                "Rotation automatique ${onOff(yes(v))}."
            }
            "wifi" -> panel(open, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
            "donnees_mobiles", "internet" -> panel(open, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS, "Internet")
            "nfc" -> panel(open, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS, "NFC")
            "bluetooth" -> panel(open, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            "mode_avion" -> panel(open, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "mode avion")
            "localisation" -> panel(open, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "localisation")
            "batterie" -> panel(open, Intent.ACTION_POWER_USAGE_SUMMARY, "batterie")
            "affichage" -> panel(open, Settings.ACTION_DISPLAY_SETTINGS, "affichage")
            "son" -> panel(open, Settings.ACTION_SOUND_SETTINGS, "son")
            "applis" -> panel(open, Settings.ACTION_APPLICATION_SETTINGS, "applis")
            "reglages_jarvis" -> {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                "Page des autorisations de Jarvis ouverte."
            }
            else -> "Échec : réglage du téléphone « $name » inconnu."
        }
    }

    private fun ringer(v: String, open: (Intent) -> Unit): String {
        val audio = context.getSystemService(AudioManager::class.java)
        val mode = when {
            v.startsWith("vib") -> AudioManager.RINGER_MODE_VIBRATE
            v.startsWith("sil") -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return runCatching { audio.ringerMode = mode; "Sonnerie : ${listOf("silencieux", "vibreur", "normale")[mode]}." }
            // Android réserve le silencieux aux applis autorisées à gérer Ne pas déranger.
            .getOrElse { needPolicy(open) }
    }

    private fun needPolicy(open: (Intent) -> Unit): String {
        open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        return "Échec : Jarvis doit d'abord être autorisé à gérer « Ne pas déranger ». La page est ouverte : l'utilisateur doit activer Jarvis, puis redemander."
    }

    private fun writeSystem(open: (Intent) -> Unit, change: () -> String): String {
        if (!Settings.System.canWrite(context)) {
            open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return "Échec : Jarvis doit d'abord être autorisé à modifier les paramètres système. La page est ouverte : l'utilisateur doit activer l'autorisation, puis redemander."
        }
        return runCatching(change).getOrElse { "Échec : ${it.message}" }
    }

    private fun panel(open: (Intent) -> Unit, action: String, label: String): String {
        open(Intent(action))
        return "Panneau $label ouvert : Android impose que l'utilisateur touche lui-même l'interrupteur."
    }

    private fun yes(v: String) = !(v.startsWith("non") || v.startsWith("off") || v.startsWith("désactiv") ||
        v.startsWith("desactiv") || v == "false" || v.startsWith("arr") || v == "0")

    private fun onOff(b: Boolean) = if (b) "activé" else "désactivé"

    companion object {
        private val SENSITIVITY = listOf("normale", "élevée", "très élevée")
    }
}
