package com.cayxu.app.ui.screens.golike

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.cayxu.app.data.local.GolikeAccountStore

/**
 * Trạng thái đăng nhập Golike - dùng chung 1 nguồn duy nhất để card trạng thái hiển thị
 * NHẤT QUÁN ở mọi màn (GolikeScreen, GolikePlatformScreen, GolikeTikTokScreen...).
 *
 * Đã đăng nhập THẬT bằng token Bearer gọi GET https://gateway.golike.net/api/users/me
 * (xem GolikeAuthRepository) - không còn là giao diện giả nữa. restore() đọc lại phiên
 * đã lưu (GolikeAccountStore) lúc app khởi động, để mở app lại vẫn còn đăng nhập.
 */
object GolikeSession {
    val isLoggedIn = mutableStateOf(false)
    val name = mutableStateOf("")
    val email = mutableStateOf("")
    val coin = mutableStateOf("")

    /** Gọi 1 lần lúc app khởi động (CayXuApp.onCreate) để khôi phục phiên đã lưu. */
    fun restore(context: Context) {
        if (GolikeAccountStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            name.value = GolikeAccountStore.getName(context)
            email.value = GolikeAccountStore.getEmail(context)
            coin.value = GolikeAccountStore.getCoin(context)
        }
    }

    /** Gọi sau khi GolikeAuthRepository.fetchMe() trả về thành công. */
    fun login(context: Context, token: String, userName: String, userEmail: String, userCoin: String) {
        GolikeAccountStore.saveLogin(context, token, userName, userEmail, userCoin)
        isLoggedIn.value = true
        name.value = userName
        email.value = userEmail
        coin.value = userCoin
    }

    fun logout(context: Context) {
        GolikeAccountStore.clear(context)
        isLoggedIn.value = false
        name.value = ""
        email.value = ""
        coin.value = ""
    }
}
