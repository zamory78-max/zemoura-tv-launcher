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

    const val ZEMOURA_TV_PACKAGE = "com.islamictv.app"
    const val YOUTUBE_PACKAGE_TV = "com.google.android.youtube.tv"

    /** المصدر المخصص أولاً (إن ضُبط)، ثم قنوات المستخدم، وإلا «الأكثر مشاهدة». */
    suspend fun all(key: String, region: String, piped: String, feed: String, channels: String): List<Video> {
        val tokens = channels.split(',', '،', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        val own = if (tokens.isNotEmpty()) channelVideos(tokens) else emptyList()
        // إن حُدّدت قنوات فنعرض فيديوهاتها بدل «الأكثر مشاهدة»
        return customFeed(feed) + own.ifEmpty { if (tokens.isEmpty()) trending(key, region, piped) else emptyList() }
    }

    private val idCache = HashMap<String, String>()
    /** آخر سبب لفشل جلب الفيديوهات (للعرض على الشاشة). */
    @Volatile var lastError: String = ""
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8")
        c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI")
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    /** يقبل: UCxxxx أو youtube.com/channel/UCxxxx أو youtube.com/@handle أو @handle أو youtube.com/c/name */
    private fun resolveChannelId(src: String): String? {
        Regex("""^(UC[\w-]{22})$""").find(src)?.let { return it.groupValues[1] }
        Regex("""/channel/(UC[\w-]{22})""").find(src)?.let { return it.groupValues[1] }
        idCache[src]?.let { return it }
        val base = when {
            src.startsWith("http") -> src.substringBefore("?").removeSuffix("/")
            src.startsWith("@") -> "https://www.youtube.com/$src"
            else -> "https://www.youtube.com/@$src"
        }
        val pages = listOf(base, base.replace("://www.", "://m."), "$base/about", "$base/videos")
        var id: String? = null
        for (page in pages) {
            val html = try { httpGet(page) } catch (e: Exception) { lastError = "$src: ${e.javaClass.simpleName} ${e.message.orEmpty().take(60)}"; continue }
            id = Regex("""<link rel="canonical" href="https://www\.youtube\.com/channel/(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
                ?: Regex(""""externalId":"(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
                ?: Regex(""""channelId":"(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
                ?: Regex("""channel_id=(UC[\w-]{22})""").find(html)?.groupValues?.get(1)
                ?: Regex("""/channel/(UC[\w-]{22})""").find(html)?.groupValues?.get(1)
            if (id != null) break
            lastError = "$src: لم يُعثر على معرّف القناة في الصفحة"
        }
        if (id != null) idCache[src] = id
        return id
    }

    /** آخر فيديوهات القنوات عبر خلاصة RSS الرسمية من يوتيوب (لا تحتاج مفتاحاً). */
    private suspend fun channelVideos(tokens: List<String>): List<Video> = withContext(Dispatchers.IO) {
        tokens.flatMap { tk ->
            runCatching {
                val id = resolveChannelId(tk) ?: return@runCatching emptyList()
                val p = android.util.Xml.newPullParser().apply {
                    setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(httpGet("https://www.youtube.com/feeds/videos.xml?channel_id=$id").reader())
                }
                val out = ArrayList<Pair<String, Video>>()
                var inEntry = false; var inAuthor = false
                var vid = ""; var title = ""; var pub = ""; var author = ""; var thumb = ""
                while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (p.eventType) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> when (p.name) {
                            "entry" -> { inEntry = true; vid = ""; title = ""; pub = ""; thumb = "" }
                            "author" -> inAuthor = true
                            "name" -> if (inAuthor) author = p.nextText()
                            "yt:videoId" -> if (inEntry) vid = p.nextText()
                            "title" -> if (inEntry) title = p.nextText()
                            "published" -> if (inEntry) pub = p.nextText()
                            "media:thumbnail" -> if (inEntry) thumb = p.getAttributeValue(null, "url").orEmpty()
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> when (p.name) {
                            "author" -> inAuthor = false
                            "entry" -> { inEntry = false
                                if (vid.isNotBlank() && out.size < 6)
                                    out.add(pub to Video(vid, title, author, thumb.ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }, ""))
                            }
                        }
                    }
                }
                out
            }.getOrElse { lastError = "${it.javaClass.simpleName} ${it.message.orEmpty().take(60)}"; emptyList() }
        }.sortedByDescending { it.first }.map { it.second }.take(40)
    }

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
        // الأولوية لتشغيل الفيديو داخل «زمورة تي في» (يستقبل المعرّف عبر DeepLinkActivity)، وإلا يوتيوب
        val zemoura = Intent().setComponent(android.content.ComponentName(ZEMOURA_TV_PACKAGE, "$ZEMOURA_TV_PACKAGE.youtube.DeepLinkActivity"))
            .setData(Uri.parse("zemoura://video/${v.id}"))
            .putExtra("title", v.title).putExtra("channel", v.channel).putExtra("thumb", v.thumb)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(zemoura) }.isSuccess) return
        val uri = Uri.parse("https://www.youtube.com/watch?v=${v.id}")
        val tv = Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_PACKAGE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val any = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(tv) }.onFailure { runCatching { context.startActivity(any) } }
    }
}
