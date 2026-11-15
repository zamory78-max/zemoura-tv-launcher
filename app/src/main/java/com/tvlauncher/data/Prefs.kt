package com.tvlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("launcher_prefs")

data class Settings(
    val wallpaper: String = "preset:0",          // preset:N أو uri:...
    val showUnsupported: Boolean = true,          // إظهار التطبيقات غير المدعومة
    val videoRegion: String = "DZ",
    val youtubeKey: String = "",
    val showVideos: Boolean = true,
    val lockedApps: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
    val pinHash: String = "",
    val pinSalt: String = "",
    val protectSettings: Boolean = true,
    val autoStart: Boolean = true,
    val city: City = City.DEFAULT,
    val method: String = "ALGERIA",
    val madhab: String = "SHAFI",
    val iqamaMinutes: Int = 10,
    val adhanEnabled: Boolean = true,
    val adhanSound: Boolean = true,
    val tileScale: Int = 1,                       // 0 صغير، 1 متوسط، 2 كبير
    val tileShape: String = "banner",            // banner | rounded | square | circle
    val videoApps: Set<String> = emptySet(),      // تطبيقات صف المشاهدة
    val pipedInstance: String = "https://pipedapi.kavin.rocks",
    val appOrder: List<String> = emptyList(),     // ترتيب التطبيقات (أسماء الحزم)
    val folders: List<Folder> = emptyList()
)

class Prefs(private val context: Context) {
    private object K {
        val wallpaper = stringPreferencesKey("wallpaper")
        val showUnsupported = booleanPreferencesKey("show_unsupported")
        val videoRegion = stringPreferencesKey("video_region")
        val youtubeKey = stringPreferencesKey("youtube_key")
        val showVideos = booleanPreferencesKey("show_videos")
        val lockedApps = stringSetPreferencesKey("locked_apps")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val protectSettings = booleanPreferencesKey("protect_settings")
        val autoStart = booleanPreferencesKey("auto_start")
        val city = stringPreferencesKey("city")
        val method = stringPreferencesKey("method")
        val madhab = stringPreferencesKey("madhab")
        val iqama = intPreferencesKey("iqama")
        val adhan = booleanPreferencesKey("adhan")
        val adhanSound = booleanPreferencesKey("adhan_sound")
        val folders = stringPreferencesKey("folders")
        val appOrder = stringPreferencesKey("app_order")
        val tileScale = intPreferencesKey("tile_scale")
        val tileShape = stringPreferencesKey("tile_shape")
        val videoApps = stringSetPreferencesKey("video_apps")
        val piped = stringPreferencesKey("piped")
    }

    val flow: Flow<Settings> = context.store.data.map { p ->
        val d = Settings()
        Settings(
            wallpaper = p[K.wallpaper] ?: d.wallpaper,
            showUnsupported = p[K.showUnsupported] ?: d.showUnsupported,
            videoRegion = p[K.videoRegion] ?: d.videoRegion,
            youtubeKey = p[K.youtubeKey] ?: d.youtubeKey,
            showVideos = p[K.showVideos] ?: d.showVideos,
            lockedApps = p[K.lockedApps] ?: d.lockedApps,
            hiddenApps = p[K.hiddenApps] ?: d.hiddenApps,
            pinHash = p[K.pinHash] ?: "",
            pinSalt = p[K.pinSalt] ?: "",
            protectSettings = p[K.protectSettings] ?: d.protectSettings,
            autoStart = p[K.autoStart] ?: d.autoStart,
            city = City.fromJson(p[K.city]),
            method = p[K.method] ?: d.method,
            madhab = p[K.madhab] ?: d.madhab,
            iqamaMinutes = p[K.iqama] ?: d.iqamaMinutes,
            adhanEnabled = p[K.adhan] ?: d.adhanEnabled,
            adhanSound = p[K.adhanSound] ?: d.adhanSound,
            tileScale = p[K.tileScale] ?: d.tileScale,
            tileShape = p[K.tileShape] ?: d.tileShape,
            videoApps = p[K.videoApps] ?: d.videoApps,
            pipedInstance = p[K.piped] ?: d.pipedInstance,
            appOrder = (p[K.appOrder] ?: "").split("\n").filter { it.isNotEmpty() },
            folders = foldersFromJson(p[K.folders])
        )
    }

    suspend fun setWallpaper(v: String) = edit { it[K.wallpaper] = v }
    suspend fun setShowUnsupported(v: Boolean) = edit { it[K.showUnsupported] = v }
    suspend fun setShowVideos(v: Boolean) = edit { it[K.showVideos] = v }
    suspend fun setVideoRegion(v: String) = edit { it[K.videoRegion] = v }
    suspend fun setYoutubeKey(v: String) = edit { it[K.youtubeKey] = v }
    suspend fun setLockedApps(v: Set<String>) = edit { it[K.lockedApps] = v }
    suspend fun setHiddenApps(v: Set<String>) = edit { it[K.hiddenApps] = v }
    suspend fun setPin(hash: String, salt: String) = edit { it[K.pinHash] = hash; it[K.pinSalt] = salt }
    suspend fun setProtectSettings(v: Boolean) = edit { it[K.protectSettings] = v }
    suspend fun setAutoStart(v: Boolean) = edit { it[K.autoStart] = v }
    suspend fun setCity(c: City) = edit { it[K.city] = c.toJson() }
    suspend fun setMethod(v: String) = edit { it[K.method] = v }
    suspend fun setMadhab(v: String) = edit { it[K.madhab] = v }
    suspend fun setIqama(v: Int) = edit { it[K.iqama] = v }
    suspend fun setAdhan(v: Boolean) = edit { it[K.adhan] = v }
    suspend fun setAdhanSound(v: Boolean) = edit { it[K.adhanSound] = v }
    suspend fun setTileScale(v: Int) = edit { it[K.tileScale] = v }
    suspend fun setTileShape(v: String) = edit { it[K.tileShape] = v }
    suspend fun setVideoApps(v: Set<String>) = edit { it[K.videoApps] = v }
    suspend fun setPiped(v: String) = edit { it[K.piped] = v }
    suspend fun setAppOrder(v: List<String>) = edit { it[K.appOrder] = v.joinToString("\n") }
    suspend fun setFolders(v: List<Folder>) = edit { it[K.folders] = foldersToJson(v) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }
}
