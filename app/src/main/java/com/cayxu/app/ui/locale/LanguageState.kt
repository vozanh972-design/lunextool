package com.cayxu.app.ui.locale

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ngôn ngữ app hỗ trợ. */
enum class AppLanguage { VI, EN }

/**
 * Trạng thái "Ngôn ngữ" toàn app - đọc/ghi SharedPreferences, giống hệt cách ThemeState.kt
 * xử lý "Chế độ tối": 1 nguồn state duy nhất, đổi phát là mọi Composable đang đọc Str.xxx
 * (trong Strings.kt) tự recompose sang ngôn ngữ mới, không cần đụng vào logic từng màn hình.
 *
 * Màn Welcome hiện đã dùng toàn bộ chữ qua Str.xxx nên đổi ngôn ngữ ở đó có hiệu lực ngay.
 * Các màn hình khác (Home, Wallet, Cài đặt...) hiện vẫn đang hardcode tiếng Việt trực tiếp -
 * cần chuyển dần từng màn sang dùng Str.xxx (thêm property mới vào Strings.kt) để áp dụng
 * tiếng Anh cho toàn app.
 */
object LanguageState {
    private const val PREFS_NAME = "cayxu_ui_prefs"
    private const val KEY_LANGUAGE = "app_language"

    var language by mutableStateOf(AppLanguage.VI)
        private set

    /** Gọi 1 lần khi mở app (MainActivity.onCreate) để nạp lựa chọn đã lưu trước đó. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.VI.name)
        language = runCatching { AppLanguage.valueOf(saved ?: AppLanguage.VI.name) }
            .getOrDefault(AppLanguage.VI)
    }

    /** Gọi khi người dùng chọn ngôn ngữ khác ở dropdown (màn Welcome, Cài đặt...). */
    fun setLanguage(context: Context, lang: AppLanguage) {
        language = lang
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, lang.name)
            .apply()
    }
}
