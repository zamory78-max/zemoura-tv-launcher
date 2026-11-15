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
        c.setRequestProperty("User-Agent", "ZemouraLauncher/1.0 (Android TV)")
        c.setRequestProperty("Accept-Language", "ar")
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    suspend fun current(city: City): WeatherInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(get(
                "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                    "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto"
            ))
            val j = root.getJSONObject("current")
            val d = root.optJSONObject("daily")
            WeatherInfo(
                tempC = Math.round(j.getDouble("temperature_2m")).toInt(),
                description = describe(j.getInt("weather_code")),
                code = j.getInt("weather_code"),
                isDay = j.optInt("is_day", 1) == 1,
                humidity = j.optInt("relative_humidity_2m", 0),
                windKmh = Math.round(j.optDouble("wind_speed_10m", 0.0)).toInt(),
                maxC = d?.optJSONArray("temperature_2m_max")?.optDouble(0)?.let { Math.round(it).toInt() } ?: 0,
                minC = d?.optJSONArray("temperature_2m_min")?.optDouble(0)?.let { Math.round(it).toInt() } ?: 0,
                days = forecast(d)
            )
        }.getOrNull()
    }

    private fun forecast(d: JSONObject?): List<DayForecast> {
        if (d == null) return emptyList()
        val times = d.optJSONArray("time") ?: return emptyList()
        val codes = d.optJSONArray("weather_code")
        val mx = d.optJSONArray("temperature_2m_max")
        val mn = d.optJSONArray("temperature_2m_min")
        val parse = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale("ar"))
        return (0 until times.length()).map { i ->
            val date = runCatching { parse.parse(times.getString(i)) }.getOrNull()
            DayForecast(
                label = if (i == 0) "اليوم" else if (i == 1) "غداً" else date?.let { dayName.format(it) } ?: "",
                code = codes?.optInt(i, 0) ?: 0,
                maxC = mx?.optDouble(i)?.let { Math.round(it).toInt() } ?: 0,
                minC = mn?.optDouble(i)?.let { Math.round(it).toInt() } ?: 0
            )
        }
    }

    suspend fun searchCities(query: String, countryCode: String? = null): List<City> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val arr = JSONObject(get(
                "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=ar&format=json" + (countryCode?.let { "&countryCode=$it" } ?: "")
            )).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                City(
                    o.getString("name"),
                    listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString("، "),
                    o.getDouble("latitude"),
                    o.getDouble("longitude"),
                    o.optString("timezone", "UTC")
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * بحث موسّع عن البلديات والقرى عبر OpenStreetMap (Nominatim) — يغطي ما لا تغطيه Open-Meteo.
     * [area] اسم الولاية/المنطقة لتضييق النتائج.
     */
    suspend fun searchPlaces(query: String, countryCode: String, area: String? = null, tz: String = "UTC"): List<City> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val clean = query.trim().removePrefix("بلدية").trim()
            val queries = buildList {
                if (area != null) add("$clean، $area")
                add(clean)
            }
            val out = LinkedHashMap<String, City>()
            for (qs in queries) {
                runCatching {
                    val q = URLEncoder.encode(qs, "UTF-8")
                    val arr = org.json.JSONArray(get(
                        "https://nominatim.openstreetmap.org/search?q=$q&format=jsonv2&limit=8&addressdetails=1" +
                            "&accept-language=ar&countrycodes=${countryCode.lowercase()}"
                    ))
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val a = o.optJSONObject("address")
                        val name = o.optString("name").ifBlank { o.optString("display_name").substringBefore("،") }
                        val ctx = listOfNotNull(
                            a?.optString("county")?.takeIf { it.isNotBlank() },
                            a?.optString("state")?.takeIf { it.isNotBlank() },
                            a?.optString("country")?.takeIf { it.isNotBlank() }
                        ).distinct().joinToString("، ")
                        val c = City(name, ctx, o.getString("lat").toDouble(), o.getString("lon").toDouble(), tz)
                        out.putIfAbsent("%.2f,%.2f".format(c.lat, c.lon), c)
                    }
                }
                if (out.size >= 5) break
            }
            // نتائج Open-Meteo كاحتياط
            if (out.size < 3) searchCities(clean, countryCode).forEach { out.putIfAbsent("%.2f,%.2f".format(it.lat, it.lon), it) }
            out.values.toList()
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
