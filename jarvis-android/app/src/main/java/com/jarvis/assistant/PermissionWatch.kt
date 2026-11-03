package com.jarvis.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat

/**
 * Surveille les autorisations dont Jarvis a besoin. Android ou le téléphone (Xiaomi…) en
 * retire parfois une, après une mise à jour ou un nettoyage : Jarvis le remarque et prévient,
 * par une notification qui ouvre directement le bon réglage.
 */
object PermissionWatch {

    private data class Check(val key: String, val label: String, val fix: (Context) -> Intent, val ok: (Context) -> Boolean)

    private val checks = listOf(
        Check("micro", "le micro", ::appDetails) { granted(it, Manifest.permission.RECORD_AUDIO) },
        Check("notifications_lecture", "la lecture des notifications (WhatsApp, Messenger, appels)",
            { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { MessageListener.isEnabled(it) },
        Check("ecran", "la lecture de l'écran (accessibilité)",
            { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { ScreenReader.isEnabled(it) },
        Check("batterie", "l'écoute écran verrouillé (batterie sans restriction)",
            { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) {
            it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName)
        },
        Check("ouvrir_applis", "l'ouverture des applis par Jarvis",
            { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}")) }) { Settings.canDrawOverlays(it) },
        Check("afficher_notifications", "l'affichage des notifications de Jarvis", ::appDetails) {
            Build.VERSION.SDK_INT < 33 || granted(it, Manifest.permission.POST_NOTIFICATIONS)
        },
    )

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Vérifie tout. Une autorisation qui marchait et qui a sauté est signalée (une seule fois,
     * jusqu'à ce qu'elle revienne). Renvoie les phrases à dire pour les nouveaux problèmes.
     */
    fun check(context: Context): List<String> {
        val prefs = context.getSharedPreferences("jarvis_permissions", Context.MODE_PRIVATE)
        val lost = mutableListOf<String>()
        checks.forEach { c ->
            val ok = runCatching { c.ok(context) }.getOrDefault(true)
            val wasOk = prefs.getBoolean(c.key, false)
            if (ok != wasOk) prefs.edit().putBoolean(c.key, ok).apply()
            if (wasOk && !ok) {
                JarvisBus.line(false, "Autorisation perdue : ${c.label}. Touchez la notification pour la réactiver.")
                notify(context, c)
                lost += c.label
            }
        }
        return lost
    }

    /** État de chaque autorisation, pour que Jarvis puisse répondre « tout est en ordre ? ». */
    fun report(context: Context): String = checks.joinToString(" ; ") { c ->
        "${c.label} : ${if (runCatching { c.ok(context) }.getOrDefault(false)) "OK" else "à réactiver"}"
    }

    private fun notify(context: Context, c: Check) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alertes de Jarvis", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, c.key.hashCode(), c.fix(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jarvis a perdu une autorisation")
            .setContentText("Réactivez ${c.label}.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(c.key.hashCode(), notification) }
    }

    private const val CHANNEL = "jarvis_alerts"
}

/** Les numéros de l'utilisateur : ceux de ses SIM et celui qu'il a donné à Jarvis. */
object OwnNumbers {

    @SuppressLint("MissingPermission", "HardwareIds") // Autorisations vérifiées juste avant.
    fun all(context: Context): List<String> {
        val numbers = mutableListOf<String>()
        Prefs(context).ownNumber.takeIf { it.isNotBlank() }?.let { numbers += it }
        val canRead = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 26 && context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED)
        if (canRead) runCatching {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sm.activeSubscriptionInfoList.orEmpty().forEach { info ->
                val number = if (Build.VERSION.SDK_INT >= 33) runCatching { sm.getPhoneNumber(info.subscriptionId) }.getOrNull()
                else @Suppress("DEPRECATION") info.number
                number?.takeIf { it.isNotBlank() }?.let { numbers += it }
            }
        }
        return numbers.distinct()
    }

    /** L'expéditeur est-il l'utilisateur lui-même (SMS envoyé depuis son propre numéro) ? */
    fun isOwn(context: Context, address: String): Boolean =
        all(context).any { runCatching { PhoneNumberUtils.compare(context, it, address) }.getOrDefault(false) }
}
