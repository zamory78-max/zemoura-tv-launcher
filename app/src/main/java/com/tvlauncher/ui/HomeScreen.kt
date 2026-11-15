package com.tvlauncher.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val LATIN_AR: Locale = Locale.forLanguageTag("ar-u-nu-latn")

/** مقاس وشكل المربعات بحسب إعدادات المستخدم. */
data class TileStyle(val w: Dp, val h: Dp, val shape: Shape, val banner: Boolean)

fun tileStyle(s: Settings): TileStyle {
    val w = listOf(150.dp, 200.dp, 260.dp)[s.tileScale.coerceIn(0, 2)]
    return when (s.tileShape) {
        "rounded" -> TileStyle(w * 0.62f, w * 0.62f, RoundedCornerShape(28.dp), false)
        "square" -> TileStyle(w * 0.62f, w * 0.62f, RoundedCornerShape(6.dp), false)
        "circle" -> TileStyle(w * 0.62f, w * 0.62f, CircleShape, false)
        else -> TileStyle(w, w * 9f / 16f, RoundedCornerShape(18.dp), true)
    }
}

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

/** خلفية موحّدة تحت كل الشاشات (الرئيسية والإعدادات والمجلدات). */
@Composable
fun WallpaperHost(vm: LauncherViewModel, content: @Composable () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        WallpaperBackground(s.wallpaper)
        content()
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
    var editMode by remember { mutableStateOf(false) }          // في وضع التعديل: OK على أي عنصر يفتح قائمته
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }

    val style = tileStyle(s)
    val ordered = remember(apps, s.appOrder) { AppRepository.applyOrder(apps, s.appOrder) }
    val visible = ordered.filter { it.pkg !in s.hiddenApps }
    val supported = visible.filter { it.supported }
    val unsupported = if (s.showUnsupported) visible.filter { !it.supported } else emptyList()
    val videoApps = ordered.filter { it.pkg in s.videoApps }

    fun launch(app: AppEntry) {
        val go = { if (!AppRepository.launch(ctx, app.pkg)) info = "تعذر التشغيل" to "لا يمكن تشغيل ${app.label}" }
        if (app.pkg in s.lockedApps && vm.hasPin) guard("هذا التطبيق مقفل", go) else go()
    }
    val onAppClick: (AppEntry) -> Unit = { if (editMode) menuFor = it else launch(it) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                TopBar(s, now, weather, editMode,
                    onEdit = { editMode = !editMode; moving = null },
                    onSettings = { if (s.protectSettings && vm.hasPin) guard("الإعدادات محمية", onSettings) else onSettings() })
            }
            if (editMode) item {
                Text("وضع التعديل: اضغط OK على أي تطبيق أو مجلد لنقله أو إخفائه أو حذفه. اضغط «تم التعديل» للخروج.",
                    color = Palette.Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (s.showVideos) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("المشاهدة")
                    if (videos.isEmpty() && videoApps.isEmpty()) {
                        Text("اختر تطبيقات المشاهدة من الإعدادات ← التطبيقات والفيديو. وتظهر فيديوهات يوتيوب هنا عند توفر الإنترنت.",
                            color = Palette.Muted, fontSize = 18.sp)
                    } else LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(videoApps, key = { "va:" + it.pkg }) { app -> VideoAppCard(app) { launch(app) } }
                        items(videos, key = { it.id }) { v -> VideoCard(v) { VideoRepository.play(ctx, v) } }
                    }
                }
            }

            item { AppsRow("التطبيقات", supported, s, style, moving, vm::moveApp, { moving = null }, onClick = onAppClick, onLong = { menuFor = it }) }

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
                                    else if (editMode) folderMenu = f
                                    else if (f.locked && vm.hasPin) guard("المجلد مقفل") { onFolder(f.id) }
                                    else if (f.locked) info = "لا يوجد رمز سري" to "عيّن رمزاً سرياً من الإعدادات أولاً."
                                    else onFolder(f.id)
                                },
                                shape = style.shape,
                                modifier = Modifier.size(style.w, style.h)
                                    .moveKeys(isMoving, { vm.moveFolder(f.id, it) }, { moving = null })
                                    .focusRequester(fr)
                            ) {
                                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isMoving) Badge("حرّك بالأسهم ثم OK")
                                    Text(f.name, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${f.items.size} عناصر", color = Palette.Muted, fontSize = 14.sp)
                                    if (f.locked) Badge("مقفل")
                                }
                            }
                        }
                        item {
                            Focusable(onClick = { newFolder = true }, shape = style.shape, modifier = Modifier.size(style.w, style.h)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("+ مجلد جديد", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (unsupported.isNotEmpty()) item {
                AppsRow("تطبيقات أخرى (غير مصممة للتلفاز)", unsupported, s, style, moving, vm::moveApp, { moving = null },
                    onClick = onAppClick, onLong = { menuFor = it })
            }
        }
    }

    menuFor?.let { app ->
        val opts = buildList {
            add("move" to "نقل (تغيير المكان)")
            add("hide" to "إخفاء من الشاشة")
            s.folders.forEach { add("f:${it.id}" to "إضافة إلى مجلد: ${it.name}") }
            add("info" to "معلومات التطبيق / إلغاء التثبيت")
        }
        ChoiceDialog(app.label, opts, onDismiss = { menuFor = null }, onPick = { key ->
            menuFor = null
            when {
                key == "move" -> { editMode = false; moving = "app:${app.pkg}" }
                key == "hide" -> vm.hideApp(app.pkg)
                key == "info" -> AppRepository.openAppSettings(ctx, app.pkg)
                key.startsWith("f:") -> vm.addToFolder(key.removePrefix("f:"), FolderItem(true, app.pkg, app.label))
            }
        })
    }
    folderMenu?.let { f ->
        ChoiceDialog(f.name, listOf("move" to "نقل (تغيير المكان)", "delete" to "حذف المجلد"), onDismiss = { folderMenu = null }, onPick = { k ->
            folderMenu = null
            if (k == "move") { editMode = false; moving = "folder:${f.id}" }
            else if (f.locked && vm.hasPin) guard("المجلد مقفل") { vm.deleteFolder(f.id) } else vm.deleteFolder(f.id)
        })
    }
    if (newFolder) TextInputDialog("اسم المجلد", hint = "مثال: الأطفال", onOk = {
        if (it.isNotBlank()) vm.addFolder(it); newFolder = false
    }, onDismiss = { newFolder = false })
    info?.let { MessageDialog(it.first, it.second) { info = null } }
}

