package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu danh sách UID tài khoản đã "liên kết" theo từng nền tảng (Facebook/TikTok/...).
 * Chỉ lưu UID (định danh công khai) do người dùng tự nhập - KHÔNG lưu mật khẩu,
 * cookie, hay token của bất kỳ ai, nên dùng SharedPreferences thường là đủ.
 */
object LinkedAccountsStore {
    private const val PREFS_NAME = "cayxu_linked_accounts"
    private const val SEPARATOR = "\u0001"

    fun getAccounts(context: Context, platform: String): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key(platform), null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addAccount(context: Context, platform: String, uid: String) {
        val trimmed = uid.trim()
        if (trimmed.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).toMutableList()
        if (trimmed !in current) current.add(trimmed)
        prefs.edit().putString(key(platform), current.joinToString(SEPARATOR)).apply()
    }

    fun removeAccount(context: Context, platform: String, uid: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).toMutableList()
        current.remove(uid)
        prefs.edit().putString(key(platform), current.joinToString(SEPARATOR)).apply()
    }

    private fun key(platform: String) = "accounts_$platform"
}
