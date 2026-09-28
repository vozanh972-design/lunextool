package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu danh sách UID tài khoản đã "liên kết" theo từng nền tảng (TikTok/Instagram/LinkedIn/...).
 * Chỉ lưu UID (định danh công khai) do người dùng tự nhập - KHÔNG lưu mật khẩu,
 * cookie, hay token của bất kỳ ai, nên dùng SharedPreferences thường là đủ.
 *
 * LƯU Ý: Facebook có màn hình + store RIÊNG (xem FacebookAccountsStore,
 * FacebookLinkAccountScreen, FacebookAddAccountScreen) - không dùng chung với store này,
 * để chỉnh sửa Facebook không ảnh hưởng tới các nền tảng khác.
 */
object LinkedAccountsStore {
    private const val PREFS_NAME = "cayxu_linked_accounts"
    private const val SEPARATOR = "\u0001"

    fun getAccounts(context: Context, platform: String): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key(platform), null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() && !it.contains("uid_mau") && !it.contains("mẫu") && !it.contains("(mau)") }
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
        removeAccounts(context, platform, listOf(uid))
    }

    fun removeAccounts(context: Context, platform: String, uids: Collection<String>) {
        val cleanSet = uids.map { it.trim().removePrefix("@").lowercase() }.filter { it.isNotBlank() }.toSet()
        if (cleanSet.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).toMutableList()
        current.removeAll { it.trim().removePrefix("@").lowercase() in cleanSet }
        prefs.edit().putString(key(platform), current.joinToString(SEPARATOR)).commit()
    }

    private fun key(platform: String) = "accounts_$platform"
}
