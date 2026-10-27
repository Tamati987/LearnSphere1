package com.jarvis.assistant

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.Normalizer

/** Un message reçu dans WhatsApp ou Messenger, lu dans sa notification. */
data class AppMessage(val app: String, val conversation: String, val sender: String, val text: String, val time: Long)

/**
 * Lit les notifications de WhatsApp et Messenger (avec l'accès aux notifications
 * accordé par l'utilisateur) : garde les derniers messages, les fait annoncer par
 * Jarvis et retient le bouton « Répondre » de chaque conversation.
 */
class MessageListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isIncomingCall(sbn)) {
            val caller = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Quelqu'un"
            JarvisService.instance?.onIncomingCallNotification(caller)
            return
        }
        val app = APPS[sbn.packageName] ?: return
        val notification = sbn.notification
        // Les notifications de résumé (« 5 messages dans 3 discussions ») ne contiennent pas de message.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            JarvisBus.line(false, "Notification $app ignorée : résumé de plusieurs discussions.")
            return
        }

        val extras = notification.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val conversation = (style?.conversationTitle ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim().orEmpty()
        if (conversation.isEmpty()) {
            JarvisBus.line(false, "Notification $app ignorée : pas d'expéditeur.")
            return
        }

        val incoming = style?.messages?.map {
            AppMessage(app, conversation, it.person?.name?.toString() ?: conversation, it.text?.toString().orEmpty(),
                // Certaines applis (Messenger) n'indiquent pas l'heure du message : on prend celle de la notification.
                if (it.timestamp > 0) it.timestamp else sbn.postTime)
        } ?: listOf(
            AppMessage(
                app, conversation, conversation,
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), sbn.postTime,
            )
        )

        replyAction(notification)?.let { replies[key(app, conversation)] = conversation to it }

        // Une même notification est mise à jour à chaque message : on ne garde que les nouveaux.
        val seen = lastSeen[key(app, conversation)] ?: 0L
        val fresh = incoming.filter { it.time > seen && it.text.isNotBlank() }
        if (fresh.isEmpty()) {
            JarvisBus.line(false, "Notification $app de $conversation : aucun nouveau message.")
            return
        }
        lastSeen[key(app, conversation)] = fresh.maxOf { it.time }
        synchronized(recent) {
            recent.addAll(fresh)
            while (recent.size > MAX_KEPT) recent.removeAt(0)
        }
        // Au premier passage après le démarrage, on ne réannonce pas les anciens messages.
        if (seen == 0L && System.currentTimeMillis() - fresh.last().time > 60_000) {
            JarvisBus.line(false, "Message $app de $conversation trop ancien : pas d'annonce.")
            return
        }
        val announced = fresh.takeLast(3)
        val service = JarvisService.instance
        if (service == null) {
            JarvisBus.line(false, "Message $app de ${announced.last().sender} reçu, mais Jarvis est désactivé : pas d'annonce.")
            return
        }
        service.announceMessage(app, announced.last().sender, conversation, announced.joinToString(" ") { it.text })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isIncomingCall(sbn)) JarvisService.instance?.onCallNotificationRemoved()
    }

    /** Notification d'appel qui sonne : catégorie « appel », avec un bouton pour décrocher. */
    private fun isIncomingCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category != Notification.CATEGORY_CALL) return false
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
            if (type != -1) return type == Notification.CallStyle.CALL_TYPE_INCOMING
        }
        // Sans CallStyle : un appel entrant propose au moins deux boutons (décrocher, refuser).
        return (n.actions?.size ?: 0) >= 2 && n.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
            n.fullScreenIntent != null
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
    }

    override fun onListenerConnected() {
        connected = this
        JarvisBus.line(false, "Accès aux notifications actif : WhatsApp et Messenger seront annoncés.")
    }

    private fun replyAction(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        }

    companion object {
        private val APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger Lite",
        )
        private const val MAX_KEPT = 100
        private val recent = mutableListOf<AppMessage>()
        private val lastSeen = mutableMapOf<String, Long>()
        private val replies = mutableMapOf<String, Pair<String, Notification.Action>>()

        private fun key(app: String, conversation: String) = "$app|${normalize(conversation)}"

        @Volatile private var connected: MessageListener? = null

        /**
         * Notifications affichées en ce moment par les autres applis (météo, banque, livraison…),
         * filtrées par nom d'appli si [app] est donné.
         */
        fun currentNotifications(context: Context, app: String?): List<String>? {
            val listener = connected ?: return null
            val pm = context.packageManager
            val wanted = app?.let(::normalize)?.takeIf { it.isNotEmpty() }
            return runCatching { listener.activeNotifications }.getOrNull().orEmpty()
                .filter { it.packageName != context.packageName }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .mapNotNull { sbn ->
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName)
                    if (wanted != null && !normalize(label).contains(wanted) && !normalize(sbn.packageName).contains(wanted)) {
                        return@mapNotNull null
                    }
                    val e = sbn.notification.extras
                    val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
                        ?.toString().orEmpty()
                    if (title.isBlank() && text.isBlank()) null else "$label : $title — ${text.take(300)}"
                }
                .take(25)
        }

        fun isEnabled(context: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Derniers messages reçus, du plus récent au plus ancien, filtrés par appli et par contact. */
        fun messages(app: String?, contact: String?, limit: Int): List<AppMessage> {
            val wantedApp = app?.let(::normalize)?.takeIf { it.isNotEmpty() }
            val wantedContact = contact?.let(::normalize)?.takeIf { it.isNotEmpty() }
            return synchronized(recent) { recent.toList() }.asReversed().filter { m ->
                (wantedApp == null || normalize(m.app).contains(wantedApp)) &&
                    (wantedContact == null || normalize(m.conversation).contains(wantedContact) ||
                        normalize(m.sender).contains(wantedContact))
            }.take(limit)
        }

        /**
         * Répond dans la conversation via le bouton « Répondre » de sa notification.
         * Renvoie le nom de la conversation, ou null si aucune notification avec réponse n'est disponible.
         */
        fun reply(context: Context, app: String?, contact: String, text: String): String? {
            val wanted = normalize(contact)
            val wantedApp = app?.let(::normalize)
            val entry = replies.entries.lastOrNull { (k, _) ->
                val (a, c) = k.split("|", limit = 2)
                c.contains(wanted) && (wantedApp.isNullOrEmpty() || normalize(a).contains(wantedApp))
            } ?: return null
            val (name, action) = entry.value
            val inputs = action.remoteInputs ?: return null
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val intent = Intent()
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            return name
        }

        private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
