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
    private const val KEY_DELETED_ACCOUNTS = "deleted_account_keys"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeletedKeys(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_DELETED_ACCOUNTS, null) ?: return emptySet()
        return raw.split(ENTRY_SEPARATOR)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun clearFromDeleted(context: Context, key: String) {
        val clean = key.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return
        val current = getDeletedKeys(context).toMutableSet()
        if (current.remove(clean)) {
            prefs(context).edit().putString(KEY_DELETED_ACCOUNTS, current.joinToString(ENTRY_SEPARATOR)).commit()
        }
    }

    private fun addDeletedKeys(context: Context, keys: Collection<String>) {
        val cleanKeys = keys
            .flatMap { listOf(it.trim().lowercase(), it.trim().removePrefix("@").lowercase()) }
            .filter { it.isNotBlank() }
        if (cleanKeys.isEmpty()) return
        val current = getDeletedKeys(context).toMutableSet()
        current.addAll(cleanKeys)
        prefs(context).edit().putString(KEY_DELETED_ACCOUNTS, current.joinToString(ENTRY_SEPARATOR)).commit()
    }

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
        val deletedSet = getDeletedKeys(context)
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
            .filter { acc ->
                val u = acc.username.trim().removePrefix("@").lowercase()
                val uid = acc.userId.trim().lowercase()
                val igUid = if (uid.isNotBlank()) "ig_$uid" else ""
                val fn = acc.fullName.trim().removePrefix("@").lowercase()
                !deletedSet.contains(u) &&
                !deletedSet.contains(acc.username.lowercase()) &&
                (uid.isBlank() || !deletedSet.contains(uid)) &&
                (igUid.isBlank() || !deletedSet.contains(igUid)) &&
                (fn.isBlank() || fn.contains(" ") || !deletedSet.contains(fn))
            }
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

        // Khi người dùng chủ động thêm lại, gỡ khỏi danh sách đen đã xóa
        trimmedNew.forEach { entry ->
            clearFromDeleted(context, entry.username)
            if (entry.userId.isNotBlank()) {
                clearFromDeleted(context, entry.userId)
                clearFromDeleted(context, "ig_${entry.userId}")
            }
        }

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
        val deletedKeys = getDeletedKeys(context)
        if (deletedKeys.contains(clean)) return // Đã xóa vĩnh viễn, cấm XSMM đồng bộ phục hồi

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
        val clean = account.username.trim().removePrefix("@").lowercase()
        val deletedKeys = getDeletedKeys(context)
        if (deletedKeys.contains(clean) || (account.userId.isNotBlank() && deletedKeys.contains(account.userId.lowercase()))) {
            return // Đã bị xóa, không ghi đè lại
        }
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

        // Lưu vào danh sách đen vĩnh viễn với .commit()
        val allDeletedTokens = mutableSetOf<String>()
        allDeletedTokens.addAll(cleanSet)
        usernamesOrIds.forEach {
            allDeletedTokens.add(it.trim().lowercase())
            allDeletedTokens.add(it.trim().removePrefix("@").lowercase())
        }

        val current = getAccounts(context).toMutableList()
        val toRemove = current.filter { acc ->
            val u = acc.username.trim().removePrefix("@").lowercase()
            val uid = acc.userId.trim().lowercase()
            val igUid = if (uid.isNotBlank()) "ig_$uid" else ""
            val fn = acc.fullName.trim().removePrefix("@").lowercase()

            cleanSet.contains(u) ||
            cleanSet.contains(acc.username.lowercase()) ||
            (uid.isNotBlank() && cleanSet.contains(uid)) ||
            (igUid.isNotBlank() && cleanSet.contains(igUid)) ||
            (fn.isNotBlank() && !fn.contains(" ") && cleanSet.contains(fn))
        }

        toRemove.forEach { acc ->
            if (acc.username.isNotBlank()) {
                allDeletedTokens.add(acc.username.trim().lowercase())
                allDeletedTokens.add(acc.username.trim().removePrefix("@").lowercase())
            }
            if (acc.userId.isNotBlank()) {
                allDeletedTokens.add(acc.userId.trim().lowercase())
                allDeletedTokens.add("ig_${acc.userId.trim().lowercase()}")
            }
            if (acc.fullName.isNotBlank() && !acc.fullName.contains(" ")) {
                allDeletedTokens.add(acc.fullName.trim().lowercase())
            }
        }

        addDeletedKeys(context, allDeletedTokens)

        current.removeAll(toRemove.toSet())
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
