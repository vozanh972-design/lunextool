package com.cayxu.app.data.local

import android.content.Context

/** Lưu phiên đăng nhập XSMM - SharedPreferences RIÊNG (cayxu_xsmm_account), không đụng tới
 *  kho dữ liệu nào khác. */
object XsmmAccountStore {
    private const val PREFS_NAME = "cayxu_xsmm_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_POINTS = "points"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_TOKEN, null).isNullOrBlank()

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getUsername(context: Context): String = prefs(context).getString(KEY_USERNAME, "").orEmpty()
    fun getPoints(context: Context): Long = prefs(context).getLong(KEY_POINTS, 0L)

    fun saveLogin(context: Context, token: String, username: String, points: Long) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_POINTS, points)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
