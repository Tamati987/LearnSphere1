package com.jarvis.assistant

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mémoire à long terme de Jarvis, gardée dans la base Jarvis (Room) sur le téléphone :
 * - les souvenirs : faits sur l'utilisateur (« sa fille s'appelle Léa ») et consignes
 *   apprises sur sa façon de faire (« ne pas lire les SMS de la banque ») ;
 * - les résumés des conversations précédentes ;
 * - la conversation en cours, pour la résumer même si Jarvis a été fermé entre-temps.
 * Elle survit aux mises à jour de l'appli ; elle disparaît seulement si l'appli est désinstallée.
 */
class JarvisMemory(private val context: Context) {

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
        // Et mot pour mot dans l'historique complet (base Room).
        History.add(context, line)
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

    /** Lit la mémoire dans la base Jarvis ; la première fois, reprend l'ancien fichier memoire.json. */
    private fun load() {
        val dao = JarvisDb.dao(context)
        if (file.exists()) migrateFile()
        dao.memories().forEach { memories += Memory(it.id, it.type, it.text, it.date) }
        dao.summaries().forEach { summaries += Summary(it.date, it.text) }
        dao.transcript().forEach { transcript += it.line }
        transcriptStart = dao.value(KEY_START).orEmpty()
    }

    /** Ancienne mémoire (fichier JSON des versions 3.x à 7.x) : copiée dans la base, puis le fichier est retiré. */
    private fun migrateFile() {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull()
        if (json != null) {
            val oldMemories = json.optJSONArray("souvenirs")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }
                    .map { MemoryRow(it.getInt("id"), it.optString("type", FAIT), it.getString("texte"), it.optString("date")) }
            }.orEmpty()
            val oldSummaries = json.optJSONArray("conversations")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { SummaryRow(date = it.optString("date"), text = it.getString("resume")) }
            }.orEmpty()
            val oldTranscript = json.optJSONArray("en_cours")?.let { a -> (0 until a.length()).map { TranscriptRow(line = a.getString(it)) } }.orEmpty()
            val dao = JarvisDb.dao(context)
            dao.replaceMemory(oldMemories, oldSummaries, oldTranscript)
            dao.setValue(ValueRow(KEY_START, json.optString("debut")))
        }
        file.renameTo(File(file.parentFile, file.name + ".ancien"))
    }

    /** Écrit toute la mémoire dans la base, en une seule transaction (jamais à moitié écrite). */
    private fun save() {
        val dao = JarvisDb.dao(context)
        dao.replaceMemory(
            memories.map { MemoryRow(it.id, it.type, it.text, it.date) },
            summaries.map { SummaryRow(date = it.date, text = it.text) },
            transcript.map { TranscriptRow(line = it) },
        )
        dao.setValue(ValueRow(KEY_START, transcriptStart))
    }

    private fun today() = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date())

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]"), "")

    companion object {
        const val FAIT = "fait"
        const val CONSIGNE = "consigne"
        private const val KEY_START = "transcript_start"
        private const val MAX_MEMORIES = 150
        private const val MAX_SUMMARIES = 40
        private const val MAX_TRANSCRIPT = 80
    }
}
