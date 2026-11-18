package com.tvlauncher.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.tvlauncher.data.RemoteKeys
import com.tvlauncher.data.Booster
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
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
import com.tvlauncher.data.Countries
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
    var pickedCountry by remember { mutableStateOf("DZ") }
    var pickedWilaya by remember { mutableStateOf(0) }
    var rowSel by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("الحماية") }
    var capturedKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sysLock by remember { mutableStateOf(ctx.getSharedPreferences("pin_guard", 0).getBoolean("sys_lock", false)) }
    var keyNote by remember { mutableStateOf("") }
    var boostTick by remember { mutableStateOf(0) }
    var boosting by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.update { setWallpaper("uri:$uri") }
        }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val allCats = listOf("الحماية", "المظهر", "الأخبار", "صندوق الأسماء", "الصفوف المخصصة", "التطبيقات والفيديو", "الخلفية", "الموقع والصلاة", "أزرار الريموت", "الإقلاع واللاونشر الافتراضي", "التسريع")
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(380.dp).fillMaxHeight().background(Color(0x66000000)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("إعدادات اللاونشر", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            PillButton("رجوع", onClick = onBack)
            Spacer(Modifier.height(8.dp))
            allCats.forEach { c ->
                Focusable(onClick = { cat = c }, modifier = Modifier.fillMaxWidth(),
                    bg = if (c == cat) Color(0x59F2B84B) else Color(0x22FFFFFF)) {
                    Text(c, color = if (c == cat) Palette.Amber else Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        if (cat == "الحماية") {
            item { Section("الحماية") }
            item {
                ToggleItem("حماية الإعدادات بالرمز", "يُطلب الرمز قبل فتح هذه الصفحة", s.protectSettings) { v ->
                    if (!vm.hasPin) msg = "عيّن رمزاً سرياً أولاً." else guard("تأكيد الرمز") { vm.update { setProtectSettings(v) } }
                }
            }
            item {
                ToggleItem("قفل إعدادات النظام", "يطلب الرمز قبل فتح إعدادات البوكس (يحتاج صلاحية إمكانية الوصول أدناه)",
                    sysLock) { v ->
                    if (!vm.hasPin) msg = "عيّن رمزاً سرياً أولاً." else guard("تأكيد الرمز") {
                        ctx.getSharedPreferences("pin_guard", 0).edit().putBoolean("sys_lock", v).apply(); sysLock = v
                    }
                }
            }
            item {
                RowItem("تفعيل صلاحية إمكانية الوصول", "افتح الصفحة ثم فعّل «قفل إعدادات النظام (Zemoura)»") {
                    ctx.getSharedPreferences("pin_guard", 0).edit().putLong("grace_until", System.currentTimeMillis() + 5 * 60_000L).apply()
                    runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

        }
        if (cat == "المظهر") {
            item { Section("المظهر") }
            item {
                RowItem("اتجاه الواجهة والتنقل", if (s.layoutRtl) "من اليمين إلى اليسار" else "من اليسار إلى اليمين",
                    if (s.layoutRtl) "يمين ← يسار" else "يسار ← يمين") { vm.update { setLayoutRtl(!s.layoutRtl) } }
            }
            item {
                RowItem("حجم الأيقونات", null, listOf("صغير", "متوسط", "كبير")[s.tileScale.coerceIn(0, 2)]) { dialog = "tileScale" }
            }
            item {
                RowItem("شكل الأيقونات", null, shapeName(s.tileShape)) { dialog = "tileShape" }
            }

        }
        if (cat == "الأخبار") {
            item { Section("الأخبار") }
            item { RowItem("اتجاه حركة الشريط", null, if (s.newsDir == "right") "نحو اليمين ←" else "→ نحو اليسار") { vm.update { setNewsDir(if (s.newsDir == "right") "left" else "right") } } }
            item { ToggleItem("شريط الأخبار", "يظهر تحت مواقيت الصلاة", s.showNews) { v -> vm.update { setShowNews(v) } } }
            item { RowItem("مصادر الأخبار", "اختر عدة مصادر (جزائرية، عربية، إسلامية سنية)", "${s.newsSources.split(',').count { it.isNotBlank() }}") { dialog = "newsSources" } }
            item { RowItem("مصدر إضافي (رابط RSS)", s.newsUrl.take(50), "تغيير") { dialog = "newsUrl" } }

        }
        if (cat == "صندوق الأسماء") {
            item { Section("صندوق الأسماء") }
            item { RowItem("أسماء الأسرة (حتى 20 اسماً)", "يفتح صندوق الهدايا كل فترة ويُخرج اسماً منها", "${s.names.split(',', '،', '\n').count { it.isNotBlank() }.coerceAtMost(20)}") { dialog = "names" } }
            item { Text("اكتب الأسماء مفصولة بفاصلة، مثل: أحمد، سارة، يوسف. اضغط على الصندوق في الرئيسية لفتحه فوراً.", color = Palette.Muted, fontSize = 18.sp) }
        }
        if (cat == "الصفوف المخصصة") {
            item { Section("الصفوف المخصصة") }
            item { RowItem("إضافة صف جديد", "مثل: ألعاب، أطفال، رياضة", "${s.rows.size}") { dialog = "newRow" } }
            itemsIndexed(s.rows, key = { _, r -> "row:" + r.id }) { _, r ->
                RowItem("صف «${r.name}»", "${r.pkgs.size} تطبيق — اضغط للإدارة") { rowSel = r.id; dialog = "rowMenu" }
            }

        }
        if (cat == "التطبيقات والفيديو") {
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
                RowItem("قنوات يوتيوب الخاصة بك", "ألصق روابط القنوات أو @الاسم مفصولة بفاصلة؛ تُعرض آخر فيديوهاتها بدون مفتاح",
                    if (s.channels.isBlank()) "غير مضبوطة" else "${s.channels.split(',').count { it.isNotBlank() }} قنوات") { dialog = "channels" }
            }
            item {
                RowItem("مصدر فيديو مخصص (رابط JSON)", "لعرض فيديوهات تطبيقك أنت (مثل Zemoura TV) داخل الصف؛ الصيغة في README",
                    if (s.feedUrl.isBlank()) "غير مضبوط" else "مضبوط") { dialog = "feed" }
            }
            item { RowItem("بلد الفيديوهات", "رمز من حرفين مثل DZ أو SA أو FR", s.videoRegion) { dialog = "region" } }

        }
        if (cat == "الخلفية") {
            item { Section("الخلفية") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(Wallpapers.photos) { (key, name, _) ->
                        Focusable(onClick = { vm.update { setWallpaper(key) } }, modifier = Modifier.size(180.dp, 100.dp)) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(model = Wallpapers.resId(key), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Text(name + if (s.wallpaper == key) " ✓" else "", color = Color.White, fontSize = 18.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).background(Color(0x99000000)).fillMaxWidth().padding(6.dp))
                            }
                        }
                    }
                    itemsIndexed(Wallpapers.presets) { i, (name, brush) ->
                        Focusable(onClick = { vm.update { setWallpaper("preset:$i") } }, modifier = Modifier.size(180.dp, 100.dp)) {
                            Box(Modifier.fillMaxSize().background(brush), contentAlignment = Alignment.BottomCenter) {
                                Text(name + if (s.wallpaper == "preset:$i") " ✓" else "", color = Palette.Text, fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                }
            }
            item {
                ToggleItem("تغيير الخلفية تلقائياً", "تتبدّل الخلفية بين الخلفيات التي تختارها أدناه", s.wallpaperAuto) { v ->
                    vm.update { setWallpaperAuto(v) }
                    if (v && s.wallpaperSet.size < 2) msg = "اختر خلفيتين على الأقل من «الخلفيات المشمولة بالتغيير»."
                }
            }
            item { RowItem("مدة التغيير", null, "${s.wallpaperMinutes} دقيقة") { dialog = "wpMinutes" } }
            item { RowItem("الخلفيات المشمولة بالتغيير", "اختر الخلفيات التي تتبدّل بينها", "${s.wallpaperSet.size}") { dialog = "wpSet" } }
            item { RowItem("اختيار صورة من الجهاز", "تُحفظ ويُعاد تحميلها بعد التشغيل") { pickImage.launch(arrayOf("image/*")) } }

        }
        if (cat == "الموقع والصلاة") {
            item { Section("الموقع والصلاة") }
            item { RowItem("المدينة (الصلاة والطقس)", "الدولة ← الولاية ← المدينة", s.city.name) { dialog = "country" } }
            item { RowItem("طريقة حساب الصلاة", null, PrayerRepository.methodName(s.method)) { dialog = "method" } }
            item {
                ToggleItem("المذهب في حساب العصر", if (s.madhab == "HANAFI") "حنفي" else "جمهور (الشافعي/المالكي/الحنبلي)", s.madhab == "HANAFI") { v ->
                    vm.update { setMadhab(if (v) "HANAFI" else "SHAFI") }
                }
            }
            item { RowItem("مدة الإقامة بعد الأذان", "تُستخدم في العد التنازلي", "${s.iqamaMinutes} دقيقة") { dialog = "iqama" } }
            item { ToggleItem("عرض شاشة الأذان", null, s.adhanEnabled) { v -> vm.update { setAdhan(v) } } }
            item { ToggleItem("تشغيل صوت الأذان", "ضع الملف في res/raw/adhan.mp3", s.adhanSound) { v -> vm.update { setAdhanSound(v) } } }

        }
        if (cat == "أزرار الريموت") {
            item { Section("أزرار الريموت") }
            item { Text("اربط الأزرار الملوّنة أو أزرار الاختصار (يوتيوب، نتفليكس…) بأي تطبيق. تعمل أثناء ظهور اللاونشر. إن احتفظ النظام بزر ما فلن يصل للاونشر ولا يمكن ربطه.", color = Palette.Muted, fontSize = 16.sp) }
            val km = RemoteKeys.parse(s.keyMap)
            km.forEach { (id, pkg) ->
                item(key = "km$id") {
                    RowItem("زر ${RemoteKeys.label(id)}", "اضغط لإزالة الربط", apps.firstOrNull { it.pkg == pkg }?.label ?: pkg) {
                        vm.update { setKeyMap(RemoteKeys.encode(km - id)) }
                    }
                }
            }
            item { RowItem("ربط زر جديد", "ستضغط الزر في الريموت ثم تختار التطبيق") { capturedKey = null; keyNote = ""; dialog = "keyCapture" } }
        }
        if (cat == "الإقلاع واللاونشر الافتراضي") {
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
        if (cat == "التسريع") {
            item { Section("التسريع") }
            item {
                val (free, total) = remember(boostTick) { Booster.memory(ctx) }
                RowItem("الذاكرة المتاحة", "كلما زادت كان الجهاز أسرع", "$free / $total ميغابايت") { boostTick++ }
            }
            item {
                RowItem(if (boosting) "جارٍ التسريع…" else "تسريع الآن", "يوقف التطبيقات المثبّتة التي تعمل في الخلفية ويفرّغ الذاكرة") {
                    if (!boosting) scope.launch {
                        boosting = true
                        val r = Booster.boost(ctx); boosting = false; boostTick++
                        msg = "أُوقفت تطبيقات الخلفية. الذاكرة المتاحة ${r.freeMb} من ${r.totalMb} ميغابايت" + if (r.freedMb > 0) " (تحرّر ${r.freedMb})." else "."
                    }
                }
            }
            item {
                Text("لا يوقف التسريع تطبيقات النظام ولا هذا اللاونشر. قد يعيد أندرويد تشغيل بعض الخدمات من تلقاء نفسه، وهذا طبيعي.",
                    color = Palette.Muted, fontSize = 18.sp)
            }
        }
        }
    }

    val selRow = s.rows.firstOrNull { it.id == rowSel }
    when (dialog) {
        "wpMinutes" -> ChoiceDialog("مدة التغيير", listOf(1, 5, 10, 30, 60).map { it to "$it دقيقة" },
            onPick = { v -> vm.update { setWallpaperMinutes(v) }; dialog = "" }, onDismiss = { dialog = "" })
        "wpSet" -> MultiToggleDialog("الخلفيات المشمولة", Wallpapers.all().map { (k, n) -> Triple(k, n, k in s.wallpaperSet) },
            onToggle = { k -> vm.update { setWallpaperSet(if (k in s.wallpaperSet) s.wallpaperSet - k else s.wallpaperSet + k) } },
            onDismiss = { dialog = "" })
        "keyCapture" -> {
            // الحوار نافذة مستقلة فلا يصلها dispatchKeyEvent الخاص بالنشاط؛ نلتقط الزر هنا مباشرة
            val fr = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            DialogFrame("ربط زر الريموت", { dialog = "" }) {
                Box(Modifier.focusRequester(fr).focusable().onPreviewKeyEvent { e ->
                    val ne = e.nativeKeyEvent
                    if (ne.action == android.view.KeyEvent.ACTION_DOWN && RemoteKeys.isReserved(ne) && ne.keyCode != android.view.KeyEvent.KEYCODE_BACK) {
                        keyNote = "وصل الزر ${RemoteKeys.describe(ne)} لكنه محجوز للتنقل ولا يمكن ربطه"; true
                    } else if (ne.action == android.view.KeyEvent.ACTION_DOWN && !RemoteKeys.isReserved(ne)) {
                        capturedKey = RemoteKeys.id(ne) to RemoteKeys.describe(ne); dialog = "keyPick"; true
                    } else false
                }) {
                    Text("اضغط الآن الزر الذي تريد ربطه في الريموت…\n(زر الرجوع للإلغاء)\nإن لم يحدث شيء فالنظام يحتفظ بهذا الزر، فاختر من القائمة.",
                        color = Palette.Text, fontSize = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (keyNote.isNotEmpty()) Text(keyNote, color = Palette.Amber, fontSize = 18.sp)
                PillButton("اختيار الزر من قائمة", onClick = { dialog = "keyList" })
            }
        }
        "keyList" -> ChoiceDialog("اختر الزر", RemoteKeys.common, onDismiss = { dialog = "" },
            onPick = { id -> capturedKey = id to RemoteKeys.label(id); dialog = "keyPick" })
        "keyPick" -> capturedKey?.let { (id, name) ->
            ChoiceDialog("الزر $name ← اختر التطبيق", apps.map { it.pkg to it.label },
                onPick = { pkg -> vm.update { setKeyMap(RemoteKeys.encode(RemoteKeys.parse(s.keyMap) + (id to pkg))) }; dialog = ""; msg = "تم ربط الزر." },
                onDismiss = { dialog = "" })
        } ?: run { dialog = "" }
        "names" -> TextInputDialog("أسماء الأسرة", s.names, hint = "أحمد، سارة، يوسف", onOk = { vm.update { setNames(it.trim()) }; dialog = "" }, onDismiss = { dialog = "" })
        "newsSources" -> {
            val cur = s.newsSources.split(',').filter { it.isNotBlank() }.toSet()
            MultiToggleDialog("مصادر الأخبار", com.tvlauncher.data.NewsSources.all.map { Triple(it.id, it.name, it.id in cur) },
                onToggle = { id -> vm.update { setNewsSources((if (id in cur) cur - id else cur + id).joinToString(",")) } }, onDismiss = { dialog = "" })
        }
        "newsUrl" -> TextInputDialog("رابط RSS للأخبار", s.newsUrl, hint = "https://...", onOk = { vm.update { setNewsUrl(it.trim()) }; dialog = "" }, onDismiss = { dialog = "" })
        "newRow" -> TextInputDialog("اسم الصف الجديد", hint = "مثال: ألعاب", onOk = { if (it.isNotBlank()) vm.addRow(it); dialog = "" }, onDismiss = { dialog = "" })
        "rowMenu" -> if (selRow == null) dialog = "" else ChoiceDialog(selRow.name,
            listOf("apps" to "اختيار تطبيقات الصف", "rename" to "تغيير الاسم", "up" to "تحريك الصف للأعلى", "down" to "تحريك الصف للأسفل", "delete" to "حذف الصف (ترجع تطبيقاته للأصل)"),
            onDismiss = { dialog = "" }, onPick = { k ->
                when (k) {
                    "apps" -> dialog = "rowApps"
                    "rename" -> dialog = "rowRename"
                    "up" -> { vm.moveRow(selRow.id, -1); dialog = "" }
                    "down" -> { vm.moveRow(selRow.id, +1); dialog = "" }
                    "delete" -> { vm.deleteRow(selRow.id); dialog = "" }
                }
            })
        "rowApps" -> if (selRow == null) dialog = "" else MultiToggleDialog("تطبيقات صف «${selRow.name}»",
            apps.map { Triple(it.pkg, it.label, it.pkg in selRow.pkgs) },
            onToggle = { pkg -> vm.toggleRowApp(selRow.id, pkg) }, onDismiss = { dialog = "" })
        "rowRename" -> if (selRow == null) dialog = "" else TextInputDialog("اسم الصف", selRow.name,
            onOk = { if (it.isNotBlank()) vm.renameRow(selRow.id, it); dialog = "" }, onDismiss = { dialog = "" })
        "coords" -> TextInputDialog("إحداثيات المدينة", hint = "36.75, 3.05 (خط العرض، خط الطول)", onOk = { v ->
            val p = v.split(',', '،', ' ').filter { it.isNotBlank() }.mapNotNull { it.toDoubleOrNull() }
            if (p.size == 2 && p[0] in -90.0..90.0 && p[1] in -180.0..180.0) {
                vm.update { setCity(City(manualName.ifBlank { "موقعي" }, "إحداثيات يدوية", p[0], p[1], "")) }; dialog = ""
            } else { msg = "صيغة غير صحيحة. مثال: 36.75, 3.05"; dialog = "" }
        }, onDismiss = { dialog = "" })
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
        "channels" -> TextInputDialog("قنوات يوتيوب (روابط أو @اسم)", s.channels, hint = "@ibnothaimeentv, @SaudiSunnahTv", onOk = { v ->
            vm.update { setChannels(v) }; dialog = ""
        }, onDismiss = { dialog = "" })
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
        "country" -> ChoiceDialog("اختر الدولة", Countries.list.map { it.code to it.name },
            onPick = { c -> pickedCountry = c; dialog = if (c == "DZ") "wilaya" else "city" }, onDismiss = { dialog = "" })
        "wilaya" -> ChoiceDialog("اختر الولاية", Countries.wilayas.mapIndexed { i, w -> i to "${i + 1} - ${w.name}" },
            onPick = { i -> pickedWilaya = i; dialog = "dzcity" }, onDismiss = { dialog = "" })
        "dzcity" -> {
            val w = Countries.wilayas[pickedWilaya]
            ChoiceDialog("ولاية ${w.name}", listOf("center" to "مركز الولاية — ${w.name}", "search" to "بحث عن بلدية / مدينة / قرية في ولاية ${w.name}", "manual" to "إدخال الإحداثيات يدوياً (دقيق لأي بلدية)"),
                onPick = { k ->
                    if (k == "center") {
                        vm.update { setCity(City(w.name, "ولاية ${w.name}، الجزائر", w.lat, w.lon, "Africa/Algiers")) }
                        dialog = ""
                    } else if (k == "manual") dialog = "manualName" else dialog = "city"
                }, onDismiss = { dialog = "" })
        }
        "manualName" -> TextInputDialog("اسم البلدية (للعرض)", hint = "أولاد بن عبد القادر", onOk = { manualName = it; dialog = "coords" }, onDismiss = { dialog = "" })
        "city" -> TextInputDialog("ابحث عن بلدية أو مدينة في ${Countries.list.first { it.code == pickedCountry }.name}", hint = "اسم البلدية أو المدينة", onOk = { q ->
            scope.launch {
                msg = null
                val area = if (pickedCountry == "DZ") "ولاية " + Countries.wilayas[pickedWilaya].name else null
                val tz = if (pickedCountry == "DZ") "Africa/Algiers" else "UTC"
                cityResults = WeatherRepository.searchPlaces(q, pickedCountry, area, tz)
                dialog = if (cityResults.isEmpty()) { msg = "لا نتائج. جرّب كتابة الاسم بدون «بلدية»، أو بالحروف اللاتينية، أو أدخل الإحداثيات يدوياً."; "" } else "cityPick"
            }
        }, onDismiss = { dialog = "" })
        "cityPick" -> ChoiceDialog("اختر المدينة", cityResults.map { it to (it.name + if (it.country.isNotBlank()) " · ${it.country}" else "") },
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
