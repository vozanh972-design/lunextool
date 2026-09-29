package com.cayxu.app.ui.screens.golike

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Quản lý trạng thái phiên đăng nhập của người dùng Golike trong app.
 * Độc lập 100%, không phụ thuộc vào bất kỳ nền tảng nào khác.
 */
object GolikeSession {
    val isLoggedIn = mutableStateOf(false)
    val username = mutableStateOf("")
    val balance = mutableStateOf(0L)
    val token = mutableStateOf("")

    /** Khôi phục phiên làm việc đã lưu từ SharedPreferences */
    fun restore(context: Context) {
        if (GolikeAccountsStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            username.value = GolikeAccountsStore.getUsername(context)
            balance.value = GolikeAccountsStore.getBalance(context)
            token.value = GolikeAccountsStore.getToken(context).orEmpty()
        }
    }

    fun login(context: Context, userToken: String, userUsername: String, userBalance: Long) {
        GolikeAccountsStore.saveLogin(context, userToken, userUsername, userBalance)
        isLoggedIn.value = true
        username.value = userUsername
        balance.value = userBalance
        token.value = userToken
    }

    fun updateBalance(context: Context, newBalance: Long) {
        GolikeAccountsStore.updateBalance(context, newBalance)
        balance.value = newBalance
    }

    fun logout(context: Context) {
        GolikeAccountsStore.clearSession(context)
        isLoggedIn.value = false
        username.value = ""
        balance.value = 0L
        token.value = ""
    }
}
