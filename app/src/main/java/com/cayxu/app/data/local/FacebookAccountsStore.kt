package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Store RIÊNG cho danh sách tài khoản Facebook đã liên kết - độc lập hoàn toàn với
 * LinkedAccountsStore (dùng cho TikTok/Instagram/...), để sau này chỉnh sửa tính năng
 * Facebook không ảnh hưởng tới các nền tảng khác.
 *
 * Chỉ lưu các trường CÔNG KHAI do người dùng tự nhập: UID, tên hiển thị, link trang cá nhân.
 * KHÔNG lưu mật khẩu, mã 2FA, cookie, token hay proxy của bất kỳ ai.
 *
 * Trường "isLive" chỉ là cờ hiển thị cho giao diện demo (mặc định true khi thêm mới),
 * KHÔNG có logic gọi mạng/xác thực thật - không phải tool check tài khoản.
 */
data class FacebookAccount(
    val uid: String,
    val name: String = "",
    val link: String = "",
    val note: String = "",
    val isLive: Boolean = true
)

object FacebookAccountsStore {
    private const val PREFS_NAME = "cayxu_facebook_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccounts(context: Context): List<FacebookAccount> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                FacebookAccount(
                    uid = parts.getOrNull(0).orEmpty(),
                    name = parts.getOrNull(1).orEmpty(),
                    link = parts.getOrNull(2).orEmpty(),
                    note = parts.getOrNull(3).orEmpty(),
                    isLive = parts.getOrNull(4) != "die"
                )
            }
            .filter { it.uid.isNotBlank() }
    }

    fun addAccount(context: Context, uid: String, name: String = "", link: String = "", note: String = "") {
        addAccounts(context, listOf(FacebookAccount(uid = uid, name = name, link = link, note = note)))
    }

    /** Thêm nhiều tài khoản cùng lúc (dùng cho tab "Nhập nhiều UID" - tên/link/ghi chú để trống). */
    fun addAccounts(context: Context, entries: List<FacebookAccount>) {
        val trimmedNew = entries
            .map {
                it.copy(uid = it.uid.trim(), name = it.name.trim(), link = it.link.trim(), note = it.note.trim())
            }
            .filter { it.uid.isNotEmpty() }
        if (trimmedNew.isEmpty()) return

        val current = getAccounts(context).toMutableList()
        val existingUids = current.map { it.uid }.toMutableSet()
        trimmedNew.forEach { entry ->
            if (entry.uid !in existingUids) {
                current.add(entry.copy(isLive = true))
                existingUids.add(entry.uid)
            }
        }
        save(context, current)
    }

    fun removeAccount(context: Context, uid: String) {
        removeAccounts(context, listOf(uid))
    }

    fun removeAccounts(context: Context, uids: List<String>) {
        val current = getAccounts(context).toMutableList()
        current.removeAll { it.uid in uids }
        save(context, current)
    }

    /**
     * Chỉ đổi cờ hiển thị "Live" trên giao diện cho các UID được chọn - không gọi mạng,
     * không xác thực gì cả. Dùng cho nút "Kiểm tra Live" (mock UI).
     */
    fun markLive(context: Context, uids: List<String>) {
        val current = getAccounts(context).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = true) else acc
        }
        save(context, current)
    }

    private fun save(context: Context, accounts: List<FacebookAccount>) {
        val raw = accounts.joinToString(ENTRY_SEPARATOR) { acc ->
            listOf(acc.uid, acc.name, acc.link, acc.note, if (acc.isLive) "live" else "die")
                .joinToString(FIELD_SEPARATOR)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).apply()
    }
}
