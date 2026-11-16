package com.tvlauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

const val DEFAULT_NEWS_URL =
    "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdb1fca3b4d"

/** يجلب عناوين الأخبار من أي رابط RSS. */
object NewsRepository {
    suspend fun headlines(url: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
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
