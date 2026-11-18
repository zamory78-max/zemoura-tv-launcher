package com.tvlauncher.data

import android.view.KeyEvent

/** ربط أزرار الريموت الخاصة (يوتيوب، نتفليكس، …) بتطبيقات. يعمل أثناء ظهور اللاونشر فقط. */
object RemoteKeys {
    /** عندما يكون غير فارغ تلتقط الأنشطة أول ضغطة زر وتمرّرها هنا بدل تنفيذها. */
    @Volatile var capture: ((String, String) -> Unit)? = null

    private val reserved = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER
    )

    /** أزرار شائعة للاختيار اليدوي (للريموتات التي لا نلتقط أزرارها بالضغط). */
    /** أزرار شائعة للاختيار اليدوي؛ أزرار التطبيقات (يوتيوب/نتفليكس/برايم/المتجر) تختلف رموزها بين الريموتات. */
    val common: List<Pair<String, String>> = run {
        val named = listOf(
            KeyEvent.KEYCODE_PROG_RED to "الأحمر", KeyEvent.KEYCODE_PROG_GREEN to "الأخضر",
            KeyEvent.KEYCODE_PROG_YELLOW to "الأصفر", KeyEvent.KEYCODE_PROG_BLUE to "الأزرق",
            KeyEvent.KEYCODE_GUIDE to "الدليل", KeyEvent.KEYCODE_INFO to "المعلومات", KeyEvent.KEYCODE_TV to "TV",
            KeyEvent.KEYCODE_SEARCH to "البحث", KeyEvent.KEYCODE_CAPTIONS to "الترجمة", KeyEvent.KEYCODE_BOOKMARK to "Bookmark",
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "تشغيل/إيقاف", KeyEvent.KEYCODE_APP_SWITCH to "تبديل التطبيقات",
            KeyEvent.KEYCODE_SETTINGS to "الإعدادات", KeyEvent.KEYCODE_EXPLORER to "متصفح (Explorer)",
            KeyEvent.KEYCODE_ENVELOPE to "بريد (Envelope)", KeyEvent.KEYCODE_MUSIC to "موسيقى (Music)",
            KeyEvent.KEYCODE_CALENDAR to "تقويم", KeyEvent.KEYCODE_CONTACTS to "جهات الاتصال", KeyEvent.KEYCODE_CALCULATOR to "آلة حاسبة",
            KeyEvent.KEYCODE_ASSIST to "المساعد", KeyEvent.KEYCODE_VOICE_ASSIST to "المساعد الصوتي",
            KeyEvent.KEYCODE_TV_INPUT to "مدخل TV", KeyEvent.KEYCODE_LAST_CHANNEL to "القناة السابقة",
            KeyEvent.KEYCODE_CHANNEL_UP to "قناة +", KeyEvent.KEYCODE_CHANNEL_DOWN to "قناة -",
            KeyEvent.KEYCODE_MEDIA_TOP_MENU to "قائمة الوسائط", KeyEvent.KEYCODE_TV_DATA_SERVICE to "TV Data",
            KeyEvent.KEYCODE_PAGE_UP to "Page Up", KeyEvent.KEYCODE_PAGE_DOWN to "Page Down"
        ).map { (k, n) -> "k$k" to "$n  (${KeyEvent.keyCodeToString(k).removePrefix("KEYCODE_")})" }
        val f = (1..12).map { "k${KeyEvent.KEYCODE_F1 + it - 1}" to "F$it" }
        val btn = (1..16).map { "k${KeyEvent.KEYCODE_BUTTON_1 + it - 1}" to "BUTTON_$it" }
        val num = (0..9).map { "k${KeyEvent.KEYCODE_0 + it}" to "$it" }
        named + f + btn + num
    }

    fun isReserved(e: KeyEvent) = e.keyCode in reserved

    /** معرّف ثابت للزر: رمز المفتاح، أو رمز المسح إن كان المفتاح غير معروف. */
    fun id(e: KeyEvent): String =
        if (e.keyCode != KeyEvent.KEYCODE_UNKNOWN) "k${e.keyCode}" else "s${e.scanCode}"

    fun describe(e: KeyEvent): String =
        if (e.keyCode != KeyEvent.KEYCODE_UNKNOWN) "${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")} (${e.keyCode})"
        else "زر رقم مسح ${e.scanCode}"

    fun parse(raw: String): Map<String, String> =
        raw.split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank() }?.let { p -> p[0] to p[1] } }.toMap()

    fun encode(m: Map<String, String>) = m.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun label(id: String): String = when {
        id.startsWith("k") -> id.drop(1).toIntOrNull()?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") + " ($it)" } ?: id
        else -> "مسح ${id.drop(1)}"
    }
}

/** تحويل أزرار الاختصار التي يعالجها النظام (يوتيوب/نتفليكس/…): عندما يفتح الزر تطبيقاً يحوّله اللاونشر إلى تطبيق آخر. */
object Redirects {
    /** وقت آخر تشغيل صدر من اللاونشر نفسه (حتى لا نحوّل فتحاً مقصوداً من الشاشة الرئيسية). */
    @Volatile var lastOwnLaunch = 0L
    fun markOwnLaunch() { lastOwnLaunch = System.currentTimeMillis() }

    /** اسم الزر ← الحزم التي يفتحها عادةً. */
    val slots = listOf(
        "يوتيوب" to listOf("com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.apps.youtube.unplugged"),
        "نتفليكس" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "برايم فيديو" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient", "com.amazon.avod"),
        "Google Play" to listOf("com.android.vending")
    )

    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("pin_guard", 0)
    fun all(ctx: android.content.Context): Map<String, String> = RemoteKeys.parse(prefs(ctx).getString("redirects", "") ?: "")
    fun targetFor(ctx: android.content.Context, slot: String): String? = slots.firstOrNull { it.first == slot }?.second?.firstNotNullOfOrNull { all(ctx)[it] }
    fun set(ctx: android.content.Context, slot: String, target: String?) {
        val m = all(ctx).toMutableMap()
        slots.firstOrNull { it.first == slot }?.second?.forEach { if (target == null || it == target) m.remove(it) else m[it] = target }
        prefs(ctx).edit().putString("redirects", RemoteKeys.encode(m)).apply()
    }
}
