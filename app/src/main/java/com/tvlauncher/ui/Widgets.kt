package com.tvlauncher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvlauncher.data.PrayerRepository
import com.tvlauncher.data.Settings
import com.tvlauncher.data.WeatherInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ======================= الطقس =======================

private fun weatherBrush(w: WeatherInfo?): Brush {
    val c = w?.code ?: 0
    val colors = when {
        w == null -> listOf(Color(0xFF334155), Color(0xFF475569))
        c in 95..99 -> listOf(Color(0xFF3B2A5C), Color(0xFF5B4A8A))
        c in 51..67 || c in 80..82 -> listOf(Color(0xFF264653), Color(0xFF3F6C7E))
        c in 71..77 -> listOf(Color(0xFF3F5A7A), Color(0xFF7FA3C7))
        c == 3 || c in 45..48 -> listOf(Color(0xFF3F4A5E), Color(0xFF6B7A90))
        !w.isDay -> listOf(Color(0xFF16204F), Color(0xFF3A3F94))
        else -> listOf(Color(0xFF1F6FB8), Color(0xFF58B2EA))
    }
    return Brush.linearGradient(colors)
}

@Composable
fun WeatherCard(w: WeatherInfo?, city: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(26.dp)).background(weatherBrush(w)).padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        WeatherIcon(w?.code ?: 0, w?.isDay ?: true, 76.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(city, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w?.let { "${it.tempC}°" } ?: "--°", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(w?.description ?: "جارٍ التحديث…", color = Color(0xFFE6EEF9), fontSize = 18.sp)
            if (w != null) Text(
                "↑${w.maxC}°  ↓${w.minC}°  ·  رطوبة ${w.humidity}%  ·  رياح ${w.windKmh} كم/س",
                color = Color(0xFFE6EEF9), fontSize = 15.sp
            )
        }
    }
}

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp) {
    val t = rememberInfiniteTransition(label = "wx")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val drift by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val drop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "drop")

    val sunny = code in 0..1
    val partly = code == 2
    val rain = code in 51..67 || code in 80..82 || code in 95..99
    val snow = code in 71..77
    val cloudy = !sunny

    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension
        if (sunny || partly) {
            val c = if (partly) Offset(u * 0.36f, u * 0.36f) else center
            val r = u * if (partly) 0.17f else 0.22f
            if (isDay) {
                rotateRays(c, r, spin)
                drawCircle(Color(0xFFFFC94D), r, c)
            } else {
                drawCircle(Color(0xFFE8ECF8), r, c)
                drawCircle(Color(0xFF16204F), r * 0.85f, c + Offset(r * 0.5f, -r * 0.2f))
            }
        }
        if (cloudy) cloud(u, drift, if (rain) Color(0xFFD5DEE9) else Color.White)
        if (rain) for (i in 0..2) {
            val x = u * (0.34f + i * 0.14f)
            val y = u * (0.70f + ((drop + i * 0.33f) % 1f) * 0.22f)
            drawLine(Color(0xFF9AD0FF), Offset(x, y), Offset(x - u * 0.03f, y + u * 0.09f), u * 0.035f, StrokeCap.Round)
        }
        if (snow) for (i in 0..2) {
            val x = u * (0.34f + i * 0.14f)
            val y = u * (0.72f + ((drop + i * 0.33f) % 1f) * 0.2f)
            drawCircle(Color.White, u * 0.025f, Offset(x, y))
        }
    }
}

private fun DrawScope.rotateRays(c: Offset, r: Float, angle: Float) {
    for (i in 0 until 8) {
        val a = Math.toRadians((angle + i * 45.0))
        val from = Offset(c.x + (r * 1.35f * Math.cos(a)).toFloat(), c.y + (r * 1.35f * Math.sin(a)).toFloat())
        val to = Offset(c.x + (r * 1.75f * Math.cos(a)).toFloat(), c.y + (r * 1.75f * Math.sin(a)).toFloat())
        drawLine(Color(0xFFFFC94D), from, to, r * 0.16f, StrokeCap.Round)
    }
}

private fun DrawScope.cloud(u: Float, dx: Float, color: Color) {
    val ox = dx
    drawCircle(color, u * 0.17f, Offset(u * 0.42f + ox, u * 0.52f))
    drawCircle(color, u * 0.21f, Offset(u * 0.60f + ox, u * 0.47f))
    drawCircle(color, u * 0.15f, Offset(u * 0.76f + ox, u * 0.55f))
    drawRoundRect(color, Offset(u * 0.25f + ox, u * 0.52f), Size(u * 0.62f, u * 0.2f), androidx.compose.ui.geometry.CornerRadius(u * 0.1f))
}

// ======================= الصلاة =======================

@Composable
fun PrayerCard(s: Settings, now: Date, modifier: Modifier = Modifier) {
    val (prev, next) = remember(s, now.time / 1000) { PrayerRepository.window(s, now) }
    val today = remember(s, now.time / 3_600_000) { PrayerRepository.list(PrayerRepository.forDay(s, 0, now)) }
    val left = ((next.time.time - now.time) / 1000).coerceAtLeast(0)
    val span = (next.time.time - prev.time.time).coerceAtLeast(1)
    val progress = ((now.time - prev.time.time).toFloat() / span).coerceIn(0f, 1f)
    val fmt = remember(s.city.tz) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(s.city.tz) } }

    val pulse by rememberInfiniteTransition(label = "pulse")
        .animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pa")

    Row(
        modifier.clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF123B3A), Color(0xFF1B2F5E), Color(0xFF3A2A66))))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        // حلقة التقدّم نحو الصلاة القادمة
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Color(0x33FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    Brush.sweepGradient(listOf(Color(0xFFFFD27A), Palette.Amber, Color(0xFFFF9F43), Color(0xFFFFD27A))),
                    -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(next.name, color = Palette.Amber, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(fmt.format(next.time), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60),
                    color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                Text("حتى ${next.name}", color = Palette.Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                today.forEach { p ->
                    val isNext = p.prayer == next.prayer
                    val past = !isNext && p.time.before(now)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (isNext) Palette.Amber.copy(alpha = pulse) else Color(if (past) 0x14FFFFFF else 0x26FFFFFF))
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(p.name, color = if (isNext) Palette.AmberText else if (past) Palette.Muted else Color.White, fontSize = 16.sp)
                        Text(fmt.format(p.time), color = if (isNext) Palette.AmberText else if (past) Palette.Muted else Color.White,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
