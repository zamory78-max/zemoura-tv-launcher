package com.tvlauncher.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Night = Color(0xFF0B1220)
    val Surface = Color(0xFF10294A)
    val Card = Color(0x24FFFFFF)
    val Amber = Color(0xFFF2B84B)
    val AmberText = Color(0xFF1A1306)
    val Text = Color(0xFFF4F6FA)
    val Muted = Color(0xFFC9D2E3)
    val Teal = Color(0xFF2E9E8A)
    val Track = Color(0xFF5B6578)
}

/** الخلفيات الجاهزة (تُستبدل بصورة من الجهاز عند اختيارها). */
object Wallpapers {
    val presets: List<Pair<String, Brush>> = listOf(
        "ليل" to Brush.linearGradient(listOf(Color(0xFF0B1220), Color(0xFF14284B), Color(0xFF1B3B5A))),
        "غروب" to Brush.linearGradient(listOf(Color(0xFF1A1033), Color(0xFF4A2149), Color(0xFF8A3F3A))),
        "غابة" to Brush.linearGradient(listOf(Color(0xFF071A18), Color(0xFF0F3B35), Color(0xFF1E5A4C))),
        "رمال" to Brush.linearGradient(listOf(Color(0xFF2A2118), Color(0xFF4B3A27), Color(0xFF6E5235)))
    )
}

@Composable
fun LauncherTheme(rtl: Boolean = true, content: @Composable () -> Unit) {
    // تصميم الواجهة مبني على عرض 1600dp؛ نُكبّره/نصغّره ليملأ أي شاشة (720p/1080p/4K) بنفس المظهر
    val widthPx = LocalContext.current.resources.displayMetrics.widthPixels
    val scaled = Density(density = widthPx / 1600f, fontScale = 1f)
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides scaled
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = Palette.Amber, background = Palette.Night, surface = Palette.Surface),
            content = content
        )
    }
}

private class KeyState { var down = false; var long = false }

/**
 * عنصر قابل للتركيز بالريموت: حلقة كهرمانية وتكبير عند التركيز.
 * - OK: نقر. OK مطوّلاً أو زر القائمة (Menu): ضغطة مطوّلة (onLongClick).
 * - اللمس/الماوس: نقر وضغطة مطوّلة.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    bg: Color = Palette.Card,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val st = remember { KeyState() }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(if (focused) 4.dp else 0.dp, Palette.Amber, shape)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount == 0) { st.down = true; st.long = false }
                        else if (st.down && !st.long && long != null) { st.long = true; long?.invoke() }
                    } else if (e.type == KeyEventType.KeyUp) {
                        val fire = st.down && !st.long
                        st.down = false; st.long = false
                        if (fire) click()
                    }
                    true
                } else if (e.key == Key.Menu && long != null) {
                    if (e.type == KeyEventType.KeyUp) long?.invoke()
                    true
                } else false
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { click() }, onLongPress = { long?.invoke() })
            },
        content = content
    )
}

@Composable
fun PillButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(40.dp),
        bg = if (primary) Palette.Amber else Palette.Card
    ) {
        Text(
            text,
            color = if (primary) Palette.AmberText else Palette.Text,
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun Badge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Amber)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Palette.AmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
