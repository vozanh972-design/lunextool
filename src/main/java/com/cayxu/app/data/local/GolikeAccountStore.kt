package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu THẬT phiên đăng nhập Golike (token Bearer + tên/handle/email/coin/thống kê hôm nay
 * đã lấy về từ GET /api/users/me) xuống SharedPreferences riêng - còn nguyên sau khi
 * tắt/mở lại app. KHÔNG lưu mật khẩu Golike vì app không đăng nhập bằng mật khẩu, chỉ
 * dùng token do người dùng tự dán vào.
 */
object GolikeAccountStore {
    private const val PREFS_NAME = "cayxu_golike_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"
    private const val KEY_HANDLE = "handle"
    private const val KEY_EMAIL = "email"
    private const val KEY_COIN = "coin"
    private const val KEY_TASKS_TODAY = "tasks_today"
    private const val KEY_REWARD_TODAY = "reward_today"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLogin(
        context: Context,
        token: String,
        name: String,
        handle: String,
        email: String,
        coin: String,
        tasksToday: String,
        rewardToday: String
    ) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .putString(KEY_HANDLE, handle)
            .putString(KEY_EMAIL, email)
            .putString(KEY_COIN, coin)
            .putString(KEY_TASKS_TODAY, tasksToday)
            .putString(KEY_REWARD_TODAY, rewardToday)
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
    fun getTasksToday(context: Context): String = prefs(context).getString(KEY_TASKS_TODAY, "0") ?: "0"
    fun getRewardToday(context: Context): String = prefs(context).getString(KEY_REWARD_TODAY, "0") ?: "0"
    fun isLoggedIn(context: Context): Boolean = !getToken(context).isNullOrBlank()
}
