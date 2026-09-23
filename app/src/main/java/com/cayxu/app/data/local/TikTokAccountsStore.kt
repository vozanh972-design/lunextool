package com.cayxu.app.data.local

import android.content.Context
import java.util.UUID

/**
 * Lưu danh sách tài khoản TikTok - RIÊNG cho TikTok (SharedPreferences riêng: cayxu_tiktok_accounts),
 * không đụng tới LinkedAccountsStore hay FacebookAccountsStore.
 */
object TikTokAccountsStore {
    private const val PREFS_NAME = "cayxu_tiktok_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val ENTRY_SEP = "\u0001"
    private const val FIELD_SEP = "\u0002"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccounts(context: Context): List<TikTokAccount> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return raw.split(ENTRY_SEP)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val p = entry.split(FIELD_SEP)
                if (p.size < 9) return@mapNotNull null
                try {
                    TikTokAccount(
                        uid = p[0],
                        handle = p.getOrElse(1) { "" },
                        displayName = p.getOrElse(2) { "" },
                        subName = p.getOrElse(3) { "" },
                        avatarUrl = p.getOrElse(4) { "" },
                        createdAt = p.getOrElse(5) { "0" }.toLongOrNull() ?: 0L,
                        status = runCatching { TikTokAccountStatus.valueOf(p.getOrElse(6) { "ACTIVE" }) }
                            .getOrDefault(TikTokAccountStatus.ACTIVE),
                        enabled = p.getOrElse(7) { "1" } == "1",
                        taskCount = p.getOrElse(8) { "0" }.toIntOrNull() ?: 0,
                        variant = runCatching { TikTokAppVariant.valueOf(p.getOrElse(9) { "STANDARD" }) }
                            .getOrDefault(TikTokAppVariant.STANDARD),
                        followerCount = p.getOrElse(10) { "0" }.toLongOrNull() ?: 0L,
                        followingCount = p.getOrElse(11) { "0" }.toLongOrNull() ?: 0L,
                        heartCount = p.getOrElse(12) { "0" }.toLongOrNull() ?: 0L,
                        videoCount = p.getOrElse(13) { "0" }.toLongOrNull() ?: 0L,
                        bio = p.getOrElse(14) { "" },
                        isLive = p.getOrElse(15) { "1" } == "1",
                        createDateFormatted = p.getOrElse(16) { "" }
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it.uid.isNotBlank() }
    }

    /** Thêm tài khoản mới lấy từ tự động check app TikTok/TikTok Lite/TikTok Studio. */
    fun addFromCapture(
        context: Context,
        handle: String,
        displayName: String = "",
        avatarUrl: String = "",
        variant: TikTokAppVariant
    ): TikTokAccount {
        val cleanHandle = handle.trim().removePrefix("@")
        val normIncoming = cleanHandle.lowercase()
        val current = getAccounts(context).toMutableList()

        // Nếu handle đã tồn tại thì cập nhật lại thay vì tạo bản ghi trùng.
        val existingIndex = current.indexOfFirst {
            val h = it.handle.trim().removePrefix("@").lowercase()
            h == normIncoming && normIncoming.isNotBlank()
        }
        val account = if (existingIndex >= 0) {
            val old = current[existingIndex]
            old.copy(
                handle = cleanHandle,
                displayName = displayName.ifBlank { old.displayName },
                avatarUrl = avatarUrl.ifBlank { old.avatarUrl },
                status = TikTokAccountStatus.ACTIVE,
                variant = variant
            ).also { current[existingIndex] = it }
        } else {
            TikTokAccount(
                uid = "tt_" + UUID.randomUUID().toString().take(8),
                handle = cleanHandle,
                displayName = displayName,
                avatarUrl = avatarUrl,
                status = TikTokAccountStatus.ACTIVE,
                variant = variant
            ).also { current.add(it) }
        }

        // Tự động khử trùng lặp (Deduplicate) các tài khoản trùng handle trong database
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<TikTokAccount>()
        for (item in current) {
            val key = item.handle.trim().removePrefix("@").lowercase()
            if (key.isBlank() || seen.add(key)) {
                deduped.add(item)
            }
        }

        save(context, deduped)
        return account
    }

    fun updateAccount(context: Context, account: TikTokAccount) {
        val current = getAccounts(context).toMutableList()
        val idx = current.indexOfFirst { it.uid == account.uid }
        if (idx >= 0) current[idx] = account else current.add(account)
        save(context, current)
    }

    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        val current = getAccounts(context).map { if (it.uid == uid) it.copy(enabled = enabled) else it }
        save(context, current)
    }

    fun setSubName(context: Context, uid: String, subName: String) {
        val current = getAccounts(context).map { if (it.uid == uid) it.copy(subName = subName) else it }
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

    fun updateFullProfile(context: Context, uid: String, profile: com.cayxu.app.tiktok.checker.TikTokFullProfile?) {
        if (profile == null) return
        val current = getAccounts(context).toMutableList()
        val idx = current.indexOfFirst { it.uid == uid }
        if (idx >= 0) {
            val old = current[idx]
            current[idx] = old.copy(
                handle = if (profile.username.isNotBlank()) profile.username else old.handle,
                displayName = if (profile.nickname.isNotBlank()) profile.nickname else old.displayName,
                avatarUrl = if (!profile.avatarHdUrl.isNullOrBlank()) profile.avatarHdUrl else old.avatarUrl,
                followerCount = profile.followerCount,
                followingCount = profile.followingCount,
                heartCount = profile.totalFavorited,
                videoCount = profile.videoCount,
                bio = profile.biography.ifBlank { old.bio },
                isLive = profile.isLive,
                createdAt = if (profile.createTimestampSec > 0) profile.createTimestampSec * 1000L else old.createdAt,
                createDateFormatted = if (profile.formattedCreateDate.isNotBlank()) profile.formattedCreateDate else old.createDateFormatted
            )
            save(context, current)
        }
    }

    private fun save(context: Context, accounts: List<TikTokAccount>) {
        val raw = accounts.joinToString(ENTRY_SEP) { a ->
            listOf(
                a.uid,
                a.handle,
                a.displayName,
                a.subName,
                a.avatarUrl,
                a.createdAt.toString(),
                a.status.name,
                if (a.enabled) "1" else "0",
                a.taskCount.toString(),
                a.variant.name,
                a.followerCount.toString(),
                a.followingCount.toString(),
                a.heartCount.toString(),
                a.videoCount.toString(),
                a.bio,
                if (a.isLive) "1" else "0",
                a.createDateFormatted
            ).joinToString(FIELD_SEP)
        }
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).apply()
    }
}
