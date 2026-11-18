package com.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** يعترض فتح إعدادات النظام ويطلب الرمز السري. لا يقرأ محتوى الشاشة. */
class SystemLockService : AccessibilityService() {
    companion object {
        /** وقت (بالميلي ثانية) يُسمح حتى بعده بفتح الإعدادات بعد إدخال الرمز الصحيح. */
        @Volatile var unlockedUntil = 0L

        fun isSettingsPkg(p: String) =
            p == "com.android.settings" || p == "com.android.tv.settings" || p == "com.google.android.tvsettings" ||
                p.endsWith(".tv.settings") || p.endsWith(".tvsettings")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (!isSettingsPkg(pkg)) return
        val sp = getSharedPreferences("pin_guard", 0)
        if (!sp.getBoolean("sys_lock", false)) return
        if (System.currentTimeMillis() < unlockedUntil) return
        startActivity(Intent(this, PinGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    override fun onInterrupt() {}
}
