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
    private val _videoError = MutableStateFlow("")
    val videoError: StateFlow<String> = _videoError
    private val _news = MutableStateFlow<List<String>>(emptyList())
    val news: StateFlow<List<String>> = _news

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private var failCount = 0
    private var lockedUntil = 0L

    init {
        refreshApps()
        // الأخبار: تحديث كل 20 دقيقة أو عند تغيير الرابط/التفعيل
        viewModelScope.launch {
            settings.map { it.showNews to it.newsUrl }.distinctUntilChanged().collectLatest { (on, url) ->
                if (!on || url.isBlank()) { _news.value = emptyList(); return@collectLatest }
                while (true) {
                    val h = NewsRepository.headlines(url)
                    if (h.isNotEmpty()) _news.value = h
                    delay(20 * 60_000L)
                }
            }
        }
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
            settings.map { listOf(it.showVideos, it.youtubeKey, it.videoRegion, it.pipedInstance, it.feedUrl, it.channels) }.distinctUntilChanged()
                .collectLatest { v ->
                    val on = v[0] as Boolean; val key = v[1] as String; val region = v[2] as String; val piped = v[3] as String; val feed = v[4] as String; val channels = v[5] as String
                    while (true) {
                        VideoRepository.lastError = ""
                        _videos.value = if (on) VideoRepository.all(key, region, piped, feed, channels) else emptyList()
                        _videoError.value = if (_videos.value.isEmpty() && on) VideoRepository.lastError.ifBlank { "لا اتصال أو لا توجد فيديوهات" } else ""
                        // إن فشل الجلب (مثلاً الشبكة لم تجهز عند الإقلاع) أعد المحاولة بعد 20 ثانية
                        delay(if (_videos.value.isEmpty()) 20_000L else 60 * 60_000L)
                    }
                }
        }
        // إعادة جدولة الأذان عند تغيّر أي إعداد متعلق بالصلاة
        viewModelScope.launch {
            settings.map { listOf(it.city, it.method, it.madhab, it.adhanEnabled, it.iqamaMinutes) }
                .distinctUntilChanged().collect { AdhanScheduler.scheduleNext(getApplication(), settings.value) }
        }
    }

    fun refreshApps() = viewModelScope.launch {
        val fresh = AppRepository.load(getApplication())
        // لا نُعيد رسم الشاشة إن لم تتغير قائمة التطبيقات (يمنع التقطيع عند كل عودة)
        if (fresh.map { it.pkg to it.label } != _apps.value.map { it.pkg to it.label }) _apps.value = fresh
        _loaded.value = true
    }

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

    // ---------- الصفوف المخصصة ----------
    private fun rows() = settings.value.rows

    fun addRow(name: String) = update { setRows(rows() + CustomRow(UUID.randomUUID().toString(), name, emptyList())) }
    fun renameRow(id: String, name: String) = update { setRows(rows().map { if (it.id == id) it.copy(name = name) else it }) }
    fun deleteRow(id: String) = update { setRows(rows().filter { it.id != id }) }

    fun moveRow(id: String, delta: Int) = update {
        val l = rows().toMutableList()
        val i = l.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in l.indices) return@update
        val t = l[i]; l[i] = l[j]; l[j] = t
        setRows(l)
    }

    /** يضيف/يزيل تطبيقاً من صف؛ والإضافة تنقله من أي صف آخر. */
    fun toggleRowApp(id: String, pkg: String) = update {
        setRows(rows().map { r ->
            when {
                r.id == id && pkg in r.pkgs -> r.copy(pkgs = r.pkgs - pkg)
                r.id == id -> r.copy(pkgs = r.pkgs + pkg)
                else -> r.copy(pkgs = r.pkgs - pkg)
            }
        })
    }

    fun removeFromRows(pkg: String) = update { setRows(rows().map { it.copy(pkgs = it.pkgs - pkg) }) }

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
