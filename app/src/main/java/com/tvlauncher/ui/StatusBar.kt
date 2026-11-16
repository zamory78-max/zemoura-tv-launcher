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
fun NewsTicker(headlines: List<String>, modifier: Modifier = Modifier) {
    if (headlines.isEmpty()) return
    val text = remember(headlines) { headlines.joinToString("   •   ") }
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x55000000)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("أخبار", color = Palette.AmberText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.background(Palette.Amber).padding(horizontal = 22.dp, vertical = 10.dp))
        Text(text, color = Color.White, fontSize = 22.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp).basicMarquee(iterations = Int.MAX_VALUE, velocity = 55.dp))
    }
}
