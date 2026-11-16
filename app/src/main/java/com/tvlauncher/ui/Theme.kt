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
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
    /** صور مضمّنة: المفتاح ← (الاسم، اسم المورد في drawable). */
    val photos: List<Triple<String, String, String>> = listOf(
        Triple("res:wp_lake", "بحيرة الغروب", "wp_lake"),
        Triple("res:wp_palms", "نخيل وانعكاس", "wp_palms"),
        Triple("res:wp_forest", "غابة الخريف", "wp_forest"),
        Triple("res:wp_canyon", "وادي الثلج", "wp_canyon")
    )
    fun resId(key: String): Int? = when (key) {
        "res:wp_lake" -> com.tvlauncher.R.drawable.wp_lake
        "res:wp_palms" -> com.tvlauncher.R.drawable.wp_palms
        "res:wp_forest" -> com.tvlauncher.R.drawable.wp_forest
        "res:wp_canyon" -> com.tvlauncher.R.drawable.wp_canyon
        else -> null
    }
    /** كل الخلفيات الجاهزة (تدرجات + صور) بمفاتيحها وأسمائها. */
    fun all(): List<Pair<String, String>> =
        presets.mapIndexed { i, p -> "preset:$i" to p.first } + photos.map { it.first to it.second }

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
    // نقيس عرض النافذة الفعلي (لا عرض الشاشة المُبلَّغ) كي لا تظهر أشرطة جانبية
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(320f)
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
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(110), label = "focusScale")
    Box(
        modifier
            .scale(scale)
            // هالة مضيئة حول العنصر المحدَّد
            .then(if (focused) Modifier.shadow(26.dp, shape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
            .clip(shape)
            .background(bg)
            // تأثير زجاجي عند التحديد: طبقة بيضاء شفافة متدرجة + حدّ ناعم
            .then(if (focused) Modifier.background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x40FFFFFF))), shape
            ) else Modifier)
            .border(if (focused) 4.dp else 0.dp, Color.White, shape)
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
