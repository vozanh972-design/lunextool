package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu THẬT phiên đăng nhập Golike (token Bearer + tên/email/coin đã lấy về từ
 * GET /api/users/me) xuống SharedPreferences riêng - còn nguyên sau khi tắt/mở lại app.
 * KHÔNG lưu mật khẩu Golike vì app không đăng nhập bằng mật khẩu, chỉ dùng token do
 * người dùng tự dán vào.
 */
object GolikeAccountStore {
    private const val PREFS_NAME = "cayxu_golike_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_COIN = "coin"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLogin(context: Context, token: String, name: String, email: String, coin: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_COIN, coin)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getName(context: Context): String = prefs(context).getString(KEY_NAME, "") ?: ""
    fun getEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "") ?: ""
    fun getCoin(context: Context): String = prefs(context).getString(KEY_COIN, "") ?: ""
    fun isLoggedIn(context: Context): Boolean = !getToken(context).isNullOrBlank()
}
