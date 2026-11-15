package com.tvlauncher.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tvlauncher.LauncherViewModel
import com.tvlauncher.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val LATIN_AR: Locale = Locale.forLanguageTag("ar-u-nu-latn")

@Composable
fun WallpaperBackground(value: String) {
    if (value.startsWith("uri:")) {
        AsyncImage(
            model = Uri.parse(value.removePrefix("uri:")), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))
    } else {
        val i = value.removePrefix("preset:").toIntOrNull() ?: 0
        Box(Modifier.fillMaxSize().background(Wallpapers.presets[i.coerceIn(0, Wallpapers.presets.lastIndex)].second))
    }
}

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onSettings: () -> Unit,
    onFolder: (String) -> Unit,
    guard: (String, () -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000) } }

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }   // "app:<pkg>" أو "folder:<id>"
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }

    val ordered = remember(apps, s.appOrder) { AppRepository.applyOrder(apps, s.appOrder) }
    val visible = ordered.filter { it.pkg !in s.hiddenApps }
    val supported = visible.filter { it.supported }
    val unsupported = if (s.showUnsupported) visible.filter { !it.supported } else emptyList()

    fun launch(app: AppEntry) {
        val go = { if (!AppRepository.launch(ctx, app.pkg)) info = "تعذر التشغيل" to "لا يمكن تشغيل ${app.label}" }
        if (app.pkg in s.lockedApps && vm.hasPin) guard("هذا التطبيق مقفل", go) else go()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item { TopBar(s, now, weather, onSettings = {
                if (s.protectSettings && vm.hasPin) guard("الإعدادات محمية", onSettings) else onSettings()
            }) }

            if (s.showVideos) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("فيديوهات يوتيوب")
                    if (videos.isEmpty()) {
                        Text("لعرض الفيديوهات: أضف مفتاح YouTube Data API من الإعدادات ← التطبيقات والفيديو.",
                            color = Palette.Muted, fontSize = 18.sp)
                    } else LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(videos, key = { it.id }) { v -> VideoCard(v) { VideoRepository.play(ctx, v) } }
                    }
                }
            }

            item { AppsRow("التطبيقات", supported, s, moving, vm::moveApp, { moving = null }, onClick = ::launch, onLong = { menuFor = it }) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("المجلدات")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(s.folders, key = { it.id }) { f ->
                            val isMoving = moving == "folder:${f.id}"
                            val fr = remember { FocusRequester() }
                            val pos = s.folders.indexOf(f)
                            LaunchedEffect(isMoving, pos) { if (isMoving) runCatching { fr.requestFocus() } }
                            Focusable(
                                onLongClick = { folderMenu = f },
                                onClick = {
                                    if (isMoving) moving = null
                                    else if (f.locked && vm.hasPin) guard("المجلد مقفل") { onFolder(f.id) }
                                    else if (f.locked) info = "لا يوجد رمز سري" to "عيّن رمزاً سرياً من الإعدادات أولاً."
                                    else onFolder(f.id)
                                },
                                modifier = Modifier.size(200.dp, 112.dp)
                                    .moveKeys(isMoving, { vm.moveFolder(f.id, it) }, { moving = null })
                                    .focusRequester(fr)
                            ) {
                                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isMoving) Badge("حرّك بالأسهم ثم OK")
                                    Text(f.name, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${f.items.size} عناصر", color = Palette.Muted, fontSize = 16.sp)
                                    if (f.locked) Badge("مقفل")
                                }
                            }
                        }
                        item {
                            Focusable(onClick = { newFolder = true }, modifier = Modifier.size(200.dp, 112.dp)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("+ مجلد جديد", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (unsupported.isNotEmpty()) item {
                AppsRow("تطبيقات غير مدعومة للتلفاز (قد لا تعمل بالريموت)", unsupported, s, moving, vm::moveApp, { moving = null }, onClick = ::launch, onLong = { menuFor = it })
            }
        }
    }

    menuFor?.let { app ->
        val opts = buildList {
            add("lock" to if (app.pkg in s.lockedApps) "إلغاء قفل التطبيق" else "قفل التطبيق بالرمز")
            add("move" to "نقل (تغيير المكان)")
            add("hide" to "إخفاء من الشاشة")
            s.folders.forEach { add("f:${it.id}" to "إضافة إلى مجلد: ${it.name}") }
            add("info" to "معلومات التطبيق")
        }
        ChoiceDialog(app.label, opts, onDismiss = { menuFor = null }, onPick = { key ->
            menuFor = null
            when {
                key == "lock" -> {
                    if (!vm.hasPin) info = "لا يوجد رمز سري" to "عيّن رمزاً سرياً من الإعدادات أولاً."
                    else guard("تأكيد الرمز") { vm.toggleAppLock(app.pkg) }
                }
                key == "move" -> moving = "app:${app.pkg}"
                key == "hide" -> vm.hideApp(app.pkg)
                key == "info" -> AppRepository.openAppSettings(ctx, app.pkg)
                key.startsWith("f:") -> vm.addToFolder(key.removePrefix("f:"), FolderItem(true, app.pkg, app.label))
            }
        })
    }
    folderMenu?.let { f ->
        ChoiceDialog(f.name, listOf("move" to "نقل (تغيير المكان)", "delete" to "حذف المجلد"), onDismiss = { folderMenu = null }, onPick = { k ->
            folderMenu = null
            if (k == "move") moving = "folder:${f.id}"
            else if (f.locked && vm.hasPin) guard("المجلد مقفل") { vm.deleteFolder(f.id) } else vm.deleteFolder(f.id)
        })
    }
    if (newFolder) TextInputDialog("اسم المجلد", hint = "مثال: الأطفال", onOk = {
        if (it.isNotBlank()) vm.addFolder(it); newFolder = false
    }, onDismiss = { newFolder = false })
    info?.let { MessageDialog(it.first, it.second) { info = null } }
}

