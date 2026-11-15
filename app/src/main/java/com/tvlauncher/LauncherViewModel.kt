package com.tvlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvlauncher.adhan.AdhanScheduler
import com.tvlauncher.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    val settings: StateFlow<Settings> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private var failCount = 0
    private var lockedUntil = 0L

    init {
        refreshApps()
        // الطقس: تحديث عند تغيّر المدينة ثم كل 30 دقيقة
        viewModelScope.launch {
            settings.map { it.city }.distinctUntilChanged().collectLatest { city ->
                while (true) {
                    _weather.value = WeatherRepository.current(city)
                    delay(30 * 60_000L)
                }
            }
        }
        // الفيديوهات: تحديث عند تغيّر المفتاح/البلد/التفعيل
        viewModelScope.launch {
            settings.map { Triple(it.showVideos, it.youtubeKey, it.videoRegion) }.distinctUntilChanged()
                .collectLatest { (on, key, region) ->
                    while (true) {
                        _videos.value = if (on) VideoRepository.trending(key, region) else emptyList()
                        delay(60 * 60_000L)
                    }
                }
        }
        // إعادة جدولة الأذان عند تغيّر أي إعداد متعلق بالصلاة
        viewModelScope.launch {
            settings.map { listOf(it.city, it.method, it.madhab, it.adhanEnabled, it.iqamaMinutes) }
                .distinctUntilChanged().collect { AdhanScheduler.scheduleNext(getApplication(), settings.value) }
        }
    }

    fun refreshApps() = viewModelScope.launch { _apps.value = AppRepository.load(getApplication()) }

    // ---------- الرمز السري ----------
    val hasPin get() = settings.value.pinHash.isNotEmpty()

    /** يعيد null عند النجاح، أو رسالة الخطأ. فيه تأخير بعد 5 محاولات خاطئة. */
    fun checkPin(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return "محاولات كثيرة، انتظر ${(lockedUntil - now) / 1000 + 1} ثانية"
        val s = settings.value
        return if (Security.verify(pin, s.pinSalt, s.pinHash)) { failCount = 0; null }
        else {
            failCount++
            if (failCount >= 5) { lockedUntil = now + 30_000; failCount = 0 }
            "الرمز غير صحيح"
        }
    }

    fun setPin(pin: String) = viewModelScope.launch {
        val salt = Security.newSalt()
        prefs.setPin(Security.hash(pin, salt), salt)
    }

    // ---------- الإعدادات ----------
    fun update(block: suspend Prefs.() -> Unit) = viewModelScope.launch { prefs.block() }

    fun toggleAppLock(pkg: String) = update {
        val cur = settings.value.lockedApps
        setLockedApps(if (pkg in cur) cur - pkg else cur + pkg)
    }

    fun hideApp(pkg: String) = update { setHiddenApps(settings.value.hiddenApps + pkg) }

    // ---------- إعادة الترتيب ----------
    /** ينقل تطبيقاً خطوة داخل صفّه المعروض (rowPkgs = ترتيب الصف الحالي). */
    fun moveApp(rowPkgs: List<String>, pkg: String, delta: Int) = update {
        val i = rowPkgs.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in rowPkgs.indices) return@update
        val full = AppRepository.applyOrder(_apps.value, settings.value.appOrder).map { it.pkg }.toMutableList()
        val a = full.indexOf(pkg); val b = full.indexOf(rowPkgs[j])
        if (a < 0 || b < 0) return@update
        full[a] = rowPkgs[j]; full[b] = pkg
        setAppOrder(full)
    }

    fun moveFolder(id: String, delta: Int) = update {
        val list = settings.value.folders.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@update
        val t = list[i]; list[i] = list[j]; list[j] = t
        setFolders(list)
    }

    fun deleteFolder(id: String) = update { setFolders(settings.value.folders.filter { it.id != id }) }

    // ---------- المجلدات ----------
    fun addFolder(name: String) = update {
        setFolders(settings.value.folders + Folder(UUID.randomUUID().toString(), name, false, emptyList()))
    }

    fun mutateFolder(id: String, f: (Folder) -> Folder?) = update {
        setFolders(settings.value.folders.mapNotNull { if (it.id == id) f(it) else it })
    }

    fun addToFolder(id: String, item: FolderItem) = mutateFolder(id) { f ->
        if (f.items.any { it.ref == item.ref }) f else f.copy(items = f.items + item)
    }
}
