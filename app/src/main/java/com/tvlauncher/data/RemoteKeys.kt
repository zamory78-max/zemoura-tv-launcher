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
    val common: List<Pair<String, String>> = listOf(
        KeyEvent.KEYCODE_PROG_RED to "الأحمر", KeyEvent.KEYCODE_PROG_GREEN to "الأخضر",
        KeyEvent.KEYCODE_PROG_YELLOW to "الأصفر", KeyEvent.KEYCODE_PROG_BLUE to "الأزرق",
        KeyEvent.KEYCODE_GUIDE to "الدليل (Guide)", KeyEvent.KEYCODE_INFO to "المعلومات (Info)",
        KeyEvent.KEYCODE_TV to "TV", KeyEvent.KEYCODE_SEARCH to "البحث", KeyEvent.KEYCODE_CAPTIONS to "الترجمة",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "تشغيل/إيقاف", KeyEvent.KEYCODE_BOOKMARK to "Bookmark",
        KeyEvent.KEYCODE_APP_SWITCH to "App switch", KeyEvent.KEYCODE_SETTINGS to "الإعدادات",
        KeyEvent.KEYCODE_0 to "0", KeyEvent.KEYCODE_1 to "1", KeyEvent.KEYCODE_2 to "2", KeyEvent.KEYCODE_3 to "3",
        KeyEvent.KEYCODE_4 to "4", KeyEvent.KEYCODE_5 to "5", KeyEvent.KEYCODE_6 to "6", KeyEvent.KEYCODE_7 to "7",
        KeyEvent.KEYCODE_8 to "8", KeyEvent.KEYCODE_9 to "9"
    ).map { (k, n) -> "k$k" to "$n  (${KeyEvent.keyCodeToString(k).removePrefix("KEYCODE_")})" }

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
