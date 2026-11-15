package com.tvlauncher.data

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
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
        "ALGERIA" to "الجزائر — وزارة الشؤون الدينية والأوقاف",
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

    private fun params(s: Settings): CalculationParameters {
        val p = if (s.method == "ALGERIA") {
            // المعتمد رسمياً في الجزائر: الفجر 18° والعشاء 17° والعصر بالمثل (جمهور)
            CalculationMethod.OTHER.parameters.also { it.fajrAngle = 18.0; it.ishaAngle = 17.0 }
        } else {
            runCatching { CalculationMethod.valueOf(s.method) }
                .getOrDefault(CalculationMethod.MUSLIM_WORLD_LEAGUE).parameters
        }
        p.madhab = if (s.madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        return p
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

    /** (السابقة، القادمة) حول اللحظة الحالية، لحساب تقدّم الحلقة. */
    fun window(s: Settings, now: Date = Date()): Pair<PrayerEntry, PrayerEntry> {
        val all = list(forDay(s, -1, now)) + list(forDay(s, 0, now)) + list(forDay(s, 1, now))
        val i = all.indexOfFirst { it.time.after(now) }.coerceAtLeast(1)
        return all[i - 1] to all[i]
    }

    /** الصلاة القادمة (قد تكون فجر الغد). */
    fun next(s: Settings, now: Date = Date()): PrayerEntry {
        val today = list(forDay(s, 0, now))
        today.firstOrNull { it.time.after(now) }?.let { return it }
        return list(forDay(s, 1, now)).first()
    }
}
