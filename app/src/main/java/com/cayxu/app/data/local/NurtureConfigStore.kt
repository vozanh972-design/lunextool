package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Cấu hình cho tính năng "Nuôi tài khoản" - CHỈ lưu lựa chọn cấu hình của người dùng ở đây
 * (SharedPreferences local). KHÔNG chứa logic thực thi tự động nào.
 */
data class NurtureConfig(
    val autoWatch: Boolean = true,
    val autoLike: Boolean = false,
    val autoFollow: Boolean = false,
    val durationMinutes: Int = 10
)

object NurtureConfigStore {
    private const val PREFS_NAME = "cayxu_nurture_config"
    private const val KEY_AUTO_WATCH = "auto_watch"
    private const val KEY_AUTO_LIKE = "auto_like"
    private const val KEY_AUTO_FOLLOW = "auto_follow"
    private const val KEY_DURATION_MINUTES = "duration_minutes"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(context: Context): NurtureConfig {
        val p = prefs(context)
        return NurtureConfig(
            autoWatch = p.getBoolean(KEY_AUTO_WATCH, true),
            autoLike = p.getBoolean(KEY_AUTO_LIKE, false),
            autoFollow = p.getBoolean(KEY_AUTO_FOLLOW, false),
            durationMinutes = p.getInt(KEY_DURATION_MINUTES, 10)
        )
    }

    fun saveConfig(context: Context, config: NurtureConfig) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_WATCH, config.autoWatch)
            .putBoolean(KEY_AUTO_LIKE, config.autoLike)
            .putBoolean(KEY_AUTO_FOLLOW, config.autoFollow)
            .putInt(KEY_DURATION_MINUTES, config.durationMinutes)
            .apply()
    }
}
