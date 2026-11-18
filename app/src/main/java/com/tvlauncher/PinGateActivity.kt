package com.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.tvlauncher.ui.LauncherTheme
import com.tvlauncher.ui.PinPrompt

/** شاشة الرمز التي تظهر فوق إعدادات النظام. */
class PinGateActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(rtl = true) {
                var err by remember { mutableStateOf<String?>(null) }
                BackHandler { goHome() }
                PinPrompt("إعدادات النظام مقفلة — أدخل الرمز", err, onDismiss = { goHome() }, onPin = { pin ->
                    val r = if (vm.hasPin) vm.checkPin(pin) else null
                    if (r == null) { GuardService.unlockedUntil = System.currentTimeMillis() + 3 * 60_000L; finish() }
                    else err = r
                })
            }
        }
    }

    // إن خرج المستخدم من الشاشة دون إدخال الرمز نعيده للرئيسية بدل ترك الإعدادات مكشوفة
    override fun onStop() {
        super.onStop()
        if (!isFinishing && System.currentTimeMillis() >= GuardService.unlockedUntil) goHome()
    }
}
