package com.tvlauncher.ui

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class NetState(val kind: String, val level: Int)   // kind: wifi | eth | none

@Composable
private fun rememberNetState(): NetState {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(readNet(ctx)) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { st = readNet(ctx) }
            override fun onLost(n: Network) { st = readNet(ctx) }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { st = readNet(ctx) }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return st
}

@Suppress("DEPRECATION")
private fun readNet(ctx: Context): NetState = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> NetState("none", 0)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val rssi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.rssi
            NetState("wifi", ((rssi + 100) / 15).coerceIn(0, 3) + 1)
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState("eth", 4)
        else -> NetState("other", 4)
    }
}.getOrDefault(NetState("none", 0))

/** مؤشر الاتصال: واي فاي بقوة الإشارة، أو كابل، أو لا اتصال. */
@Composable
fun NetworkChip() {
    val n = rememberNetState()
    Row(
        Modifier.clip(RoundedCornerShape(40.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(30.dp, 26.dp)) {
            val w = size.width / 7f
            for (i in 0 until 4) {
                val h = size.height * (0.30f + 0.23f * i)
                drawRoundRect(
                    if (i < n.level) Color.White else Color(0x55FFFFFF),
                    Offset(i * w * 1.75f, size.height - h), Size(w, h), CornerRadius(w / 2)
                )
            }
        }
        Text(
            when (n.kind) { "wifi" -> "Wi-Fi"; "eth" -> "كابل"; "other" -> "متصل"; else -> "لا اتصال" },
            color = if (n.kind == "none") Color(0xFFFF8A80) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
    }
}

/** مداخل التلفاز (HDMI وغيرها): يظهر فقط على أجهزة تلفاز أندرويد التي تكشف مداخل. */
@Composable
fun InputsChip(onMessage: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val inputs = remember {
        runCatching {
            val m = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            m.tvInputList.filter { it.isPassthroughInput }
                .map { it.id to (it.loadCustomLabel(ctx)?.toString() ?: it.loadLabel(ctx)?.toString() ?: it.id) }
        }.getOrDefault(emptyList())
    }
    if (inputs.isEmpty()) return
    Focusable(onClick = { open = true }, shape = RoundedCornerShape(40.dp), bg = Color(0x33FFFFFF)) {
        Text("المداخل", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    }
    if (open) ChoiceDialog("مداخل التلفاز", inputs, onDismiss = { open = false }, onPick = { id ->
        open = false
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { onMessage("تعذر فتح المدخل", "لا يسمح الجهاز بفتح هذا المدخل من اللاونشر.") }
    })
}

/** زر التسريع: إيقاف تطبيقات الخلفية وتفريغ الذاكرة. */
@Composable
fun BoostChip(onMessage: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Focusable(onClick = {
        if (!busy) { busy = true; scope.launch {
            val r = com.tvlauncher.data.Booster.boost(ctx); busy = false
            onMessage("تم التسريع", "أُوقفت تطبيقات الخلفية. الذاكرة المتاحة الآن ${r.freeMb} من ${r.totalMb} ميغابايت" +
                if (r.freedMb > 0) " (تحرّر ${r.freedMb} ميغابايت)." else ".")
        } }
    }, shape = RoundedCornerShape(40.dp), bg = Color(0x33FFFFFF)) {
        Text(if (busy) "جارٍ التسريع…" else "تسريع", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    }
}

/** شريط الأخبار المتحرك. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsTicker(headlines: List<String>, moveRight: Boolean, modifier: Modifier = Modifier) {
    if (headlines.isEmpty()) return
    val text = remember(headlines) { headlines.joinToString("   •   ") }
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x55000000)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("أخبار", color = Palette.AmberText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.background(Palette.Amber).padding(horizontal = 22.dp, vertical = 10.dp))
        // اتجاه الحركة يتبع اتجاه التخطيط: Rtl ← نحو اليمين، Ltr ← نحو اليسار
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides
                if (moveRight) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
            Text(text, color = Color.White, fontSize = 22.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp).basicMarquee(iterations = Int.MAX_VALUE, velocity = 55.dp))
        }
    }
}


/** صندوق هدايا يفتح كل فترة ويُخرج اسماً من الأسماء التي اختارها المستخدم. */
@Composable
fun GiftBox(namesRaw: String, modifier: Modifier = Modifier) {
    val names = remember(namesRaw) { namesRaw.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(20) }
    if (names.isEmpty()) return
    val greetings = remember { listOf("مرحباً يا %s", "أهلاً بك يا %s", "نوّرت يا %s", "يوماً سعيداً يا %s", "بارك الله فيك يا %s", "حفظك الله يا %s") }
    var current by remember { mutableStateOf("") }
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    var order by remember(names) { mutableStateOf(names.shuffled()) }
    var idx by remember(names) { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }       // يزيد عند الضغط لفتح الصندوق فوراً

    LaunchedEffect(names, tick) {
        if (tick == 0) kotlinx.coroutines.delay(4000)
        while (true) {
            if (idx >= order.size) { order = names.shuffled(); idx = 0 }
            current = greetings.random().format(order[idx]); idx++
            open.animateTo(1f, androidx.compose.animation.core.tween(700))
            kotlinx.coroutines.delay(6000)
            open.animateTo(0f, androidx.compose.animation.core.tween(500))
            kotlinx.coroutines.delay(14000)
        }
    }
    val o = open.value
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Focusable(onClick = { tick++ }, shape = RoundedCornerShape(20.dp), bg = Color(0x22FFFFFF)) {
            Canvas(Modifier.padding(8.dp).size(76.dp)) {
                val u = size.minDimension
                val body = Color(0xFFE04F5F); val ribbon = Color(0xFFF2B84B)
                drawRoundRect(body, Offset(u * 0.12f, u * 0.44f), Size(u * 0.76f, u * 0.50f), CornerRadius(u * 0.06f))
                drawRect(ribbon, Offset(u * 0.44f, u * 0.44f), Size(u * 0.12f, u * 0.50f))
                // الغطاء يرتفع ويميل عند الفتح
                withTransform({
                    translate(left = u * 0.10f * o, top = -u * 0.22f * o)
                    rotate(-18f * o, pivot = Offset(u * 0.12f, u * 0.44f))
                }) {
                    drawRoundRect(body, Offset(u * 0.06f, u * 0.28f), Size(u * 0.88f, u * 0.18f), CornerRadius(u * 0.05f))
                    drawRect(ribbon, Offset(u * 0.44f, u * 0.28f), Size(u * 0.12f, u * 0.18f))
                    drawCircle(ribbon, u * 0.07f, Offset(u * 0.40f, u * 0.24f))
                    drawCircle(ribbon, u * 0.07f, Offset(u * 0.60f, u * 0.24f))
                }
                // شرارات
                if (o > 0.2f) for (i in 0..3) {
                    val a = Math.toRadians(-60.0 + i * 40.0)
                    val r = u * (0.30f + 0.25f * o)
                    drawCircle(Color(0xFFFFE08A), u * 0.025f, Offset(u * 0.5f + (r * Math.cos(a)).toFloat(), u * 0.35f + (r * Math.sin(a)).toFloat()))
                }
            }
        }
        Text(current, color = Palette.Amber, fontSize = (30 * (0.6f + 0.4f * o)).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = o; scaleX = 0.7f + 0.3f * o; scaleY = 0.7f + 0.3f * o })
    }
}
