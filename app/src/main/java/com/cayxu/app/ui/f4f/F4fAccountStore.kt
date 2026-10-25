package com.cayxu.app.ui.f4f

import android.content.Context
import com.cayxu.app.data.local.TikTokAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Quản lý lưu trữ phiên đăng nhập và dữ liệu Follow4Follow (F4F) độc lập.
 * Tách biệt hoàn toàn, không liên quan đến XSMM hay bất kỳ nền tảng nào khác.
 */
object F4fAccountStore {
    private const val PREFS_NAME = "cayxu_f4f_account"
    private const val KEY_TOKEN = "f4f_token"
    private const val KEY_USERNAME = "f4f_username"
    private const val KEY_POINTS = "f4f_points"
    private const val KEY_ACCOUNTS_MAP = "f4f_accounts_map"
    private const val KEY_INTERNAL_IDS_MAP = "f4f_internal_ids_map"
    private const val KEY_SAVED_TIKTOK_ACCOUNTS = "f4f_tiktok_accounts_saved"

    private val gson = Gson()

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

    fun updatePoints(context: Context, points: Long) {
        prefs(context).edit().putLong(KEY_POINTS, points).apply()
    }

    fun saveAccountIdMap(context: Context, map: Map<String, String>) {
        prefs(context).edit()
            .remove(KEY_ACCOUNTS_MAP)
            .putString(KEY_ACCOUNTS_MAP, gson.toJson(map))
            .apply()
    }

    fun getAccountIdForHandle(context: Context, handle: String): String? {
        val clean = handle.trim().removePrefix("@").lowercase()
        return getAccountIdMap(context)[clean]
    }

    fun getAccountIdMap(context: Context): Map<String, String> {
        val json = prefs(context).getString(KEY_ACCOUNTS_MAP, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
        }.getOrDefault(emptyMap())
    }

    fun saveSavedTikTokAccounts(context: Context, accounts: List<TikTokAccount>) {
        val json = gson.toJson(accounts)
        prefs(context).edit().putString(KEY_SAVED_TIKTOK_ACCOUNTS, json).apply()
    }

    fun getSavedTikTokAccounts(context: Context): List<TikTokAccount> {
        val json = prefs(context).getString(KEY_SAVED_TIKTOK_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<TikTokAccount>>() {}.type
            gson.fromJson<List<TikTokAccount>>(json, type) ?: emptyList<TikTokAccount>()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
