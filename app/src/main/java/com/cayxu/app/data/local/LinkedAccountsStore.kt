package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu danh sách UID tài khoản đã "liên kết" theo từng nền tảng (Facebook/TikTok/...).
 * Chỉ lưu UID (định danh công khai) do người dùng tự nhập - KHÔNG lưu mật khẩu,
 * cookie, hay token của bất kỳ ai, nên dùng SharedPreferences thường là đủ.
 *
 * Trường "isLive" chỉ là cờ hiển thị cho giao diện demo (mặc định luôn true khi thêm mới),
 * KHÔNG có bất kỳ logic gọi mạng/kiểm tra thật nào - không phải tool check tài khoản.
 */
data class LinkedAccount(
    val uid: String,
    val isLive: Boolean = true
)

object LinkedAccountsStore {
    private const val PREFS_NAME = "cayxu_linked_accounts"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"

    fun getAccounts(context: Context, platform: String): List<LinkedAccount> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key(platform), null) ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                val uid = parts.getOrNull(0).orEmpty()
                val isLive = parts.getOrNull(1) != "die"
                LinkedAccount(uid, isLive)
            }
            .filter { it.uid.isNotBlank() }
    }

    fun addAccount(context: Context, platform: String, uid: String) {
        addAccounts(context, platform, listOf(uid))
    }

    /** Thêm nhiều UID cùng lúc, mỗi UID một dòng (dùng cho ô "nhập nhiều UID"). */
    fun addAccounts(context: Context, platform: String, uids: List<String>) {
        val trimmedNew = uids.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedNew.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).toMutableList()
        val existingUids = current.map { it.uid }.toMutableSet()
        trimmedNew.forEach { uid ->
            if (uid !in existingUids) {
                current.add(LinkedAccount(uid, isLive = true))
                existingUids.add(uid)
            }
        }
        save(prefs, platform, current)
    }

    fun removeAccount(context: Context, platform: String, uid: String) {
        removeAccounts(context, platform, listOf(uid))
    }

    fun removeAccounts(context: Context, platform: String, uids: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).toMutableList()
        current.removeAll { it.uid in uids }
        save(prefs, platform, current)
    }

    /**
     * Chỉ đổi cờ hiển thị "Live" trên giao diện cho các UID được chọn - không gọi mạng,
     * không xác thực gì cả. Dùng cho nút "Kiểm tra Live" ở màn danh sách (mock UI).
     */
    fun markLive(context: Context, platform: String, uids: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAccounts(context, platform).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = true) else acc
        }
        save(prefs, platform, current)
    }

    private fun save(prefs: android.content.SharedPreferences, platform: String, accounts: List<LinkedAccount>) {
        val raw = accounts.joinToString(ENTRY_SEPARATOR) { acc ->
            "${acc.uid}$FIELD_SEPARATOR${if (acc.isLive) "live" else "die"}"
        }
        prefs.edit().putString(key(platform), raw).apply()
    }

    private fun key(platform: String) = "accounts_$platform"
}
