package com.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Modifier
import com.tvlauncher.ui.*

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Folder(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ملء الشاشة بالكامل وإخفاء أي أشرطة نظام
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val st by vm.settings.collectAsStateWithLifecycle()
            LauncherTheme(rtl = st.layoutRtl) {
                var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
                var pinError by remember { mutableStateOf<String?>(null) }

                // يطلب الرمز (إن وُجد) قبل تنفيذ الإجراء المحمي
                val guard: (String, () -> Unit) -> Unit = { title, action ->
                    if (vm.hasPin) { pinError = null; pending = title to action } else action()
                }

                // زر الرجوع: من أي شاشة فرعية إلى الرئيسية، وفي الرئيسية لا يفعل شيئاً (لاونشر)
                BackHandler { screen = Screen.Home }

                Box(Modifier.fillMaxSize().background(Palette.Night)) {
                    WallpaperHost(vm) {
                        when (val sc = screen) {
                            Screen.Home -> HomeScreen(vm, onSettings = { screen = Screen.Settings },
                                onFolder = { screen = Screen.Folder(it) }, guard = guard)
                            Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Home }, guard = guard)
                            is Screen.Folder -> FolderScreen(vm, sc.id, onBack = { screen = Screen.Home }, guard = guard)
                        }
                    }
                }

                pending?.let { (title, action) ->
                    PinPrompt(title, pinError, onDismiss = { pending = null }, onPin = { pin ->
                        val err = vm.checkPin(pin)
                        if (err == null) { pending = null; action() } else pinError = err
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()   // لالتقاط التطبيقات المثبتة/المحذوفة حديثاً
    }

    /** الضغط على زر الصفحة الرئيسية يعيدنا للشاشة الرئيسية. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.Home
    }
}
