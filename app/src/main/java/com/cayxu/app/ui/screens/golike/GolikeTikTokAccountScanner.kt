package com.cayxu.app.ui.screens.golike

import android.content.Context
import com.cayxu.app.data.local.TikTokAppVariant

/**
 * Quét và lưu trữ tài khoản TikTok độc lập cho module Golike.
 * Hoàn toàn tách biệt khỏi XSMM TikTok.
 */
object GolikeTikTokAccountScanner {

    /**
     * Lưu tài khoản TikTok quét được từ máy vào store riêng của Golike ("golike_accounts_pref").
     * TUYỆT ĐỐI KHÔNG ghi vào store của XSMM.
     */
    fun saveScannedAccount(
        context: Context,
        handle: String,
        displayName: String = "",
        variant: String = "STANDARD"
    ) {
        val clean = handle.trim().removePrefix("@")
        if (clean.isBlank()) return

        val currentList = GolikeAccountsStore.getAccounts(context, "tiktok")
        val existing = currentList.find { it.id.equals(clean, ignoreCase = true) }
        val gAcc = existing?.copy(
            username = displayName.ifBlank { existing.username.ifBlank { clean } }
        ) ?: GolikeAccount(
            id = clean,
            platform = "tiktok",
            username = displayName.ifBlank { clean },
            avatar = "",
            isLive = true,
            isGolikeLinked = false,
            lastStatus = "Cần liên kết Golike"
        )

        GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
    }

    /**
     * Bắt đầu quét tài khoản TikTok bằng màn nổi riêng của Golike.
     */
    fun startScan(context: Context, variant: TikTokAppVariant) {
        GolikeAccountsStore.setScanningTikTok(context, true)
        startGolikeVerifyTikTokAccount(context, variant)
    }
}
