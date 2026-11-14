package com.tvlauncher.adhan

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvlauncher.ui.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** شاشة الأذان: اسم الصلاة + عدّ تنازلي لإقامة الصلاة + كتم الصوت. */
class AdhanActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: "الصلاة"
        val time = intent.getLongExtra("time", System.currentTimeMillis())
        val iqamaMin = intent.getIntExtra("iqama", 10)
        val sound = intent.getBooleanExtra("sound", true)

        // ضع ملف الأذان في app/src/main/res/raw/adhan.mp3 ليُشغَّل تلقائياً
        val res = resources.getIdentifier("adhan", "raw", packageName)
        if (sound && res != 0) {
            player = MediaPlayer.create(this, res)?.also { it.start() }
        }

        val iqamaAt = time + iqamaMin * 60_000L
        val hm = SimpleDateFormat("HH:mm", Locale.US).format(Date(time))

        setContent {
            LauncherTheme {
                var muted by remember { mutableStateOf(false) }
                var left by remember { mutableStateOf(((iqamaAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
                LaunchedEffect(Unit) {
                    while (left > 0) {
                        delay(1000)
                        left = ((iqamaAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    }
                    finish()
                }
                val total = (iqamaMin * 60).coerceAtLeast(1)
                Box(Modifier.fillMaxSize().background(Palette.Night), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.width(900.dp).clip(RoundedCornerShape(32.dp)).background(Palette.Surface).padding(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("حان الآن موعد الأذان", color = Palette.Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("أذان $name", color = Palette.Text, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
                        Text(hm, color = Palette.Muted, fontSize = 24.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("الوقت المتبقي لإقامة الصلاة", color = Palette.Muted, fontSize = 20.sp)
                        Text(
                            "%02d:%02d".format(left / 60, left % 60),
                            color = Palette.Amber, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Box(Modifier.width(420.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Track)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(((total - left).toFloat() / total).coerceIn(0f, 1f)).background(Palette.Amber))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            PillButton(if (muted) "الأذان مكتوم" else "كتم الأذان", primary = true) {
                                muted = !muted
                                player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                            }
                            PillButton("إغلاق") { finish() }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release(); player = null
        super.onDestroy()
    }
}
