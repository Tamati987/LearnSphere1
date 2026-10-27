package com.jarvis.assistant

import android.Manifest
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Exécute sur le téléphone les outils que Claude demande. */
class PhoneActions(private val context: Context) : ToolExecutor {

    // Une appli a dû attendre le déverrouillage pendant l'action en cours.
    private var waitingForUnlock = false

    override fun execute(name: String, input: Map<String, Any?>): String {
        waitingForUnlock = false
        val result = run(name, input)
        return if (waitingForUnlock && !result.startsWith("Échec")) {
            "$result Le téléphone est verrouillé : le déverrouillage est affiché, l'action se fera dès que l'utilisateur aura déverrouillé."
        } else {
            result
        }
    }

    private fun run(name: String, input: Map<String, Any?>): String = when (name) {
        "deverrouiller" -> unlock()
        "appeler" -> call(input.str("destinataire"))
        "envoyer_sms" -> sms(input.str("destinataire"), input.str("message"))
        "regler_alarme" -> alarm(input.int("heure"), input.int("minute"), input.optStr("libelle"))
        "minuteur" -> timer(input.int("secondes"), input.optStr("libelle"))
        "ouvrir_application" -> openApp(input.str("nom"))
        "lampe_torche" -> torch(input["allumer"] == true)
        "regler_volume" -> volume(input.int("pourcentage"))
        "jouer_musique" -> music(input.str("recherche"))
        "musique_newpipe" -> playMusic(input.str("recherche"), input["playlist"] != false)
        "newpipe_lire" -> newPipePlay(input.str("lien"))
        "newpipe_rechercher" -> newPipeSearch(input.str("recherche"))
        "controle_musique" -> mediaControl(input.str("action"))
        "itineraire" -> navigate(input.str("destination"))
        "reveil_jarvis" -> wakeUp(input.int("heure"), input.int("minute"), input["jours"], input.optStr("phrase"), input["agenda"] == true)
        "lister_reveils" -> JarvisAlarm.all(context).takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { JarvisAlarm.describe(it) } ?: "Aucun réveil de Jarvis n'est programmé."
        "annuler_reveil" -> cancelWakeUp(input["tous"] == true, input["heure"]?.let { input.int("heure") }, input.int("minute"))
        "lire_agenda" -> readCalendar(input.optStr("date"), input.int("jours"))
        "ajouter_evenement" -> addEvent(
            input.str("titre"), input.str("date"), input.optStr("heure"),
            input.int("duree_minutes"), input.optStr("lieu"),
        )
        "lire_messages_applis" -> readAppMessages(input.optStr("appli"), input.optStr("contact"), input.int("nombre"))
        "repondre_message_appli" -> replyAppMessage(input.optStr("appli"), input.str("contact"), input.str("message"))
        "meteo" -> runCatching { Weather.report(context, input.optStr("ville"), input.int("jours").takeIf { it > 0 } ?: 1) }
            .getOrElse { "Échec : ${it.message}" }
        "lire_notifications" -> MessageListener.currentNotifications(context, input.optStr("appli"))?.let { list ->
            if (list.isEmpty()) "Aucune notification affichée${input.optStr("appli")?.let { " pour « $it »" }.orEmpty()}."
            else list.joinToString("\n")
        } ?: "Échec : l'accès aux notifications n'est pas accordé à Jarvis."
        "lire_sms" -> readSms(input.optStr("contact"), input.int("nombre"), input["non_lus"] == true)
        "etat_telephone" -> status()
        else -> "Échec : outil inconnu « $name »."
    }

    private fun call(who: String): String {
        if (!granted(Manifest.permission.CALL_PHONE)) return "Échec : permission d'appel non accordée dans l'appli."
        val (label, number) = resolveNumber(who) ?: return "Échec : aucun contact ou numéro trouvé pour « $who »."
        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
        return "Appel lancé vers $label ($number)."
    }

    private fun sms(who: String, text: String): String {
        if (!granted(Manifest.permission.SEND_SMS)) return "Échec : permission SMS non accordée dans l'appli."
        val (label, number) = resolveNumber(who) ?: return "Échec : aucun contact ou numéro trouvé pour « $who »."
        val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        manager.sendMultipartTextMessage(number, null, manager.divideMessage(text), null, null)
        return "SMS envoyé à $label ($number)."
    }

