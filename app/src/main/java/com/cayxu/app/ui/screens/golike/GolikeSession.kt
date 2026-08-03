package com.cayxu.app.ui.screens.golike

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.cayxu.app.data.local.GolikeAccountStore
import com.cayxu.app.data.repository.GolikeAuthRepository
import com.cayxu.app.data.repository.GolikeLoginResult
import com.cayxu.app.data.repository.GolikePlatformStat
import com.cayxu.app.data.repository.GolikeStatisticsReport
import com.cayxu.app.data.repository.GolikeStatisticsRepository
import com.cayxu.app.data.repository.GolikeStatisticsResult

/**
 * Trạng thái đăng nhập Golike - dùng chung 1 nguồn duy nhất để card trạng thái hiển thị
 * NHẤT QUÁN ở mọi màn (GolikeScreen, GolikePlatformScreen, GolikeTikTokScreen, Wallet,
 * Home...).
 *
 * Đã đăng nhập THẬT bằng token Bearer gọi GET https://gateway.golike.net/api/users/me
 * (xem GolikeAuthRepository) - không còn là giao diện giả nữa. restore() đọc lại phiên
 * đã lưu (GolikeAccountStore) lúc app khởi động, để mở app lại vẫn còn đăng nhập.
 *
 * "Thu nhập hôm nay" + thu nhập theo từng nền tảng (facebook/instagram/tiktok/...) lấy
 * THẬT từ GET /api/statistics/report (xem GolikeStatisticsRepository) - gọi ngay sau khi
 * đăng nhập thành công và mỗi lần refresh().
 */
object GolikeSession {
    val isLoggedIn = mutableStateOf(false)
    val name = mutableStateOf("")
    val handle = mutableStateOf("")
    val email = mutableStateOf("")
    val coin = mutableStateOf("")

    /** Tổng thu nhập hôm nay (tổng pending_coin của mọi nền tảng) - số THẬT từ statistics/report. */
    val todayIncome = mutableStateOf(0L)

    /** Thu nhập hôm nay theo TỪNG nền tảng (facebook/instagram/tiktok/...) - số THẬT, dùng
     *  để vẽ biểu đồ/danh sách phân bổ ở Wallet & Home. */
    val platformStats = mutableStateOf<List<GolikePlatformStat>>(emptyList())

    /** Gọi 1 lần lúc app khởi động (CayXuApp.onCreate) để khôi phục phiên đã lưu. */
    fun restore(context: Context) {
        if (GolikeAccountStore.isLoggedIn(context)) {
            isLoggedIn.value = true
            name.value = GolikeAccountStore.getName(context)
            handle.value = GolikeAccountStore.getHandle(context)
            email.value = GolikeAccountStore.getEmail(context)
            coin.value = GolikeAccountStore.getCoin(context)
            todayIncome.value = GolikeAccountStore.getTodayIncome(context)
            platformStats.value = deserializePlatformStats(GolikeAccountStore.getPlatformStatsSerialized(context))
        }
    }

    /** Gọi sau khi GolikeAuthRepository.fetchMe() trả về thành công (màn Đăng nhập). */
    fun login(
        context: Context,
        token: String,
        userName: String,
        userHandle: String,
        userEmail: String,
        userCoin: String
    ) {
        GolikeAccountStore.saveLogin(context, token, userName, userHandle, userEmail, userCoin)
        isLoggedIn.value = true
        name.value = userName
        handle.value = userHandle
        email.value = userEmail
        coin.value = userCoin
    }

    /** Gọi sau khi GolikeStatisticsRepository.fetchReport() trả về thành công. */
    fun updateStatistics(context: Context, report: GolikeStatisticsReport) {
        todayIncome.value = report.totalPendingToday
        platformStats.value = report.platformStats
        GolikeAccountStore.saveStatistics(context, report.totalPendingToday, serializePlatformStats(report.platformStats))
    }

    /**
     * Làm mới lại số dư/thu nhập bằng CHÍNH token đã lưu (không cần dán lại token) - dùng
     * cho nút refresh trên card tài khoản Golike. Làm mới CẢ /users/me (tên/số dư) và
     * /statistics/report (thu nhập hôm nay theo nền tảng). Trả về true nếu làm mới /users/me
     * thành công (thống kê lỗi thì bỏ qua, không ảnh hưởng kết quả trả về).
     */
    suspend fun refresh(context: Context): Boolean {
        val token = GolikeAccountStore.getToken(context) ?: return false

        val meOk = when (val result = GolikeAuthRepository.fetchMe(token)) {
            is GolikeLoginResult.Success -> {
                login(
                    context = context,
                    token = token,
                    userName = result.info.name,
                    userHandle = result.info.handle,
                    userEmail = result.info.email,
                    userCoin = result.info.coin
                )
                true
            }
            is GolikeLoginResult.Error -> false
        }

        when (val statsResult = GolikeStatisticsRepository.fetchReport(token)) {
            is GolikeStatisticsResult.Success -> updateStatistics(context, statsResult.report)
            is GolikeStatisticsResult.Error -> Unit // giữ số liệu cũ, không báo lỗi ở đây
        }

        return meOk
    }

    fun logout(context: Context) {
        GolikeAccountStore.clear(context)
        isLoggedIn.value = false
        name.value = ""
        handle.value = ""
        email.value = ""
        coin.value = ""
        todayIncome.value = 0L
        platformStats.value = emptyList()
    }

    private fun serializePlatformStats(stats: List<GolikePlatformStat>): String =
        stats.joinToString("|") { "${it.platform}:${it.pendingCoin}:${it.holdCoin}" }

    private fun deserializePlatformStats(raw: String): List<GolikePlatformStat> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val pending = parts[1].toLongOrNull() ?: return@mapNotNull null
            val hold = parts[2].toLongOrNull() ?: return@mapNotNull null
            GolikePlatformStat(parts[0], pending, hold)
        }
    }
}
