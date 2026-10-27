package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mémoire à long terme de Jarvis, gardée dans un fichier privé sur le téléphone :
 * - les souvenirs : faits sur l'utilisateur (« sa fille s'appelle Léa ») et consignes
 *   apprises sur sa façon de faire (« ne pas lire les SMS de la banque ») ;
 * - les résumés des conversations précédentes ;
 * - la conversation en cours, pour la résumer même si Jarvis a été fermé entre-temps.
 * Elle survit aux mises à jour de l'appli ; elle disparaît seulement si l'appli est désinstallée.
 */
class JarvisMemory(context: Context) {

    data class Memory(val id: Int, val type: String, val text: String, val date: String)
    data class Summary(val date: String, val text: String)

    private val file = File(context.filesDir, "memoire.json")
    private val memories = mutableListOf<Memory>()
    private val summaries = mutableListOf<Summary>()
    private val transcript = mutableListOf<String>()
    private var transcriptStart = ""

    init { load() }

    /** Retient un souvenir ; remplace un souvenir quasi identique. Renvoie le compte rendu pour Claude. */
    @Synchronized
    fun remember(type: String, text: String): String {
        val clean = text.trim().take(300)
        if (clean.isEmpty()) return "Échec : rien à retenir."
        val kind = if (type == CONSIGNE) CONSIGNE else FAIT
        memories.removeAll { normalize(it.text) == normalize(clean) }
        val id = (memories.maxOfOrNull { it.id } ?: 0) + 1
        memories += Memory(id, kind, clean, today())
        while (memories.size > MAX_MEMORIES) memories.removeAt(0)
        save()
        JarvisBus.line(false, "Mémorisé : $clean")
        return "Retenu (souvenir n° $id)."
    }

    /** Oublie les souvenirs dont le texte contient [query], ou le souvenir n° [query]. */
    @Synchronized
    fun forget(query: String): String {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return "Échec : précisez ce qu'il faut oublier."
        val gone = memories.filter { it.id.toString() == query.trim() || normalize(it.text).contains(wanted) }
        if (gone.isEmpty()) return "Aucun souvenir ne correspond à « $query »."
        memories.removeAll(gone.toSet())
        save()
        return "Oublié : " + gone.joinToString(" ; ") { it.text }
    }

    @Synchronized
    fun forgetAll(): String {
        memories.clear(); summaries.clear(); transcript.clear()
        save()
        return "Toute la mémoire a été effacée."
    }

    /** Ce que Claude doit savoir au début de chaque conversation. Vide s'il n'y a encore rien. */
    @Synchronized
    fun promptBlock(): String {
        if (memories.isEmpty() && summaries.isEmpty()) return ""
        val out = StringBuilder("Ta mémoire à long terme (gardée sur le téléphone) :\n")
        memories.filter { it.type == CONSIGNE }.takeIf { it.isNotEmpty() }?.let { list ->
            out.append("\nConsignes apprises de l'utilisateur, à toujours appliquer :\n")
            list.forEach { out.append("- [n° ${it.id}] ${it.text}\n") }
        }
        memories.filter { it.type == FAIT }.takeIf { it.isNotEmpty() }?.let { list ->
            out.append("\nCe que tu sais de l'utilisateur :\n")
            list.forEach { out.append("- [n° ${it.id}, ${it.date}] ${it.text}\n") }
        }
        if (summaries.isNotEmpty()) {
            out.append("\nRésumés de vos conversations précédentes, de la plus ancienne à la plus récente :\n")
            summaries.forEach { out.append("- ${it.date} : ${it.text}\n") }
        }
        return out.toString().trim()
    }

    /** Ajoute une réplique à la conversation en cours (gardée sur disque jusqu'à son résumé). */
    @Synchronized
    fun addLine(line: String) {
        if (transcript.isEmpty()) transcriptStart = SimpleDateFormat("EEEE d MMMM yyyy à HH:mm", Locale.FRANCE).format(Date())
        transcript += line.take(1500)
        while (transcript.size > MAX_TRANSCRIPT) transcript.removeAt(0)
        save()
    }

    /** Retire la conversation terminée pour la résumer ; null s'il n'y a rien à résumer. */
    @Synchronized
    fun takeTranscript(): Pair<String, String>? {
        if (transcript.isEmpty()) return null
        val result = transcriptStart to transcript.joinToString("\n")
        transcript.clear()
        save()
        return result
    }

    @Synchronized
    fun addSummary(date: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.equals("RIEN", ignoreCase = true)) return
        summaries += Summary(date, clean.take(700))
        while (summaries.size > MAX_SUMMARIES) summaries.removeAt(0)
        save()
    }

    private fun load() {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        json.optJSONArray("souvenirs")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                memories += Memory(it.getInt("id"), it.optString("type", FAIT), it.getString("texte"), it.optString("date"))
            }
        }
        json.optJSONArray("conversations")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { summaries += Summary(it.optString("date"), it.getString("resume")) }
        }
        json.optJSONArray("en_cours")?.let { a -> for (i in 0 until a.length()) transcript += a.getString(i) }
        transcriptStart = json.optString("debut")
    }

    private fun save() {
        val json = JSONObject()
            .put("souvenirs", JSONArray().apply {
                memories.forEach { put(JSONObject().put("id", it.id).put("type", it.type).put("texte", it.text).put("date", it.date)) }
            })
            .put("conversations", JSONArray().apply {
                summaries.forEach { put(JSONObject().put("date", it.date).put("resume", it.text)) }
            })
            .put("en_cours", JSONArray(transcript))
            .put("debut", transcriptStart)
        // Écriture dans un fichier temporaire puis renommage : jamais de mémoire à moitié écrite.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    private fun today() = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date())

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]"), "")

    companion object {
        const val FAIT = "fait"
        const val CONSIGNE = "consigne"
        private const val MAX_MEMORIES = 150
        private const val MAX_SUMMARIES = 40
        private const val MAX_TRANSCRIPT = 80
    }
}
