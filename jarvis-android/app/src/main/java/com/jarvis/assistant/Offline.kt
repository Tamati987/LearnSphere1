package com.jarvis.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mode hors ligne : sans internet, Claude est injoignable. Jarvis reconnaît alors lui-même les
 * commandes simples (heure, date, lampe, volume, appel, réveil, minuteur, coach, musique,
 * applis, batterie, veille) et les exécute avec ses outils habituels, sur le téléphone.
 */
object Offline {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Réponse à dire pour [text], en exécutant l'action correspondante. */
    fun answer(text: String, actions: ToolExecutor): String {
        val q = normalize(text)
        val numbers = Regex("\\d+").findAll(q).map { it.value.toInt() }.toList()
        fun after(vararg words: String): String? = words.firstNotNullOfOrNull { w ->
            Regex("\\b$w\\s+(.+)").find(q)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun done(result: String, ok: String) = if (result.startsWith("Échec")) result.removePrefix("Échec : ") else ok

        return when {
            "heure" in q && ("quelle" in q || "il est" in q) ->
                "Il est ${SimpleDateFormat("H 'heures' mm", Locale.FRANCE).format(Date())}, Monsieur."
            ("date" in q || "quel jour" in q) ->
                "Nous sommes le ${SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date())}."
            "lampe" in q || "torche" in q -> {
                val on = !("eteins" in q || "eteint" in q || "coupe" in q)
                done(actions.execute("lampe_torche", mapOf("allumer" to on)), if (on) "Lampe allumée." else "Lampe éteinte.")
            }
            "volume" in q || "son" in q && ("monte" in q || "baisse" in q) -> {
                val percent = numbers.firstOrNull() ?: if ("baisse" in q) 30 else 70
                done(actions.execute("regler_volume", mapOf("pourcentage" to percent)), "Volume à $percent %.")
            }
            q.startsWith("appelle") || "telephone a" in q -> {
                val who = after("appelle", "telephone a") ?: return "Qui dois-je appeler, Monsieur ?"
                done(actions.execute("appeler", mapOf("destinataire" to who)), "J'appelle $who.")
            }
            "reveille" in q || "reveil" in q -> {
                val hour = numbers.getOrNull(0) ?: return "À quelle heure dois-je vous réveiller ?"
                val minute = numbers.getOrNull(1) ?: 0
                done(actions.execute("reveil_jarvis", mapOf("heure" to hour, "minute" to minute)), "Réveil réglé à $hour heures ${if (minute > 0) minute else ""}.")
            }
            "minuteur" in q || "chrono" in q -> {
                val n = numbers.firstOrNull() ?: return "Pour combien de temps, Monsieur ?"
                val seconds = if ("seconde" in q) n else n * 60
                done(actions.execute("minuteur", mapOf("secondes" to seconds)), "Minuteur lancé.")
            }
            "coach" in q || "seance" in q || "entrainement" in q ->
                done(actions.execute("coach", mapOf("action" to if ("arrete" in q || "stop" in q) "arreter" else "demarrer")), "Séance lancée.")
            "pause" in q || "suivant" in q || "precedent" in q || "reprends" in q -> {
                val action = when {
                    "suivant" in q -> "suivant"; "precedent" in q -> "precedent"; "reprends" in q -> "lecture"; else -> "pause"
                }
                done(actions.execute("controle_musique", mapOf("action" to action)), "C'est fait.")
            }
            q.startsWith("ouvre") || q.startsWith("lance") -> {
                val app = after("ouvre", "lance") ?: return "Quelle application dois-je ouvrir ?"
                done(actions.execute("ouvrir_application", mapOf("nom" to app)), "J'ouvre $app.")
            }
            "batterie" in q -> actions.execute("etat_telephone", emptyMap())
            "veille" in q || "dors" in q -> done(actions.execute("veille", emptyMap()), "Je me mets en veille.")
            else -> "Je suis hors ligne, Monsieur. Sans internet, je ne sais faire que les commandes simples : l'heure, " +
                "la lampe, le volume, un appel, un réveil, un minuteur, le coach, la musique ou ouvrir une application."
        }
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
