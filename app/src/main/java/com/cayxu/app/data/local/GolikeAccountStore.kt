package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu THẬT phiên đăng nhập Golike (token Bearer + tên/handle/email/coin/thu nhập hôm nay
 * theo từng nền tảng - lấy từ GET /api/users/me và GET /api/statistics/report) xuống
 * SharedPreferences riêng - còn nguyên sau khi tắt/mở lại app. KHÔNG lưu mật khẩu Golike vì
 * app không đăng nhập bằng mật khẩu, chỉ dùng token do người dùng tự dán vào.
 */
object GolikeAccountStore {
    private const val PREFS_NAME = "cayxu_golike_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"
    private const val KEY_HANDLE = "handle"
    private const val KEY_EMAIL = "email"
    private const val KEY_COIN = "coin"
    private const val KEY_TODAY_INCOME = "today_income"
    private const val KEY_PLATFORM_STATS = "platform_stats"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLogin(
        context: Context,
        token: String,
        name: String,
        handle: String,
        email: String,
        coin: String
    ) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .putString(KEY_HANDLE, handle)
            .putString(KEY_EMAIL, email)
            .putString(KEY_COIN, coin)
            .apply()
    }

    /** [platformStatsSerialized]: chuỗi dạng "facebook:1529:0|tiktok:50:0|..." (nền
     *  tảng:pending_coin:hold_coin), xem GolikeSession để biết cách mã hoá/giải mã. */
    fun saveStatistics(context: Context, todayIncome: Long, platformStatsSerialized: String) {
        prefs(context).edit()
            .putLong(KEY_TODAY_INCOME, todayIncome)
            .putString(KEY_PLATFORM_STATS, platformStatsSerialized)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getName(context: Context): String = prefs(context).getString(KEY_NAME, "") ?: ""
    fun getHandle(context: Context): String = prefs(context).getString(KEY_HANDLE, "") ?: ""
    fun getEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "") ?: ""
    fun getCoin(context: Context): String = prefs(context).getString(KEY_COIN, "") ?: ""
    fun getTodayIncome(context: Context): Long = prefs(context).getLong(KEY_TODAY_INCOME, 0L)
    fun getPlatformStatsSerialized(context: Context): String = prefs(context).getString(KEY_PLATFORM_STATS, "") ?: ""
    fun isLoggedIn(context: Context): Boolean = !getToken(context).isNullOrBlank()
}
