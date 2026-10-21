package com.cayxu.app.ui.crossttc

import android.content.Context
import android.content.SharedPreferences

/**
 * Lưu trữ phiên làm việc của RIÊNG Tương tác chéo TikTok (TikTok TTC).
 * Hoàn toàn độc lập, SharedPreferences riêng ("crossttc_prefs"), không đụng chạm
 * đến bất kỳ cấu hình hay token nào của Tuongtaccheo Facebook.
 */
object CrossTTCStore {
    private const val PREFS_NAME = "crossttc_prefs"
    private const val KEY_TOKEN = "tiktok_ttc_access_token"
    private const val KEY_USERNAME = "tiktok_ttc_username"
    private const val KEY_COINS = "tiktok_ttc_coins"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).commit()
    }

    fun getToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun clearToken(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_USERNAME).remove(KEY_COINS).commit()
    }

    fun saveSession(context: Context, username: String, coins: Long) {
        prefs(context).edit()
            .putString(KEY_USERNAME, username.trim())
            .putLong(KEY_COINS, coins)
            .commit()
    }

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "") ?: ""

    fun getCoins(context: Context): Long =
        prefs(context).getLong(KEY_COINS, 0L)
}
