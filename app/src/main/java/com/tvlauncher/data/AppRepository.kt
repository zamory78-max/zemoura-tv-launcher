package com.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRepository {

    /**
     * يجمع التطبيقات القابلة للتشغيل:
     *  - المدعومة: لها نشاط بفئة LEANBACK_LAUNCHER (مصممة للتلفاز).
     *  - غير المدعومة: لها فقط فئة LAUNCHER (تطبيقات هاتف/تابلت) وتظهر بوسم خاص.
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val self = context.packageName

        fun query(category: String): List<ResolveInfo> =
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackPkgs = leanback.map { it.activityInfo.packageName }.toSet()
        val phoneOnly = query(Intent.CATEGORY_LAUNCHER)
            .filter { it.activityInfo.packageName !in leanbackPkgs }

        fun toEntry(ri: ResolveInfo, supported: Boolean) = AppEntry(
            pkg = ri.activityInfo.packageName,
            label = ri.loadLabel(pm).toString(),
            supported = supported,
            icon = runCatching { ri.loadIcon(pm) }.getOrNull(),
            banner = if (supported) runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull() else null
        )

        (leanback.map { toEntry(it, true) } + phoneOnly.map { toEntry(it, false) })
            .filter { it.pkg != self }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        return runCatching {
            context.startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun openFile(context: Context, uri: String): Boolean = runCatching {
        val u = Uri.parse(uri)
        val type = context.contentResolver.getType(u)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(u, type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        true
    }.getOrDefault(false)

    fun openAppSettings(context: Context, pkg: String) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Suppress("unused")
    fun isInstalled(context: Context, pkg: String) = runCatching {
        context.packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)
}
