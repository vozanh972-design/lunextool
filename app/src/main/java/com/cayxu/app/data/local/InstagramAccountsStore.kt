package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences

data class InstagramAccount(
    val username: String,
    val userId: String = "",
    val cookie: String = "",
    val userAgent: String = "",
    val proxy: String = "",
    val isLive: Boolean = true,
    val fullName: String = "",
    val avatar: String = "",
    val fbDtsg: String = "",
    val lsd: String = ""
)

object InstagramAccountsStore {
    private const val PREFS_NAME = "cayxu_instagram_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccounts(context: Context): List<InstagramAccount> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                try {
                    InstagramAccount(
                        username = parts[0],
                        userId = parts.getOrElse(1) { "" },
                        cookie = parts.getOrElse(2) { "" },
                        userAgent = parts.getOrElse(3) { "" },
                        proxy = parts.getOrElse(4) { "" },
                        isLive = parts.getOrElse(5) { "true" } == "true",
                        fullName = parts.getOrElse(6) { "" },
                        avatar = parts.getOrElse(7) { "" },
                        fbDtsg = parts.getOrElse(8) { "" },
                        lsd = parts.getOrElse(9) { "" }
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it.username.isNotBlank() }
    }

    fun getAccount(context: Context, usernameOrId: String): InstagramAccount? {
        val clean = usernameOrId.trim().removePrefix("@").lowercase()
        return getAccounts(context).firstOrNull { 
            it.username.trim().removePrefix("@").lowercase() == clean ||
            (it.userId.isNotBlank() && ("IG_" + it.userId).lowercase() == clean) ||
            it.userId == clean
        }
    }

    fun addAccount(context: Context, account: InstagramAccount) {
        addAccounts(context, listOf(account))
    }

    fun addAccounts(context: Context, entries: List<InstagramAccount>) {
        val trimmedNew = entries
            .map {
                it.copy(
                    username = it.username.trim().removePrefix("@"),
                    userId = it.userId.trim(),
                    cookie = it.cookie.trim(),
                    userAgent = it.userAgent.trim(),
                    proxy = it.proxy.trim(),
                    fullName = it.fullName.trim()
                )
            }
            .filter { it.username.isNotEmpty() }
        if (trimmedNew.isEmpty()) return

        val current = getAccounts(context).toMutableList()
        trimmedNew.forEach { entry ->
            val idx = current.indexOfFirst { it.username.equals(entry.username, ignoreCase = true) }
            if (idx >= 0) {
                current[idx] = entry
            } else {
                current.add(entry)
            }
        }
        save(context, current)
    }

    fun removeAccount(context: Context, username: String) {
        val clean = username.trim().removePrefix("@")
        val current = getAccounts(context).toMutableList()
        current.removeAll { it.username.equals(clean, ignoreCase = true) }
        save(context, current)
    }

    private fun save(context: Context, accounts: List<InstagramAccount>) {
        val raw = accounts.joinToString(ENTRY_SEPARATOR) { acc ->
            listOf(
                acc.username,
                acc.userId,
                acc.cookie,
                acc.userAgent,
                acc.proxy,
                if (acc.isLive) "true" else "false",
                acc.fullName,
                acc.avatar,
                acc.fbDtsg,
                acc.lsd
            ).joinToString(FIELD_SEPARATOR)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).apply()
    }
}
