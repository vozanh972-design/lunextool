package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences

data class FacebookAccount(
    val uid: String,
    val name: String = "",      // Password
    val link: String = "",      // 2FA
    val note: String = "",      // Cookie
    val phone: String = "",     // Proxy
    val bio: String = "",       // Token
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
                    phone = parts.getOrNull(4).orEmpty(),
                    bio = parts.getOrNull(5).orEmpty(),
                    isLive = parts.getOrNull(6) != "die"
                )
            }
            .filter { it.uid.isNotBlank() }
    }

    fun addAccount(
        context: Context,
        uid: String,
        name: String = "",
        link: String = "",
        note: String = "",
        phone: String = "",
        bio: String = "",
        isLive: Boolean = true
    ) {
        val account = FacebookAccount(
            uid = uid,
            name = name,
            link = link,
            note = note,
            phone = phone,
            bio = bio,
            isLive = isLive
        )
        addAccounts(context, listOf(account))
    }

    fun addAccounts(context: Context, entries: List<FacebookAccount>) {
        val trimmedNew = entries
            .map {
                it.copy(
                    uid = it.uid.trim(),
                    name = it.name.trim(),
                    link = it.link.trim(),
                    note = it.note.trim(),
                    phone = it.phone.trim(),
                    bio = it.bio.trim()
                )
            }
            .filter { it.uid.isNotEmpty() }
        if (trimmedNew.isEmpty()) return

        val current = getAccounts(context).toMutableList()
        val existingUids = current.map { it.uid }.toMutableSet()
        trimmedNew.forEach { entry ->
            if (entry.uid !in existingUids) {
                current.add(entry)
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

    fun markLive(context: Context, uids: List<String>) {
        val current = getAccounts(context).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = true) else acc
        }
        save(context, current)
    }

    // Thêm hàm markDie
    fun markDie(context: Context, uids: List<String>) {
        val current = getAccounts(context).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = false) else acc
        }
        save(context, current)
    }

    private fun save(context: Context, accounts: List<FacebookAccount>) {
        val raw = accounts.joinToString(ENTRY_SEPARATOR) { acc ->
            listOf(acc.uid, acc.name, acc.link, acc.note, acc.phone, acc.bio, if (acc.isLive) "live" else "die")
                .joinToString(FIELD_SEPARATOR)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).apply()
    }
}
