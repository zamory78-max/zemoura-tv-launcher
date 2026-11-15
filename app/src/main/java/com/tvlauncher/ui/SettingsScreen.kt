package com.tvlauncher.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvlauncher.LauncherViewModel
import com.tvlauncher.data.City
import com.tvlauncher.data.PrayerRepository
import com.tvlauncher.data.WeatherRepository
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String) =
    Text(title, color = Palette.Amber, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp))

@Composable
private fun RowItem(title: String, sub: String? = null, value: String? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (sub != null) Text(sub, color = Palette.Muted, fontSize = 16.sp)
            }
            if (value != null) Text(value, color = Palette.Amber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToggleItem(title: String, sub: String? = null, on: Boolean, onChange: (Boolean) -> Unit) =
    RowItem(title, sub, if (on) "مفعّل" else "متوقف") { onChange(!on) }

@Composable
fun SettingsScreen(vm: LauncherViewModel, onBack: () -> Unit, guard: (String, () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf("") }
    var cityResults by remember { mutableStateOf<List<City>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.update { setWallpaper("uri:$uri") }
        }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("رجوع", onClick = onBack)
                Text("إعدادات اللاونشر", color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        item { Section("الحماية") }
        item {
            ToggleItem("حماية الإعدادات بالرمز", "يُطلب الرمز قبل فتح هذه الصفحة", s.protectSettings) { v ->
                if (!vm.hasPin) msg = "عيّن رمزاً سرياً أولاً." else guard("تأكيد الرمز") { vm.update { setProtectSettings(v) } }
            }
        }
        item {
            RowItem(if (vm.hasPin) "تغيير الرمز السري" else "تعيين رمز سري", "4 أرقام، يحمي الإعدادات والتطبيقات والمجلدات المقفلة") {
                if (vm.hasPin) guard("أدخل الرمز الحالي") { dialog = "pin" } else dialog = "pin"
            }
        }
        item {
            RowItem("قفل التطبيقات", "اختر التطبيقات التي تُطلب لها الرمز قبل التشغيل", "${s.lockedApps.size}") {
                if (!vm.hasPin) msg = "عيّن رمزاً سرياً أولاً." else dialog = "lockApps"
            }
        }
        item {
            RowItem("قفل المجلدات", "اختر المجلدات التي تُطلب لها الرمز قبل الفتح", "${s.folders.count { it.locked }}") {
                if (!vm.hasPin) msg = "عيّن رمزاً سرياً أولاً." else dialog = "lockFolders"
            }
        }
        item {
            RowItem("إظهار التطبيقات المخفية", "يعيد إظهار كل ما أخفيته", "${s.hiddenApps.size}") {
                guard("تأكيد الرمز") { vm.update { setHiddenApps(emptySet()) } }
            }
        }

        item { Section("المظهر") }
        item {
            RowItem("حجم الأيقونات", null, listOf("صغير", "متوسط", "كبير")[s.tileScale.coerceIn(0, 2)]) { dialog = "tileScale" }
        }
        item {
            RowItem("شكل الأيقونات", null, shapeName(s.tileShape)) { dialog = "tileShape" }
        }

        item { Section("التطبيقات والفيديو") }
        item {
            ToggleItem("إظهار التطبيقات غير المدعومة", "التطبيقات المصممة للهاتف وبلا واجهة تلفاز؛ تظهر في صف منفصل بوسم «غير مدعوم»",
                s.showUnsupported) { v -> vm.update { setShowUnsupported(v) } }
        }
        item { ToggleItem("صف المشاهدة", "يعرض تطبيقات المشاهدة التي تختارها وفيديوهات يوتيوب", s.showVideos) { v -> vm.update { setShowVideos(v) } } }
        item {
            RowItem("تطبيقات صف المشاهدة", "مثل يوتيوب ونتفليكس وشاهد؛ تظهر كبطاقات تفتح التطبيق مباشرة", "${s.videoApps.size}") { dialog = "videoApps" }
        }
        item {
            RowItem("مفتاح YouTube Data API (اختياري)", "المفتاح يجلب الفيديوهات رسمياً من جوجل. بدونه تُجلب من مصدر Piped المفتوح (غير رسمي)",
                if (s.youtubeKey.isBlank()) "غير مضبوط" else "مضبوط") { dialog = "ytkey" }
        }
        item {
            RowItem("خادم Piped (بلا مفتاح)", "غيّره إن توقف الخادم الافتراضي", s.pipedInstance.removePrefix("https://")) { dialog = "piped" }
        }
        item {
            RowItem("مصدر فيديو مخصص (رابط JSON)", "لعرض فيديوهات تطبيقك أنت (مثل Zemoura TV) داخل الصف؛ الصيغة في README",
                if (s.feedUrl.isBlank()) "غير مضبوط" else "مضبوط") { dialog = "feed" }
        }
        item { RowItem("بلد الفيديوهات", "رمز من حرفين مثل DZ أو SA أو FR", s.videoRegion) { dialog = "region" } }

        item { Section("الخلفية") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(Wallpapers.presets) { i, (name, brush) ->
                    Focusable(onClick = { vm.update { setWallpaper("preset:$i") } }, modifier = Modifier.size(180.dp, 100.dp)) {
                        Box(Modifier.fillMaxSize().background(brush), contentAlignment = Alignment.BottomCenter) {
                            Text(name + if (s.wallpaper == "preset:$i") " ✓" else "", color = Palette.Text, fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
        item { RowItem("اختيار صورة من الجهاز", "تُحفظ ويُعاد تحميلها بعد التشغيل") { pickImage.launch(arrayOf("image/*")) } }

        item { Section("الموقع والصلاة") }
        item { RowItem("المدينة (الصلاة والطقس)", "ابحث عن أي مدينة بالاسم", s.city.name) { dialog = "city" } }
        item { RowItem("طريقة حساب الصلاة", null, PrayerRepository.methodName(s.method)) { dialog = "method" } }
        item {
            ToggleItem("المذهب في حساب العصر", if (s.madhab == "HANAFI") "حنفي" else "جمهور (الشافعي/المالكي/الحنبلي)", s.madhab == "HANAFI") { v ->
                vm.update { setMadhab(if (v) "HANAFI" else "SHAFI") }
            }
        }
        item { RowItem("مدة الإقامة بعد الأذان", "تُستخدم في العد التنازلي", "${s.iqamaMinutes} دقيقة") { dialog = "iqama" } }
        item { ToggleItem("عرض شاشة الأذان", null, s.adhanEnabled) { v -> vm.update { setAdhan(v) } } }
        item { ToggleItem("تشغيل صوت الأذان", "ضع الملف في res/raw/adhan.mp3", s.adhanSound) { v -> vm.update { setAdhanSound(v) } } }

        item { Section("الإقلاع واللاونشر الافتراضي") }
        item { ToggleItem("التشغيل التلقائي عند الإقلاع", "يفتح اللاونشر بعد تشغيل الجهاز أو عودة الكهرباء", s.autoStart) { v -> vm.update { setAutoStart(v) } } }
        item {
            RowItem("تعيين كلاونشر افتراضي", "الطريقة المضمونة لبقائه الشاشة الأولى بعد كل تشغيل") {
                requestHomeRole(ctx) { roleLauncher.launch(it) }
            }
        }
        item {
            RowItem("السماح بالعرض فوق التطبيقات", "ضروري لظهور شاشة الأذان أثناء تشغيل تطبيق آخر") {
                ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
            RowItem("السماح بالمنبّهات الدقيقة", "لإطلاق الأذان في وقته بالضبط") {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    when (dialog) {
        "pin" -> PinSetup(onDone = { vm.setPin(it); dialog = ""; msg = "تم حفظ الرمز السري." }, onDismiss = { dialog = "" })
        "tileScale" -> ChoiceDialog("حجم الأيقونات", listOf(0 to "صغير", 1 to "متوسط", 2 to "كبير"),
            onPick = { v -> vm.update { setTileScale(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "tileShape" -> ChoiceDialog("شكل الأيقونات", listOf("banner", "rounded", "square", "circle").map { it to shapeName(it) },
            onPick = { v -> vm.update { setTileShape(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "lockApps" -> MultiToggleDialog("التطبيقات المقفلة", apps.map { Triple(it.pkg, it.label, it.pkg in s.lockedApps) },
            onToggle = { pkg -> vm.toggleAppLock(pkg) }, onDismiss = { dialog = "" })
        "lockFolders" -> MultiToggleDialog("المجلدات المقفلة", s.folders.map { Triple(it.id, it.name, it.locked) },
            onToggle = { id -> vm.mutateFolder(id) { f -> f.copy(locked = !f.locked) } }, onDismiss = { dialog = "" })
        "videoApps" -> MultiToggleDialog("تطبيقات صف المشاهدة", apps.map { Triple(it.pkg, it.label, it.pkg in s.videoApps) },
            onToggle = { pkg -> vm.update { setVideoApps(if (pkg in s.videoApps) s.videoApps - pkg else s.videoApps + pkg) } },
            onDismiss = { dialog = "" })
        "feed" -> TextInputDialog("رابط JSON للفيديوهات", s.feedUrl, hint = "https://...", onOk = { v ->
            vm.update { setFeedUrl(if (v.startsWith("https://") || v.isEmpty()) v else s.feedUrl) }; dialog = ""
        }, onDismiss = { dialog = "" })
        "piped" -> TextInputDialog("عنوان خادم Piped", s.pipedInstance, hint = "https://...", onOk = { v ->
            if (v.startsWith("https://")) vm.update { setPiped(v.trimEnd('/')) }; dialog = ""
        }, onDismiss = { dialog = "" })
        "ytkey" -> TextInputDialog("مفتاح YouTube API", s.youtubeKey, onOk = { v -> vm.update { setYoutubeKey(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "region" -> TextInputDialog("رمز البلد", s.videoRegion, onOk = { v ->
            if (v.length == 2) vm.update { setVideoRegion(v.uppercase()) }; dialog = ""
        }, onDismiss = { dialog = "" })
        "iqama" -> ChoiceDialog("مدة الإقامة", listOf(5, 8, 10, 15, 20, 25).map { it to "$it دقيقة" },
            onPick = { v -> vm.update { setIqama(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "method" -> ChoiceDialog("طريقة الحساب", PrayerRepository.methods,
            onPick = { v -> vm.update { setMethod(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "city" -> TextInputDialog("ابحث عن مدينة", hint = "مثال: وهران", onOk = { q ->
            scope.launch {
                cityResults = WeatherRepository.searchCities(q)
                dialog = if (cityResults.isEmpty()) { msg = "لا نتائج، تحقق من الاتصال بالإنترنت أو جرّب اسماً آخر."; "" } else "cityPick"
            }
        }, onDismiss = { dialog = "" })
        "cityPick" -> ChoiceDialog("اختر المدينة", cityResults.map { it to "${it.name} · ${it.country}" },
            onPick = { c -> vm.update { setCity(c) }; dialog = "" }, onDismiss = { dialog = "" })
    }
    msg?.let { MessageDialog("إعدادات اللاونشر", it) { msg = null } }
}

private fun requestHomeRole(ctx: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return
        }
    }
    // احتياطي: شاشة اختيار التطبيق الرئيسي في النظام
    runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

private fun shapeName(k: String) = when (k) {
    "rounded" -> "مربع بحواف مدورة"
    "square" -> "مربع"
    "circle" -> "دائرة"
    else -> "مستطيل (بنر)"
}
