package com.tvlauncher

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process

/**
 * يراقب التطبيق الظاهر؛ إن فُتحت إعدادات النظام يعرض شاشة الرمز فوقها.
 * يعتمد على صلاحية «الوصول إلى بيانات الاستخدام» (وليس خدمة إمكانية الوصول).
 */
class GuardService : Service() {
    companion object {
        /** حتى هذا الوقت يُسمح بفتح الإعدادات (بعد إدخال الرمز الصحيح). */
        @Volatile var unlockedUntil = 0L

        fun isSettingsPkg(p: String) =
            p == "com.android.settings" || p == "com.android.tv.settings" || p == "com.google.android.tvsettings" ||
                p.endsWith(".tv.settings") || p.endsWith(".tvsettings")

        fun hasUsageAccess(ctx: Context): Boolean = runCatching {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        /** يشغّل الخدمة إن كان القفل مفعّلاً والصلاحية ممنوحة. */
        fun sync(ctx: Context) {
            val on = ctx.getSharedPreferences("pin_guard", 0).getBoolean("sys_lock", false)
            runCatching {
                if (on && hasUsageAccess(ctx)) ctx.startForegroundService(Intent(ctx, GuardService::class.java))
                else ctx.stopService(Intent(ctx, GuardService::class.java))
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTs = 0L
    private var current = ""
    private var lastLaunch = 0L

    private val tick = object : Runnable {
        override fun run() {
            runCatching { poll() }
            handler.postDelayed(this, 700)
        }
    }

    private fun poll() {
        val sp = getSharedPreferences("pin_guard", 0)
        if (!sp.getBoolean("sys_lock", false)) { stopSelf(); return }
        val now = System.currentTimeMillis()
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = um.queryEvents(if (lastTs == 0L) now - 5_000 else lastTs - 500, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.packageName ?: current
        }
        lastTs = now
        if (!isSettingsPkg(current)) return
        if (now < unlockedUntil || now < sp.getLong("grace_until", 0L) || now - lastLaunch < 2000) return
        lastLaunch = now
        startActivity(Intent(this, PinGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("guard", "حماية الإعدادات", NotificationManager.IMPORTANCE_MIN))
        startForeground(77, Notification.Builder(this, "guard").setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("قفل إعدادات النظام مفعّل").build())
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
