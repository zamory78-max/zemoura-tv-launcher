package com.tvlauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

const val DEFAULT_NEWS_URL =
    "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdb1fca3b4d"

data class NewsSource(val id: String, val name: String, val url: String, val domain: String, val color: Long, val note: String = "")
data class NewsItem(val sourceId: String, val title: String)

object NewsSources {
    val all = listOf(
        NewsSource("aljazeera", "الجزيرة", DEFAULT_NEWS_URL, "aljazeera.net", 0xFFC8A04A),
        NewsSource("skynews", "سكاي نيوز عربية", "https://www.skynewsarabia.com/web/rss", "skynewsarabia.com", 0xFFD0021B),
        NewsSource("bbc", "بي بي سي عربي", "https://feeds.bbci.co.uk/arabic/rss.xml", "bbc.com", 0xFFBB1919),
        NewsSource("dw", "DW عربية", "https://rss.dw.com/xml/rss-ar-all", "dw.com", 0xFF05A0E1),
        NewsSource("france24", "فرانس 24", "https://www.france24.com/ar/rss", "france24.com", 0xFF00B4E6),
        NewsSource("echorouk", "الشروق (الجزائر)", "https://www.echoroukonline.com/feed", "echoroukonline.com", 0xFF1E8E3E),
        NewsSource("ennahar", "النهار (الجزائر)", "https://www.ennaharonline.com/feed/", "ennaharonline.com", 0xFFE85D04),
        NewsSource("alquds", "القدس العربي", "https://www.alquds.co.uk/feed/", "alquds.co.uk", 0xFF8E24AA),
        NewsSource("alukah", "الألوكة (إسلامي سني)", "https://www.alukah.net/RSS/Rss-Feeds.aspx", "alukah.net", 0xFF2E7D32,
            "موقع إسلامي سني؛ لم أتمكن من التحقق من رابطه، فإن لم تظهر أخباره فهو متوقف")
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** يجلب عناوين الأخبار من مصادر RSS متعددة ويمزجها بالتناوب. */
object NewsRepository {
    suspend fun headlines(sourceIds: List<String>, customUrl: String): List<NewsItem> = coroutineScope {
        val srcs = sourceIds.mapNotNull { NewsSources.byId(it) }.map { Triple(it.id, it.url, 8) } +
            (if (customUrl.isNotBlank() && customUrl != DEFAULT_NEWS_URL && NewsSources.all.none { it.url == customUrl }) listOf(Triple("custom", customUrl, 8)) else emptyList())
        val lists = srcs.map { (id, url, n) -> async { fetch(url).take(n).map { NewsItem(id, it) } } }.awaitAll()
        // تناوب بين المصادر
        val out = ArrayList<NewsItem>()
        var i = 0
        while (lists.any { i < it.size }) { lists.forEach { l -> if (i < l.size) out.add(l[i]) }; i++ }
        out
    }

    private suspend fun fetch(url: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "ZemouraLauncher/1.0 (Android TV)")
            c.inputStream.use { ins ->
                val p = XmlPullParserFactory.newInstance().newPullParser()
                p.setInput(ins, null)
                val out = ArrayList<String>()
                var inItem = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT && out.size < 15) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (p.name) {
                            "item", "entry" -> inItem = true
                            "title" -> if (inItem) p.nextText().trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                        }
                        XmlPullParser.END_TAG -> if (p.name == "item" || p.name == "entry") inItem = false
                    }
                    ev = p.next()
                }
                out
            }
        }.getOrDefault(emptyList())
    }
}
