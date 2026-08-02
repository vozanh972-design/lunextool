package com.cayxu.app.ui.screens.golike

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.cayxu.app.data.local.GolikeAccountStore
import com.cayxu.app.data.repository.GolikeAuthRepository
import com.cayxu.app.data.repository.GolikeLoginResult

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
    val handle = mutableStateOf("")
    val email = mutableStateOf("")
    val coin = mutableStateOf("")
    val tasksToday = mutableStateOf("0")
    val rewardToday = mutableStateOf("0")

    /** Gọi 1 lần lúc app khởi động (CayXuApp.onCreate) để khôi phục phiên đã lưu. */
    fun restore(context: Context) {
        if (GolikeAccountStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            name.value = GolikeAccountStore.getName(context)
            handle.value = GolikeAccountStore.getHandle(context)
            email.value = GolikeAccountStore.getEmail(context)
            coin.value = GolikeAccountStore.getCoin(context)
            tasksToday.value = GolikeAccountStore.getTasksToday(context)
            rewardToday.value = GolikeAccountStore.getRewardToday(context)
        }
    }

    /** Gọi sau khi GolikeAuthRepository.fetchMe() trả về thành công (màn Đăng nhập). */
    fun login(
        context: Context,
        token: String,
        userName: String,
        userHandle: String,
        userEmail: String,
        userCoin: String,
        userTasksToday: String,
        userRewardToday: String
    ) {
        GolikeAccountStore.saveLogin(context, token, userName, userHandle, userEmail, userCoin, userTasksToday, userRewardToday)
        isLoggedIn.value = true
        name.value = userName
        handle.value = userHandle
        email.value = userEmail
        coin.value = userCoin
        tasksToday.value = userTasksToday
        rewardToday.value = userRewardToday
    }

    /**
     * Làm mới lại số dư/thống kê bằng CHÍNH token đã lưu (không cần dán lại token) - dùng
     * cho nút refresh trên card tài khoản Golike. Trả về true nếu làm mới thành công.
     */
    suspend fun refresh(context: Context): Boolean {
        val token = GolikeAccountStore.getToken(context) ?: return false
        return when (val result = GolikeAuthRepository.fetchMe(token)) {
            is GolikeLoginResult.Success -> {
                login(
                    context = context,
                    token = token,
                    userName = result.info.name,
                    userHandle = result.info.handle,
                    userEmail = result.info.email,
                    userCoin = result.info.coin,
                    userTasksToday = result.info.tasksToday,
                    userRewardToday = result.info.rewardToday
                )
                true
            }
            is GolikeLoginResult.Error -> false
        }
    }

    fun logout(context: Context) {
        GolikeAccountStore.clear(context)
        isLoggedIn.value = false
        name.value = ""
        handle.value = ""
        email.value = ""
        coin.value = ""
        tasksToday.value = "0"
        rewardToday.value = "0"
    }
}
