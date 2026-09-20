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
    val proxy: String = "",
    val coins: Long = 0L,
    val isLive: Boolean = true
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
                        proxy = obj.optString("proxy", ""),
                        coins = obj.optLong("coins", 0L),
                        isLive = obj.optBoolean("isLive", true)
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
                put("proxy", acc.proxy)
                put("coins", acc.coins)
                put("isLive", acc.isLive)
            }
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun addAccount(context: Context, account: TtcAccount) {
        val current = getAccounts(context).toMutableList()
        current.removeAll { it.username.equals(account.username, ignoreCase = true) }
        current.add(0, account)
        saveAccounts(context, current)
    }

    fun removeAccount(context: Context, username: String) {
        val current = getAccounts(context).toMutableList()
        current.removeAll { it.username.equals(username, ignoreCase = true) }
        saveAccounts(context, current)
    }
}
