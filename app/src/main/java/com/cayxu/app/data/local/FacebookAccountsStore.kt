package com.cayxu.app.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FacebookAccount(
    val uid: String,
    val name: String = "",
    val link: String = "",
    val note: String = "",
    val phone: String = "",
    val bio: String = "",
    val token: String = "",      // thêm token
    val isLive: Boolean = false
)

object FacebookAccountsStore {
    private const val PREF_NAME = "fb_accounts"
    private const val KEY_ACCOUNTS = "accounts"

    fun addAccount(context: Context, uid: String, name: String = "", token: String = "") {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val array = JSONArray(json)
        val obj = JSONObject().apply {
            put("uid", uid)
            put("name", name)
            put("token", token)
            // các trường khác nếu cần
        }
        array.put(obj)
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun getAccounts(context: Context): List<FacebookAccount> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<FacebookAccount>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                FacebookAccount(
                    uid = obj.optString("uid"),
                    name = obj.optString("name"),
                    token = obj.optString("token")
                )
            )
        }
        return list
    }

    fun removeAccount(context: Context, uid: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val array = JSONArray(json)
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("uid") != uid) {
                newArray.put(obj)
            }
        }
        prefs.edit().putString(KEY_ACCOUNTS, newArray.toString()).apply()
    }
}
