package com.tvlauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** الطقس والبحث عن المدن عبر Open-Meteo (مجاني وبدون مفتاح). */
object WeatherRepository {

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    suspend fun current(city: City): WeatherInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(get(
                "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )).getJSONObject("current")
            WeatherInfo(Math.round(j.getDouble("temperature_2m")).toInt(), describe(j.getInt("weather_code")))
        }.getOrNull()
    }

    suspend fun searchCities(query: String): List<City> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val arr = JSONObject(get(
                "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=ar&format=json"
            )).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                City(
                    o.getString("name"),
                    o.optString("country"),
                    o.getDouble("latitude"),
                    o.getDouble("longitude"),
                    o.optString("timezone", "UTC")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun describe(code: Int) = when (code) {
        0 -> "صافٍ"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        in 51..57 -> "رذاذ"
        in 61..67 -> "مطر"
        in 71..77 -> "ثلج"
        in 80..82 -> "زخات مطر"
        in 95..99 -> "عاصفة رعدية"
        else -> "—"
    }
}
