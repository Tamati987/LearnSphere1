package com.jarvis.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Météo via Open-Meteo (gratuit, sans clé) : conditions actuelles et prévisions.
 * La position vient du téléphone, mémorisée à chaque ouverture de Jarvis, ou d'une ville donnée.
 */
object Weather {
    private const val PREFS = "jarvis_weather"

    /** Mémorise la dernière position connue ; à appeler quand Jarvis est à l'écran (accès à la position autorisé). */
    @SuppressLint("MissingPermission")
    fun rememberLocation(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = context.getSystemService(LocationManager::class.java)
        val best = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("lat", best.latitude.toFloat())
            .putFloat("lon", best.longitude.toFloat())
            .apply()
    }

    /** Météo pour [city], ou pour la position mémorisée. Appel réseau : hors du fil principal. */
    fun report(context: Context, city: String?, days: Int): String {
        val (lat, lon, place) = if (!city.isNullOrBlank()) {
            geocode(city) ?: return "Échec : ville « $city » introuvable."
        } else {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains("lat")) {
                return "Échec : position inconnue. L'utilisateur doit autoriser la position et ouvrir Jarvis une fois, ou donner une ville."
            }
            Triple(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(), "votre position")
        }
        val forecastDays = days.coerceIn(1, 7)
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,precipitation" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&timezone=auto&forecast_days=$forecastDays"
        )
        val current = json.getJSONObject("current")
        val lines = mutableListOf(
            "Maintenant à $place : ${describe(current.getInt("weather_code"))}, " +
                "${current.getDouble("temperature_2m").roundToInt()} °C " +
                "(ressenti ${current.getDouble("apparent_temperature").roundToInt()} °C), " +
                "humidité ${current.getInt("relative_humidity_2m")} %, " +
                "vent ${current.getDouble("wind_speed_10m").roundToInt()} km/h, " +
                "pluie ${current.optDouble("precipitation", 0.0)} mm."
        )
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val output = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
        for (i in 0 until dates.length()) {
            val day = runCatching { output.format(input.parse(dates.getString(i))!!) }.getOrDefault(dates.getString(i))
            lines += "$day : ${describe(daily.getJSONArray("weather_code").getInt(i))}, " +
                "de ${daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()} " +
                "à ${daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()} °C, " +
                "risque de pluie ${daily.getJSONArray("precipitation_probability_max").optInt(i, 0)} %."
        }
        return lines.joinToString("\n")
    }

    private fun geocode(city: String): Triple<Double, Double, String>? {
        val json = get(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" + URLEncoder.encode(city, "UTF-8")
        )
        val first = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val name = listOfNotNull(first.optString("name").takeIf { it.isNotBlank() }, first.optString("country").takeIf { it.isNotBlank() })
            .joinToString(", ")
        return Triple(first.getDouble("latitude"), first.getDouble("longitude"), name)
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) throw IllegalStateException("service météo indisponible (${connection.responseCode})")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Codes météo WMO utilisés par Open-Meteo, en français. */
    private fun describe(code: Int) = when (code) {
        0 -> "ciel dégagé"
        1 -> "plutôt dégagé"
        2 -> "partiellement nuageux"
        3 -> "couvert"
        45, 48 -> "brouillard"
        51, 53, 55 -> "bruine"
        56, 57 -> "bruine verglaçante"
        61 -> "pluie faible"
        63 -> "pluie"
        65 -> "forte pluie"
        66, 67 -> "pluie verglaçante"
        71, 73, 75, 77 -> "neige"
        80, 81 -> "averses"
        82 -> "fortes averses"
        85, 86 -> "averses de neige"
        95 -> "orage"
        96, 99 -> "orage avec grêle"
        else -> "temps variable"
    }
}
