package com.tvlauncher.adhan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tvlauncher.data.PrayerRepository
import com.tvlauncher.data.Settings

object AdhanScheduler {
    private const val REQ = 4711

    /** يجدول منبّهاً واحداً للصلاة القادمة؛ يعيد المستقبِل جدولة التالي بعد كل أذان. */
    fun scheduleNext(context: Context, s: Settings) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, null)
        am.cancel(pi)
        if (!s.adhanEnabled) return

        val next = PrayerRepository.next(s)
        val withExtras = pending(context, next.name to next.time.time)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time.time, withExtras)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time.time, withExtras)
    }

    private fun pending(context: Context, extras: Pair<String, Long>?): PendingIntent {
        val i = Intent(context, AdhanReceiver::class.java)
        extras?.let { i.putExtra("name", it.first).putExtra("time", it.second) }
        return PendingIntent.getBroadcast(
            context, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
