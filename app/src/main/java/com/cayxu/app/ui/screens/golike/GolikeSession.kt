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
    val tToken = mutableStateOf("")
    val deviceId = mutableStateOf("")
    val gAuth = mutableStateOf("")

    /** Khôi phục phiên làm việc đã lưu từ SharedPreferences */
    fun restore(context: Context) {
        val savedToken = GolikeAccountsStore.getToken(context).orEmpty()
        val logged = !savedToken.isBlank()
        isLoggedIn.value = logged
        if (logged) {
            username.value = GolikeAccountsStore.getUsername(context)
            balance.value = GolikeAccountsStore.getBalance(context)
            token.value = savedToken
            tToken.value = GolikeAccountsStore.getTToken(context)
            deviceId.value = GolikeAccountsStore.getDeviceId(context)
            gAuth.value = GolikeAccountsStore.getGAuth(context)
        } else {
            username.value = ""
            balance.value = 0L
            token.value = ""
            tToken.value = ""
            deviceId.value = ""
            gAuth.value = ""
        }
    }

    fun login(
        context: Context,
        userToken: String,
        userUsername: String,
        userBalance: Long,
        tTokenStr: String = "",
        deviceIdStr: String = "",
        gAuthStr: String = ""
    ) {
        GolikeAccountsStore.saveLogin(
            context = context,
            token = userToken,
            username = userUsername,
            balance = userBalance,
            tToken = tTokenStr,
            deviceId = deviceIdStr,
            gAuth = gAuthStr
        )
        isLoggedIn.value = true
        username.value = userUsername
        balance.value = userBalance
        token.value = userToken
        tToken.value = tTokenStr
        deviceId.value = deviceIdStr
        gAuth.value = gAuthStr
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
        tToken.value = ""
        deviceId.value = ""
        gAuth.value = ""
    }
}
