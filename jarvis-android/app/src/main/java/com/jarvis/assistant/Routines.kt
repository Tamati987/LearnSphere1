package com.jarvis.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Routines de Jarvis : à une heure et certains jours, Jarvis prend l'initiative et suit une
 * consigne donnée par l'utilisateur (« propose du reggae roots », « lance le coach »,
 * « donne-moi la météo »). La consigne est confiée à Claude, qui agit comme si on la lui demandait.
 */
object Routines {
    data class Routine(val id: Int, val hour: Int, val minute: Int, val days: Set<Int>, val instruction: String)

    private const val PREFS = "jarvis_routines"
    const val EXTRA_ID = "routine_id"

    fun all(context: Context): List<Routine> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("routines", "[]")
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val days = o.getJSONArray("days").let { d -> (0 until d.length()).map { d.getInt(it) }.toSet() }
            Routine(o.getInt("id"), o.getInt("hour"), o.getInt("minute"), days, o.getString("instruction"))
        }
    }

    fun add(context: Context, hour: Int, minute: Int, days: Set<Int>, instruction: String): Routine {
        val list = all(context)
        val routine = Routine((list.maxOfOrNull { it.id } ?: 0) + 1, hour, minute, days, instruction.trim())
        save(context, list + routine)
        schedule(context, routine)
        return routine
    }

    /** Supprime la routine n° [id], ou toutes si [id] est null. Renvoie le nombre supprimé. */
    fun remove(context: Context, id: Int?): Int {
        val (gone, kept) = all(context).partition { id == null || it.id == id }
        gone.forEach { cancel(context, it.id) }
        save(context, kept)
        return gone.size
    }

    fun describe(r: Routine): String {
        val days = when {
            r.days.isEmpty() -> "une seule fois"
            r.days.size == 7 -> "tous les jours"
            else -> JarvisAlarm.DAY_NAMES.filterValues { it in r.days }.keys.joinToString(", ")
        }
        return "n° ${r.id} : %02d:%02d, %s : « %s »".format(r.hour, r.minute, days, r.instruction)
    }

    fun scheduleAll(context: Context) = all(context).forEach { runCatching { schedule(context, it) } }

    /** La routine vient de se déclencher : prochaine fois, ou suppression si elle était unique. */
    fun fired(context: Context, id: Int): Routine? {
        val routine = all(context).firstOrNull { it.id == id } ?: return null
        if (routine.days.isEmpty()) save(context, all(context).filterNot { it.id == id })
        else runCatching { schedule(context, routine) }
        return routine
    }

    private fun schedule(context: Context, r: Routine) {
        val at = JarvisAlarm.nextTrigger(Alarm(r.id, r.hour, r.minute, r.days, r.instruction))
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, intent(context, r.id))
    }

    private fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context, id))
    }

    private fun intent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, 500 + id,
        Intent(context, RoutineReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun save(context: Context, list: List<Routine>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute)
                    .put("days", JSONArray(it.days.sorted())).put("instruction", it.instruction)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("routines", array.toString()).apply()
    }

    /** Heure courante, pour les messages du journal. */
    fun now(): String = "%02d:%02d".format(
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE),
    )
}

/** L'heure d'une routine est arrivée : Jarvis s'en charge s'il est éveillé. */
class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routine = Routines.fired(context, intent.getIntExtra(Routines.EXTRA_ID, -1)) ?: return
        val service = JarvisService.instance
        if (service == null) {
            JarvisBus.line(false, "Routine de ${Routines.now()} non faite : Jarvis était en veille (« ${routine.instruction} »).")
            return
        }
        service.runRoutine(routine.instruction)
    }
}
