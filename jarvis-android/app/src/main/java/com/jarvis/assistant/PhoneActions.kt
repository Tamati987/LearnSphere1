package com.jarvis.assistant

import android.Manifest
import android.app.SearchManager
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

    override fun execute(name: String, input: Map<String, Any?>): String = when (name) {
        "appeler" -> call(input.str("destinataire"))
        "envoyer_sms" -> sms(input.str("destinataire"), input.str("message"))
        "regler_alarme" -> alarm(input.int("heure"), input.int("minute"), input.optStr("libelle"))
        "minuteur" -> timer(input.int("secondes"), input.optStr("libelle"))
        "ouvrir_application" -> openApp(input.str("nom"))
        "lampe_torche" -> torch(input["allumer"] == true)
        "regler_volume" -> volume(input.int("pourcentage"))
        "jouer_musique" -> music(input.str("recherche"))
        "itineraire" -> navigate(input.str("destination"))
        "reveil_jarvis" -> wakeUp(input.int("heure"), input.int("minute"), input.optStr("message"))
        "annuler_reveil" -> if (JarvisAlarm.cancel(context)) "Réveil annulé." else "Aucun réveil de Jarvis n'était programmé."
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

    private fun wakeUp(hour: Int, minute: Int, message: String?): String {
        val at = try {
            JarvisAlarm.schedule(context, hour.coerceIn(0, 23), minute.coerceIn(0, 59), message)
        } catch (e: IllegalStateException) {
            // Android 12 : l'autorisation « Alarmes et rappels » se donne dans les réglages.
            launch(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            return "Échec : ${e.message}. J'ai ouvert le réglage « Alarmes et rappels » pour que l'utilisateur l'active."
        }
        val day = if (at.get(java.util.Calendar.DAY_OF_YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) "aujourd'hui" else "demain"
        return "Réveil de Jarvis programmé $day à %02d:%02d.".format(hour, minute)
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
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
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
