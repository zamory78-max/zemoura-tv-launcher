package com.tvlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** تسريع الجهاز بإيقاف عمليات التطبيقات المثبّتة التي تعمل في الخلفية وتفريغ الذاكرة. */
object Booster {
    data class Result(val stopped: Int, val freedMb: Long, val freeMb: Long, val totalMb: Long)

    /** (المتاحة، الإجمالية) بالميغابايت. */
    fun memory(ctx: Context): Pair<Long, Long> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return (mi.availMem / 1_048_576) to (mi.totalMem / 1_048_576)
    }

    suspend fun boost(ctx: Context): Result = withContext(Dispatchers.Default) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = memory(ctx).first
        // نستثني تطبيقات النظام واللاونشر نفسه
        val targets = ctx.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
        var n = 0
        for (a in targets) { runCatching { am.killBackgroundProcesses(a.packageName); n++ } }
        delay(1500)
        val (after, total) = memory(ctx)
        Result(n, (after - before).coerceAtLeast(0), after, total)
    }
}
