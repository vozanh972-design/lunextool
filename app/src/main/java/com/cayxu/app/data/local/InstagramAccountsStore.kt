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
    val lsd: String = "",
    val biography: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val password: String = "",
    val twoFactor: String = "",
    val isXsmmLinked: Boolean = false
)

object InstagramAccountsStore {
    private const val PREFS_NAME = "cayxu_instagram_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun unescape(str: String): String {
        if (!str.contains("\\u")) return str
        val regex = Regex("""\\u([0-9a-fA-F]{4})""")
        return regex.replace(str) { matchResult ->
            try {
                matchResult.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                matchResult.value
            }
        }
    }

    fun getAccounts(context: Context): List<InstagramAccount> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                try {
                    InstagramAccount(
                        username = unescape(parts[0]),
                        userId = parts.getOrElse(1) { "" },
                        cookie = parts.getOrElse(2) { "" },
                        userAgent = parts.getOrElse(3) { "" },
                        proxy = parts.getOrElse(4) { "" },
                        isLive = parts.getOrElse(5) { "true" } == "true",
                        fullName = unescape(parts.getOrElse(6) { "" }),
                        avatar = parts.getOrElse(7) { "" },
                        fbDtsg = parts.getOrElse(8) { "" },
                        lsd = parts.getOrElse(9) { "" },
                        biography = unescape(parts.getOrElse(10) { "" }),
                        followersCount = parts.getOrElse(11) { "0" }.toIntOrNull() ?: 0,
                        followingCount = parts.getOrElse(12) { "0" }.toIntOrNull() ?: 0,
                        postsCount = parts.getOrElse(13) { "0" }.toIntOrNull() ?: 0,
                        password = parts.getOrElse(14) { "" },
                        twoFactor = parts.getOrElse(15) { "" },
                        isXsmmLinked = parts.getOrElse(16) { "false" } == "true"
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
                    fullName = it.fullName.trim(),
                    biography = it.biography.trim(),
                    password = it.password.trim(),
                    twoFactor = it.twoFactor.trim()
                )
            }
            .filter { it.username.isNotEmpty() }
        if (trimmedNew.isEmpty()) return

        val current = getAccounts(context).toMutableList()
        trimmedNew.forEach { entry ->
            val idx = current.indexOfFirst { 
                it.username.equals(entry.username, ignoreCase = true) ||
                (entry.userId.isNotBlank() && it.userId == entry.userId) ||
                (entry.userId.isNotBlank() && ("IG_" + entry.userId).equals(it.username, ignoreCase = true)) ||
                (it.userId.isNotBlank() && ("IG_" + it.userId).equals(entry.username, ignoreCase = true))
            }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = entry.copy(
                    isXsmmLinked = entry.isXsmmLinked || existing.isXsmmLinked
                )
            } else {
                current.add(entry)
            }
        }
        save(context, current)
    }

    fun setXsmmLinked(context: Context, usernameOrId: String, linked: Boolean) {
        val clean = usernameOrId.trim().removePrefix("@").lowercase()
        val current = getAccounts(context).toMutableList()
        val idx = current.indexOfFirst {
            it.username.trim().removePrefix("@").lowercase() == clean ||
            (it.userId.isNotBlank() && ("IG_" + it.userId).lowercase() == clean) ||
            it.userId.lowercase() == clean
        }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isXsmmLinked = linked)
            save(context, current)
        }
    }

    fun updateAccount(context: Context, account: InstagramAccount) {
        addAccount(context, account)
    }

    fun deleteAccount(context: Context, usernameOrId: String): List<InstagramAccount> {
        return deleteAccounts(context, listOf(usernameOrId))
    }

    fun deleteAccounts(context: Context, usernamesOrIds: Collection<String>): List<InstagramAccount> {
        val cleanSet = usernamesOrIds
            .map { it.trim().removePrefix("@").lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        if (cleanSet.isEmpty()) return getAccounts(context)

        val current = getAccounts(context).toMutableList()
        current.removeAll { acc ->
            val u = acc.username.trim().removePrefix("@").lowercase()
            val uid = acc.userId.trim().lowercase()
            val igUid = if (uid.isNotBlank()) "ig_$uid" else ""
            val fn = acc.fullName.trim().removePrefix("@").lowercase()

            cleanSet.contains(u) ||
            (uid.isNotBlank() && cleanSet.contains(uid)) ||
            (igUid.isNotBlank() && cleanSet.contains(igUid)) ||
            (fn.isNotBlank() && !fn.contains(" ") && cleanSet.contains(fn))
        }
        save(context, current)
        return current
    }

    fun removeAccount(context: Context, usernameOrId: String) {
        deleteAccount(context, usernameOrId)
    }

    fun removeAccounts(context: Context, usernames: List<String>) {
        deleteAccounts(context, usernames)
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
                acc.lsd,
                acc.biography,
                acc.followersCount.toString(),
                acc.followingCount.toString(),
                acc.postsCount.toString(),
                acc.password,
                acc.twoFactor,
                if (acc.isXsmmLinked) "true" else "false"
            ).joinToString(FIELD_SEPARATOR)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).commit()
    }
}
