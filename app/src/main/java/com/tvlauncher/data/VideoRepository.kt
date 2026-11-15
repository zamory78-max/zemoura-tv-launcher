package com.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * صف الفيديوهات بأسلوب يوتيوب. يستخدم YouTube Data API v3 (الأكثر مشاهدة حسب البلد).
 * يتطلب مفتاح API: من gradle.properties (YOUTUBE_API_KEY) أو من شاشة الإعدادات.
 */
object VideoRepository {

    const val YOUTUBE_PACKAGE_TV = "com.google.android.youtube.tv"

    /** المصدر المخصص أولاً (إن ضُبط)، ثم يوتيوب. */
    suspend fun all(key: String, region: String, piped: String, feed: String): List<Video> =
        customFeed(feed) + trending(key, region, piped)

    /**
     * مصدر مخصص: رابط JSON يعيد قائمة (أو {"items":[...]}) عناصرها:
     * {"title":"..","channel":"..","thumb":"https://..","link":"https://.. أو zemoura://..","duration":"12:30"}
     */
    private suspend fun customFeed(url: String): List<Video> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext emptyList()
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val body = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val arr = if (body.trimStart().startsWith("[")) org.json.JSONArray(body)
                      else JSONObject(body).getJSONArray("items")
            (0 until minOf(arr.length(), 20)).mapNotNull {
                val o = arr.getJSONObject(it)
                val link = o.optString("link")
                if (link.isEmpty()) null else Video(
                    id = link, title = o.optString("title"), channel = o.optString("channel"),
                    thumb = o.optString("thumb"), duration = o.optString("duration"), link = link
                )
            }
        }.getOrDefault(emptyList())
    }

    /** الرسمي (بمفتاح) إن وُجد، وإلا مصدر Piped المفتوح بلا مفتاح (غير رسمي وقد يتعطل). */
    suspend fun trending(key: String, region: String, piped: String): List<Video> {
        val k = key.ifBlank { BuildConfig.YOUTUBE_API_KEY }
        val official = if (k.isNotBlank()) official(k, region) else emptyList()
        return official.ifEmpty { pipedTrending(piped, region) }
    }

    private suspend fun pipedTrending(base: String, region: String): List<Video> = withContext(Dispatchers.IO) {
        if (!base.startsWith("https://")) return@withContext emptyList()
        runCatching {
            val c = URL(base.trimEnd('/') + "/trending?region=$region").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val body = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val arr = org.json.JSONArray(body)
            (0 until minOf(arr.length(), 12)).mapNotNull {
                val o = arr.getJSONObject(it)
                val id = o.optString("url").substringAfter("v=", "")
                if (id.isEmpty()) null else Video(
                    id = id,
                    title = o.optString("title"),
                    channel = o.optString("uploaderName"),
                    thumb = o.optString("thumbnail"),
                    duration = fmtSeconds(o.optLong("duration", 0))
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun fmtSeconds(s: Long): String {
        if (s <= 0) return ""
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private suspend fun official(k: String, region: String): List<Video> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails" +
                "&chart=mostPopular&maxResults=10&regionCode=$region&key=$k"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val body = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val items = JSONObject(body).getJSONArray("items")
            (0 until items.length()).map {
                val o = items.getJSONObject(it)
                val sn = o.getJSONObject("snippet")
                val th = sn.getJSONObject("thumbnails")
                val thumb = (th.optJSONObject("medium") ?: th.optJSONObject("default"))?.optString("url").orEmpty()
                Video(
                    id = o.getString("id"),
                    title = sn.getString("title"),
                    channel = sn.getString("channelTitle"),
                    thumb = thumb,
                    duration = fmt(o.getJSONObject("contentDetails").optString("duration"))
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun fmt(iso: String): String = runCatching {
        val s = Duration.parse(iso).seconds
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }.getOrDefault("")

    fun play(context: Context, v: Video) {
        if (v.link.isNotEmpty()) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val uri = Uri.parse("https://www.youtube.com/watch?v=${v.id}")
        val tv = Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_PACKAGE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val any = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(tv) }.onFailure { runCatching { context.startActivity(any) } }
    }
}
