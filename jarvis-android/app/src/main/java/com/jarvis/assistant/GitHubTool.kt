package com.jarvis.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/** Secrets chiffrés sur le téléphone (clé gardée par le Keystore d'Android). */
object SecretStore {
    @Volatile private var prefs: SharedPreferences? = null

    private fun open(context: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: EncryptedSharedPreferences.create(
            "jarvis_secrets",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { prefs = it }
    }

    fun githubToken(context: Context): String =
        runCatching { open(context).getString("github_token", "").orEmpty() }.getOrDefault("")

    fun setGithubToken(context: Context, token: String) {
        runCatching { open(context).edit().putString("github_token", token.trim()).apply() }
            .onFailure { JarvisBus.line(false, "Jeton GitHub non enregistré : ${it.message}") }
    }
}

/**
 * Outil GitHub de Jarvis, avec le jeton personnel de l'utilisateur : lire le dépôt, voir les
 * derniers commits, lister et créer des tickets. Le jeton n'est envoyé qu'à api.github.com.
 */
class GitHubTool(private val context: Context) {

    fun run(action: String, input: Map<String, Any?>): String {
        val token = SecretStore.githubToken(context)
        if (token.isBlank()) {
            return "Échec : aucun jeton GitHub enregistré. L'utilisateur doit en créer un (GitHub → Settings → Developer settings → " +
                "Personal access tokens) et le coller dans l'écran des clés de Jarvis."
        }
        val repo = (input["depot"]?.toString()?.takeIf { it.contains('/') } ?: Prefs(context).githubRepo).trim()
        return runCatching {
            when (action) {
                "derniers_commits" -> commits(token, repo, (input["nombre"] as? Number)?.toInt() ?: 5)
                "lister_fichiers" -> listFiles(token, repo, input["chemin"]?.toString().orEmpty())
                "lire_fichier" -> readFile(token, repo, input["chemin"]?.toString().orEmpty())
                "lister_tickets" -> issues(token, repo, input["etat"]?.toString() ?: "open")
                "creer_ticket" -> createIssue(token, repo, input["titre"]?.toString().orEmpty(), input["description"]?.toString().orEmpty())
                else -> "Échec : action GitHub inconnue « $action »."
            }
        }.getOrElse { "Échec : ${it.message}" }
    }

    private fun commits(token: String, repo: String, count: Int): String {
        val array = JSONArray(get(token, "/repos/$repo/commits?per_page=${count.coerceIn(1, 20)}"))
        if (array.length() == 0) return "Aucun commit dans $repo."
        return (0 until array.length()).joinToString("\n") { i ->
            val c = array.getJSONObject(i)
            val commit = c.getJSONObject("commit")
            val date = commit.getJSONObject("author").optString("date").take(10)
            "${c.getString("sha").take(7)} · $date · ${commit.getString("message").lineSequence().first()}"
        }
    }

    private fun listFiles(token: String, repo: String, path: String): String {
        val array = JSONArray(get(token, "/repos/$repo/contents/${encodePath(path)}"))
        return (0 until array.length()).joinToString("\n") { i ->
            val f = array.getJSONObject(i)
            (if (f.getString("type") == "dir") "📁 " else "") + f.getString("path")
        }
    }

    private fun readFile(token: String, repo: String, path: String): String {
        if (path.isBlank()) return "Échec : précisez le chemin du fichier."
        val file = JSONObject(get(token, "/repos/$repo/contents/${encodePath(path)}"))
        val text = String(Base64.getMimeDecoder().decode(file.getString("content")), Charsets.UTF_8)
        return if (text.length > MAX_FILE) text.take(MAX_FILE) + "\n[… fichier tronqué, ${text.length} caractères au total]" else text
    }

    private fun issues(token: String, repo: String, state: String): String {
        val wanted = if (state.startsWith("ferm") || state == "closed") "closed" else if (state.startsWith("tou") || state == "all") "all" else "open"
        val array = JSONArray(get(token, "/repos/$repo/issues?state=$wanted&per_page=30"))
        // L'API mélange tickets et demandes de fusion : on ne garde que les tickets.
        val list = (0 until array.length()).map { array.getJSONObject(it) }.filter { !it.has("pull_request") }
        if (list.isEmpty()) return "Aucun ticket ($wanted) dans $repo."
        return list.joinToString("\n") { "#${it.getInt("number")} [${it.getString("state")}] ${it.getString("title")}" }
    }

    private fun createIssue(token: String, repo: String, title: String, body: String): String {
        if (title.isBlank()) return "Échec : le ticket a besoin d'un titre."
        val issue = JSONObject(send(token, "POST", "/repos/$repo/issues", JSONObject().put("title", title).put("body", body)))
        return "Ticket #${issue.getInt("number")} créé dans $repo : ${issue.getString("html_url")}"
    }

    private fun encodePath(path: String) = path.trim('/').split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun get(token: String, path: String) = send(token, "GET", path, null)

    private fun send(token: String, method: String, path: String, body: JSONObject?): String {
        val connection = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Jarvis-Android")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = when (code) {
                    401 -> "jeton refusé (expiré ou mal copié)"
                    403 -> "accès refusé : le jeton n'a pas les droits nécessaires"
                    404 -> "introuvable (dépôt, chemin, ou jeton sans accès à ce dépôt)"
                    else -> "erreur $code"
                }
                throw IllegalStateException("GitHub : $reason")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_FILE = 6000
    }
}
