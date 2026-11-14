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

    suspend fun trending(key: String, region: String): List<Video> = withContext(Dispatchers.IO) {
        val k = key.ifBlank { BuildConfig.YOUTUBE_API_KEY }
        if (k.isBlank()) return@withContext emptyList()
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
        val uri = Uri.parse("https://www.youtube.com/watch?v=${v.id}")
        val tv = Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_PACKAGE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val any = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(tv) }.onFailure { runCatching { context.startActivity(any) } }
    }
}
