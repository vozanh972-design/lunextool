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
    val signingKey = mutableStateOf("")
    val userId = mutableStateOf("")
    val webData = mutableStateOf("")

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
            signingKey.value = GolikeAccountsStore.getSigningKey(context)
            userId.value = GolikeAccountsStore.getUserId(context)
            webData.value = GolikeAccountsStore.getWebData(context)
        } else {
            username.value = ""
            balance.value = 0L
            token.value = ""
            tToken.value = ""
            deviceId.value = ""
            gAuth.value = ""
            signingKey.value = ""
            userId.value = ""
            webData.value = ""
        }
    }

    fun login(
        context: Context,
        userToken: String,
        userUsername: String,
        userBalance: Long,
        tToken: String = "",
        deviceId: String = "",
        gAuth: String = "",
        signingKey: String = "",
        userId: String = "",
        webData: String = ""
    ) {
        GolikeAccountsStore.saveLogin(
            context = context,
            token = userToken,
            username = userUsername,
            balance = userBalance,
            tToken = tToken,
            deviceId = deviceId,
            gAuth = gAuth,
            signingKey = signingKey,
            userId = userId,
            webData = webData
        )
        isLoggedIn.value = true
        username.value = userUsername
        balance.value = userBalance
        token.value = userToken
        this.tToken.value = tToken
        this.deviceId.value = deviceId
        this.gAuth.value = gAuth
        this.signingKey.value = signingKey
        this.userId.value = userId
        this.webData.value = webData
    }

    fun updateBalance(context: Context, newBalance: Long) {
        GolikeAccountsStore.updateBalance(context, newBalance)
        balance.value = newBalance
    }

    fun logout(context: Context) {
        // 1. Dọn sạch phiên web cũ: Cookie và Storage của WebView
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {}

        // 2. Dọn sạch dữ liệu tài khoản cũ trong App:
        GolikeAccountsStore.clearSession(context)
        isLoggedIn.value = false
        username.value = ""
        balance.value = 0L
        token.value = ""
        tToken.value = ""
        deviceId.value = ""
        gAuth.value = ""
        signingKey.value = ""
        userId.value = ""
        webData.value = ""
    }
}