@Composable
private fun TopBar(s: Settings, now: Date, weather: WeatherInfo?, onSettings: () -> Unit) {
    val tzFmt = { p: String -> SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone(s.city.tz) } }
    val next = remember(s, now.time / 1000) { PrayerRepository.next(s, now) }
    val today = remember(s, now.time / 3_600_000) { PrayerRepository.list(PrayerRepository.forDay(s, 0, now)) }
    val left = ((next.time.time - now.time) / 1000).coerceAtLeast(0)
    val hm = tzFmt("HH:mm")

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(SimpleDateFormat("HH:mm", Locale.US).format(now), color = Palette.Text, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
            Text(SimpleDateFormat("EEEE، d MMMM", LATIN_AR).format(now), color = Palette.Muted, fontSize = 20.sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Card).padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weather?.let { "${it.tempC}°" } ?: "--°", color = Palette.Text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Column {
                Text(s.city.name, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(weather?.description ?: "…", color = Palette.Muted, fontSize = 18.sp)
            }
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Palette.Card).padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("الصلاة القادمة: ${next.name}", color = Palette.Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60),
                    color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                today.forEach { p ->
                    val isNext = p.prayer == next.prayer
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (isNext) Palette.Amber else Color.Transparent).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(p.name, color = if (isNext) Palette.AmberText else Palette.Muted, fontSize = 16.sp)
                        Text(hm.format(p.time), color = if (isNext) Palette.AmberText else Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        PillButton("الإعدادات", onClick = onSettings)
    }
}

@Composable
private fun AppsRow(
    title: String, list: List<AppEntry>, s: Settings,
    moving: String?, onMove: (List<String>, String, Int) -> Unit, onDone: () -> Unit,
    onClick: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit
) {
    val rowPkgs = list.map { it.pkg }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(list, key = { it.pkg }) { app ->
                val isMoving = moving == "app:${app.pkg}"
                val fr = remember { FocusRequester() }
                LaunchedEffect(isMoving, rowPkgs.indexOf(app.pkg)) { if (isMoving) runCatching { fr.requestFocus() } }
                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Focusable(
                        onClick = { if (isMoving) onDone() else onClick(app) },
                        onLongClick = { onLong(app) },
                        modifier = Modifier.size(200.dp, 112.dp)
                            .moveKeys(isMoving, { onMove(rowPkgs, app.pkg, it) }, onDone)
                            .focusRequester(fr)
                    ) {
                        if (isMoving) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Badge("حرّك بالأسهم ثم OK") }
                        val bmp = remember(app.pkg) {
                            runCatching {
                                if (app.banner != null) app.banner.toBitmap(320, 180).asImageBitmap()
                                else app.icon?.toBitmap(96, 96)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bmp != null) Image(
                            bmp, app.label,
                            contentScale = if (app.banner != null) ContentScale.Crop else ContentScale.Fit,
                            modifier = if (app.banner != null) Modifier.fillMaxSize() else Modifier.align(Alignment.Center).size(72.dp)
                        )
                        if (app.pkg in s.lockedApps) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { Badge("مقفل") }
                        if (!app.supported) Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) { Badge("غير مدعوم") }
                    }
                    Text(app.label, color = Palette.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun VideoCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable(onClick = onClick, modifier = Modifier.size(300.dp, 169.dp)) {
            AsyncImage(model = v.thumb, contentDescription = v.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (v.duration.isNotEmpty()) Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(v.duration, color = Color.White, fontSize = 14.sp) }
        }
        Text(v.title, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(v.channel, color = Palette.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** خلفية موحّدة تحت كل الشاشات (الرئيسية والإعدادات والمجلدات). */
@Composable
fun WallpaperHost(vm: LauncherViewModel, content: @Composable () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        WallpaperBackground(s.wallpaper)
        content()
    }
}

/**
 * وضع النقل: السهم الأيسر/الأيمن يحرّك العنصر، وزر الرجوع ينهي النقل.
 * الواجهة من اليمين لليسار، لذا اليسار = العنصر التالي (+1) واليمين = السابق (-1).
 */
fun Modifier.moveKeys(active: Boolean, onMove: (Int) -> Unit, onDone: () -> Unit): Modifier =
    if (!active) this else this.onPreviewKeyEvent { e ->
        val handled = e.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Back, Key.Escape)
        if (handled && e.type == KeyEventType.KeyDown) {
            when (e.key) {
                Key.DirectionLeft -> onMove(+1)
                Key.DirectionRight -> onMove(-1)
                Key.Back, Key.Escape -> onDone()
                else -> {}
            }
        }
        handled
    }
