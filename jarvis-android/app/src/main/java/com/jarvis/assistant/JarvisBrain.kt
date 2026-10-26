package com.jarvis.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.WebSearchTool20260209
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ce que Jarvis peut faire sur le téléphone. Implémenté par [PhoneActions]. */
interface ToolExecutor {
    /** Exécute l'outil et renvoie un compte rendu court pour Claude. */
    fun execute(name: String, input: Map<String, Any?>): String
}

/**
 * Le « cerveau » de Jarvis : une conversation avec Claude, avec des outils
 * qui agissent sur le téléphone et la recherche web côté serveur.
 */
class JarvisBrain(apiKey: String, private val executor: ToolExecutor) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    private val history = mutableListOf<MessageParam>()
    private var lastExchange = 0L

    /** Envoie ce que l'utilisateur a dit et renvoie la réponse à prononcer. */
    @Synchronized
    fun ask(userText: String): String {
        // Nouvelle conversation après 10 minutes de silence : l'historique
        // reste en ajout seul pendant une conversation, jamais réécrit.
        val now = System.currentTimeMillis()
        if (now - lastExchange > IDLE_RESET_MS || history.size > MAX_MESSAGES) history.clear()
        lastExchange = now

        val stamp = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.FRANCE).format(Date())
        history += MessageParam.ofUser("[$stamp] $userText")

        try {
            repeat(MAX_STEPS) {
                val response = client.messages().create(buildParams())
                history += response.toParam()

                when (response.stopReason().orElse(null)) {
                    StopReason.TOOL_USE -> {
                        val results = response.content().mapNotNull { block ->
                            block.toolUse().orElse(null)?.let { use ->
                                val input = jsonToMap(use._input())
                                val output = runCatching { executor.execute(use.name(), input) }
                                    .getOrElse { "Échec : ${it.message}" }
                                ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                        .toolUseId(use.id())
                                        .content(output)
                                        .isError(output.startsWith("Échec"))
                                        .build()
                                )
                            }
                        }
                        history += MessageParam.ofUserOfContentBlockParams(results)
                    }
                    // La recherche web a fait une pause : on relance tel quel.
                    StopReason.PAUSE_TURN -> Unit
                    StopReason.REFUSAL -> return "Je crains de ne pas pouvoir vous aider sur ce point, Monsieur."
                    else -> {
                        val text = response.content()
                            .mapNotNull { it.text().orElse(null)?.text() }
                            .joinToString(" ")
                            .trim()
                        return text.ifEmpty { "C'est fait, Monsieur." }
                    }
                }
            }
            return "Cette demande me prend trop d'étapes, Monsieur. Pouvez-vous la simplifier ?"
        } catch (e: UnauthorizedException) {
            history.clear()
            return "Ma clé d'accès à Claude est refusée. Vérifiez-la dans les réglages."
        } catch (e: RateLimitException) {
            history.clear()
            return "Trop de demandes d'un coup, Monsieur. Réessayez dans un instant."
        } catch (e: AnthropicServiceException) {
            history.clear()
            return "Le serveur a répondu par une erreur ${e.statusCode()}."
        } catch (e: Exception) {
            history.clear()
            return "Je n'arrive pas à joindre mes serveurs. Vérifiez la connexion internet."
        }
    }

    fun reset() = synchronized(this) { history.clear() }

    private fun buildParams(): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4096L)
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(SYSTEM_PROMPT)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()
                )
            )
            // Effort bas : réponses rapides, adaptées à la voix.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            // Si Claude Opus 5.5 refuse une demande, le serveur la repasse à un autre modèle.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addTool(WebSearchTool20260209.builder().maxUses(3L).build())
            .messages(history)
        PHONE_TOOLS.forEach { builder.addTool(it) }
        return builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonToMap(value: JsonValue): Map<String, Any?> =
        (value.convert(Map::class.java) as? Map<String, Any?>) ?: emptyMap()

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val IDLE_RESET_MS = 10 * 60 * 1000L
        private const val MAX_MESSAGES = 60
        private const val MAX_STEPS = 8

        private val SYSTEM_PROMPT = """
            Tu es J.A.R.V.I.S., l'assistant vocal personnel de l'utilisateur, sur son téléphone Android.
            Style : poli, efficace, une pointe d'humour britannique pince-sans-rire. Appelle l'utilisateur « Monsieur », sauf s'il te demande autre chose.
            Tes réponses sont lues à voix haute : une à trois phrases courtes, sans markdown, sans listes à puces, sans emoji, sans URL.
            Réponds dans la langue de l'utilisateur, en français par défaut. Chaque message commence par la date et l'heure actuelles entre crochets.
            Tu peux agir sur le téléphone avec tes outils et chercher sur le web pour l'actualité, la météo ou tout fait récent.
            Quand une demande correspond à un outil, utilise-le puis confirme en une phrase ce que tu as fait.
            Avant d'envoyer un SMS, relis le destinataire et le message à l'utilisateur et attends son « oui ». Si un contact est ambigu ou introuvable, demande une précision.
            Quand tu poses une question à l'utilisateur, termine ta réponse par un point d'interrogation : le micro se rouvrira pour sa réponse.
        """.trimIndent()

        private fun prop(type: String, description: String): JsonValue =
            JsonValue.from(mapOf("type" to type, "description" to description))

        private fun tool(
            name: String,
            description: String,
            props: Map<String, JsonValue> = emptyMap(),
            required: List<String> = emptyList(),
        ): Tool {
            val properties = Tool.InputSchema.Properties.builder()
            props.forEach { (k, v) -> properties.putAdditionalProperty(k, v) }
            return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(
                    Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(required)
                        .build()
                )
                .build()
        }

        private val PHONE_TOOLS = listOf(
            tool(
                "appeler", "Passe un appel téléphonique à un contact ou à un numéro.",
                mapOf("destinataire" to prop("string", "Nom du contact tel qu'enregistré, ou numéro de téléphone")),
                listOf("destinataire"),
            ),
            tool(
                "envoyer_sms",
                "Envoie un SMS. N'utilise cet outil qu'après avoir relu le destinataire et le message à l'utilisateur et obtenu sa confirmation.",
                mapOf(
                    "destinataire" to prop("string", "Nom du contact ou numéro de téléphone"),
                    "message" to prop("string", "Texte exact du SMS"),
                ),
                listOf("destinataire", "message"),
            ),
            tool(
                "regler_alarme", "Crée une alarme dans l'application Horloge.",
                mapOf(
                    "heure" to prop("integer", "Heure de 0 à 23"),
                    "minute" to prop("integer", "Minute de 0 à 59"),
                    "libelle" to prop("string", "Nom de l'alarme, facultatif"),
                ),
                listOf("heure", "minute"),
            ),
            tool(
                "minuteur", "Lance un minuteur.",
                mapOf(
                    "secondes" to prop("integer", "Durée totale en secondes"),
                    "libelle" to prop("string", "Nom du minuteur, facultatif"),
                ),
                listOf("secondes"),
            ),
            tool(
                "ouvrir_application", "Ouvre une application installée à partir de son nom.",
                mapOf("nom" to prop("string", "Nom de l'application, par exemple WhatsApp ou Spotify")),
                listOf("nom"),
            ),
            tool(
                "lampe_torche", "Allume ou éteint la lampe torche.",
                mapOf("allumer" to prop("boolean", "true pour allumer, false pour éteindre")),
                listOf("allumer"),
            ),
            tool(
                "regler_volume", "Règle le volume multimédia.",
                mapOf("pourcentage" to prop("integer", "Volume de 0 à 100")),
                listOf("pourcentage"),
            ),
            tool(
                "jouer_musique", "Lance de la musique dans l'application musicale par défaut.",
                mapOf("recherche" to prop("string", "Artiste, titre, album ou ambiance")),
                listOf("recherche"),
            ),
            tool(
                "itineraire", "Lance la navigation GPS vers une destination dans Google Maps.",
                mapOf("destination" to prop("string", "Adresse ou lieu")),
                listOf("destination"),
            ),
            tool("etat_telephone", "Donne le niveau de batterie, la charge et la connexion réseau."),
        )
    }
}
