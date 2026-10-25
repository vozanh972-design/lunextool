package com.cayxu.app.ui.f4f

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Trạng thái phiên đăng nhập Follow4Follow (F4F) độc lập.
 */
object F4fSession {
    val isLoggedIn = mutableStateOf(false)
    val username = mutableStateOf("")
    val points = mutableStateOf(0L)

    fun restore(context: Context) {
        if (F4fAccountStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            username.value = F4fAccountStore.getUsername(context)
            points.value = F4fAccountStore.getPoints(context)
        }
    }

    fun login(context: Context, token: String, userUsername: String, userPoints: Long) {
        F4fAccountStore.saveLogin(context, token, userUsername, userPoints)
        isLoggedIn.value = true
        username.value = userUsername
        points.value = userPoints
    }

    fun logout(context: Context) {
        F4fAccountStore.clear(context)
        isLoggedIn.value = false
        username.value = ""
        points.value = 0L
    }
}
