package com.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** يعترض فتح إعدادات النظام ويطلب الرمز السري. لا يقرأ محتوى الشاشة. */
class SystemLockService : AccessibilityService() {
    companion object {
        /** وقت (بالميلي ثانية) يُسمح حتى بعده بفتح الإعدادات بعد إدخال الرمز الصحيح. */
        @Volatile var unlockedUntil = 0L
        @Volatile private var lastLaunch = 0L

        fun isSettingsPkg(p: String) =
            p == "com.android.settings" || p == "com.android.tv.settings" || p == "com.google.android.tvsettings" ||
                p.endsWith(".tv.settings") || p.endsWith(".tvsettings")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        runCatching {
            if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = e.packageName?.toString() ?: return
            if (!isSettingsPkg(pkg)) return
            val now = System.currentTimeMillis()
            val sp = getSharedPreferences("pin_guard", 0)
            if (!sp.getBoolean("sys_lock", false)) return
            // مهلة تُمنح عند فتح صفحة التفعيل من إعدادات اللاونشر (أو بعد إدخال الرمز)
            if (now < unlockedUntil || now < sp.getLong("grace_until", 0L)) return
            if (now - lastLaunch < 1500) return   // لا نكرر إطلاق الشاشة
            lastLaunch = now
            startActivity(Intent(this, PinGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun onInterrupt() {}
}
