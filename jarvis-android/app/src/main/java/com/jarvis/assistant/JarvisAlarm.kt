package com.jarvis.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Un réveil de Jarvis : une heure, des jours de la semaine (aucun = une seule fois) et la phrase à dire. */
data class Alarm(val id: Int, val hour: Int, val minute: Int, val days: Set<Int>, val text: String)

/**
 * Réveils de Jarvis : à l'heure dite, [WakeUpActivity] s'ouvre (même téléphone
 * verrouillé) et Jarvis dit la phrase du réveil de sa voix.
 * Les réveils sont gardés dans les préférences pour survivre au redémarrage ;
 * ceux qui se répètent sont reprogrammés à chaque sonnerie.
 */
object JarvisAlarm {
    private const val PREFS = "jarvis_alarm"
    private const val SNOOZE_ID = 9999
    const val EXTRA_ID = "alarm_id"

    /** Jours au format de Calendar, du lundi au dimanche. */
    val DAY_NAMES = linkedMapOf(
        "lundi" to Calendar.MONDAY, "mardi" to Calendar.TUESDAY, "mercredi" to Calendar.WEDNESDAY,
        "jeudi" to Calendar.THURSDAY, "vendredi" to Calendar.FRIDAY, "samedi" to Calendar.SATURDAY,
        "dimanche" to Calendar.SUNDAY,
    )
    private val WEEKDAYS = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

    /** Réveils de départ, installés une fois au premier lancement. */
    fun installDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("defaults_v1", false)) return
        add(
            context, 6, 0, WEEKDAYS,
            "Bonjour, Monsieur. Il est six heures. C'est l'heure de vous préparer pour le travail. Je vous souhaite une excellente journée.",
        )
        add(
            context, 12, 0, DAY_NAMES.values.toSet(),
            "Monsieur, il est midi. C'est l'heure du déjeuner. Je vous suggère de faire une pause, vous l'avez bien mérité.",
        )
        prefs.edit().putBoolean("defaults_v1", true).apply()
    }

    /** Ajoute ou remplace le réveil de [hour]:[minute] et le programme. */
    fun add(context: Context, hour: Int, minute: Int, days: Set<Int>, text: String?): Alarm {
        val alarms = all(context).filterNot { it.hour == hour && it.minute == minute && it.id != SNOOZE_ID }
        val id = (alarms.filter { it.id != SNOOZE_ID }.maxOfOrNull { it.id } ?: 0) + 1
        val alarm = Alarm(id, hour, minute, days, text ?: defaultText(hour, minute))
        save(context, alarms + alarm)
        schedule(context, alarm)
        return alarm
    }

    /** Supprime les réveils de [hour]:[minute], ou tous si [hour] est null. Renvoie le nombre supprimé. */
    fun remove(context: Context, hour: Int?, minute: Int?): Int {
        val alarms = all(context)
        val (gone, kept) = alarms.partition { hour == null || (it.hour == hour && (minute == null || it.minute == minute)) }
        gone.forEach { cancel(context, it.id) }
        save(context, kept)
        return gone.size
    }

    fun get(context: Context, id: Int): Alarm? = all(context).firstOrNull { it.id == id }

    fun all(context: Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alarms", "[]")
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val days = o.getJSONArray("days").let { d -> (0 until d.length()).map { d.getInt(it) }.toSet() }
            Alarm(o.getInt("id"), o.getInt("hour"), o.getInt("minute"), days, o.getString("text"))
        }
    }

    /** Le réveil vient de sonner : on programme sa prochaine fois, ou on l'efface s'il était unique. */
    fun fired(context: Context, id: Int) {
        val alarm = get(context, id) ?: return
        if (alarm.days.isEmpty()) save(context, all(context).filterNot { it.id == id })
        else schedule(context, alarm)
    }

    /** Fait sonner à nouveau [text] dans 10 minutes. */
    fun snooze(context: Context, text: String) {
        val at = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
        val alarm = Alarm(SNOOZE_ID, at.get(Calendar.HOUR_OF_DAY), at.get(Calendar.MINUTE), emptySet(), text)
        save(context, all(context).filterNot { it.id == SNOOZE_ID } + alarm)
        schedule(context, alarm)
    }

    /** Reprogramme tous les réveils, après un redémarrage du téléphone. */
    fun scheduleAll(context: Context) = all(context).forEach { runCatching { schedule(context, it) } }

    /** Description lisible, pour que Jarvis puisse les énumérer. */
    fun describe(alarm: Alarm): String {
        val days = when {
            alarm.days.isEmpty() -> "une seule fois"
            alarm.days.size == 7 -> "tous les jours"
            alarm.days == WEEKDAYS -> "du lundi au vendredi"
            else -> DAY_NAMES.filterValues { it in alarm.days }.keys.joinToString(", ")
        }
        return "%02d:%02d, %s : « %s »".format(alarm.hour, alarm.minute, days, alarm.text)
    }

    fun nextTrigger(alarm: Alarm, from: Calendar = Calendar.getInstance()): Calendar {
        val at = (from.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val dayOk = alarm.days.isEmpty() || at.get(Calendar.DAY_OF_WEEK) in alarm.days
            if (at.after(from) && dayOk) return at
            at.add(Calendar.DAY_OF_YEAR, 1)
        }
        return at
    }

    private fun schedule(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            throw IllegalStateException("Android n'autorise pas encore Jarvis à programmer des réveils précis")
        }
        val show = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val time = nextTrigger(alarm).timeInMillis
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), wakeIntent(context, alarm.id))
    }

    private fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(wakeIntent(context, id))
    }

    private fun save(context: Context, alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { a ->
            array.put(
                JSONObject().put("id", a.id).put("hour", a.hour).put("minute", a.minute)
                    .put("days", JSONArray(a.days.sorted())).put("text", a.text)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alarms", array.toString()).apply()
    }

    private fun defaultText(hour: Int, minute: Int): String {
        val hello = if (hour in 5..17) "Bonjour, Monsieur." else "Bonsoir, Monsieur."
        val time = if (minute == 0) "$hour heures" else "$hour heures $minute"
        return "$hello Il est $time. C'est l'heure de vous réveiller."
    }

    private fun wakeIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context, 100 + id,
        Intent(context, WakeUpActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Après un redémarrage du téléphone, Android oublie les réveils : on reprogramme les nôtres. */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) JarvisAlarm.scheduleAll(context)
    }
}