    private fun alarm(hour: Int, minute: Int, label: String?): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        launch(intent)
        return "Alarme réglée à %02d:%02d.".format(hour, minute)
    }

    private fun wakeUp(hour: Int, minute: Int, days: Any?, phrase: String?, agenda: Boolean): String {
        val daySet = (days as? List<*>).orEmpty()
            .mapNotNull { JarvisAlarm.DAY_NAMES[it.toString().lowercase().trim()] }
            .toSet()
        val alarm = try {
            JarvisAlarm.add(context, hour.coerceIn(0, 23), minute.coerceIn(0, 59), daySet, phrase, agenda)
        } catch (e: IllegalStateException) {
            // Android 12 : l'autorisation « Alarmes et rappels » se donne dans les réglages.
            launch(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            return "Échec : ${e.message}. J'ai ouvert le réglage « Alarmes et rappels » pour que l'utilisateur l'active."
        }
        return "Réveil de Jarvis programmé : ${JarvisAlarm.describe(alarm)}."
    }

    /**
     * Programme du reste de la journée, en phrases à dire au réveil, sans passer par Claude.
     * Null si l'agenda n'est pas accessible.
     */
    fun todaySummary(): String? {
        if (!granted(Manifest.permission.READ_CALENDAR)) return null
        val now = System.currentTimeMillis()
        val endOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, endOfDay)
        }.build()
        val items = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            val cal = java.util.Calendar.getInstance()
            while (c.moveToNext() && items.size < 6) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                if (c.getInt(2) == 1) { items += title; continue }
                cal.timeInMillis = c.getLong(1)
                val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val m = cal.get(java.util.Calendar.MINUTE)
                items += if (m == 0) "$title à $h heures" else "$title à $h heures $m"
            }
        }
        return when (items.size) {
            0 -> "Votre agenda est libre aujourd'hui."
            1 -> "Au programme aujourd'hui : ${items[0]}."
            else -> "Au programme aujourd'hui : ${items.dropLast(1).joinToString(", ")} et ${items.last()}."
        }
    }

    private fun readCalendar(date: String?, days: Int): String {
        if (!granted(Manifest.permission.READ_CALENDAR)) return "Échec : permission d'accès à l'agenda non accordée dans l'appli."
        val start = parseDate(date) ?: return "Échec : date « $date » illisible, format attendu AAAA-MM-JJ."
        val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, if (days in 1..14) days else 1) }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start.timeInMillis)
            ContentUris.appendId(it, end.timeInMillis)
        }.build()
        val dayFormat = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
        val hourFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
        // Les événements « toute la journée » sont stockés en heure UTC.
        val utcDay = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val lines = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && lines.size < 30) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(sans titre)"
                val begin = c.getLong(1)
                val finish = c.getLong(2)
                val where = c.getString(4)?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                lines += if (c.getInt(3) == 1) {
                    "${utcDay.format(Date(begin))}, toute la journée : $title$where"
                } else {
                    "${dayFormat.format(Date(begin))}, ${hourFormat.format(Date(begin))} à ${hourFormat.format(Date(finish))} : $title$where"
                }
            }
        }
        return if (lines.isEmpty()) "Aucun événement dans l'agenda sur cette période." else lines.joinToString("\n")
    }

    private fun addEvent(title: String, date: String, time: String?, duration: Int, location: String?): String {
        if (!granted(Manifest.permission.WRITE_CALENDAR)) return "Échec : permission de modifier l'agenda non accordée dans l'appli."
        val day = parseDate(date) ?: return "Échec : date « $date » illisible, format attendu AAAA-MM-JJ."
        val calendarId = writableCalendar() ?: return "Échec : aucun agenda modifiable trouvé sur le téléphone."
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        val parts = time?.split(":", "h")?.mapNotNull { it.trim().toIntOrNull() }
        if (parts.isNullOrEmpty()) {
            // Sans heure : événement sur toute la journée, en UTC comme le veut Android.
            val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(day.get(java.util.Calendar.YEAR), day.get(java.util.Calendar.MONTH), day.get(java.util.Calendar.DAY_OF_MONTH))
            }
            values.put(CalendarContract.Events.ALL_DAY, 1)
            values.put(CalendarContract.Events.DTSTART, utc.timeInMillis)
            values.put(CalendarContract.Events.DTEND, utc.timeInMillis + 24 * 3600_000L)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            day.set(java.util.Calendar.HOUR_OF_DAY, parts[0].coerceIn(0, 23))
            day.set(java.util.Calendar.MINUTE, parts.getOrElse(1) { 0 }.coerceIn(0, 59))
            val minutes = if (duration > 0) duration else 60
            values.put(CalendarContract.Events.DTSTART, day.timeInMillis)
            values.put(CalendarContract.Events.DTEND, day.timeInMillis + minutes * 60_000L)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return "Échec : l'agenda a refusé l'événement."
        // Rappel 30 minutes avant, comme un assistant attentionné.
        runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, android.content.ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(eventUri))
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                put(CalendarContract.Reminders.MINUTES, 30)
            })
        }
        val dayText = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(day.time)
        val at = if (parts.isNullOrEmpty()) "toute la journée" else "à %02d:%02d".format(day.get(java.util.Calendar.HOUR_OF_DAY), day.get(java.util.Calendar.MINUTE))
        return "Événement « $title » ajouté le $dayText $at, avec un rappel 30 minutes avant."
    }

    /** Agenda où écrire : le principal s'il existe, sinon le premier agenda visible modifiable. */
    private fun writableCalendar(): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null, null,
        )?.use { c ->
            var first: Long? = null
            while (c.moveToNext()) {
                if (first == null) first = c.getLong(0)
                if (c.getInt(1) == 1) return c.getLong(0)
            }
            return first
        }
        return null
    }

    /** « AAAA-MM-JJ » (ou vide pour aujourd'hui) vers minuit de ce jour, heure locale. */
    private fun parseDate(text: String?): java.util.Calendar? {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        if (text.isNullOrBlank()) return cal
        val parts = text.trim().split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        cal.set(parts[0], parts[1] - 1, parts[2])
        return cal
    }

    private fun cancelWakeUp(all: Boolean, hour: Int?, minute: Int): String {
        if (!all && hour == null) return "Échec : précise l'heure du réveil à annuler, ou tous."
        val n = JarvisAlarm.remove(context, if (all) null else hour, if (all) null else minute)
        return if (n > 0) "$n réveil(s) annulé(s)." else "Aucun réveil ne correspond."
    }

    private fun timer(seconds: Int, label: String?): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        launch(intent)
        return "Minuteur de $seconds secondes lancé."
    }

    private fun openApp(name: String): String {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val wanted = normalize(name)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val match = apps.firstOrNull { normalize(it.first) == wanted }
            ?: apps.firstOrNull { normalize(it.first).contains(wanted) || wanted.contains(normalize(it.first)) }
            ?: return "Échec : aucune application nommée « $name »."
        val intent = pm.getLaunchIntentForPackage(match.second) ?: return "Échec : ${match.first} ne peut pas être ouverte."
        launch(intent)
        return "${match.first} ouverte."
    }

    private fun torch(on: Boolean): String {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Échec : ce téléphone n'a pas de flash."
        cm.setTorchMode(id, on)
        return if (on) "Lampe torche allumée." else "Lampe torche éteinte."
    }

    private fun volume(percent: Int): String {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = (percent.coerceIn(0, 100) / 100f * max).roundToInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
        return "Volume multimédia réglé à $percent %."
    }

    private fun music(query: String): String {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        return if (launch(intent)) "Lecture de « $query » lancée."
        else "Échec : aucune application musicale ne gère la recherche vocale."
    }

    private fun newPipePlay(link: String): String {
        val uri = Uri.parse(link.trim())
        val host = uri.host.orEmpty().removePrefix("www.").removePrefix("m.")
        if (uri.scheme !in setOf("http", "https") || host !in NEWPIPE_HOSTS) {
            return "Échec : NewPipe n'ouvre que des liens YouTube, YouTube Music ou SoundCloud, pas « $link »."
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(NEWPIPE)
        return if (launch(intent)) "Lien ouvert dans NewPipe : $link"
        else "Échec : NewPipe n'est pas installé sur ce téléphone."
    }

    /**
     * Cherche sur YouTube et lance aussitôt le premier résultat dans NewPipe.
     * [playlist] : une playlist (la musique continue), sinon un seul morceau.
     */
    private fun playMusic(query: String, playlist: Boolean): String {
        val found = runCatching {
            (if (playlist) youtubeSearch(query, playlist = true) else null) ?: youtubeSearch(query, playlist = false)
        }.getOrNull()
        if (found == null) {
            // Recherche impossible (réseau, page YouTube changée) : on ouvre au moins la recherche dans NewPipe.
            val fallback = newPipeSearch(query)
            return if (fallback.startsWith("Échec")) fallback
            else "Je n'ai pas pu lancer directement la musique ; la recherche « $query » est ouverte dans NewPipe."
        }
        val (link, title) = found
        val opened = newPipePlay(link)
        if (opened.startsWith("Échec")) return opened
        return "Lecture lancée dans NewPipe : ${title ?: query}."
    }

    /** Premier résultat de la recherche YouTube : (lien, titre). */
    private fun youtubeSearch(query: String, playlist: Boolean): Pair<String, String?>? {
        // Filtres de la page de recherche YouTube : vidéos seules ou playlists seules.
        val filter = if (playlist) "EgIQAw%3D%3D" else "EgIQAQ%3D%3D"
        val url = "https://www.youtube.com/results?search_query=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&sp=" + filter
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        val html = try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            )
            connection.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
            // Évite la page de consentement aux cookies en Europe.
            connection.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val match = if (playlist) {
            PLAYLIST_ID.find(html) ?: LOCKUP_PLAYLIST_ID.find(html)
        } else {
            VIDEO_ID.find(html)
        } ?: return null
        val id = match.groupValues[1]
        val title = TITLE.find(html, match.range.last)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")?.replace("\\\"", "\"")
        val link = if (playlist) "https://www.youtube.com/playlist?list=$id" else "https://www.youtube.com/watch?v=$id"
        return link to title
    }

    private fun newPipeSearch(query: String): String {
        // NewPipe lance une recherche quand on lui partage un texte qui n'est pas un lien.
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, query)
            .setPackage(NEWPIPE)
        return if (launch(intent)) "Recherche « $query » ouverte dans NewPipe."
        else "Échec : NewPipe n'est pas installé sur ce téléphone."
    }

    /** Pause, lecture, suivant, précédent : agit sur le lecteur en cours (NewPipe ou autre). */
    private fun mediaControl(action: String): String {
        val code = when (normalize(action)) {
            "pause", "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "lecture", "play", "reprendre" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "suivant", "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "precedent", "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Échec : action « $action » inconnue (pause, lecture, suivant ou précédent)."
        }
        val am = context.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        return "Commande « $action » envoyée au lecteur."
    }

    private fun navigate(destination: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
        if (!launch(intent)) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))))
        }
        return "Navigation vers $destination lancée."
    }

    private fun status(): String {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val network = when {
            caps == null -> "aucune connexion"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "données mobiles"
            else -> "connecté"
        }
        return "Batterie à $level %${if (charging) ", en charge" else ""}. Réseau : $network."
    }

    private fun readAppMessages(app: String?, contact: String?, count: Int): String {
        if (!MessageListener.isEnabled(context)) {
            return "Échec : l'accès aux notifications n'est pas accordé ; l'utilisateur doit toucher « Autoriser la lecture de WhatsApp et Messenger » dans l'appli Jarvis."
        }
        val format = SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE)
        val messages = MessageListener.messages(app, contact, if (count in 1..30) count else 10)
        if (messages.isEmpty()) {
            return "Aucun message reçu correspondant depuis que Jarvis a l'accès aux notifications (l'historique plus ancien n'est pas accessible)."
        }
        return messages.joinToString("\n") { m ->
            val where = if (m.conversation != m.sender) " dans « ${m.conversation} »" else ""
            "${m.app}, de ${m.sender}$where, ${format.format(Date(m.time))} : ${m.text.take(600)}"
        }
    }

    private fun replyAppMessage(app: String?, contact: String, text: String): String {
        val name = runCatching { MessageListener.reply(context, app, contact, text) }.getOrNull()
            ?: return "Échec : pas de notification récente de « $contact » avec un bouton Répondre ; il faut ouvrir l'appli pour répondre."
        return "Réponse envoyée à $name."
    }

    private fun readSms(contact: String?, count: Int, unreadOnly: Boolean): String {
        if (!granted(Manifest.permission.READ_SMS)) return "Échec : permission de lecture des SMS non accordée dans l'appli."
        val wanted = contact?.let(::normalize)?.takeIf { it.isNotEmpty() }
        val wantedDigits = contact.orEmpty().filter { it.isDigit() }.takeIf { it.length >= 4 }
        val limit = if (count in 1..20) count else 5
        val format = SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE)
        val found = mutableListOf<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            if (unreadOnly) "${Telephony.Sms.READ} = 0" else null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER,
        )?.use { c ->
            var scanned = 0
            while (c.moveToNext() && found.size < limit && scanned++ < 500) {
                val address = c.getString(0) ?: continue
                val name = contactName(address)
                if (wanted != null) {
                    val n = name?.let(::normalize).orEmpty()
                    val byName = n.isNotEmpty() && (n.contains(wanted) || wanted.contains(n))
                    val byNumber = wantedDigits != null && address.filter { it.isDigit() }.endsWith(wantedDigits)
                    if (!byName && !byNumber) continue
                }
                val body = c.getString(1).orEmpty().take(600)
                found += "De ${name ?: address}, ${format.format(Date(c.getLong(2)))} : $body"
            }
        }
        if (found.isEmpty()) return if (contact != null) "Aucun SMS trouvé de « $contact »." else "Aucun SMS trouvé."
        return found.joinToString("\n")
    }

    /** Nom du contact pour un numéro, ou null s'il n'est pas dans le répertoire. */
    fun contactName(number: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        names[number]?.let { return it.ifEmpty { null } }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        names[number] = name.orEmpty()
        return name
    }

    private val names = mutableMapOf<String, String>()

    companion object {
        private const val NEWPIPE = "org.schabi.newpipe"
        private val NEWPIPE_HOSTS = setOf("youtube.com", "music.youtube.com", "youtu.be", "soundcloud.com", "on.soundcloud.com")
        private val VIDEO_ID = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")
        private val PLAYLIST_ID = Regex("\"playlistRenderer\":\\{\"playlistId\":\"([A-Za-z0-9_-]+)\"")
        private val LOCKUP_PLAYLIST_ID = Regex("\"contentId\":\"((?:PL|RD|OLAK5uy_)[A-Za-z0-9_-]+)\"")
        private val TITLE = Regex("\"(?:text|content)\":\"([^\"]{2,120})\"")
    }

    /** Cherche un numéro : tel quel s'il s'agit déjà d'un numéro, sinon dans les contacts. */
    private fun resolveNumber(who: String): Pair<String, String>? {
        val digits = who.filter { it.isDigit() || it == '+' }
        if (digits.length >= 4 && digits.length >= who.count { !it.isWhitespace() } - 2) return who to digits
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val wanted = normalize(who)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null
        )?.use { c ->
            var partial: Pair<String, String>? = null
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val n = normalize(name)
                if (n == wanted) return name to number
                if (partial == null && (n.contains(wanted) || wanted.contains(n))) partial = name to number
            }
            return partial
        }
        return null
    }

    /** Lance une activité depuis le service. Renvoie false si aucune appli ne la gère. */
    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Téléphone verrouillé : on affiche d'abord le déverrouillage (empreinte, visage ou code),
        // l'appli s'ouvrira ensuite. Un appel, lui, peut partir écran verrouillé.
        if (intent.action != Intent.ACTION_CALL && UnlockActivity.isLocked(context)) {
            UnlockActivity.request(context, intent)
            waitingForUnlock = true
            true
        } else {
            context.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        false
    }

    private fun unlock(): String {
        if (!UnlockActivity.isLocked(context)) return "Le téléphone est déjà déverrouillé."
        UnlockActivity.request(context)
        return "Écran de déverrouillage affiché : l'utilisateur doit poser son doigt, montrer son visage ou taper son code."
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun Map<String, Any?>.str(key: String) = this[key]?.toString().orEmpty()
    private fun Map<String, Any?>.optStr(key: String) = this[key]?.toString()?.takeIf { it.isNotBlank() }
    private fun Map<String, Any?>.int(key: String) = when (val v = this[key]) {
        is Number -> v.toInt()
        else -> v?.toString()?.toDoubleOrNull()?.toInt() ?: 0
    }
}
