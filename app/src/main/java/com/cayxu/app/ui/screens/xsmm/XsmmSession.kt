package com.cayxu.app.ui.screens.xsmm

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.cayxu.app.data.local.XsmmAccountStore

/** Trạng thái đăng nhập XSMM - dùng chung 1 nguồn để UI hiển thị nhất quán. */
object XsmmSession {
    val isLoggedIn = mutableStateOf(false)
    val username = mutableStateOf("")
    val points = mutableStateOf(0L)

    /** Gọi 1 lần lúc app khởi động để khôi phục phiên đã lưu. */
    fun restore(context: Context) {
        if (XsmmAccountStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            username.value = XsmmAccountStore.getUsername(context)
            points.value = XsmmAccountStore.getPoints(context)
        }
    }

    fun login(context: Context, token: String, userUsername: String, userPoints: Long) {
        XsmmAccountStore.saveLogin(context, token, userUsername, userPoints)
        isLoggedIn.value = true
        username.value = userUsername
        points.value = userPoints
    }

    fun logout(context: Context) {
        XsmmAccountStore.clear(context)
        isLoggedIn.value = false
        username.value = ""
        points.value = 0L
    }
}
