package com.jarvis.assistant

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sauvegarde de Jarvis en un seul fichier JSON, dans Téléchargements : réglages, mémoire à long
 * terme et consignes, résumés, réveils, routines et historique des conversations. Les clés
 * d'accès (Anthropic, ElevenLabs, GitHub) n'y sont jamais écrites. La réimportation fusionne :
 * ce qui existe déjà n'est pas dupliqué.
 */
object DataTransfer {

    private const val FORMAT = "jarvis-sauvegarde"
    // Clés jamais exportées : secrets et états propres à l'installation.
    private val SECRET_KEYS = setOf("anthropic_key", "elevenlabs_key")

    /** Écrit la sauvegarde ; renvoie le nom du fichier créé dans Téléchargements. */
    fun export(context: Context): String {
        val dao = JarvisDb.dao(context)
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exporte_le", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(Date()))
            .put("reglages", settings(context))
            .put("souvenirs", JSONArray().apply {
                dao.memories().forEach { put(JSONObject().put("type", it.type).put("texte", it.text).put("date", it.date)) }
            })
            .put("resumes", JSONArray().apply {
                dao.summaries().forEach { put(JSONObject().put("date", it.date).put("texte", it.text)) }
            })
            .put("reveils", JSONArray().apply {
                JarvisAlarm.all(context).forEach {
                    put(
                        JSONObject().put("heure", it.hour).put("minute", it.minute).put("jours", JSONArray(it.days.sorted()))
                            .put("phrase", it.text).put("agenda", it.agenda).put("meteo", it.weather).put("musique", it.music ?: "")
                    )
                }
            })
            .put("routines", JSONArray().apply {
                Routines.all(context).forEach {
                    put(JSONObject().put("heure", it.hour).put("minute", it.minute).put("jours", JSONArray(it.days.sorted())).put("consigne", it.instruction))
                }
            })
            .put("historique", JSONArray().apply {
                dao.allExchanges().forEach { put(JSONObject().put("heure", it.time).put("auteur", it.role).put("texte", it.text)) }
            })
        val name = "jarvis-sauvegarde-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".json"
        write(context, name, json.toString(2))
        return name
    }

    private fun settings(context: Context): JSONObject {
        val out = JSONObject()
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (key in SECRET_KEYS || value == null) return@forEach
            val type = when (value) {
                is Boolean -> "bool"; is Int -> "int"; is Long -> "long"; is Float -> "float"; else -> "text"
            }
            out.put(key, JSONObject().put("type", type).put("valeur", value.toString()))
        }
        return out
    }

    private fun write(context: Context, name: String, text: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Téléchargements inaccessible")
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: throw IllegalStateException("écriture impossible")
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, name).writeText(text)
        }
    }

    /** Réimporte une sauvegarde choisie par l'utilisateur ; renvoie le compte rendu de la fusion. */
    fun import(context: Context, uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return "Fichier illisible."
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json == null || json.optString("format") != FORMAT) return "Ce fichier n'est pas une sauvegarde de Jarvis."
        val dao = JarvisDb.dao(context)

        // Réglages : ceux de la sauvegarde remplacent les valeurs actuelles.
        var settings = 0
        json.optJSONObject("reglages")?.let { r ->
            val editor = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit()
            r.keys().forEach { key ->
                if (key in SECRET_KEYS) return@forEach
                val o = r.optJSONObject(key) ?: return@forEach
                val v = o.optString("valeur")
                when (o.optString("type")) {
                    "bool" -> editor.putBoolean(key, v.toBoolean())
                    "int" -> v.toIntOrNull()?.let { editor.putInt(key, it) }
                    "long" -> v.toLongOrNull()?.let { editor.putLong(key, it) }
                    "float" -> v.toFloatOrNull()?.let { editor.putFloat(key, it) }
                    else -> editor.putString(key, v)
                }
                settings++
            }
            editor.apply()
        }

        // Mémoire : un souvenir déjà connu (même texte) ou un résumé identique n'est pas ajouté.
        val memories = dao.memories().toMutableList()
        var newMemories = 0
        json.optJSONArray("souvenirs")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val textValue = o.optString("texte").trim()
                if (textValue.isEmpty() || memories.any { normalize(it.text) == normalize(textValue) }) continue
                memories += MemoryRow((memories.maxOfOrNull { it.id } ?: 0) + 1, o.optString("type", JarvisMemory.FAIT), textValue, o.optString("date"))
                newMemories++
            }
        }
        val summaries = dao.summaries().toMutableList()
        var newSummaries = 0
        json.optJSONArray("resumes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (summaries.any { it.date == o.optString("date") && it.text == o.optString("texte") }) continue
                summaries += SummaryRow(date = o.optString("date"), text = o.optString("texte"))
                newSummaries++
            }
        }
        dao.replaceMemory(memories, summaries.map { it.copy(id = 0) }, dao.transcript())

        // Réveils : ajoutés s'il n'y en a pas déjà un à la même heure.
        var newAlarms = 0
        json.optJSONArray("reveils")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val hour = o.optInt("heure"); val minute = o.optInt("minute")
                if (JarvisAlarm.all(context).any { it.hour == hour && it.minute == minute }) continue
                runCatching {
                    JarvisAlarm.add(
                        context, hour, minute, days(o.optJSONArray("jours")), o.optString("phrase").takeIf { it.isNotBlank() },
                        o.optBoolean("agenda"), o.optString("musique").takeIf { it.isNotBlank() }, o.optBoolean("meteo"),
                    )
                    newAlarms++
                }
            }
        }

        // Routines : ajoutées si la même (heure et consigne) n'existe pas.
        var newRoutines = 0
        json.optJSONArray("routines")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val hour = o.optInt("heure"); val minute = o.optInt("minute"); val instruction = o.optString("consigne")
                if (instruction.isBlank() || Routines.all(context).any { it.hour == hour && it.minute == minute && it.instruction == instruction }) continue
                runCatching { Routines.add(context, hour, minute, days(o.optJSONArray("jours")), instruction); newRoutines++ }
            }
        }

        // Historique : un message identique (même heure, auteur et texte) est ignoré par la base.
        val before = dao.allExchanges().size
        json.optJSONArray("historique")?.let { a ->
            dao.insertExchanges((0 until a.length()).map { a.getJSONObject(it) }.map {
                Exchange(time = it.optLong("heure"), role = it.optString("auteur"), text = it.optString("texte"))
            })
        }
        val newExchanges = dao.allExchanges().size - before

        return "Sauvegarde importée : $settings réglages, $newMemories souvenirs, $newSummaries résumés, " +
            "$newAlarms réveils, $newRoutines routines et $newExchanges messages ajoutés (doublons ignorés)."
    }

    private fun days(array: JSONArray?): Set<Int> = array?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }.orEmpty()

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")
}
