package com.cayxu.app.data

import android.content.Context
import com.cayxu.app.worker.AppAlertNotifier
import com.cayxu.app.worker.AppBackgroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cài đặt người dùng, lưu THẬT trên máy (SharedPreferences).
 * Các giá trị mặc định: bật thông báo, tắt chạy ngầm, tắt giữ màn hình sáng.
 * Được đồng bộ trực tiếp với [AppAlertNotifier] và [AppBackgroundService] của hệ thống.
 */
data class AppSettings(
    val push: Boolean = true,
    val background: Boolean = false,
    val keepAwake: Boolean = false,
)

class AppSettingsStore(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("autolunex_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())

    /** Luôn có giá trị mới nhất; màn Cài đặt và các nơi khác (dịch vụ chạy ngầm, Activity) cùng đọc ở đây. */
    val settings: StateFlow<AppSettings> = _settings

    fun setPush(on: Boolean) {
        write { copy(push = on) }
        AppAlertNotifier.setPushEnabled(appContext, on)
    }

    fun setBackground(on: Boolean) {
        write { copy(background = on) }
        if (on) {
            AppBackgroundService.start(appContext)
        } else {
            AppBackgroundService.stop(appContext)
        }
    }

    fun setKeepAwake(on: Boolean) = write { copy(keepAwake = on) }

    private fun read(): AppSettings {
        val push = if (prefs.contains("push")) prefs.getBoolean("push", true) else AppAlertNotifier.isPushEnabled(appContext)
        val background = if (prefs.contains("background")) prefs.getBoolean("background", false) else AppBackgroundService.isEnabled(appContext)
        val keepAwake = prefs.getBoolean("keepAwake", false)
        return AppSettings(push = push, background = background, keepAwake = keepAwake)
    }

    private fun write(change: AppSettings.() -> AppSettings) {
        val next = _settings.value.change()
        prefs.edit()
            .putBoolean("push", next.push)
            .putBoolean("background", next.background)
            .putBoolean("keepAwake", next.keepAwake)
            .apply()
        _settings.value = next
    }
}

/** Tên phiên bản thật của app (versionName trong build.gradle), dùng cho dòng "Phiên bản ứng dụng". */
fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
