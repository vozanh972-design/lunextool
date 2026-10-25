package com.cayxu.app.ui.f4f

import android.content.Context
import androidx.annotation.Keep
import com.cayxu.app.data.local.TikTokAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Quản lý lưu trữ phiên đăng nhập và dữ liệu Follow4Follow (F4F) độc lập.
 * Tách biệt hoàn toàn, không liên quan đến XSMM hay bất kỳ nền tảng nào khác.
 * Tất cả Key SharedPreferences được cấp phát động từ F4fNativeBridge (không chuỗi tĩnh trong DEX).
 */
@Keep
object F4fAccountStore {
    private val gson = Gson()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(F4fNativeBridge.fetchPrefsAccount(), Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(F4fNativeBridge.fetchKeyToken(), null).isNullOrBlank()

    fun getToken(context: Context): String? =
        prefs(context).getString(F4fNativeBridge.fetchKeyToken(), null)

    fun getUsername(context: Context): String =
        prefs(context).getString(F4fNativeBridge.fetchKeyUsername(), "").orEmpty()

    fun getPoints(context: Context): Long =
        prefs(context).getLong(F4fNativeBridge.fetchKeyPoints(), 0L)

    fun saveLogin(context: Context, token: String, username: String, points: Long) {
        prefs(context).edit()
            .putString(F4fNativeBridge.fetchKeyToken(), token)
            .putString(F4fNativeBridge.fetchKeyUsername(), username)
            .putLong(F4fNativeBridge.fetchKeyPoints(), points)
            .apply()
    }

    fun updatePoints(context: Context, points: Long) {
        prefs(context).edit().putLong(F4fNativeBridge.fetchKeyPoints(), points).apply()
    }

    fun saveAccountIdMap(context: Context, map: Map<String, String>) {
        val key = F4fNativeBridge.fetchKeyAccountsMap()
        prefs(context).edit()
            .remove(key)
            .putString(key, gson.toJson(map))
            .apply()
    }

    fun getAccountIdForHandle(context: Context, handle: String): String? {
        val clean = handle.trim().removePrefix("@").lowercase()
        return getAccountIdMap(context)[clean]
    }

    fun getAccountIdMap(context: Context): Map<String, String> {
        val json = prefs(context).getString(F4fNativeBridge.fetchKeyAccountsMap(), null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
        }.getOrDefault(emptyMap())
    }

    fun saveSavedTikTokAccounts(context: Context, accounts: List<TikTokAccount>) {
        val json = gson.toJson(accounts)
        prefs(context).edit().putString(F4fNativeBridge.fetchKeySavedAccounts(), json).apply()
    }

    fun getSavedTikTokAccounts(context: Context): List<TikTokAccount> {
        val json = prefs(context).getString(F4fNativeBridge.fetchKeySavedAccounts(), null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<TikTokAccount>>() {}.type
            gson.fromJson<List<TikTokAccount>>(json, type) ?: emptyList<TikTokAccount>()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
