package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Cấu hình cho tính năng "Nuôi tài khoản" - CHỈ lưu lựa chọn cấu hình của người dùng ở đây
 * (SharedPreferences local). KHÔNG chứa logic thực thi tự động nào.
 */
data class NurtureConfig(
    val autoWatch: Boolean = true,
    val viewComments: Boolean = false,
    val copyLink: Boolean = false,
    val repost: Boolean = false,
    val durationMinutes: Int = 15
)

object NurtureConfigStore {
    private const val PREFS_NAME = "cayxu_nurture_config"
    private const val KEY_AUTO_WATCH = "auto_watch"
    private const val KEY_VIEW_COMMENTS = "view_comments"
    private const val KEY_COPY_LINK = "copy_link"
    private const val KEY_REPOST = "repost"
    private const val KEY_DURATION_MINUTES = "duration_minutes"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(context: Context): NurtureConfig {
        val p = prefs(context)
        return NurtureConfig(
            autoWatch = p.getBoolean(KEY_AUTO_WATCH, true),
            viewComments = p.getBoolean(KEY_VIEW_COMMENTS, false),
            copyLink = p.getBoolean(KEY_COPY_LINK, false),
            repost = p.getBoolean(KEY_REPOST, false),
            durationMinutes = p.getInt(KEY_DURATION_MINUTES, 15)
        )
    }

    fun saveConfig(context: Context, config: NurtureConfig) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_WATCH, config.autoWatch)
            .putBoolean(KEY_VIEW_COMMENTS, config.viewComments)
            .putBoolean(KEY_COPY_LINK, config.copyLink)
            .putBoolean(KEY_REPOST, config.repost)
            .putInt(KEY_DURATION_MINUTES, config.durationMinutes)
            .apply()
    }
}
