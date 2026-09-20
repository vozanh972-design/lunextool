package com.cayxu.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Lưu phiên đăng nhập XSMM & map tài khoản (handle -> account_id trên XSMM). */
object XsmmAccountStore {
    private const val PREFS_NAME = "cayxu_xsmm_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_POINTS = "points"
    private const val KEY_ACCOUNTS_MAP = "xsmm_accounts_map"
    private const val KEY_INTERNAL_IDS_MAP = "xsmm_internal_ids_map"

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

    /** Lưu map: handle (lowercase) -> account_id (uid trên XSMM dùng cho tasks2) - Ghi đè toàn bộ, không merge */
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

    /** Lưu map: handle -> id nội bộ XSMM (dùng cho set-active) - Ghi đè toàn bộ, không merge */
    fun saveInternalIdMap(context: Context, map: Map<String, String>) {
        prefs(context).edit()
            .remove(KEY_INTERNAL_IDS_MAP)
            .putString(KEY_INTERNAL_IDS_MAP, gson.toJson(map))
            .apply()
    }

    fun getInternalIdForHandle(context: Context, handle: String): String? {
        val clean = handle.trim().removePrefix("@").lowercase()
        return getInternalIdMap(context)[clean]
    }

    fun getInternalIdMap(context: Context): Map<String, String> {
        val json = prefs(context).getString(KEY_INTERNAL_IDS_MAP, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
        }.getOrDefault(emptyMap())
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
