package com.cayxu.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Trạng thái "Chế độ tối" toàn app - đọc/ghi vào SharedPreferences thường (không nhạy cảm,
 * không cần EncryptedSharedPreferences như key đăng nhập), để nhớ lựa chọn của người dùng
 * giữa các lần mở app.
 */
object ThemeState {
    private const val PREFS_NAME = "cayxu_ui_prefs"
    private const val KEY_DARK_MODE = "dark_mode_enabled"

    var isDarkMode by mutableStateOf(false)
        private set

    /** Gọi 1 lần khi mở app (MainActivity.onCreate) để nạp lựa chọn đã lưu trước đó. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
    }

    /** Gọi khi người dùng gạt công tắc "Chế độ tối" ở màn Cài đặt. */
    fun setDarkMode(context: Context, enabled: Boolean) {
        isDarkMode = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }
}