@Composable
private fun TopBar(
    s: Settings, now: Date, weather: WeatherInfo?, editMode: Boolean,
    onEdit: () -> Unit, onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(SimpleDateFormat("HH:mm", Locale.US).format(now), color = Palette.Text, fontSize = 68.sp, fontWeight = FontWeight.ExtraBold)
            Text(SimpleDateFormat("EEEE، d MMMM", LATIN_AR).format(now), color = Palette.Muted, fontSize = 20.sp)
        }
        WeatherCard(weather, s.city.name)
        PrayerCard(s, now, Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(if (editMode) "تم التعديل" else "تعديل", primary = editMode, onClick = onEdit)
            PillButton("الإعدادات", onClick = onSettings)
        }
    }
}

@Composable
private fun AppsRow(
    title: String, list: List<AppEntry>, s: Settings, style: TileStyle,
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
                Column(Modifier.width(maxOf(style.w, 110.dp)), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Focusable(
                        onClick = { if (isMoving) onDone() else onClick(app) },
                        onLongClick = { onLong(app) },
                        shape = style.shape,
                        modifier = Modifier.size(style.w, style.h)
                            .moveKeys(isMoving, { onMove(rowPkgs, app.pkg, it) }, onDone)
                            .focusRequester(fr)
                    ) {
                        val useBanner = style.banner && app.banner != null
                        val bmp = remember(app.pkg, useBanner) {
                            runCatching {
                                if (useBanner) app.banner!!.toBitmap(320, 180).asImageBitmap()
                                else app.icon?.toBitmap(128, 128)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bmp != null) Image(
                            bmp, app.label,
                            contentScale = if (useBanner) ContentScale.Crop else ContentScale.Fit,
                            modifier = if (useBanner) Modifier.fillMaxSize()
                            else Modifier.fillMaxSize().padding(if (style.banner) 18.dp else 12.dp)
                        )
                        if (app.pkg in s.lockedApps) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { Badge("مقفل") }
                        if (isMoving) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Badge("حرّك بالأسهم ثم OK") }
                    }
                    Text(app.label, color = Palette.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun VideoAppCard(app: AppEntry, onClick: () -> Unit) {
    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable(onClick = onClick, modifier = Modifier.size(300.dp, 169.dp)) {
            val bmp = remember(app.pkg) {
                runCatching { (app.banner ?: app.icon)?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
            }
            if (bmp != null) Image(bmp, app.label, contentScale = if (app.banner != null) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(if (app.banner != null) 0.dp else 28.dp))
        }
        Text(app.label, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("افتح التطبيق", color = Palette.Muted, fontSize = 15.sp)
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
