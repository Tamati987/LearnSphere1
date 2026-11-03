package com.jarvis.assistant

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/**
 * Cerveau hors ligne de Jarvis : un petit modèle de langage (Gemma, format .task de MediaPipe)
 * installé sur le téléphone. Il ne sert que sans internet, pour discuter ; les commandes du
 * téléphone passent d'abord par [Offline]. Il lit la mémoire à long terme, les consignes et les
 * derniers messages de l'historique pour garder le contexte.
 */
object LocalBrain {
    private var llm: LlmInference? = null

    /** Fichier du modèle, dans le stockage propre à l'appli (exclu des sauvegardes). */
    fun modelFile(context: Context) = File(context.getExternalFilesDir("models") ?: File(context.filesDir, "models"), "offline.task")

    fun isInstalled(context: Context) = modelFile(context).let { it.exists() && it.length() > 50_000_000 }

    fun describe(context: Context): String {
        val f = modelFile(context)
        return if (isInstalled(context)) "Modèle hors ligne installé (${f.length() / 1_000_000} Mo)." else "Aucun modèle hors ligne installé."
    }

    /** Réponse du modèle local, ou null s'il n'est pas installé ou ne peut pas tourner sur ce téléphone. */
    @Synchronized
    fun answer(context: Context, memory: JarvisMemory, question: String): String? {
        if (!isInstalled(context)) return null
        val engine = llm ?: runCatching {
            LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile(context).absolutePath)
                    .setMaxTokens(1536)
                    .build(),
            )
        }.onFailure { JarvisBus.line(false, "Modèle hors ligne impossible à charger : ${it.message}") }.getOrNull()?.also { llm = it }
            ?: return null
        val history = History.recent(context, 10).joinToString("\n") { "${it.role} : ${it.text.take(300)}" }
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append(
                "Tu es Jarvis, l'assistant vocal de l'utilisateur, en mode hors ligne (sans internet). " +
                    "Réponds en français, en une à trois phrases courtes, poliment, en l'appelant Monsieur. " +
                    "Sans internet, tu ne connais pas l'actualité ni la météo : dis-le simplement si on te les demande.\n\n"
            )
            memory.promptBlock().takeIf { it.isNotBlank() }?.let { append(it.take(3000)).append("\n\n") }
            if (history.isNotBlank()) append("Derniers échanges :\n").append(history).append("\n\n")
            append("Message de l'utilisateur : ").append(question)
            append("<end_of_turn>\n<start_of_turn>model\n")
        }
        return runCatching { engine.generateResponse(prompt).replace("<end_of_turn>", "").trim() }
            .onFailure { JarvisBus.line(false, "Modèle hors ligne en erreur : ${it.message}") }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Libère la mémoire du téléphone (le modèle occupe environ 1 Go) quand le réseau revient. */
    @Synchronized
    fun release() {
        llm?.close()
        llm = null
    }

    /** Copie un modèle .task choisi par l'utilisateur (déjà téléchargé). Appel long : hors du fil principal. */
    fun importFrom(context: Context, uri: Uri): String {
        release()
        val target = modelFile(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "offline.part")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
            ?: return "Fichier illisible."
        if (tmp.length() < 50_000_000) { tmp.delete(); return "Ce fichier est trop petit pour être un modèle de langage (.task)." }
        target.delete()
        tmp.renameTo(target)
        return describe(context)
    }

    /** Télécharge le modèle depuis [url], en Wi-Fi seulement, avec le gestionnaire de téléchargements d'Android. */
    fun download(context: Context, url: String): String {
        val dir = modelFile(context).parentFile ?: return "Stockage indisponible."
        dir.mkdirs()
        File(dir, DOWNLOAD_NAME).delete()
        val request = DownloadManager.Request(Uri.parse(url.trim()))
            .setTitle("Jarvis : modèle hors ligne")
            .setDescription("Téléchargement en Wi-Fi uniquement")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", DOWNLOAD_NAME)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putLong(KEY_DOWNLOAD, id).apply()
        return "Téléchargement lancé : il se fera en Wi-Fi uniquement (environ 0,5 à 2 Go)."
    }

    /** Fin d'un téléchargement : si c'est celui du modèle et qu'il a réussi, il devient le modèle hors ligne. */
    fun onDownloadComplete(context: Context, id: Long) {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        if (id != prefs.getLong(KEY_DOWNLOAD, -1)) return
        prefs.edit().remove(KEY_DOWNLOAD).apply()
        val ok = context.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id))?.use { c ->
            c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        } ?: false
        val part = File(modelFile(context).parentFile, DOWNLOAD_NAME)
        if (!ok || part.length() < 50_000_000) {
            part.delete()
            JarvisBus.line(false, "Téléchargement du modèle hors ligne échoué (lien invalide, accès refusé ou fichier incomplet).")
            return
        }
        release()
        modelFile(context).delete()
        part.renameTo(modelFile(context))
        JarvisBus.line(false, describe(context))
    }

    private const val DOWNLOAD_NAME = "offline-download.task"
    private const val KEY_DOWNLOAD = "offline_model_download"
}

/** Le gestionnaire de téléchargements d'Android prévient Jarvis à la fin d'un téléchargement. */
class ModelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val pending = goAsync()
        Thread { try { LocalBrain.onDownloadComplete(context.applicationContext, id) } finally { pending.finish() } }.start()
    }
}
