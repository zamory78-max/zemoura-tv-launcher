package com.tvlauncher.data

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class PrayerEntry(val prayer: Prayer, val name: String, val time: Date)

object PrayerRepository {

    /** طرق الحساب المتاحة (الاسم المعروض). */
    val methods: List<Pair<String, String>> = listOf(
        "MUSLIM_WORLD_LEAGUE" to "رابطة العالم الإسلامي",
        "EGYPTIAN" to "الهيئة المصرية العامة للمساحة",
        "KARACHI" to "جامعة العلوم الإسلامية، كراتشي",
        "UMM_AL_QURA" to "جامعة أم القرى، مكة",
        "DUBAI" to "دبي",
        "QATAR" to "قطر",
        "KUWAIT" to "الكويت",
        "NORTH_AMERICA" to "أمريكا الشمالية (ISNA)",
        "TURKEY" to "رئاسة الشؤون الدينية التركية",
        "SINGAPORE" to "سنغافورة",
        "MOON_SIGHTING_COMMITTEE" to "لجنة رؤية الهلال"
    )

    fun methodName(key: String) = methods.firstOrNull { it.first == key }?.second ?: key

    private fun params(s: Settings) = runCatching { CalculationMethod.valueOf(s.method) }
        .getOrDefault(CalculationMethod.MUSLIM_WORLD_LEAGUE).parameters.also {
            it.madhab = if (s.madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        }

    /** أوقات يوم معيّن بحسب المنطقة الزمنية للمدينة. */
    fun forDay(s: Settings, dayOffset: Int = 0, now: Date = Date()): PrayerTimes {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(s.city.tz))
        cal.time = now
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        val dc = DateComponents(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return PrayerTimes(Coordinates(s.city.lat, s.city.lon), dc, params(s))
    }

    fun list(t: PrayerTimes): List<PrayerEntry> = listOf(
        PrayerEntry(Prayer.FAJR, "الفجر", t.fajr),
        PrayerEntry(Prayer.DHUHR, "الظهر", t.dhuhr),
        PrayerEntry(Prayer.ASR, "العصر", t.asr),
        PrayerEntry(Prayer.MAGHRIB, "المغرب", t.maghrib),
        PrayerEntry(Prayer.ISHA, "العشاء", t.isha)
    )

    /** الصلاة القادمة (قد تكون فجر الغد). */
    fun next(s: Settings, now: Date = Date()): PrayerEntry {
        val today = list(forDay(s, 0, now))
        today.firstOrNull { it.time.after(now) }?.let { return it }
        return list(forDay(s, 1, now)).first()
    }
}
