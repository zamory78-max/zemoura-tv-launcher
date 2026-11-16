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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
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

                val loaded by vm.loaded.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(Palette.Night)) {
                    if (!loaded) {
                        // شاشة تحميل بشعار زمورة بدل الشاشة السوداء
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.res.painterResource(R.drawable.logo), "Zemoura Launcher",
                                Modifier.size(160.dp))
                        }
                    } else WallpaperHost(vm) {
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

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !com.tvlauncher.data.RemoteKeys.isReserved(event)) {
            val id = com.tvlauncher.data.RemoteKeys.id(event)
            com.tvlauncher.data.RemoteKeys.capture?.let { it(id, com.tvlauncher.data.RemoteKeys.describe(event)); return true }
            val st = vm.settings.value
            val pkg = com.tvlauncher.data.RemoteKeys.parse(st.keyMap)[id]
            if (pkg != null && (pkg !in st.lockedApps || !vm.hasPin) && com.tvlauncher.data.AppRepository.launch(this, pkg)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshVideos()
        vm.refreshApps()   // لالتقاط التطبيقات المثبتة/المحذوفة حديثاً
    }

    /** الضغط على زر الصفحة الرئيسية يعيدنا للشاشة الرئيسية. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.Home
    }
}
