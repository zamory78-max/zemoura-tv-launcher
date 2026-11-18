package com.tvlauncher.adhan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvlauncher.MainActivity
import com.tvlauncher.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * عند الإقلاع (بما في ذلك بعد انقطاع الكهرباء) أو تغيّر الوقت/المنطقة:
 * يعيد جدولة الأذان، ويحاول فتح اللاونشر إن كان "التشغيل التلقائي" مفعّلاً.
 * ملاحظة: بقاء اللاونشر هو الافتراضي يتحقق من كونه تطبيق HOME الافتراضي في النظام.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = runBlocking { Prefs(context).flow.first() }
        AdhanScheduler.scheduleNext(context, s)
        com.tvlauncher.GuardService.sync(context)
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        if (boot && s.autoStart) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
    }
}
