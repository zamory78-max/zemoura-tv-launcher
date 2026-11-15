package com.tvlauncher.data

import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject

/** تطبيق مثبت. supported=false يعني أنه لا يملك واجهة Leanback (غير مخصص للتلفاز). */
data class AppEntry(
    val pkg: String,
    val label: String,
    val supported: Boolean,
    val icon: Drawable?,
    val banner: Drawable?
)

data class City(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val tz: String
) {
    fun toJson() = JSONObject().put("name", name).put("country", country)
        .put("lat", lat).put("lon", lon).put("tz", tz).toString()

    companion object {
        val DEFAULT = City("الجزائر", "الجزائر", 36.7538, 3.0588, "Africa/Algiers")
        fun fromJson(s: String?): City = try {
            val o = JSONObject(s ?: "")
            City(o.getString("name"), o.optString("country"), o.getDouble("lat"), o.getDouble("lon"), o.getString("tz"))
        } catch (e: Exception) { DEFAULT }
    }
}

/** عنصر داخل مجلد: تطبيق (ref = اسم الحزمة) أو ملف (ref = Uri من مستعرض الملفات). */
data class FolderItem(val isApp: Boolean, val ref: String, val label: String)

data class Folder(
    val id: String,
    val name: String,
    val locked: Boolean,
    val items: List<FolderItem>
)

fun foldersToJson(list: List<Folder>): String = JSONArray().also { arr ->
    list.forEach { f ->
        arr.put(JSONObject().put("id", f.id).put("name", f.name).put("locked", f.locked)
            .put("items", JSONArray().also { ia ->
                f.items.forEach { ia.put(JSONObject().put("app", it.isApp).put("ref", it.ref).put("label", it.label)) }
            }))
    }
}.toString()

fun foldersFromJson(s: String?): List<Folder> = try {
    val arr = JSONArray(s ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val ia = o.getJSONArray("items")
        Folder(o.getString("id"), o.getString("name"), o.getBoolean("locked"),
            (0 until ia.length()).map { j ->
                val it = ia.getJSONObject(j)
                FolderItem(it.getBoolean("app"), it.getString("ref"), it.getString("label"))
            })
    }
} catch (e: Exception) { emptyList() }

/** صف مخصص باسم يختاره المستخدم (مثل «ألعاب») ويحوي التطبيقات التي يحددها. */
data class CustomRow(val id: String, val name: String, val pkgs: List<String>)

fun rowsToJson(list: List<CustomRow>): String = JSONArray().also { arr ->
    list.forEach { r ->
        arr.put(JSONObject().put("id", r.id).put("name", r.name).put("pkgs", JSONArray(r.pkgs)))
    }
}.toString()

fun rowsFromJson(s: String?): List<CustomRow> = try {
    val arr = JSONArray(s ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val p = o.getJSONArray("pkgs")
        CustomRow(o.getString("id"), o.getString("name"), (0 until p.length()).map { p.getString(it) })
    }
} catch (e: Exception) { emptyList() }

data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val thumb: String,
    val duration: String,
    val link: String = ""      // رابط مخصص (للمصدر الخاص) يُفتح عند الضغط
)

data class WeatherInfo(
    val tempC: Int,
    val description: String,
    val code: Int = 0,
    val isDay: Boolean = true,
    val humidity: Int = 0,
    val windKmh: Int = 0,
    val maxC: Int = 0,
    val minC: Int = 0
)
