package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

@Keep
data class TtcAccount(
    val username: String = "",
    val token: String = "",
    val cookie: String = "",
    val proxy: String = "",
    val coins: Long = 0L,
    val isLive: Boolean = true,
    val lastError: String = ""
)

object TtcAccountsStore {
    private const val PREFS_NAME = "cayxu_ttc_accounts"
    private const val KEY_ACCOUNTS = "accounts"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccounts(context: Context): List<TtcAccount> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val list = mutableListOf<TtcAccount>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TtcAccount(
                        username = obj.optString("username", ""),
                        token = obj.optString("token", ""),
                        cookie = obj.optString("cookie", ""),
                        proxy = obj.optString("proxy", ""),
                        coins = obj.optLong("coins", 0L),
                        isLive = obj.optBoolean("isLive", true),
                        lastError = obj.optString("lastError", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAccounts(context: Context, accounts: List<TtcAccount>) {
        val arr = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("username", acc.username)
                put("token", acc.token)
                put("cookie", acc.cookie)
                put("proxy", acc.proxy)
                put("coins", acc.coins)
                put("isLive", acc.isLive)
                put("lastError", acc.lastError)
            }
            arr.put(obj)
        }
        // Dùng commit() để ghi ngay lập tức vào đĩa, tránh bị hồi sinh acc cũ khi chuyển tab
        prefs(context).edit().putString(KEY_ACCOUNTS, arr.toString()).commit()
    }

    fun addAccount(context: Context, account: TtcAccount) {
        val current = getAccounts(context).toMutableList()
        val accToken = account.token.trim()
        val accUser = account.username.trim()
        // Định danh duy nhất theo Token chuẩn 100%, tránh ghi đè nhầm tài khoản
        current.removeAll {
            if (accToken.isNotBlank() && it.token.isNotBlank()) {
                it.token.trim().equals(accToken, ignoreCase = true)
            } else if (accUser.isNotBlank() && !accUser.equals("Unknown", ignoreCase = true)) {
                it.username.trim().equals(accUser, ignoreCase = true)
            } else {
                false
            }
        }
        current.add(0, account)
        saveAccounts(context, current)
    }

    fun removeAccount(context: Context, tokenOrUsername: String) {
        val current = getAccounts(context).toMutableList()
        val target = tokenOrUsername.trim()
        current.removeAll {
            it.token.trim().equals(target, ignoreCase = true) ||
            it.username.trim().equals(target, ignoreCase = true)
        }
        saveAccounts(context, current)
    }
}
