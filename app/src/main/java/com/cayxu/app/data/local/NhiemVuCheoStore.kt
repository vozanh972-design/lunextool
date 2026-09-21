package com.cayxu.app.data.local

import android.content.Context

data class NvcUserInfo(
    val id: Long = 0L,
    val username: String = "",
    val displayName: String = "",
    val coinBalance: String = "0",
    val status: String = "active"
)

/**
 * Lưu phiên đăng nhập & thông tin tài khoản Nhiệm Vụ Chéo (NVC).
 * Token dạng: nvc_sk_...
 */
object NhiemVuCheoStore {
    private const val PREFS_NAME = "cayxu_nvc_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_COIN_BALANCE = "coin_balance"
    private const val KEY_STATUS = "status"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_TOKEN, null).isNullOrBlank()

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getUser(context: Context): NvcUserInfo {
        val p = prefs(context)
        return NvcUserInfo(
            id = p.getLong(KEY_USER_ID, 0L),
            username = p.getString(KEY_USERNAME, "").orEmpty(),
            displayName = p.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            coinBalance = p.getString(KEY_COIN_BALANCE, "0") ?: "0",
            status = p.getString(KEY_STATUS, "active") ?: "active"
        )
    }

    fun saveLogin(
        context: Context,
        token: String,
        id: Long = 0L,
        username: String,
        displayName: String = "",
        coinBalance: String = "0",
        status: String = "active"
    ) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token.trim())
            .putLong(KEY_USER_ID, id)
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .putString(KEY_COIN_BALANCE, coinBalance.trim())
            .putString(KEY_STATUS, status.trim())
            .apply()
    }

    fun updateCoinBalance(context: Context, coinBalance: String) {
        prefs(context).edit().putString(KEY_COIN_BALANCE, coinBalance.trim()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
