package com.tvlauncher.adhan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvlauncher.R
import com.tvlauncher.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = runBlocking { Prefs(context).flow.first() }
        val name = intent.getStringExtra("name")
        val time = intent.getLongExtra("time", 0L)

        if (s.adhanEnabled && name != null) {
            val show = Intent(context, AdhanActivity::class.java)
                .putExtra("name", name).putExtra("time", time)
                .putExtra("iqama", s.iqamaMinutes).putExtra("sound", s.adhanSound)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // 1) إشعار بملء الشاشة (الطريقة المسموحة في الإصدارات الحديثة)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_HIGH)
            )
            val pi = PendingIntent.getActivity(
                context, 1, show, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                1,
                Notification.Builder(context, "adhan")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("حان موعد أذان $name")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .build()
            )
            // 2) محاولة مباشرة (تنجح عند منح إذن العرض فوق التطبيقات)
            runCatching { context.startActivity(show) }
        }
        // جدولة الصلاة التالية
        AdhanScheduler.scheduleNext(context, s)
    }
}
