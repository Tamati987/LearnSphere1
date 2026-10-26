package com.jarvis.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Réveil de Jarvis : à l'heure dite, [WakeUpActivity] s'ouvre (même téléphone
 * verrouillé) et Jarvis annonce de sa voix que c'est l'heure.
 * Un seul réveil à la fois, gardé dans les préférences pour survivre au redémarrage.
 */
object JarvisAlarm {
    private const val PREFS = "jarvis_alarm"

    /** Programme le réveil à la prochaine occurrence de [hour]:[minute]. Renvoie l'heure exacte. */
    fun schedule(context: Context, hour: Int, minute: Int, message: String?): Calendar {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleAt(context, at.timeInMillis, message)
        return at
    }

    fun scheduleAt(context: Context, timeMs: Long, message: String?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            throw IllegalStateException("Android n'autorise pas encore Jarvis à programmer des réveils précis")
        }
        val show = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(timeMs, show), wakeIntent(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("time", timeMs)
            .putString("message", message)
            .apply()
    }

    fun cancel(context: Context): Boolean {
        val had = nextTime(context) != null
        context.getSystemService(AlarmManager::class.java).cancel(wakeIntent(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        return had
    }

    /** Heure du prochain réveil, ou null s'il n'y en a pas. */
    fun nextTime(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("time", 0L).takeIf { it > 0L }

    fun message(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("message", null)

    /** Le réveil a sonné : on l'oublie, sauf si l'utilisateur le repousse. */
    fun consumed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("time").apply()
    }

    private fun wakeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, WakeUpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Après un redémarrage du téléphone, Android oublie les réveils : on reprogramme le nôtre. */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val time = JarvisAlarm.nextTime(context) ?: return
        if (time > System.currentTimeMillis()) {
            runCatching { JarvisAlarm.scheduleAt(context, time, JarvisAlarm.message(context)) }
        } else {
            JarvisAlarm.consumed(context)
        }
    }
}
