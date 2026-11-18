package com.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.tvlauncher.R
import com.tvlauncher.data.License

/** تظهر بدل الشاشة الرئيسية عند انتهاء الفترة التجريبية. */
@Composable
fun LicenseScreen(onActivated: () -> Unit) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp))
        Spacer(Modifier.height(20.dp))
        Text("انتهت الفترة التجريبية", color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("أرسل رمز الجهاز التالي للبائع (واتساب أو تيليغرام) ليرسل لك مفتاح التفعيل", color = Palette.Muted, fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(License.deviceCode(ctx), color = Palette.Amber, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
        if (License.SELLER_CONTACT.isNotBlank()) Text("للتواصل: ${License.SELLER_CONTACT}", color = Palette.Text, fontSize = 22.sp)
        Spacer(Modifier.height(24.dp))
        PillButton("إدخال مفتاح التفعيل", primary = true, onClick = { err = ""; dialog = true })
        if (err.isNotEmpty()) Text(err, color = androidx.compose.ui.graphics.Color(0xFFFFB4A8), fontSize = 20.sp)
    }
    if (dialog) TextInputDialog("مفتاح التفعيل", hint = "XXXX-XXXX-XXXX-XXXX-XXXX",
        onOk = { k -> dialog = false; if (License.activate(ctx, k)) onActivated() else err = "المفتاح غير صحيح لهذا الجهاز أو منتهي" },
        onDismiss = { dialog = false })
}
